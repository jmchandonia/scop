/*
 * Software to build and maintain SCOPe, https://scop.berkeley.edu/
 *
 * Copyright (C) 2012-2018 The Regents of the University of California
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
    final public static void makeChainSeq(int rafID) throws Exception {
        Statement stmt = LocalSQL.createStatement();

        stmt.executeUpdate("delete from astral_chain where raf_id="+rafID);
	
        ResultSet rs = stmt.executeQuery("select e.code, c.chain, raf_get_body(r.id), r.last_release_id, c.id from raf r, pdb_chain c, pdb_entry e, pdb_release re where r.id="+rafID+" and r.pdb_chain_id=c.id and c.pdb_release_id=re.id and re.pdb_entry_id=e.id");
        if (!rs.next())
            return;
	
        String code = rs.getString(1);
        char chain = rs.getString(2).charAt(0);
        String body = rs.getString(3);
        int lastReleaseID = rs.getInt(4);
        int pdbChainID = rs.getInt(5);
        rs.close();

        String sid = code;
        if (chain==' ')
            sid+='_';
        else
            sid+=chain;

        System.out.println("making chain seqs for "+sid+" "+rafID);
        // System.out.println("raf body = '"+body+"'");
        System.out.flush();

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

            stmt.executeUpdate("insert into astral_chain values (null,"+rafID+","+
                               seqType+", \""+
                               StringUtil.replace(sid,"\"","\\\"")+
                               "\", "+
                               seqID+")",
                               Statement.RETURN_GENERATED_KEYS);

            rs = stmt.getGeneratedKeys();
            rs.next();
            int astralChainID = rs.getInt(1);
            rs.close();

            // find tags after making SEQRES chain seq
            if (seqType==2)
                FindTags.findTagsInChain(astralChainID);

            // start ASTEROIDS jobs for new RAF entries
            if ((seqType==2) && (lastReleaseID==0)) {
                rs = stmt.executeQuery("select pfam_release_id, scop_release_id from asteroid where domain_id is not null order by id desc limit 1");
                if (rs.next()) {
                    int pfamReleaseID = rs.getInt(1);
                    int scopReleaseID = rs.getInt(2);
                    rs.close();
		    
                    LocalSQL.newJob(11,
                                    seqID,
                                    pfamReleaseID+"",
                                    stmt);

                    /*
                      LocalSQL.newJob(12,
                      astralChainID,
                      scopReleaseID,
                      stmt);
                    */
		
                    LocalSQL.newJob(20,
                                    seqID,
                                    "2 1 3 "+scopReleaseID,
                                    stmt);

                    LocalSQL.newJob(16,
                                    astralChainID,
                                    pfamReleaseID+" "+scopReleaseID,
                                    stmt);
                }
            }
        }
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
