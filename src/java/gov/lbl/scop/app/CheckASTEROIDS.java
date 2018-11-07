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
import org.strbio.util.StringUtil;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Vector;

/**
   Check ASTEROIDS; produce stats for precision-recall curve
*/
public class CheckASTEROIDS {
    /**
       Checks whether an ASTEROID passes filters:

       filterLevel is bitmap of:
       0 = evaluate all ASTEROIDS based on BLAST hits
       1 = all of chain or none
       2 = require at most 10-residue overhang with match
       4 = require same number of regions in hit
       8 = require only one domain per chain
       16 = change full-chain hits to cover whole chain
       32 = remove low res, synthetic, different length sequences
       64 = all of hit chain or none
       128 = all hits to same chain (relevant only to multi-domain chains)
       256 = noGaps (relevant only to multi-domain chains)
    */
    final public static boolean passesFilter(int asteroidID,
                                             int filterLevel)
        throws Exception {
        // if filter level is set to 0, anything goes!
        if (filterLevel == 0)
            return true;

        Statement stmt = LocalSQL.createStatement();

        // gather information on BLAST hit
        ResultSet rs = stmt.executeQuery("select a.chain_id, a.sid, a.header, a.description, a.scop_release_id, a.pfam_release_id, a.blast_hit_id, length(s.seq), s.is_reject from asteroid a, astral_seq s where a.seq_id=s.id and a.id=" + asteroidID);
        if (!rs.next()) {
            rs.close();
            stmt.close();
            return false;
        }
        int astralChainID = rs.getInt(1);
        String sid = rs.getString(2);
        String header = rs.getString(3);
        String description = rs.getString(4);
        int scopReleaseID = rs.getInt(5);
        int pfamReleaseID = rs.getInt(6);
        int hitID = rs.getInt(7);
        int seqLength = rs.getInt(8);
        boolean isReject = (rs.getInt(9) == 1);
        rs.close();

        if (description == null) {
            stmt.close();
            System.out.println("no-desc");
            return false;
        }

        if (filterLevel % 2 == 1) {
            // check that this entire chain has been classified
            // i.e. see if there are any regions without blast hits
            rs = stmt.executeQuery("select id from asteroid where chain_id=" + astralChainID + " and scop_release_id=" + scopReleaseID + " and pfam_release_id=" + pfamReleaseID + " and blast_hit_id is null limit 1");
            if (rs.next()) {
                rs.close();
                stmt.close();
                System.out.println("not-entire-chain");
                return false;
            }
            rs.close();
        }

        if ((filterLevel / 8) % 2 == 1) {
            // check that entire chain fits in one domain
            rs = stmt.executeQuery("select id from asteroid where chain_id=" + astralChainID + " and scop_release_id=" + scopReleaseID + " and pfam_release_id=" + pfamReleaseID + " and id != " + asteroidID + " limit 1");
            if (rs.next()) {
                rs.close();
                stmt.close();
                System.out.println("not-one-domain");
                return false;
            }
            rs.close();
        }

        int hitNode;
        int hitSunid;
        String hitSCCS;
        String hitSid;
        String hitDescription;
        int hitSeqLength;
        double hitLog10E;
        double hitPctID;
        int hitStart;
        int hitLength;

        if (header.contains(ASTEROIDS.Annotation.sourceToString(ASTEROIDS.Annotation.Source.SCOPSEQMATCH))) {
            hitSid = ASTEROIDS.Annotation.getSidFromHeaderString(header);
            hitNode = LocalSQL.lookupNodeBySid(hitSid, scopReleaseID);
            hitPctID = 100;
            hitLog10E = -9999;
            rs = stmt.executeQuery("select n.sccs, n.description, length(s.seq) from astral_domain d, scop_node n, astral_seq s where n.id=d.node_id and d.seq_id=s.id and d.style_id=1 and d.source_id=2 and n.id="+hitNode);
            if (!rs.next()) {
                rs.close();
                stmt.close();
                System.out.println("no-template-data");
                return false;
            }
            hitSCCS = rs.getString(1);
            hitDescription = rs.getString(2);
            hitSeqLength = rs.getInt(3);
            rs.close();
	    
            hitStart = 0;
            hitLength = hitSeqLength;
        }
        else {
            // get blast hit details
            rs = stmt.executeQuery("select n.id, n.sunid, n.sccs, n.sid, n.description, length(s.seq), b.blast_log10_e, b.pct_identical, b.seq2_start, b.seq2_length from astral_seq_blast b, astral_domain d, scop_node n, astral_seq s where n.id=d.node_id and d.seq_id=s.id and s.id=b.seq2_id and b.id=" + hitID + " and b.source_id=d.source_id and b.style1_id=1 and (b.style2_id=d.style_id or d.style_id=1) and b.release_id=n.release_id order by n.sccs asc limit 1");
            if (!rs.next()) {
                rs.close();
                stmt.close();
                // System.out.println("blast");
                return false;
            }
            hitNode = rs.getInt(1);
            hitSunid = rs.getInt(2);
            hitSCCS = rs.getString(3);
            hitSid = rs.getString(4);
            hitDescription = rs.getString(5);
            hitSeqLength = rs.getInt(6);
            hitLog10E = rs.getDouble(7);
            hitPctID = rs.getDouble(8);
            hitStart = rs.getInt(9);
            hitLength = rs.getInt(10);
            rs.close();
        }

        // skip if hit is to a genetic domain
        if (hitSid.indexOf('.') > -1) {
            stmt.close();
            System.out.println("genetic-domain");
            return false;
        }

        if ((filterLevel / 2) % 2 == 1) {
            // check that hit covers entire domain
            if ((hitStart >= 10) || (hitStart + hitLength + 10 < hitSeqLength)) {
                stmt.close();
                System.out.println("short-hit");
                return false;
            }
        }

        if ((filterLevel / 4) % 2 == 1) {
            // check that hit has same number of regions
            int n1 = description.split(",").length;
            int n2 = hitDescription.split(",").length;
            if (n1 != n2) {
                stmt.close();
                System.out.println("hit-wrong-regions");
                return false;
            }
        }

        // "lo-res" filter: avoid confusing first 7 classes with others
        double res = 0.0;
        if ((filterLevel / 32) % 2 == 1) {
            // remove low resolution structures
            // this includes structures over 3.2 angstroms,
            // as well as NMR structures that are "model" "backbone"
            // "predict" or "based on" another structure
            rs = stmt.executeQuery("select pr.resolution, pm.summary, pe.description from pdb_entry pe, pdb_method pm, pdb_release pr, pdb_chain pc, astral_chain ac, raf r where pe.id=pr.pdb_entry_id and pm.id=pr.method_id and pc.pdb_release_id=pr.id and r.pdb_chain_id=pc.id and ac.raf_id=r.id and ac.id=" + astralChainID);
            if (rs.next()) {
                res = rs.getDouble(1);
                String method = rs.getString(2);
                String pdbDescription = rs.getString(3);
                if ((res >= 3.2) || (method==null)) {
                    rs.close();
                    stmt.close();
                    System.out.println("low-res");
                    return false;
                }
                else if ((res == 0.0) && (method.startsWith("NMR"))) {
                    // do more keyword blocking to ensure NMR
                    // structure is not mostly a model with a
                    // little bit of experimental data
                    // these will create FP, but better than
                    // blocking all NMR structures
                    if ((pdbDescription==null) ||
                        (pdbDescription.indexOf("model") > -1) ||
                        (pdbDescription.indexOf("backbone") > -1) ||
                        (pdbDescription.indexOf("predict") > -1) ||
                        (pdbDescription.indexOf("based on") > -1)) {
                        rs.close();
                        stmt.close();
                        System.out.println("nmr-model");
                        return false;
                    }
                }
                else if (res==0.0) {
                    rs.close();
                    stmt.close();
                    System.out.println("unk-res");
                    return false;
                }
            }
            rs.close();

            // check for ribosomes,
            // only OK if hit was to a ribosomal protein
            if (!hitSCCS.startsWith("i.1.")) {
                int protNode = LocalSQL.findParent(hitNode, 6);
                int famNode = LocalSQL.findParent(protNode, 5);
                rs = stmt.executeQuery("select id from scop_node where description like '%ibosomal%' and id in (" + protNode + "," + famNode + ")");
                if (rs.next()) {
                    rs.close();
                    stmt.close();
                    System.out.println("ribo");
                    return false;
                }
                rs.close();
            }

            // remove synthetic constructs
            rs = stmt.executeQuery("select s.id from pdb_source s, pdb_chain_source pcs, pdb_release pr1, pdb_release pr2, pdb_chain pc1, pdb_chain pc2, astral_chain ac, raf r where s.is_synthetic=1 and pr1.pdb_entry_id=pr2.pdb_entry_id and pc2.pdb_release_id=pr2.id and pc2.id=pcs.pdb_chain_id and pcs.pdb_source_id=s.id and pc1.pdb_release_id=pr1.id and r.pdb_chain_id=pc1.id and ac.raf_id=r.id and ac.id=" + astralChainID);
            if (rs.next()) {
                rs.close();
                stmt.close();
                System.out.println("synthetic1");
                return false;
            }
            rs.close();
            rs = stmt.executeQuery("select e.id from pdb_entry e, pdb_release pr, pdb_chain pc, astral_chain ac, raf r where (e.description like '%designed%' or e.description like '%synthesized%') and pc.pdb_release_id=pr.id and pr.pdb_entry_id=e.id and r.pdb_chain_id=pc.id and ac.raf_id=r.id and ac.id=" + astralChainID);
            if (rs.next()) {
                rs.close();
                stmt.close();
                System.out.println("synthetic2");
                return false;
            }
            rs.close();

            // remove fragments
            if ((Math.abs(hitLength - seqLength) >
                 (Math.min(30,
                           hitLength / 3))) ||
                (isReject)) {
                stmt.close();
                System.out.println("fragment "+hitLength+" "+seqLength);
                return false;
            }

            // remove domains that have >20% of residues missing
            rs = stmt.executeQuery("select r.line from raf r, astral_chain ac where r.id=ac.raf_id and ac.id=" + astralChainID);
            rs.next();
            String rafLine = rs.getString(1);
            rs.close();
            ASTEROIDS.Annotation a = new ASTEROIDS.Annotation();
            a.parseHeaderRegions(description,
                                 rafLine);
            int nTotal = a.length();
            int nObserved = a.observedLength(rafLine);
            if (nObserved < nTotal * 0.8) {
                stmt.close();
                System.out.println("fragment2 "+nObserved+" "+nTotal);
                return false;
            }
        }

        if ((filterLevel / 64) % 2 == 1) {
            // check that entire chain of hit has been classified
            String hitPDBCodeCh = hitSid.substring(1, 6);
            rs = stmt.executeQuery("select id from scop_node where level_id=8 and release_id=" + scopReleaseID + " and sid like \"d" + hitPDBCodeCh + "%\" and id!=" + hitNode + " limit 1");
            if (rs.next()) {
                rs.close();
                stmt.close();
                System.out.println("entire-chain-not-classified");
                return false;
            }
        }

        if ((filterLevel / 128) % 2 == 1) {
            // check that every other domain in chain resulted from a blast hit to the same chain
            int pdbChain = -1;
            boolean allSameChain = true;

            // first, get all asteroids in the chain
            int[] asteroidsInChain = new int[100]; //arbitrarily setting this number for now
            String query = "select a.id from asteroid a where scop_release_id=" + scopReleaseID + " and a.chain_id=" + astralChainID;
            rs = stmt.executeQuery(query);
            int ctr = 0;
            while (rs.next()) {
                asteroidsInChain[ctr] = rs.getInt(1);
                ctr++;
            }
            rs.close();

            // second, for each asteroid in chain, get a pdb_chain_id for the hit chain
            // get a pdb_chains_id for each asteroidInChain

            for (int i = 0; i < ctr; i++) {
                query = "select l.pdb_chain_id, a.id from asteroid a, astral_seq_blast b, astral_domain d, link_pdb l "
                    + "where a.blast_hit_id=b.id and b.seq2_id=d.seq_id and d.node_id=l.node_id "
                    + "and a.id=" + asteroidsInChain[i] + " and a.chain_id=" + astralChainID + " order by l.pdb_chain_id limit 1";
                rs = stmt.executeQuery(query);
                if (!rs.next()) {
                    stmt.close();
                    System.err.println("error occurred.");
                    return false;
                }
                int pdbChainCurrent = rs.getInt(1);
                rs.close();
                if (pdbChain < 0) {
                    pdbChain = pdbChainCurrent;
                }
                else if (pdbChainCurrent != pdbChain) {
                    allSameChain = false;
                    break;
                }
            }
            if (!allSameChain) {
                stmt.close();
                System.err.println("not-all-same-chain");
                return false;
            }
        }

        // cache whether chain has only one asteroid domain
        boolean singleDomainChain = true;
        rs = stmt.executeQuery("select id from asteroid where chain_id=" + astralChainID + " and scop_release_id=" + scopReleaseID + " and pfam_release_id=" + pfamReleaseID + " and id != " + asteroidID + " limit 1");
        if (rs.next()) {
            singleDomainChain = false;
        }
        rs.close();

        if (((filterLevel / 256) % 2 == 1) && (!singleDomainChain)) {
            // check that there are no gaps anywhere in the chain like a japanese ham sandwich
            // First, Get RAF for this chain
            rs = stmt.executeQuery("select r.line from raf r, astral_chain ac where r.id=ac.raf_id and ac.id=" + astralChainID);
            rs.next();
            String rafLine = rs.getString(1);

            // Second, get all asteroid domains from the same chain that resulted from BLAST hits, and create an Annotation for each
            Vector<ASTEROIDS.Annotation> annotationsForChain = new Vector<ASTEROIDS.Annotation>();
            String query = "select a.id, a.description from asteroid a where a.blast_hit_id is not null and a.chain_id=" + astralChainID;
            rs = stmt.executeQuery(query);
            while (rs.next()) {
                String description2 = rs.getString(2);
                if (description2 == null)
                    continue; // null description means that the region appeared in seqres but not in atomres
                ASTEROIDS.Annotation annotation = new ASTEROIDS.Annotation();
                try {
                    annotation.parseHeaderRegions(description2, rafLine);
                } catch (Exception e) {
                    System.err.println("problem parsing description2" + description2);
                }
                annotationsForChain.add(annotation);
            }
            rs.close();

            // Third, create a "monster" annotation, grouping all these annotations together,
            // used only for checking for gaps
            ASTEROIDS.Annotation aggregatedAnnotation = new ASTEROIDS.Annotation();
            for (ASTEROIDS.Annotation annotation : annotationsForChain) {
                for (ASTEROIDS.AnnotationRegion region : annotation.regions) {
                    aggregatedAnnotation.regions.add(region);
                }
            }

            // Fourth, find the largest gap between regions in the annotation
            int maxGapSize = aggregatedAnnotation.maxGapSize();
            if (maxGapSize > 0) {
                stmt.close();
                System.err.println("big-gaps");
                return false;
            }

            // check whether there are any unmatched residues in the chain
            ASTEROIDS.AnnotationSet as = new ASTEROIDS.AnnotationSet(astralChainID);
            for (ASTEROIDS.Annotation annotation : annotationsForChain) {
                as.annotate(annotation, 0);
            }
            int numAnnotationsBeforeAddingUnmatched = as.annotations.size();
            as.load(stmt);
            as.addUnmatched(0);
            if (as.annotations.size() > numAnnotationsBeforeAddingUnmatched) {
                stmt.close();
                System.err.println("unmatched-residues");
                return false;
            }
        }
        stmt.close();
        return true;
    }

