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

import java.sql.*;
import java.io.*;
import java.util.*;
import java.text.*;
import java.util.regex.*;
import org.strbio.util.StringUtil;
import gov.lbl.scop.local.*;
import gov.lbl.scop.util.*;

/**
   Check for:

   1) missing history

   for domains:
   
   2) missing raf
   3) missing chain seq
   4) missing/null domain seq
   5) missing pdb-style file
   6) missing/incomplete thumbnail or thumbnail file
   7) duplicate automated hierarchy nodes
   8) missing hierarchy metadata
   9) missing SPACI
   10) missing legacy or API search index entries
   11) missing rep, or missing/stale rep thumbnails, for levels above
*/
public class CheckPromoted {
    /**
       Does a query return at least one row?
    */
    final private static boolean hasRow(Statement stmt,
                                        String query) throws Exception {
        ResultSet rs = stmt.executeQuery(query);
        boolean rv = rs.next();
        rs.close();
        return rv;
    }

    /**
       A completed domain thumbnail run creates all eight links below, and
       every referenced file must still exist and be non-empty.
    */
    final private static boolean validThumbnails(Statement stmt,
                                                 int nodeID) throws Exception {
        ResultSet rs = stmt.executeQuery("select main, domain_small, chain_small, structure_small, domain_large, chain_large, structure_large, domain_tiny from scop_node_thumbnail where node_id="+nodeID);
        if (!rs.next()) {
            rs.close();
            return false;
        }

        HashSet<Integer> thumbnailIDs = new HashSet<Integer>();
        for (int i=1; i<=8; i++) {
            int thumbnailID = rs.getInt(i);
            if (rs.wasNull() || (thumbnailID==0)) {
                rs.close();
                return false;
            }
            thumbnailIDs.add(new Integer(thumbnailID));
        }
        rs.close();

        for (Integer thumbnailID : thumbnailIDs) {
            rs = stmt.executeQuery("select image_path from thumbnail where id="+thumbnailID.intValue());
            if (!rs.next()) {
                rs.close();
                return false;
            }
            String path = rs.getString(1);
            rs.close();
            if (path==null)
                return false;
            File f = new File(path);
            if ((!f.isFile()) || (f.length()==0))
                return false;
        }
        return true;
    }

    /**
       Check the two search indexes written at the end of promotion.  Do not
       re-index an existing node: MakeIndex.indexNode is insert-only, and an
       unnecessary API re-index deletes and rebuilds otherwise valid rows.
    */
    final private static void checkIndexes(Statement stmt,
                                           int nodeID,
                                           int releaseID,
                                           int sunid,
                                           String label,
                                           boolean fix) throws Exception {
        if (!hasRow(stmt,"select node_id from scop_index where node_id="+nodeID)) {
            System.out.println("missing search index for "+label);
            if (fix) {
                MakeIndex.indexNode(nodeID);
                if (hasRow(stmt,"select node_id from scop_index where node_id="+nodeID))
                    System.out.println("  fixed");
                else
                    System.out.println("  not fixed");
            }
        }

        if (sunid<=0) {
            System.out.println("missing positive sunid for API search index on "+label+"; can't fix safely");
            return;
        }

        boolean apiTarget = hasRow(stmt,"select release_id from scop_api_search_target where release_id="+releaseID+" and target_kind=\"node\" and target_public_id=\""+sunid+"\"");
        boolean apiToken = hasRow(stmt,"select release_id from scop_api_search_token where release_id="+releaseID+" and target_kind=\"node\" and target_public_id=\""+sunid+"\" limit 1");
        if ((!apiTarget) || (!apiToken)) {
            System.out.println("missing API search index for "+label);
            if (fix) {
                MakeAPISearchIndex.indexNode(nodeID);
                apiTarget = hasRow(stmt,"select release_id from scop_api_search_target where release_id="+releaseID+" and target_kind=\"node\" and target_public_id=\""+sunid+"\"");
                apiToken = hasRow(stmt,"select release_id from scop_api_search_token where release_id="+releaseID+" and target_kind=\"node\" and target_public_id=\""+sunid+"\" limit 1");
                if (apiTarget && apiToken)
                    System.out.println("  fixed");
                else
                    System.out.println("  not fixed");
            }
        }
    }

