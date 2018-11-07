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

import gov.lbl.scop.local.LocalSQL;
import gov.lbl.scop.util.ASTEROIDS;
import gov.lbl.scop.util.RAF;
import gov.lbl.scop.util.annotation.BlastAnnotator;
import gov.lbl.scop.util.annotation.ChainAnnotator;
import gov.lbl.scop.util.annotation.ExactSequenceMatchChainAnnotator;
import org.strbio.util.StringUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Vector;

/**
   Make ASTEROIDS, including running all jobs
*/
public class MakeASTEROIDS {
    /**
       waits for any jobs of type 11, 12, and 20 that are in
       the queue already.
    */
    final public static void makeASTEROIDS(int astralChainID, int pfamReleaseID)
        throws Exception {
        Statement stmt = LocalSQL.createStatement();
	
        ResultSet rs = stmt.executeQuery("select seq_id from astral_chain where id=" + astralChainID);
        if (!rs.next()) {
            rs.close();
            stmt.close();
            return;
        }
        int seqID = rs.getInt(1);
        rs.close();
	
        // WaitForJobs.waitFor(11,seqID,0);
        // WaitForJobs.waitFor(12,seqID,0);
        WaitForJobs.waitFor(20, seqID,0);
	
        rs = stmt.executeQuery("select max(id) from scop_release where is_public=1");
        rs.next();
        int scopReleaseID = rs.getInt(1);
        rs.close();
        stmt.close();
	
        makeASTEROIDS(astralChainID, pfamReleaseID, scopReleaseID);
    }


    /**
     * Delete all entries from asteroid table for the specified chain, pfam release, and scop release
     * If scop release is public, also delete astral_domain
     *
     * @param astralChainID
     * @param pfamReleaseID
     * @param scopReleaseID
     */
    public static void cleanDB(int astralChainID, int pfamReleaseID, int scopReleaseID) throws SQLException {
        Statement stmt = LocalSQL.createStatement();

        // Look up the public releases
        ResultSet rs = stmt.executeQuery("select id from scop_release where is_public=1 order by id desc limit 1");
        rs.next();
        int scopPublicReleaseID = rs.getInt(1);
        rs.close();

        // slow due to mysql optimization bug!
        // stmt.executeUpdate("delete from astral_domain where style_id=4 and id in (select domain_id from astral_chain_link_domain where chain_id="+astralChainID+")");
        if (scopReleaseID == scopPublicReleaseID)
            stmt.executeUpdate("delete a from astral_domain a join astral_chain_link_domain b on (a.id=b.domain_id) where a.style_id=4 and b.chain_id=" + astralChainID);
        stmt.executeUpdate("delete from asteroid where chain_id=" + astralChainID + " and scop_release_id=" + scopReleaseID + " and pfam_release_id=" + pfamReleaseID);

        stmt.close();
    }

    /**
       Add all annotations to the asteroid table for the specified chain.
       If the scopReleaseID is the latest scop release ID and the latest scop release ID is public,
       add a promotion job

       TODO: add check if the any of the new domains added to ASTEROIDS are actually promotable

       @param astralChainID       the ID of the astral_chain which to be added to ASTEROIDS
       @param pfamReleaseID the ID of the Pfam release used for annotating the chain
       @param scopReleaseID the ID of the SCOP release used for annotating the chain
       @throws Exception
    */
    final public static void makeASTEROIDS(int astralChainID, int pfamReleaseID, int scopReleaseID)
        throws Exception {
        cleanDB(astralChainID, pfamReleaseID, scopReleaseID);
        makeOriginalASTEROIDS(astralChainID, pfamReleaseID, scopReleaseID);
        Statement stmt = LocalSQL.createStatement();
        ExactSequenceMatchChainAnnotator exactSequenceMatchChainAnnotator = new ExactSequenceMatchChainAnnotator(stmt, pfamReleaseID, scopReleaseID);
        exactSequenceMatchChainAnnotator.annotateChainAndWriteToDB(astralChainID);
        BlastAnnotator blastAnnotator = new BlastAnnotator(stmt, pfamReleaseID, scopReleaseID);
        blastAnnotator.annotateChainAndWriteToDB(astralChainID);
        stmt.close();

        int publicSCOPReleaseID = LocalSQL.getLatestSCOPRelease(true);
        int latestSCOPReleaseID = LocalSQL.getLatestSCOPRelease(false);
        // don't promote during public releases:
        if ((scopReleaseID==publicSCOPReleaseID) &&
            (publicSCOPReleaseID==latestSCOPReleaseID))
            ChainAnnotator.addPromotionJob(astralChainID, pfamReleaseID, scopReleaseID, 127);
    }


