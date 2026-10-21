/*
 * Software to build and maintain SCOPe, https://scop.berkeley.edu/
 *
 * Copyright (C) 2012-2021 The Regents of the University of California
 *
 * For feedback, mailto:scope@compbio.berkeley.edu
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * Version 2.1 of the License, or (at your option) any later version.
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301
 * USA
 */
package gov.lbl.scop.app;

import java.sql.*;
import java.io.*;
import java.util.*;
import java.text.*;
import org.strbio.io.*;
import org.strbio.local.*;
import org.strbio.math.*;
import org.strbio.mol.*;
import org.strbio.util.*;
import org.strbio.IO;
import gov.lbl.scop.local.*;
import gov.lbl.scop.util.*;

/**
   Makes chain sequences for all RAF entries that don't already have
   them.

   Note:  this works only for post-1.65 chains, since it doesn't
   look at the SCOP version (and make a source=3 chain if appropriate).
   pre-1.65 chains are created all at once using MakeChainSeq2.
*/
public class MakeChainSeq {
    final public static synchronized void makeChainSeq(int rafID) throws Exception {
        Statement stmt = LocalSQL.createStatement();

        ResultSet rs = stmt.executeQuery("select e.code, c.chain, raf_get_body(r.id), r.last_release_id, c.id, re.id from raf r, pdb_chain c, pdb_entry e, pdb_release re where r.id="+rafID+" and r.pdb_chain_id=c.id and c.pdb_release_id=re.id and re.pdb_entry_id=e.id");
        if (!rs.next()) {
            rs.close();
            stmt.close();
            return;
        }
	
        String code = rs.getString(1);
        char chain = rs.getString(2).charAt(0);
        String body = rs.getString(3);
        int lastReleaseID = rs.getInt(4);
        int pdbChainID = rs.getInt(5);
        int pdbReleaseID = rs.getInt(6);
        rs.close();

        String sid = code;
        if (chain==' ')
            sid+='_';
        else
            sid+=chain;

        System.out.println("making chain seqs for "+sid+" "+rafID);
        // System.out.println("raf body = '"+body+"'");
        System.out.flush();

        boolean queueDownstream = false;
        int downstreamSeqID = 0;
        int downstreamChainID = 0;
        int pfamReleaseID = 0;
        int scopReleaseID = 0;
        LocalSQL.setAutoCommit(false);
        try {
        // make seq types 1, 2, 4 automatically
        for (int seqType = 1; seqType<=4; seqType*=2) {
            RAF.SequenceFragment sf = null;
            if (seqType < 4)
                sf = RAF.wholeChainSeq(body,seqType);
            else {
                // boundaries of non-tag sequence
                int start = 0;
                int end = RAF.getSeqLength(body)-1;
                rs = stmt.executeQuery("select tag_start, tag_end from pdb_chain_tag where pdb_chain_id="+pdbChainID);
                while (rs.next()) {
                    int tagStart = rs.getInt(1);
                    int tagEnd = rs.getInt(2);
                    if (tagStart == 0)
                        start = tagEnd+1;
                    else
                        end = tagStart-1;
                }
                rs.close();
                sf = RAF.partialChainSeq(body,2,start,end);
            }
            String seq = sf.getSequence();
            int isReject = 0;
            if (sf.isReject()) isReject = 1;

            int seqID = MakeDomainSeq.lookupOrCreateSeq(seq);

            rs = stmt.executeQuery("select id, sid, seq_id from astral_chain where raf_id="+
                                   rafID+" and source_id="+seqType);
            int astralChainID = 0;
            int oldSeqID = 0;
            String oldSID = null;
            if (rs.next()) {
                astralChainID = rs.getInt(1);
                oldSID = rs.getString(2);
                oldSeqID = rs.getInt(3);
                if (rs.next()) {
                    rs.close();
                    throw new Exception("multiple astral_chain rows for RAF " + rafID +
                                        " source " + seqType);
                }
            }
            rs.close();

            boolean changed = (astralChainID == 0) || (oldSeqID != seqID) ||
                (!sid.equals(oldSID));
            if (astralChainID == 0) {
                PreparedStatement insertChain = LocalSQL.prepareStatement(
                    "insert into astral_chain (raf_id,source_id,sid,seq_id) values (?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
                insertChain.setInt(1,rafID);
                insertChain.setInt(2,seqType);
                insertChain.setString(3,sid);
                insertChain.setInt(4,seqID);
                insertChain.executeUpdate();
                rs = insertChain.getGeneratedKeys();
                rs.next();
                astralChainID = rs.getInt(1);
                rs.close();
                insertChain.close();
            }
            else if (changed) {
                PreparedStatement updateChain = LocalSQL.prepareStatement(
                    "update astral_chain set sid=?, seq_id=? where id=?");
                updateChain.setString(1,sid);
                updateChain.setInt(2,seqID);
                updateChain.setInt(3,astralChainID);
                updateChain.executeUpdate();
                updateChain.close();
            }

            // find tags after making SEQRES chain seq
            if ((seqType==2) && changed)
                FindTags.findTagsInChain(astralChainID);

            // start ASTEROIDS jobs for new RAF entries
            if ((seqType==2) && changed && (lastReleaseID==0)) {
                rs = stmt.executeQuery("select pfam_release_id, scop_release_id from asteroid where domain_id is not null order by id desc limit 1");
                if (rs.next()) {
                    pfamReleaseID = rs.getInt(1);
                    scopReleaseID = rs.getInt(2);
                    queueDownstream = true;
                    downstreamSeqID = seqID;
                    downstreamChainID = astralChainID;
                }
                rs.close();
            }
        }
        LocalSQL.commit();
        }
        catch (Exception e) {
            LocalSQL.rollback();
            throw e;
        }
        finally {
            LocalSQL.setAutoCommit(true);
        }

        // Queue jobs only after the three chain rows and tag changes commit;
        // LocalSQL.newJob uses LOCK TABLES, which would implicitly commit.
        if (queueDownstream) {
            LocalSQL.newJob(11,downstreamSeqID,pfamReleaseID+"",stmt);
            LocalSQL.newJob(20,downstreamSeqID,"2 1 3 "+scopReleaseID,stmt);
            LocalSQL.newJob(16,downstreamChainID,
                            pfamReleaseID+" "+scopReleaseID,stmt);
        }

        // This is deliberately safe when the new schema has not yet been
        // installed.  Once installed, the other prerequisite branch (SPACI)
        // may queue the same idempotent finalizer again.
        StableChainFinalizer.queueIfInstalled(pdbReleaseID);
        stmt.close();
    }

    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();

            BufferedReader infile = null;

            int scopReleaseID = LocalSQL.getLatestSCOPRelease(false);
            
            ResultSet rs = stmt.executeQuery("select id from raf where last_release_id="+scopReleaseID+" and id not in (select raf_id from astral_chain where source_id=4)");
            while (rs.next()) {
                int rafID = rs.getInt(1);

                if (rafID==0)
                    continue;

                makeChainSeq(rafID);
            }
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
