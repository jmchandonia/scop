/*
 * Software to build and maintain SCOPe, https://scop.berkeley.edu/
 *
 * Copyright (C) 2008-2018 The Regents of the University of California
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

import gov.lbl.scop.local.Blast;
import gov.lbl.scop.local.LocalSQL;
import org.strbio.io.*;
import org.strbio.util.*;
import org.strbio.mol.Polymer;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Vector;

/**
   Sets up jobs to calculate BLAST results (all vs all) for a given
   version of SCOP.
*/
public class BlastSeqs {
    /**
       set up and run BLAST, for either a chain or domain sequence.
       Does not close stmt
    */
    final public static void blastSeq(Statement stmt,
                                      File baseDir,
                                      String dbName,
                                      String seq,
                                      int seqID,
                                      int sourceID,
                                      int styleID1,
                                      int styleID2,
                                      int scopReleaseID) throws Exception {
        // write sequence to input file
        Polymer p = new Polymer(seq);
        p.name = ""+seqID;

        // set up blast
        Blast bl;
        int minGapLength = 0;

        if ((styleID1==1) && (styleID2!=1)) {
            bl = new Blast();
            bl.baseDir = baseDir;
            bl.inputs = new String[19];
            bl.inputs[0] = "-d";
            bl.inputs[1] = baseDir.getPath()+"/"+dbName;
            bl.inputs[2] = "-i";
            bl.inputs[3] = null;
            bl.inputs[4] = "-j";
            bl.inputs[5] = "1";
            bl.inputs[6] = "-h";
            bl.inputs[7] = "1e-2";
            bl.inputs[8] = "-v";
            bl.inputs[9] = "5000";
            bl.inputs[10] = "-b";
            bl.inputs[11] = "5000";
            bl.inputs[12] = "-a2";
            bl.inputs[13] = "-F";
            bl.inputs[14] = "T";
            bl.inputs[15] = "-J";
            bl.inputs[16] = "T";
            bl.inputs[17] = "-O";
            bl.inputs[18] = null;

            minGapLength = 10;
        }
        else {
            bl = new Blast("2.2.18");
            bl.baseDir = baseDir;
            bl.inputs = new String[19];
            bl.inputs[0] = "-d";
            bl.inputs[1] = baseDir.getPath()+"/"+dbName;
            bl.inputs[2] = "-i";
            bl.inputs[3] = null;
            bl.inputs[4] = "-j";
            bl.inputs[5] = "1";
            bl.inputs[6] = "-h";
            bl.inputs[7] = "1e-2";
            bl.inputs[8] = "-v";
            bl.inputs[9] = "5000";
            bl.inputs[10] = "-b";
            bl.inputs[11] = "5000";
            bl.inputs[12] = "-a2";
            bl.inputs[13] = "-z";
            bl.inputs[14] = "100000000";
            bl.inputs[15] = "-J";
            bl.inputs[16] = "T";
            bl.inputs[17] = "-O";
            bl.inputs[18] = null;
        }

        File[] files = bl.process(p,dbName,3,18);
	    
        bl.processOutput(stmt,
                         files[0],
                         files[1],
                         seqID,
                         sourceID,
                         styleID1,
                         styleID2,
                         scopReleaseID,
                         minGapLength);
    }

