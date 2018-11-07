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
import org.strbio.io.*;
import org.strbio.math.*;
import org.strbio.mol.*;
import org.strbio.util.*;
import org.strbio.IO;
import gov.lbl.scop.local.*;

/**
   Sets up jobs to calculate HMMER results (vs Pfam) for new
   astral chains, vs a version of Pfam.
*/
public class HMMERSeqs {
    /**
       set up and run HMMER, for either an ASTEROIDS or Pfam
       search database.  Does not close stmt.  releaseID refers
       to the Pfam release if positive and the SCOP release (for
       ASTEROIDS) if negative.
    */
    final public static void hmmerSeq(Statement stmt,
                                      File baseDir,
                                      String dbName,
                                      int seqID,
                                      int releaseID,
                                      String seq)  throws Exception {

        // write sequence to input file
        Polymer p = new Polymer(seq);
        p.name = ""+seqID;

        // figure out HMMER version
        String hmmerRelease = null;
        if (releaseID < -10)
            hmmerRelease = "3.0";
        else if (releaseID < 0)
            hmmerRelease = "2.3.2";
        else {
            ResultSet rs = stmt.executeQuery("select hr.version from hmmer_release hr, pfam_release pr where pr.id="+releaseID+" and pr.hmmer_release_id=hr.id");
            if (rs.next())
                hmmerRelease = rs.getString(1);
            else
                hmmerRelease = "2.3.2"; // assume compatible with older release
            rs.close();
        }

        // set up hmmer
        HMMER h = new HMMER(hmmerRelease);
        h.baseDir = baseDir;
        File outFile = null;
        int nInputs = 2;
        if ((hmmerRelease == null) ||
            (hmmerRelease.startsWith("3.")))
            nInputs+=2;
        if (releaseID > 0)
            nInputs++;
        h.inputs = new String[nInputs];
        int i = 0;
        if ((hmmerRelease == null) ||
            (hmmerRelease.startsWith("3."))) {
            h.inputs[i++] = "--acc";
            h.inputs[i++] = "--notextw";
        }
        if (releaseID > 0)
            h.inputs[i++] = "--cut_tc";
	
        h.inputs[i++] = dbName;
        outFile = h.process(p, i);

        h.processOutput(stmt,
                        outFile,
                        seqID,
                        releaseID,
                        10);
    }

    /**
       Run an ASTRAL seq against pfam or asteroids HMM library.
       releaseID refers to the Pfam release if positive and the SCOP
       release (for ASTEROIDS) if negative.
    */
    final public static void hmmerSeq(int seqID,
                                      int releaseID) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        String releaseName = null;
        if (releaseID > 0) {
            ResultSet rs = stmt.executeQuery("select version from pfam_release where id="+releaseID);
            if (rs.next())
                releaseName = rs.getString(1);
            rs.close();
        }
        else if (releaseID < 0) {
            ResultSet rs = stmt.executeQuery("select version from scop_release where id="+(0-releaseID));
            if (rs.next())
                releaseName = rs.getString(1);
            rs.close();
        }
        if (releaseName==null) {
            stmt.close();
            return;
        }

        ResultSet rs = stmt.executeQuery("select seq from astral_seq where id="+seqID);
        if (!rs.next()) {
            rs.close();
            stmt.close();
            return;
        }
        String seq = rs.getString(1);
        rs.close();

        if ((seq==null) || (seq.length()==0)) {
            stmt.close();
            return;
        }

        System.out.println("HMMERing seq "+seqID);

        String localPfamDB = null;

        if (releaseID > 0) {
            // slow due to mysql bug
            // stmt.executeUpdate("delete from astral_seq_hmm_pfam where seq_id="+seqID+" and pfam_id in (select id from pfam where release_id="+releaseID+")");
            stmt.executeUpdate("delete a from astral_seq_hmm_pfam a join pfam p on (a.pfam_id=p.id) where a.seq_id="+seqID+" and p.release_id="+releaseID);
            rs = stmt.executeQuery("select pfama_global_hmm_file from pfam_local where release_id="+releaseID);
            if (rs.next())
                localPfamDB = rs.getString(1);
            else
                throw new Exception("can't find global hmm file for Pfam id "+releaseID);
            rs.close();
        }
        else {
            // slow due to mysql bug
            // stmt.executeUpdate("delete from astral_seq_hmm_asteroids where seq_id="+seqID+" and node_id in (select id from scop_node where release_id="+(0-releaseID)+" and (level_id=4 or level_id=5))");
            stmt.executeUpdate("delete a from astral_seq_hmm_asteroids a join scop_node n on (a.node_id=n.id) where a.seq_id="+seqID+" and n.release_id="+(0-releaseID)+" and (n.level_id=4 or n.level_id=5)");
        }