    /**
       prints info on whether an ASTEROID is correct or not, based
       on next version of SCOP.
       <p/>
       filterLevel is bitmap of:
       0 = evaluate all ASTEROIDS based on BLAST hits
       1 = all of chain or none
       2 = require at most 10-residue overhang with match
       4 = require same number of regions in hit
       8 = require only one domain per chain
       16 = change full-chain hits to cover whole chain (relevant only to single-domain chains)
       32 = remove low res, synthetic, different length sequences
       64 = all of hit chain or none
       128 = all hits to same chain (relevant only to multi-domain chains)
       256 = noGaps (relevant only to multi-domain chains)
    */
    final public static void checkASTEROID(int asteroidID,
                                           int filterLevel)
        throws Exception {

        if (!passesFilter(asteroidID,
                          filterLevel))
            return;

        Statement stmt = LocalSQL.createStatement();

        ResultSet rs = stmt.executeQuery("select max(id) from scop_release where is_public=1");
        rs.next();
        int scopPublicReleaseID = rs.getInt(1);
        rs.close();

        rs = stmt.executeQuery("select a.chain_id, a.sid, a.header, a.description, a.scop_release_id, a.pfam_release_id, a.blast_hit_id, length(s.seq), s.is_reject from asteroid a, astral_seq s where s.id=a.seq_id and a.id=" + asteroidID);
        rs.next();
        int astralChainID = rs.getInt(1);
        String sid = rs.getString(2);
        String header = rs.getString(3);
        String description = rs.getString(4);
        int scopReleaseID = rs.getInt(5);
        int pfamReleaseID = rs.getInt(6);
        int hitID = rs.getInt(7);
        int seqLength = rs.getInt(8);
        boolean isReject = (rs.getInt(9) == 1);
        rs.close();

        // can't do anything without parseable description
        if (description == null) {
            stmt.close();
            return;
        }

        // cache whether chain has only one asteroid domain
        boolean singleDomainChain = true;
        rs = stmt.executeQuery("select id from asteroid where chain_id=" + astralChainID + " and scop_release_id=" + scopReleaseID + " and pfam_release_id=" + pfamReleaseID + " and id != " + asteroidID + " limit 1");
        if (rs.next()) {
            singleDomainChain = false;
        }
        rs.close();

        if (scopReleaseID == scopPublicReleaseID) {
            // ignore any classified in current SCOP
            String pdbCode = sid.substring(1, 5);
            rs = stmt.executeQuery("select id from scop_node where level_id=8 and release_id=" + scopReleaseID + " and sid like \"d" + pdbCode + "%\" limit 1");
            if (rs.next()) {
                rs.close();
                stmt.close();
                return;
            }
            rs.close();
        }

        // get blast hit details; preference for 1st 7 SCOP classes
        rs = stmt.executeQuery("select n.id, n.sunid, n.sccs, n.sid, n.description, length(s.seq), b.blast_log10_e, b.pct_identical, b.seq2_start, b.seq2_length from astral_seq_blast b, astral_domain d, scop_node n, astral_seq s where n.id=d.node_id and d.seq_id=s.id and s.id=b.seq2_id and b.id=" + hitID + " and b.source_id=d.source_id and b.style1_id=1 and (b.style2_id=d.style_id or d.style_id=1) and b.release_id=n.release_id order by n.sccs asc limit 1");
        if (!rs.next()) {
            rs.close();
            stmt.close();
            return;
        }
        int hitNode = rs.getInt(1);
        int hitSunid = rs.getInt(2);
        String hitSCCS = rs.getString(3);
        String hitSid = rs.getString(4);
        String hitDescription = rs.getString(5);
        int hitSeqLength = rs.getInt(6);
        double hitLog10E = rs.getDouble(7);
        double hitPctID = rs.getDouble(8);
        int hitStart = rs.getInt(9);
        int hitLength = rs.getInt(10);
        rs.close();

        // skip if hit is to a genetic domain
        if (hitSid.indexOf('.') > -1) {
            stmt.close();
            return;
        }

        // is hit node the same in new SCOP?
        // can't evaluate for now, although we should check
        // whether we can detect these events
        if (scopReleaseID != scopPublicReleaseID) {
            rs = stmt.executeQuery("select id from scop_node where sunid=" + hitSunid + " and sccs=\"" + hitSCCS + "\" and release_id=" + (scopReleaseID + 1));
            if (rs.next()) {
                hitNode = rs.getInt(1);
            }
            else {
                rs.close();
                stmt.close();
                return;
            }
        }

        // find PDB chain id, RAF for this chain
        rs = stmt.executeQuery("select c.id, r.line from pdb_chain c, raf r, astral_chain ac where c.id=r.pdb_chain_id and r.id=ac.raf_id and ac.id=" + astralChainID);
        if (!rs.next()) {
            rs.close();
            stmt.close();
            return;
        }
        int pdbChainID = rs.getInt(1);
        String rafLine = rs.getString(2);
        rs.close();

        // parse description into an Annotation
        ASTEROIDS.Annotation a = new ASTEROIDS.Annotation();
        a.parseHeaderRegions(description,
                             rafLine);

        if ((filterLevel / 16) % 2 == 1) {
            // expand description until (near) ends  (only applies to domains)

            //first, check if the domain is in a single-domain chain
            if (singleDomainChain) {
                a.expandToNearEnds(rafLine);
                description = a.getHeaderRegions(rafLine);
            }
        }

        // find all domains for this chain in the new SCOP
        // save best match, if there is one
        int bestNode = 0;
        String bestSCCS = null;
        String bestSid = null;
        String bestDescription = null;
        int bestMaxError = 0; // max error from a single region end
        int bestTotalError = 0; // total error in region ends
        int bestSeqLength = 0; // length of match SEQRES sequence
        String allMatches = "";

        if (scopReleaseID != scopPublicReleaseID) {
            String query = "select n.id, n.sccs, n.sid, n.description, length(s.seq) from scop_node n, link_pdb l, astral_domain d, astral_seq s where d.seq_id=s.id and l.node_id=n.id and n.release_id=" + (scopReleaseID + 1) + " and d.node_id=n.id and d.source_id=2 and (d.style_id=1 or d.style_id=3) and l.pdb_chain_id=" + pdbChainID;
            rs = stmt.executeQuery(query);
            while (rs.next()) {
                int matchNode = rs.getInt(1);
                String matchSCCS = rs.getString(2);
                String matchSid = rs.getString(3);
                String matchDescription = rs.getString(4);
                int matchSeqLength = rs.getInt(5);

                allMatches += matchSid + " " + matchDescription + "; ";

                ASTEROIDS.Annotation b = new ASTEROIDS.Annotation();
                b.parseHeaderRegions(matchDescription,
                                     rafLine);

                if (a.regions.size() != b.regions.size())
                    continue;

                int maxError = a.maxUnmatched(b);
                if ((bestNode == 0) || (maxError < bestMaxError)) {
                    bestNode = matchNode;
                    bestSCCS = matchSCCS;
                    bestSid = matchSid;
                    bestDescription = matchDescription;
                    bestMaxError = maxError;
                    bestTotalError = a.nUnmatched(b);
                    bestSeqLength = matchSeqLength;
                }
            }
            rs.close();
        }

        // check whether best match is "right"
        if (bestNode == 0) {
            // wrong by default; print all possibilities
            System.out.println(0 + "\t" + sid + "\t" + hitLog10E + "\t" + "\t" + hitPctID + "\t" + header + "\t" + description + "\t" + allMatches + "\thttp://www.strgen.org/~jmc/scop-asteroids/asteroid.php?asteroid=" + asteroidID + "&filterLevel=" + filterLevel);
            stmt.executeUpdate("delete from asteroid_match where asteroid_id=" + asteroidID);//+" and filter_level="+filterLevel);
            stmt.executeUpdate("insert into asteroid_match values (" +
                               asteroidID + ", \"" +
                               description + "\", null, 0, 4)");// + "," + filterLevel+")");
        }
        else {
            String maxDiff = null;
            int maxDiffID = 1;
            int correct = 1;
            if (bestMaxError > 10)
                correct = 0;
            if (!hitSCCS.equals(bestSCCS)) {
                int pos = hitSCCS.lastIndexOf('.');
                String x = hitSCCS.substring(0, pos);
                pos = bestSCCS.lastIndexOf('.');
                String y = bestSCCS.substring(0, pos);
                if (!x.equals(y)) {
                    correct = 0;
                    maxDiff = "SUPERFAM";
                    maxDiffID = 4;
                } else {
                    maxDiff = "FAMILY";
                    maxDiffID = 5;
                }
            }
            else {
                int species1 = LocalSQL.findParent(hitNode, 7);
                int species2 = LocalSQL.findParent(bestNode, 7);
                if (species1 == species2) {
                    maxDiff = "DOMAIN";
                    maxDiffID = 8;
                } else {
                    int prot1 = LocalSQL.findParent(species1, 6);
                    int prot2 = LocalSQL.findParent(species2, 6);
                    if (prot1 == prot2) {
                        maxDiff = "SPECIES";
                        maxDiffID = 7;
                    } else {
                        maxDiff = "PROTEIN";
                        maxDiffID = 6;
                    }
                }
            }
            if (bestTotalError > bestSeqLength / 5)
                correct = 0;
            System.out.println(correct + "\t" + sid + "\t" + hitLog10E + "\t" + "\t" + hitPctID + "\t" + header + "\t" + description + "\t" + bestSid + "\t" + "\t" + bestSCCS + "\t" + bestDescription + "\t" + bestMaxError + "\t" + bestTotalError + "\t" + bestSeqLength + "\t" + maxDiff + "\thttp://www.strgen.org/~jmc/scop-asteroids/asteroid.php?asteroid=" + asteroidID + "&filterLevel=" + filterLevel);
            stmt.executeUpdate("delete from asteroid_match where asteroid_id=" + asteroidID); //and filter_level="+filterLevel);
            stmt.executeUpdate("insert into asteroid_match values (" +
                               asteroidID + ", \"" +
                               description + "\", " +
                               bestNode + ", " +
                               correct + ", " +
                               maxDiffID + ")");
        }
        stmt.close();
    }