    /**
       Blast a seq against all others of a given type
       in a given SCOP version.  Use 0 for null SCOP version.
    */
    final public static void blastSeq(int seqID,
                                      int sourceID,
                                      int styleID1,
                                      int styleID2,
                                      int scopReleaseID) throws Exception {
        Statement stmt = LocalSQL.createStatement();
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
	
        String scopRelease = "update";
        if (scopReleaseID > 0) {
            rs = stmt.executeQuery("select version from scop_release where id="+scopReleaseID);
            if (!rs.next()) {
                rs.close();
                stmt.close();
                return;
            }
            scopRelease = rs.getString(1);
            rs.close();
        }
	
        String seqType = ((styleID2 == 1) ? "chain" : "domain");
        if ((styleID1==1) && (styleID2!=1))
            seqType = "chain_domain";
        String sourceType = ((sourceID == 1) ? "atom" : "seqres");
        if (sourceID==4)
            sourceType = "seqres-tags";

        System.out.println("Blasting seq "+seqID);

        stmt.executeUpdate("delete from astral_seq_blast where seq1_id="+seqID+" and source_id="+sourceID+" and style1_id="+styleID1+" and style2_id="+styleID2+" and release_id "+(scopReleaseID==0 ? "is null" : "="+scopReleaseID));

        File baseDir = new File("/lab/proj/astral/blast/"+scopRelease+"/"+seqType+"/"+sourceType);
        if (!baseDir.canRead())
            throw new Exception("blast db not ready");

        LocalSQL.setAutoCommit(false);

        if (styleID2 != 2) {
            blastSeq(stmt,
                     baseDir,
                     "seqs.fa",
                     seq,
                     seqID,
                     sourceID,
                     styleID1,
                     styleID2,
                     scopReleaseID);

            blastSeq(stmt,
                     baseDir,
                     "reject.fa",
                     seq,
                     seqID,
                     sourceID,
                     styleID1,
                     styleID2,
                     scopReleaseID);
        }
	
        if (styleID2==2)
            blastSeq(stmt,
                     baseDir,
                     "os.fa",
                     seq,
                     seqID,
                     sourceID,
                     styleID1,
                     styleID2,
                     scopReleaseID);

        if (styleID2==3)
            blastSeq(stmt,
                     baseDir,
                     "ntc.fa",
                     seq,
                     seqID,
                     sourceID,
                     styleID1,
                     styleID2,
                     scopReleaseID);

        try {
            LocalSQL.commit();
        }
        catch (SQLException e) {
            LocalSQL.rollback();
            throw(e);
        }
	
        LocalSQL.setAutoCommit(true);
	
        stmt.close();
    }