        if (releaseID > 0) {
            File baseDir = new File("/lab/proj/astral/hmmer/Pfam-"+releaseName);
            if (!baseDir.isDirectory())
                baseDir.mkdir();
	    
            if (!baseDir.canRead())
                throw new Exception("hmmer pfam db not ready: "+baseDir);
	    
            hmmerSeq(stmt,
                     baseDir,
                     localPfamDB,
                     seqID,
                     releaseID,
                     seq);
        }
        else {
            File baseDir = new File("/lab/proj/astral/hmmer/ASTEROIDS-"+releaseName+"-sf");
            if (!baseDir.isDirectory())
                baseDir.mkdir();
	    
            if (!baseDir.canRead())
                throw new Exception("ASTEROIDS sf db not ready");

            hmmerSeq(stmt,
                     baseDir,
                     "astral-"+releaseName+"-hmm-sf.db",
                     seqID,
                     releaseID,
                     seq);

            baseDir = new File("/lab/proj/astral/hmmer/ASTEROIDS-"+releaseName+"-fam");
            if (!baseDir.isDirectory())
                baseDir.mkdir();
	    
            if (!baseDir.canRead())
                throw new Exception("ASTEROIDS fam db not ready");

            hmmerSeq(stmt,
                     baseDir,
                     "astral-"+releaseName+"-hmm-fam.db",
                     seqID,
                     releaseID,
                     seq);
        }
	
        stmt.close();
    }

    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            ResultSet rs;

            // hmmerSeq(83958,55);  // good for testing multiple hits
            // hmmerSeq(122697,45); // Pfam 16 failed for some reason
            // System.exit(0);

            java.util.Date start=null;
            java.util.Date end=null;
            int pfamReleaseID = 0;
            int scopReleaseID = 0;
            boolean onHold = false;
            boolean oldStr = false;
            boolean uniprot = false;

            // figure out what release we're parsing
            scopReleaseID = LocalSQL.lookupSCOPRelease(argv[0]);
            pfamReleaseID = LocalSQL.lookupPfamRelease(argv[1]);

            if (argv.length > 2) {
                // check for hold keyword
                if (argv[2].equals("onhold"))
                    onHold = true;
                else if (argv[2].equals("structure"))
                    oldStr = true;
                else if (argv[2].equals("uniprot"))
                    uniprot = true;
                else {
                    // new chain sequences between dates
                    // must be one or 2 dates, in 6-digit fmt
                    if (!argv[2].equals("000000"))
                        start = ParsePDB.snapshotDateFormat.parse(argv[2]);
                    end = ParsePDB.snapshotDateFormat.parse(argv[3]);
                }
            }

            if (scopReleaseID==0)
                throw new Exception("Can't determine SCOP version from "+argv[0]);
            if (pfamReleaseID==0)
                throw new Exception("Can't determine Pfam version from "+argv[1]);

            Vector<Integer> ids;
            if (onHold)
                ids = DumpSeqs.getOnHoldSeqs();
            else if (oldStr)
                ids = DumpSeqs.getStructureSeqs();
            else if (uniprot)
                ids = DumpSeqs.getUniprotSeqs();
            else if (end == null)
                ids = DumpSeqs.getSeqs(scopReleaseID,
                                       true, // chain
                                       1, // include rejects
                                       1, // include ntc
                                       false, // no sort
                                       2, // seqres
                                       2, // gd+os
                                       true);  // unique
            else
                ids = DumpSeqs.getNewSeqs(start,
                                          end,
                                          true, // unique
                                          true, // ignoreObs
                                          true, // ignoreReject
                                          2, // seqRes 
                                          false, // ignoreClassified
                                          false); // ignoreReliable
            for (Integer i : ids) {
                LocalSQL.newJob(11,
                                i.intValue(),
                                pfamReleaseID+"",
                                stmt);
                /*
                  LocalSQL.newJob(12,
                  i.intValue(),
                  scopReleaseID+"",
                  stmt);
                */
            }
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