    /**
       Attempt to determine ASTEROID domains the chain from the
       astral_chain table.

       The SCOP and Pfam releases are used in the annotation.  When
       testing, older versions of SCOP and Pfam are used.  When in
       production, the latest release of SCOP should be used.

       depends on having jobs type 11, 12, and 14 run on the same
       astralChainID already.

       If scopReleaseID is public, if annotation source is BLAST, and
       scopReleaseID==scopLastPublicReleaseID, a promoteASTEROIDS job
       will be added to the job queue

       @param astralChainID the ID of the astral_chain to be added to ASTEROIDS
       @param pfamReleaseID the ID of the Pfam release used for annotating the chain
       @param scopReleaseID the ID of the SCOP release used for annotating the chain
       @throws Exception
    */
    final public static void makeOriginalASTEROIDS(int astralChainID, int pfamReleaseID, int scopReleaseID)
        throws Exception {
        Statement stmt = LocalSQL.createStatement();

        // Look up the public releases
        ResultSet rs = stmt.executeQuery("select id from scop_release where is_public=1 order by id desc limit 2");
        rs.next();
        int scopPublicReleaseID = rs.getInt(1);
        rs.next();
        int scopLastPublicReleaseID = rs.getInt(1);
        rs.close();

        rs = stmt.executeQuery("select id from scop_node where level_id=1 and release_id=" + scopReleaseID);
        rs.next();
        int rootNodeID = rs.getInt(1);
        rs.close();

        ASTEROIDS.AnnotationSet as =
            new ASTEROIDS.AnnotationSet(astralChainID);
        ASTEROIDS.AnnotationSet as2 =
            new ASTEROIDS.AnnotationSet(astralChainID);

        as2.loadAnnotations(stmt,
                            scopReleaseID,
                            pfamReleaseID);
        for (ASTEROIDS.Annotation a : as2.annotations)
            as.annotate(a, 10);
        as.fillGaps(50);

        as.load(stmt);
        as.addUnmatched(20);
        as.assignSids();

        boolean hasPromotable = false;

        for (ASTEROIDS.Annotation a : as.annotations) {
            String seq = a.getSequence(as.seq);
            String description = a.getHeaderRegions(as.rafLine);
            String header = a.getHeaderFull(as.seq);
            RAF.SequenceFragment sf = new RAF.SequenceFragment(seq);
            // System.out.println("ASTEROID "+a.sid+" "+a.getHeaderFull(as.seq));
            int domainID = 0;
            if (a.source == ASTEROIDS.Annotation.Source.BLAST)
                hasPromotable = true;

            int seqID = MakeDomainSeq.lookupOrCreateSeq(seq);

            // make ASTRAL domain of type "asteroid"
            if (scopReleaseID == scopPublicReleaseID) {
                // System.out.println("making ASTRAL domain");
                stmt.executeUpdate("delete from astral_domain where node_id=" +
                                   rootNodeID + " and source_id=2 and style_id=4 and sid=\"" +
                                   a.sid + "\"");
                stmt.executeUpdate("insert into astral_domain values (null, " +
                                   rootNodeID + ", 2, 4, \"" +
                                   a.sid + "\", \"" +
                                   header + "\", " +
                                   seqID + ")",
                                   Statement.RETURN_GENERATED_KEYS);
                rs = stmt.getGeneratedKeys();
                rs.next();
                domainID = rs.getInt(1);
                rs.close();
                stmt.executeUpdate("insert into astral_chain_link_domain values (" + astralChainID + ", " + domainID + ")");
            }

            stmt.executeUpdate("insert into asteroid values (null, " +
                               astralChainID + ", " +
                               pfamReleaseID + ", " +
                               scopReleaseID + ", " +
                               (domainID == 0 ? "null" : domainID) + ", \"" +
                               a.sid + "\", \"" +
                               header + "\", " +
                               (description == null ? "null, " : "\"" + description + "\", ") +
                               (a.source == ASTEROIDS.Annotation.Source.BLAST ? a.sourceID : "null") + ", " +
                               seqID + ")");
        }

        // if appropriate, try to promote asteroids from this chain
        if ((scopReleaseID == scopPublicReleaseID) &&
            (hasPromotable)) {
            LocalSQL.newJob(21,
                            astralChainID,
                            pfamReleaseID + " " + scopReleaseID + " 127",
                            stmt);
        }
        stmt.close();
    }
    
    
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
	    
            // makeASTEROIDS(1456096, 56, 13);
            // makeASTEROIDS(944080, 56, 13);
            // System.exit(0);

            // debug a single chain
            if (argv[0].startsWith("C")) {
                int astralChainID = StringUtil.atoi(argv[0],1);
                makeASTEROIDS(astralChainID, 56, 15);
                System.exit(0);
            }
	    