    /**
       Usage 1: java gov.lbl.scop.app.CheckASTEROIDS \n";
       Usage 2: java gov.lbl.scop.app.CheckASTEROIDS filter-level\n";
       Usage 3: java gov.lbl.scop.app.CheckASTEROIDS filter-level PFam-version SCOP-version\n\n";

       Example filter levels: 0 (no filters, default), 127 (all filters)\n";
       Example Pfam version: 26.0 (from scop database, "select * from pfam_release")
       Example SCOP versions: 1.55 (2001), 1.75A (2012) ("select * from scop_release)
    */
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            ResultSet rs;

            // get all ASTEROIDS based on BLAST hits:
            String query = "select a.id from asteroid a, astral_seq_blast b where a.blast_hit_id=b.id";

            // optional filtering of set to look at:
            int filterLevel = 0;
            if (argv.length > 0)
                filterLevel = StringUtil.atoi(argv[0]);
            if (argv.length > 2) {
                int pfamReleaseID = LocalSQL.lookupPfamRelease(argv[1]);
                if (pfamReleaseID == 0)
                    throw new Exception("Can't determine Pfam version from " + argv[1]);

                int scopReleaseID = LocalSQL.lookupSCOPRelease(argv[2]);
                if (scopReleaseID == 0)
                    throw new Exception("Can't determine SCOP version from " + argv[2]);
                query += " and a.scop_release_id=" + scopReleaseID + " and a.pfam_release_id=" + pfamReleaseID;
            }
            else {
                rs = stmt.executeQuery("select max(id) from scop_release where is_public=1");
                rs.next();
                int scopPublicReleaseID = rs.getInt(1);
                rs.close();
                query += " and a.scop_release_id != " + scopPublicReleaseID;
            }

            if ((argv.length > 3) || (argv.length == 2))
                query += " order by b.pct_identical desc, b.blast_log10_e asc, (b.seq1_length+b.seq2_length) desc";
            else
                query += " order by b.blast_log10_e asc, b.pct_identical desc, (b.seq1_length+b.seq2_length) desc";

            // print out header. useful for post-processing in R.
            // System.out.println("correct"+"\t"+"sid1"+"\t"+"hitLog10E"+"\t"+"\t"+"hitPctID"+"\t"+"header"+"\t"+"description"+"\t"+"sid2"+"\t"+"\t"+"SCCS"+"\t"+"Description"+"\t"+"MaxError"+"\t"+"TotalError"+"\t"+"SeqLength"+"\t"+"maxDiff"+"\turl"); 

            rs = stmt.executeQuery(query);
            while (rs.next())
                checkASTEROID(rs.getInt(1), filterLevel);
            rs.close();
            stmt.close();
        }
        catch (Exception e) {
            System.out.println("Exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