    /**
       Setup blast databases for a given version of SCOP
    */
    final public static void setupDB(String scopRelease,
                                     boolean isChain,
                                     boolean isDBChain) throws Exception {
        int scopReleaseID = LocalSQL.lookupSCOPRelease(scopRelease);
        int maxSourceID = 2;
        if ((scopReleaseID >= 17) && (isDBChain))
            maxSourceID = 4;
        
        for (int sourceID=1; sourceID<=maxSourceID; sourceID*=2) {
            String sourceType = ((sourceID == 1) ? "atom" : "seqres");
            if (sourceID==4)
                sourceType = "seqres-tags";

            File baseDir = null;

            if (isChain) {
                if (isDBChain)
                    baseDir = new File("/lab/proj/astral/blast/"+scopRelease+"/chain/"+sourceType);
                else
                    baseDir = new File("/lab/proj/astral/blast/"+scopRelease+"/chain_domain/"+sourceType);
            }
            else {
                if (isDBChain)
                    throw new Exception("no code for blasting domains vs chain db");
                else
                    baseDir = new File("/lab/proj/astral/blast/"+scopRelease+"/domain/"+sourceType);
            }
            baseDir.mkdirs();
            Vector<Integer> ids = DumpSeqs.getSeqs(scopReleaseID,
                                                   isDBChain,
                                                   0, // exclude rejects
                                                   0, // exclude ntc
                                                   false, // no sort
                                                   sourceID,
                                                   1, // gds+single
                                                   true); // unique seqs
	    
            String seqFile = baseDir.getPath()+"/seqs.fa";

            PrintfWriter outfile = new PrintfWriter(seqFile);
            DumpSeqs.writeFasta(outfile,ids,0,false);
            outfile.close();

            Blast bl;
            if (isChain && !isDBChain)
                bl = new Blast();
            else
                bl = new Blast("2.2.18");

            bl.baseDir = baseDir;

            bl.formatDB(seqFile);

            ids = DumpSeqs.getSeqs(scopReleaseID,
                                   isDBChain,
                                   2, // only rejects 
                                   1, // include ntc
                                   false, // no sort
                                   sourceID,
                                   2, // gd + os
                                   true); // unique seqs
	    
            seqFile = baseDir.getPath()+"/reject.fa";

            outfile = new PrintfWriter(seqFile);
            DumpSeqs.writeFasta(outfile,ids,0,false);
            outfile.close();

            if (isChain && !isDBChain)
                bl = new Blast();
            else
                bl = new Blast("2.2.18");

            bl.baseDir = baseDir;

            bl.formatDB(seqFile);

            if (!isDBChain) {
                ids = DumpSeqs.getSeqs(scopReleaseID,
                                       isDBChain,
                                       0, // exclude rejects
                                       2, // ntc only 
                                       false, // no sort
                                       sourceID,
                                       2, // gd + os
                                       true);
	    
                seqFile = baseDir.getPath()+"/ntc.fa";

                outfile = new PrintfWriter(seqFile);
                DumpSeqs.writeFasta(outfile,ids,0,false);
                outfile.close();

                if (isChain && !isDBChain)
                    bl = new Blast();
                else
                    bl = new Blast("2.2.18");

                bl.baseDir = baseDir;
		
                bl.formatDB(seqFile);

                ids = DumpSeqs.getSeqs(scopReleaseID,
                                       isDBChain,
                                       0, // exclude rejects
                                       0, // exclude ntc
                                       false, // no sort
                                       sourceID,
                                       0, // os+single
                                       true);
	    
                seqFile = baseDir.getPath()+"/os.fa";

                outfile = new PrintfWriter(seqFile);
                DumpSeqs.writeFasta(outfile,ids,0,false);
                outfile.close();

                if (isChain && !isDBChain)
                    bl = new Blast();
                else
                    bl = new Blast("2.2.18");

                bl.baseDir = baseDir;
		
                bl.formatDB(seqFile);
            }
        }
    }

    
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            Statement stmt2 = LocalSQL.createStatement();
            ResultSet rs;

            // 90954, 2 is an interesting test case because it triggers
            // a bug in which the same identifier is used for a different
            // sequence in the search db.
            // the bug appears to have no effect other than printing
            // a warning, though.
            // blastChainDomain(90954, 2);
            // blastChainDomain(91772, 1);
            // blastChainDomain(62162, 1);
            // blastDomain(525913);
            // blastChain(177347, 11);
            // blastChainDomain(1449414,11);
            // blastChain(2035202,12);

            /*
              blastSeq(16,1,1,1,12);
              rs = stmt.executeQuery("select count(*) from astral_seq_blast where seq1_id=16 and seq2_id=16 and source_id=1 and style1_id=1 and style2_id=1");
              rs.next();
              System.out.println(rs.getInt(1));
              blastSeq(16,1,2,2,12);
              rs = stmt.executeQuery("select count(*) from astral_seq_blast where seq1_id=16 and seq2_id=16 and source_id=1 and style1_id=2 and style2_id=2");
              rs.next();
              System.out.println(rs.getInt(1));
              blastSeq(16,1,3,3,12);
              rs = stmt.executeQuery("select count(*) from astral_seq_blast where seq1_id=16 and seq2_id=16 and source_id=1 and style1_id=3 and style2_id=3");
              rs.next();
              System.out.println(rs.getInt(1));
              blastSeq(16,2,1,1,12);
              rs = stmt.executeQuery("select count(*) from astral_seq_blast where seq1_id=16 and seq2_id=16 and source_id=2 and style1_id=1 and style2_id=1");
              rs.next();
              System.out.println(rs.getInt(1));
              blastSeq(16,2,2,2,12);
              rs = stmt.executeQuery("select count(*) from astral_seq_blast where seq1_id=16 and seq2_id=16 and source_id=2 and style1_id=2 and style2_id=2");
              rs.next();
              System.out.println(rs.getInt(1));
              blastSeq(16,2,3,3,12);
              rs = stmt.executeQuery("select count(*) from astral_seq_blast where seq1_id=16 and seq2_id=16 and source_id=2 and style1_id=3 and style2_id=3");
              rs.next();
              System.out.println(rs.getInt(1));
              System.exit(0);
            */