            if (argv.length != 4) {
                throw new Exception("Usage exception.  Need: pfam-version scop-version date-start date-end.\nExample: 26.0 1.75B 000000 000000");
            }

            int pfamReleaseID = LocalSQL.lookupPfamRelease(argv[0]);
            if (pfamReleaseID == 0)
                throw new Exception("Can't determine Pfam version from " + argv[0]);
	    
            int scopReleaseID = LocalSQL.lookupSCOPRelease(argv[1]);
            if (scopReleaseID == 0)
                throw new Exception("Can't determine SCOP version from " + argv[1]);
	    
            ResultSet rs = stmt.executeQuery("select max(id) from scop_release where is_public=1");
            rs.next();
            int scopPublicReleaseID = rs.getInt(1);
            rs.close();
	    
            // for 1.75A/B:  make using BLAST only
            /*
              rs = stmt.executeQuery("select distinct(chain_id) from asteroid where scop_release_id=11 and blast_hit_id is null");
              while (rs.next()) {
              LocalSQL.newJob(16,
              rs.getInt(1),
              "52");
              }
              System.exit(0);
            */
	    
            if (scopReleaseID < scopPublicReleaseID) {
                // just do all asteroids for that release,
                // assuming BLAST is already done
                Vector<Integer> ids = DumpSeqs.getNewSeqs(scopReleaseID,
                                                          scopReleaseID + 1,
                                                          false, // unique seqs
                                                          false, // ignore obs
                                                          0); // manually curated
		
                for (Integer i : ids) {
                    LocalSQL.newJob(16,
                                    i.intValue(),
                                    pfamReleaseID + " " + scopReleaseID,
                                    stmt);
                }
		
                System.exit(0);
            }
	    
            // this makes ASTEROIDS for current release:
            java.util.Date start = null;
            if (!argv[2].equals("000000"))
                start = ParsePDB.snapshotDateFormat.parse(argv[2]);
            java.util.Date end = ParsePDB.snapshotDateFormat.parse(argv[3]);
	    
            Vector<Integer> ids = DumpSeqs.getNewSeqs(start,
                                                      end,
                                                      false, // unique
                                                      true, // ignoreObs
                                                      true, // ignoreReject
                                                      2, // seqRes 
                                                      false, // ignoreClassified
                                                      true); // ignoreReliable
	    
            // HashSet<Integer> done11 = new HashSet<Integer>();
            // HashSet<Integer> done12 = new HashSet<Integer>();
            HashSet<Integer> done20 = new HashSet<Integer>();
            rs = stmt.executeQuery("select job_type_id, target_id from job where job_type_id in (11, 12, 20) and n_failures < 9 and args=\"2 1 3 "+scopReleaseID+"\"");
            while (rs.next()) {
                int jobType = rs.getInt(1);
                int target = rs.getInt(2);
                /*
                  if (jobType==11)
                  done11.add(new Integer(target));
                  else if (jobType==12)
                  done12.add(new Integer(target));
                */
                if (jobType == 20)
                    done20.add(new Integer(target));
            }

            rs = stmt.executeQuery("select job_type_id, target_id from job_done where job_type_id in (11, 12, 20) and n_failures < 9 and args=\"2 1 3 "+scopReleaseID+"\"");
            while (rs.next()) {
                int jobType = rs.getInt(1);
                int target = rs.getInt(2);
                if (jobType == 20)
                    done20.add(new Integer(target));
            }
	    
            for (Integer i : ids) {
                // get sequence id
                rs = stmt.executeQuery("select seq_id from astral_chain where id=" + i);
                rs.next();
                Integer seqID = new Integer(rs.getInt(1));

                // don't re-run these if already run
                /*
                  if (!done11.contains(seqID)) {
                  LocalSQL.newJob(11,
                  seqID.intValue(),
                  pfamReleaseID+"",
                  stmt);
                  done11.add(seqID);
                  }
		
                  if (!done12.contains(seqID)) {
                  LocalSQL.newJob(12,
                  seqID.intValue(),
                  scopReleaseID+"",
                  stmt);
                  done12.add(seqID);
                  }
                */
                if (!done20.contains(seqID)) {
                    LocalSQL.newJob(20,
                                    seqID.intValue(),
                                    "2 1 3 " + scopReleaseID,
                                    stmt);
                    done20.add(seqID);
                }
            }
            for (Integer i : ids)
                LocalSQL.newJob(16,
                                i.intValue(),
                                pfamReleaseID + " " + scopReleaseID,
                                stmt);
        }
        catch (Exception e) {
            System.out.println("Exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