    /**
       Promotion freezes SPACI for the PDB entry after making thumbnails.
       Entries whose code starts with 0 are intentionally skipped by
       FreezeSPACI.addSPACI.
    */
    final private static void checkSPACI(Statement stmt,
                                         int nodeID,
                                         int releaseID,
                                         String sid,
                                         boolean fix) throws Exception {
        ResultSet rs = stmt.executeQuery("select distinct e.id, e.code from pdb_entry e, pdb_release r, pdb_chain c, link_pdb l where l.node_id="+nodeID+" and l.pdb_chain_id=c.id and c.pdb_release_id=r.id and r.pdb_entry_id=e.id limit 1");
        if (!rs.next()) {
            rs.close();
            return;
        }
        int pdbEntryID = rs.getInt(1);
        String code = rs.getString(2);
        rs.close();
        if ((code==null) || code.startsWith("0"))
            return;

        if (!hasRow(stmt,"select id from aerospaci where pdb_entry_id="+pdbEntryID+" and release_id="+releaseID)) {
            System.out.println("missing SPACI for "+sid);
            if (fix) {
                FreezeSPACI.addSPACI(pdbEntryID,releaseID,true);
                if (hasRow(stmt,"select id from aerospaci where pdb_entry_id="+pdbEntryID+" and release_id="+releaseID))
                    System.out.println("  fixed");
                else
                    System.out.println("  not fixed");
            }
        }
    }

    /**
       MakeAutoComments legitimately creates no row for many domains.  Only
       call it when the linked PDB metadata contains one of the conditions
       that makes a generated comment mandatory.
    */
    final private static boolean needsAutoComment(Statement stmt,
                                                   int nodeID) throws Exception {
        if (hasRow(stmt,"select h.id from link_pdb l, pdb_chain c, pdb_release_heterogen rh, pdb_heterogen h where l.node_id="+nodeID+" and l.pdb_chain_id=c.id and c.pdb_release_id=rh.pdb_release_id and rh.pdb_heterogen_id=h.id and lower(h.description)!=\"hoh\" limit 1"))
            return true;
        if (hasRow(stmt,"select p.id from link_pdb l, pdb_chain_compound pc, pdb_compound p where l.node_id="+nodeID+" and l.pdb_chain_id=pc.pdb_chain_id and pc.pdb_compound_id=p.id and (lower(p.description) like \"%mutant%\" or lower(p.description) like \"%mutation%\") limit 1"))
            return true;
        return hasRow(stmt,"select h.pdb_release_id from link_pdb l, pdb_chain c, pdb_headers h where l.node_id="+nodeID+" and l.pdb_chain_id=c.id and c.pdb_release_id=h.pdb_release_id and ((lower(concat_ws(\" \",h.title,h.class,h.keywords)) like \"%complex%\" and (binary concat_ws(\" \",h.title,h.class,h.keywords) like \"%DNA%\" or binary concat_ws(\" \",h.title,h.class,h.keywords) like \"%RNA%\")) or lower(h.title) like \"%mutant%\" or lower(h.title) like \"%mutation%\") limit 1");
    }

    /**
       Recover the hit comment only when the retained ASTEROID rows identify
       exactly one hit for this node.  Returning null is preferable to
       inventing an ambiguous provenance comment.
    */
    final private static String recoverAutomatedMatchComment(Statement stmt,
                                                              int nodeID,
                                                              int releaseID) throws Exception {
        HashSet<String> hitSids = new HashSet<String>();
        ResultSet rs = stmt.executeQuery("select distinct a.header, a.blast_hit_id from asteroid a, astral_chain ac, raf r, link_pdb l, scop_node n where n.id="+nodeID+" and a.scop_release_id="+releaseID+" and a.description=n.description and a.chain_id=ac.id and ac.raf_id=r.id and r.pdb_chain_id=l.pdb_chain_id and l.node_id=n.id");
        while (rs.next()) {
            String header = rs.getString(1);
            int hitID = rs.getInt(2);
            String hitSid = null;
            if ((header!=null) &&
                header.contains(ASTEROIDS.Annotation.sourceToString(ASTEROIDS.Annotation.Source.SCOPSEQMATCH))) {
                hitSid = ASTEROIDS.Annotation.getSidFromHeaderString(header);
            }
            else if ((hitID>0) && (!rs.wasNull())) {
                Statement stmt2 = LocalSQL.createStatement();
                ResultSet rs2 = stmt2.executeQuery("select n.sid from astral_seq_blast b, astral_domain d, scop_node n, astral_seq s where n.id=d.node_id and d.seq_id=s.id and s.id=b.seq2_id and b.id="+hitID+" and b.source_id=d.source_id and b.style1_id=1 and (b.style2_id=d.style_id or d.style_id=1) and b.release_id=n.release_id");
                if (rs2.next())
                    hitSid = rs2.getString(1);
                rs2.close();
                stmt2.close();
            }
            if (hitSid!=null)
                hitSids.add(hitSid);
        }
        rs.close();
        if (hitSids.size()!=1)
            return null;
        return "automated match to "+hitSids.iterator().next();
    }