            // manual, for debugging or checking a specific sequence
            if (argv.length==5) {
                blastSeq(StringUtil.atoi(argv[0]),
                         StringUtil.atoi(argv[1]),
                         StringUtil.atoi(argv[2]),
                         StringUtil.atoi(argv[3]),
                         StringUtil.atoi(argv[4]));
                System.exit(0);
            }

            // figure out what release we're parsing
            int scopReleaseID = LocalSQL.lookupSCOPRelease(argv[0]);
            if (scopReleaseID==0)
                throw new Exception("Can't determine SCOP version from "+argv[0]);

            boolean isChain = false;
            boolean isDBChain = false;
            if (argv[1].equals("chain")) {
                isChain = true;
                isDBChain = true;
            }
            else if ((argv[1].equals("chain_domain")) ||
                     (argv[1].equals("asteroids")))
                isChain = true;
	    
            java.util.Date start=null;
            java.util.Date end=null;
            if (argv.length > 2) {
                // new chain sequences between dates
                // must be one or 2 dates, in 6-digit fmt
                if (!argv[2].equals("000000"))
                    start = ParsePDB.snapshotDateFormat.parse(argv[2]);
                end = ParsePDB.snapshotDateFormat.parse(argv[3]);
            }

            setupDB(argv[0],isChain,isDBChain);

            if ((end == null) &&
                (!argv[1].equals("asteroids"))) {
                // this is the usual case of blasting
                // all chains and/or domains in a release
                int maxSourceID = 2;
                if ((scopReleaseID >= 17) && (isDBChain))
                    maxSourceID = 4;
        
                for (int sourceID=1; sourceID<=maxSourceID; sourceID*=2) {
                    Vector<Integer> ids;
                    int stStart = 2;
                    int stEnd = 3;
                    if (isChain)
                        stStart = stEnd = 1;
                    for (int styleID=stStart; styleID<=stEnd; styleID++) {
                        int gdStyle = 2;
                        if (styleID==2)
                            gdStyle = 0;
                        else if (styleID==3)
                            gdStyle = 1;
                        ids = DumpSeqs.getSeqs(scopReleaseID,
                                               isChain,
                                               1, // include rejects
                                               1, // include ntc
                                               false, // no sort
                                               sourceID,
                                               gdStyle,
                                               true); // unique seqs
                        for (Integer i : ids) {
                            LocalSQL.newJob(20,
                                            i.intValue(),
                                            sourceID+" "+styleID+" "+styleID+" "+scopReleaseID,
                                            stmt);
                        }
                    }
                }
            }
            else {
                // this is for date ranges for asteroids
                Vector<Integer> ids = null;

                if (end != null)
                    ids = DumpSeqs.getNewSeqs(start,
                                              end,
                                              true, // uniqueSeqs
                                              true, // ignoreObs
                                              true, // ignoreReject
                                              2, // seqres
                                              false, // ignoreClassified
                                              false); // ignoreReliable
                else
                    ids = DumpSeqs.getNewSeqs(scopReleaseID,
                                              scopReleaseID+1,
                                              true, // uniqueSeqs
                                              true, // ignoreObs
                                              0); // manual curation
		
                for (Integer i : ids) {
                    LocalSQL.newJob(20,
                                    i.intValue(),
                                    "2 1 3 "+scopReleaseID,
                                    stmt);
                }
            }
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