    /**
       The parent thumbnail row must be an exact copy of the representative
       domain views, as produced by MakeThumbnails.copyRepThumbnails.
    */
    final private static boolean currentRepresentativeThumbnails(Statement stmt,
                                                                  int repNodeID,
                                                                  int levelNodeID) throws Exception {
        return hasRow(stmt,"select p.node_id from scop_node_thumbnail r, scop_node_thumbnail p where r.node_id="+repNodeID+" and p.node_id="+levelNodeID+" and p.domain_tiny=r.domain_tiny and p.domain_small=r.domain_small and p.chain_small=r.domain_small and p.structure_small=r.domain_small and p.domain_large=r.domain_large and p.chain_large=r.domain_large and p.structure_large=r.domain_large and p.main=r.domain_tiny");
    }

    /**
       Fill a missing representative row only when a candidate descendant was
       observed, and refresh parent thumbnails only when they are absent or no
       longer match the recorded representative.
    */
    final private static void checkRepresentative(Statement stmt,
                                                  int levelNodeID,
                                                  int candidateNodeID,
                                                  boolean fix) throws Exception {
        int repNodeID = 0;
        ResultSet rs = stmt.executeQuery("select rep_node_id from scop_subset_level where level_node_id="+levelNodeID);
        if (rs.next())
            repNodeID = rs.getInt(1);
        rs.close();

        if (repNodeID==0) {
            System.out.println("missing rep for level node "+levelNodeID);
            if (fix) {
                stmt.executeUpdate("insert into scop_subset_level values ("+candidateNodeID+", "+levelNodeID+")");
                repNodeID = candidateNodeID;
                System.out.println("  fixed");
            }
        }

        if ((repNodeID!=0) &&
            (!currentRepresentativeThumbnails(stmt,repNodeID,levelNodeID))) {
            System.out.println("missing/stale representative thumbnails for level node "+levelNodeID);
            if (fix) {
                if (validThumbnails(stmt,repNodeID)) {
                    MakeThumbnails.copyRepThumbnails(repNodeID,levelNodeID);
                    if (currentRepresentativeThumbnails(stmt,repNodeID,levelNodeID))
                        System.out.println("  fixed");
                    else
                        System.out.println("  not fixed");
                }
                else {
                    System.out.println("  not fixed; representative node "+repNodeID+" has incomplete thumbnails");
                }
            }
        }
    }

    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            Statement stmt2 = LocalSQL.createStatement();

            ResultSet rs, rs2;

            int scopReleaseID = LocalSQL.lookupSCOPRelease(argv[0]);
            if (scopReleaseID==0)
                throw new Exception("Can't determine SCOP version from "+argv[0]);

            boolean fix = false;
            if ((argv.length > 1) &&
                (argv[1].equals("fix")))
                fix = true;

            // get max sunid from stable release
            rs = stmt.executeQuery("select min(n.sunid) from scop_node n, scop_history h where n.id=h.old_node_id and n.release_id="+scopReleaseID+" and n.release_id=h.release_id and h.change_type_id=12");
            rs.next();
            int maxStableSunid = rs.getInt(1)-1;
            rs.close();
            if (maxStableSunid == -1) {
                // all are stable
                rs = stmt.executeQuery("select max(sunid) from scop_node where release_id="+scopReleaseID);
                rs.next();
                maxStableSunid = rs.getInt(1);
                rs.close();
            }

            // get all chains covered in a release
            rs = stmt.executeQuery("select id, level_id, sunid, sid from scop_node where curation_type_id>2 and release_id="+scopReleaseID+" and (level_id=8 or sunid>"+maxStableSunid+")");
            while (rs.next()) {
                int nodeID = rs.getInt(1);
                int levelID = rs.getInt(2);
                int sunid = rs.getInt(3);
                String sid = rs.getString(4);

                if (sunid > maxStableSunid) {
                    rs2 = stmt2.executeQuery("select id from scop_history where old_node_id="+nodeID+" and release_id="+scopReleaseID+" and change_type_id=12");
                    if (!rs2.next()) {
                        System.out.println("missing history for "+(levelID==8 ? sid : sunid));
                        rs2.close();
                        if (fix) {
                            String historyTime = null;
                            rs2 = stmt2.executeQuery("select h.time_occurred from scop_history h, scop_node n where n.sunid>="+(sunid-5)+" and n.sunid<="+(sunid+5)+" and n.id=h.old_node_id and h.change_type_id=12 and h.release_id=n.release_id and n.release_id="+scopReleaseID+" limit 1");
                            if (rs2.next())
                                historyTime = rs2.getString(1);
                            rs2.close();
                            if (historyTime!=null) {
                                stmt2.executeUpdate("insert into scop_history values (null, "+nodeID+", null, "+scopReleaseID+", 12, \""+historyTime+"\")");
                                System.out.println("  fixed");
                            }
                        }
                    }
                    else
                        rs2.close();
                }

                if (levelID < 8)
                    continue;

                int pdbChainID = 0;
                rs2 = stmt2.executeQuery("select pdb_chain_id from link_pdb where node_id="+nodeID);
                if (rs2.next()) {
                    pdbChainID = rs2.getInt(1);
                }
                else {
                    System.out.println("missing chain link for "+sid);
                    rs2.close();
                    boolean fixed = false;
                    if (fix) {
                        MakeLinks.linkPDB(nodeID);
                        rs2 = stmt2.executeQuery("select pdb_chain_id from link_pdb where node_id="+nodeID);
                        if (rs2.next()) {
                            pdbChainID = rs2.getInt(1);
                            fixed = true;
                            System.out.println("  fixed");
                        }
                        rs2.close();
                    }
                    if (!fixed)
                        continue;
                }
                rs2.close();
		
                int rafID = 0;
                rs2 = stmt2.executeQuery("select id from raf where first_release_id<="+scopReleaseID+" and last_release_id>="+scopReleaseID+" and pdb_chain_id="+pdbChainID);
                if (rs2.next()) {
                    rafID = rs2.getInt(1);
                }
                else {
                    System.out.println("missing raf for "+sid);
                    // stmt2.executeUpdate("delete from link_pdb where node_id="+nodeID);
                    boolean fixed = false;
                    if (fix) {
                        rafID = FreezeRAF.addRAF(pdbChainID, scopReleaseID);
                        if (rafID > 0)
                            fixed = true;
                    }
                    if (!fixed)
                        continue;
                }
                rs2.close();

                rs2 = stmt2.executeQuery("select id from astral_chain where raf_id="+rafID);
                if (!rs2.next()) {
                    System.out.println("missing chain seq for "+sid);
                    rs2.close();
                    boolean fixed = false;
                    if (fix) {
                        MakeChainSeq.makeChainSeq(rafID);
                        fixed = hasRow(stmt2,"select id from astral_chain where raf_id="+rafID);
                        if (fixed)
                            System.out.println("  fixed");
                    }
                    if (!fixed)
                        continue;
                }
                rs2.close();

                rs2 = stmt2.executeQuery("select id, seq_id from astral_domain where node_id="+nodeID+" and seq_id != 43558");
                if (!rs2.next()) {
                    System.out.println("missing/null domain seq for "+sid);
                    rs2.close();
                    boolean fixed = false;
                    if (fix) {
                        stmt2.executeUpdate("delete from astral_domain where node_id="+nodeID);
                        MakeDomainSeq.makeDomainSeqs(nodeID,false);
                        fixed = hasRow(stmt2,"select id from astral_domain where node_id="+nodeID+" and seq_id != 43558");
                        if (fixed)
                            System.out.println("  fixed");
                    }
                    if (!fixed)
                        continue;
                }
                else {
                    rs2.close();
                }

                boolean validPDBStyle = false;
                rs2 = stmt2.executeQuery("select file_path from scop_node_pdbstyle where node_id="+nodeID);
                if (!rs2.next()) {
                    System.out.println("missing pdb-style link for "+sid);
                }
                else {
                    String path = rs2.getString(1);
                    File f = new File(path);
                    if (f.isFile() && (f.length()>0))
                        validPDBStyle = true;
                    else
                        System.out.println("missing pdb-style file for "+sid);
                }
                rs2.close();
                if (!validPDBStyle) {
                    boolean fixed = false;
                    if (fix) {
                        rs2 = stmt2.executeQuery("select d.id from astral_domain d, scop_node n, astral_seq s where d.seq_id=s.id and d.node_id=n.id and length(s.seq) > 0 and d.source_id=2 and (d.style_id=1 or d.style_id=3) and n.id="+nodeID);
                        while (rs2.next()) {
                            int domainID = rs2.getInt(1);
                            MakePDBStyle.makePDBStyle(domainID);
                        }
                        rs2.close();
                        rs2 = stmt2.executeQuery("select file_path from scop_node_pdbstyle where node_id="+nodeID);
                        if (rs2.next()) {
                            File f = new File(rs2.getString(1));
                            fixed = f.isFile() && (f.length()>0);
                        }
                        rs2.close();
                        if (fixed)
                            System.out.println("  fixed");
                    }
                    if (!fixed)
                        continue;
                }

                if (!validThumbnails(stmt2,nodeID)) {
                    System.out.println("missing/incomplete thumbnail file for "+sid);
                    boolean fixed = false;
                    if (fix) {
                        try {
                            MakeThumbnails.makeThumbnail(nodeID,true);
                            fixed = validThumbnails(stmt2,nodeID);
                            if (fixed)
                                System.out.println("  fixed");
                        }
                        catch (Exception e2) {
                            e2.printStackTrace();
                        }
                    }
                    if (!fixed)
                        continue;
                }

                // fill in missing representative subset data
                // and promote thumbnail to parents, if needed
                HashSet<Integer> parentIDs = new HashSet<Integer>();
                for (levelID=5; levelID<8; levelID++) {
                    int levelNodeID = LocalSQL.findParent(nodeID,levelID);
                    if (levelNodeID == 0) {
                        System.out.println("missing parent at level "+levelID+" for "+sid+"; can't fix!");
                    }
                    else {
                        rs2 = stmt2.executeQuery("select rep_node_id from scop_subset_level where level_node_id="+levelNodeID);
                        if (rs2.next())
                            rs2.close();
                        else {
                            System.out.println("missing rep for level node "+levelNodeID);
                            rs2.close();
                            boolean fixed = false;
                            if (fix) {
                                stmt2.executeUpdate("insert into scop_subset_level values ("+nodeID+", "+levelNodeID+")");
                                MakeThumbnails.copyRepThumbnails(nodeID, levelNodeID);
                                fixed = true;
                            }
                            if (!fixed)
                                continue;
                        }
                    }
                }
            }
            rs.close();

            /*
              The original checks above cover the work through thumbnail
              generation.  Promotion still has several durable steps after
              that point.  Check them in a separate pass so a missing history
              row or an earlier missing artifact does not hide later damage
              from an interrupted job.
            */
            HashMap<Integer,Integer> representativeCandidates = new HashMap<Integer,Integer>();
            rs = stmt.executeQuery("select id, level_id, sunid, sid, description, curation_type_id from scop_node where curation_type_id>2 and release_id="+scopReleaseID+" and (level_id=8 or sunid>"+maxStableSunid+")");
            while (rs.next()) {
                int nodeID = rs.getInt(1);
                int levelID = rs.getInt(2);
                int sunid = rs.getInt(3);
                String sid = rs.getString(4);
                String description = rs.getString(5);
                int curationTypeID = rs.getInt(6);
                String label = (levelID==8 ? sid : Integer.toString(sunid));

                // Promotion may create the same automated parent twice in a
                // race.  It normally merges the higher id after thumbnails.
                if ((levelID>=5) && (levelID<8)) {
                    int mergeID = 0;
                    rs2 = stmt2.executeQuery("select n1.id from scop_node n1, scop_node n2 where n2.id="+nodeID+" and n1.id<n2.id and n1.release_id=n2.release_id and n1.level_id=n2.level_id and n1.parent_node_id=n2.parent_node_id and n1.description=n2.description order by n1.id asc limit 1");
                    if (rs2.next())
                        mergeID = rs2.getInt(1);
                    rs2.close();
                    if (mergeID!=0) {
                        System.out.println("duplicate promoted node "+label+"; should merge into "+mergeID);
                        if (fix) {
                            ManualEdit.mergeNode(nodeID,mergeID,false);
                            System.out.println("  fixed");
                        }
                        // The duplicate is either intentionally left alone in
                        // report mode or no longer exists in fix mode.  Do not
                        // create other artifacts for it.
                        continue;
                    }
                }

                // These comments are inserted immediately after creating new
                // automated family/protein nodes.
                if ((levelID==5) && "automated matches".equals(description) &&
                    (!hasRow(stmt2,"select id from scop_comment where node_id="+nodeID+" and description=\"not a true family\""))) {
                    System.out.println("missing automated-family comment for "+label);
                    if (fix) {
                        stmt2.executeUpdate("insert into scop_comment values(null, "+nodeID+", \"not a true family\", 0)");
                        System.out.println("  fixed");
                    }
                }
                if ((levelID==6) && "automated matches".equals(description) &&
                    (!hasRow(stmt2,"select id from scop_comment where node_id="+nodeID+" and description=\"not a true protein\""))) {
                    System.out.println("missing automated-protein comment for "+label);
                    if (fix) {
                        stmt2.executeUpdate("insert into scop_comment values(null, "+nodeID+", \"not a true protein\", 0)");
                        System.out.println("  fixed");
                    }
                }

                // A species node is created just before MakeSpecies.processNode
                // installs its normalized species link.
                if ((levelID==7) &&
                    (!hasRow(stmt2,"select species_id from link_species where node_id="+nodeID))) {
                    System.out.println("missing species link for "+label);
                    if (fix) {
                        int speciesID = MakeSpecies.processNode(nodeID,description);
                        MakeSpecies.linkPDBSpecies(speciesID);
                        System.out.println("  fixed");
                    }
                }

                if (levelID==8) {
                    if (needsAutoComment(stmt2,nodeID) &&
                        (!hasRow(stmt2,"select id from scop_comment where node_id="+nodeID+" and is_autogenerated=1"))) {
                        System.out.println("missing autogenerated comment for "+sid);
                        if (fix) {
                            MakeAutoComments.makeComments(nodeID);
                            if (hasRow(stmt2,"select id from scop_comment where node_id="+nodeID+" and is_autogenerated=1"))
                                System.out.println("  fixed");
                            else
                                System.out.println("  not fixed");
                        }
                    }

                    // The non-autogenerated hit comment is made while the
                    // domain node is created.  Restore it only if the retained
                    // ASTEROID rows identify one unambiguous hit.
                    if ((curationTypeID>=3) && (curationTypeID<=6) &&
                        (!hasRow(stmt2,"select id from scop_comment where node_id="+nodeID+" and is_autogenerated=0 and (description like \"automated match to %\" or description like \"automatically matched to %\")"))) {
                        System.out.println("missing automated-match comment for "+sid);
                        if (fix) {
                            String comment = recoverAutomatedMatchComment(stmt2,nodeID,scopReleaseID);
                            if (comment!=null) {
                                comment = StringUtil.replace(comment,"\"","\\\"");
                                stmt2.executeUpdate("insert into scop_comment values(null, "+nodeID+", \""+comment+"\", 0)");
                                System.out.println("  fixed");
                            }
                            else
                                System.out.println("  not fixed; ASTEROID hit is missing or ambiguous");
                        }
                    }

                    checkSPACI(stmt2,nodeID,scopReleaseID,sid,fix);

                    // Defer representative checks until every candidate domain
                    // has had a chance to repair its own thumbnails.
                    for (int parentLevel=5; parentLevel<8; parentLevel++) {
                        int levelNodeID = LocalSQL.findParent(nodeID,parentLevel);
                        if ((levelNodeID!=0) &&
                            (!representativeCandidates.containsKey(new Integer(levelNodeID))))
                            representativeCandidates.put(new Integer(levelNodeID),new Integer(nodeID));
                    }
                }

                checkIndexes(stmt2,nodeID,scopReleaseID,sunid,label,fix);
            }
            rs.close();

            for (Integer levelNodeID : representativeCandidates.keySet())
                checkRepresentative(stmt2,
                                    levelNodeID.intValue(),
                                    representativeCandidates.get(levelNodeID).intValue(),
                                    fix);

            stmt2.close();
            stmt.close();
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
