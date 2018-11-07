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
import gov.lbl.scop.util.*;
import static gov.lbl.scop.util.ASTEROIDS.*;

/**
   Finds cloning tags in SCOP sequences.  To run on 2.06, need to
   prepare:

   java gov.lbl.scop.app.ManualEdit new 0 "Artifacts"
   // update sccs to 'l'
   java gov.lbl.scop.app.ManualEdit new l "Tags"
   java gov.lbl.scop.app.ManualEdit new l.1 "Tags"
   java gov.lbl.scop.app.ManualEdit new l.1.1 "Tags"
   set x=`java gov.lbl.scop.app.ManualEdit new l.1.1.1 "N-terminal Tags"`
   java gov.lbl.scop.app.ManualEdit new N$x "Synthetic"
   set x=`java gov.lbl.scop.app.ManualEdit new l.1.1.1 "C-terminal Tags"`
   java gov.lbl.scop.app.ManualEdit new N$x "Synthetic"

   Check 1xd3B-A to be sure ubiquitin isn't listed as a tag
*/
public class FindTags {
    // minimum before calling a tag "long"
    final public static int MIN_TAG_LENGTH = 5;

    // maximum we can extend the nearby domain to meet the tag
    final public static int MAX_LINKER = 15;

    // other global vars
    public static int nParentID = -1;
    public static int cParentID = -1;
    public static ArrayList<String> longNTags = null;
    public static ArrayList<String> longCTags = null;
    public static HashMap<String,Integer> nTagIndex = null;
    public static HashMap<String,Integer> cTagIndex = null;

    /**
       Set global ids
    */
    final public static void startLiveChanges() throws Exception {
        if ((nParentID > -1) &&
            (cParentID > -1))
            return; // already live
            
        int scopReleaseID = LocalSQL.getLatestSCOPRelease(false);
        int tagFamID = LocalSQL.lookupNodeBySCCS("l.1.1.1",
                                                 scopReleaseID);
        if (tagFamID==0)
            return;
        
        int protID = LocalSQL.lookupNodeByDescription("N-terminal Tags",
                                                      scopReleaseID,
                                                      tagFamID);
        nParentID = LocalSQL.lookupNodeByDescription("Synthetic",
                                                     scopReleaseID,
                                                     protID);
        protID = LocalSQL.lookupNodeByDescription("C-terminal Tags",
                                                  scopReleaseID,
                                                  tagFamID);
        cParentID = LocalSQL.lookupNodeByDescription("Synthetic",
                                                     scopReleaseID,
                                                     protID);
    }

    /**
       process a tag, changing existing domains to either:
       1) extend them to adjacent the tag, or
       2) remove the tag sequence from the domains

       returns node ID of the added tag node, or 0 if none added.
    */
    final public static int processTag(int scopReleaseID,
                                       Annotation tag,
                                       AnnotationSet asDomains,
                                       String rafBody,
                                       boolean isNTag,
                                       int pdbChainID) throws Exception {
        int rv = 0;
        
        AnnotationRegion tagRegion = tag.regions.elementAt(0);
        if (!tagRegion.isRegionInAtomRes(rafBody))
            return 0;

        Vector<AnnotationRegion> allRegions = asDomains.getAllRegions();
        AnnotationRegion scopRegion = null;
        int parentID = -1;
        if (isNTag) {
            scopRegion = allRegions.firstElement();
            parentID = nParentID;
        }
        else {
            scopRegion = allRegions.lastElement();
            parentID = cParentID;
        }

        Annotation scopDomain = asDomains.getAnnotation(scopRegion);

        // if domain is a tag, skip it
        int newNodeID = LocalSQL.lookupNodeBySid(scopDomain.sid,
                                                 scopReleaseID);
        String scopDomainSCCS = LocalSQL.getSCCS(newNodeID);
        if (scopDomainSCCS.startsWith("l")) {
            System.out.println("skipping "+scopDomain.sid+"; already annotated as tag");
            return 0;
        }
        
        String oldHeader = scopDomain.getHeaderRegions(asDomains.rafLine);
        int oldNodeID = LocalSQL.lookupNodeBySid(scopDomain.sid,
                                                 scopReleaseID-1);

        Statement stmt = LocalSQL.createStatement();
        AnnotationSet asDomainsOverlap = asDomains.getAllMatches(tag);
        if (asDomainsOverlap==null) {
            // extend nearest region by up to MAX_LINKER residues
            int linkerSize = AnnotationSet.getLinkerSize(tagRegion, scopRegion);
            if ((linkerSize <= MAX_LINKER) && (linkerSize > 0)) {
                scopRegion.length += linkerSize;
                if (isNTag)
                    scopRegion.start -= linkerSize;
                System.out.println("adjusted domain "+scopDomain.sid+" "+oldHeader+" -> "+scopDomain.getHeaderRegions(asDomains.rafLine));
                if (parentID > -1) {
                    stmt.executeUpdate("update scop_node set description=\""+scopDomain.getHeaderRegions(asDomains.rafLine)+"\" where id="+scopDomain.hitNodeID);
                    // but keep same sunid
                    if (oldNodeID > 0) {
                        stmt.executeUpdate("insert into scop_history values (null, "+oldNodeID+", "+scopDomain.hitNodeID+", "+scopReleaseID+", 11, now())");
                    }
                }
            }

            // add new region
            asDomains.annotations.add(tag);
            tag.sid = StableSid.assignNewSid(tag.getHeaderRegions(asDomains.rafLine));
            System.out.println("added tag domain "+tag.sid+" "+tag.getHeaderRegions(asDomains.rafLine));
            if (parentID > -1) {
                int nodeID = LocalSQL.createNode(0,
                                                 "l.1.1.1",
                                                 tag.sid,
                                                 tag.getHeaderRegions(asDomains.rafLine),
                                                 8,
                                                 parentID,
                                                 scopReleaseID,
                                                 7);
                stmt.executeUpdate("insert into link_pdb values("+
                                   nodeID+
                                   ", "+pdbChainID+")");

                rv = nodeID;
            }
        }
        else {
            // if more than 1 overlap, need manual intervention
            int n = 0;
            if (asDomainsOverlap.annotations.size() > 1) {
                for (Annotation a : asDomainsOverlap.annotations) {
                    System.out.println("need manual curation 1 domain"+(n++)+" "+a.sid+" "+a.getHeaderRegions(asDomains.rafLine));
                }
                stmt.close();
                return 0;
            }

            int olap = scopRegion.nOverlap(tagRegion);
            if (scopRegion.length <= olap) {
                System.out.println("need manual curation 2 domain0 "+scopDomain.sid+" "+scopDomain.getHeaderRegions(asDomains.rafLine));
                stmt.close();
                return 0;
            }
            scopRegion.length -= olap;
            if (isNTag)
                scopRegion.start += olap;
            if (scopDomain.nOverlap(tag) > 0)
                throw new Exception("bug in olap code!");
            
            if (scopDomain.sid.endsWith("_"))
                scopDomain.sid = StableSid.assignNewSid(scopDomain.getHeaderRegions(asDomains.rafLine));
            System.out.println("adjusted domain "+scopDomain.sid+" "+oldHeader+" -> "+scopDomain.getHeaderRegions(asDomains.rafLine));
            if (parentID > -1) {
                stmt.executeUpdate("update scop_node set description=\""+scopDomain.getHeaderRegions(asDomains.rafLine)+"\", sid=\""+scopDomain.sid+"\" where id="+scopDomain.hitNodeID);
                // but keep same sunid
                if (oldNodeID > 0) {
                    stmt.executeUpdate("insert into scop_history values (null, "+oldNodeID+", "+scopDomain.hitNodeID+", "+scopReleaseID+", 11, now())");
                }
            }

            // add tag region last
            tag.sid = StableSid.assignNewSid(tag.getHeaderRegions(asDomains.rafLine));
            System.out.println("added tag domain "+tag.sid+" "+tag.getHeaderRegions(asDomains.rafLine));
            if (parentID > -1) {
                int nodeID = LocalSQL.createNode(0,
                                                 "l.1.1.1",
                                                 tag.sid,
                                                 tag.getHeaderRegions(asDomains.rafLine),
                                                 8,
                                                 parentID,
                                                 scopReleaseID,
                                                 7);
                stmt.executeUpdate("insert into link_pdb values("+
                                   nodeID+
                                   ", "+pdbChainID+")");
                rv = nodeID;
                
                // add "split" record   
                if (oldNodeID > 0) {
                    stmt.executeUpdate("insert into scop_history values (null, "+oldNodeID+", "+scopDomain.hitNodeID+", "+scopReleaseID+", 5, now())");
                    stmt.executeUpdate("insert into scop_history values (null, "+oldNodeID+", "+nodeID+", "+scopReleaseID+", 5, now())");
                }
            }
        }
        stmt.close();
        return rv;
    }

    /**
       Splits out any tag domains from an astral chain (must already
       have assigned scop domains). does nothing for genetic
       domains or chains without scop domains.  returns list of
       node ids of tag domains created.
    */
    final public static HashSet<Integer> splitTagDomains(int astralChainID) throws Exception {
        HashSet<Integer> rv = new HashSet<Integer>();
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select ac.sid, ac.source_id, r.last_release_id, r.pdb_chain_id from astral_chain ac, raf r where ac.raf_id=r.id and ac.id="+astralChainID);
        rs.next();
        String sid = rs.getString(1);
        int sourceType = rs.getInt(2);
        sid += (sourceType==1 ? "-A" : "-S");
        int scopReleaseID = rs.getInt(3);
        int pdbChainID = rs.getInt(4);
        rs.close();
        stmt.close();

        Annotation[] tags = findTagsInChain(astralChainID);

        // we're done with SEQRES seqs:
        // only need to split if in ATOM residues
        if (sid.endsWith("-S"))
            return rv;

        // load existing scop domains, and sort by start point
        AnnotationSet asDomains = null;
        try {
            asDomains = AnnotationSet.getSCOPDomains(astralChainID,
                                                     scopReleaseID);
            if (asDomains.annotations.size() == 0) {
                System.out.println("error getting scop domains "+astralChainID+" "+scopReleaseID);
                return rv;
            }
        }
        catch (Exception gde) {
            System.out.println("manually fix genetic domain "+sid);
            return rv;
        }

        for (Annotation a : asDomains.annotations)
            a.sortByStart = true;
        Collections.sort(asDomains.annotations);

        Annotation nTag = tags[0];
        Annotation cTag = tags[1];
        String rafBody = RAF.getRAFBody(asDomains.rafLine);
        
        // adjust existing domains
        if (nTag != null) {
            int nodeID = processTag(scopReleaseID,
                                    nTag,
                                    asDomains,
                                    rafBody,
                                    true,
                                    pdbChainID);
            if (nodeID != 0)
                rv.add(new Integer(nodeID));
        }
        if (cTag != null) {
            int nodeID = processTag(scopReleaseID,
                                    cTag,
                                    asDomains,
                                    rafBody,
                                    false,
                                    pdbChainID);
            if (nodeID != 0)
                rv.add(new Integer(nodeID));
        }
        return rv;
    }

    /**
       initialize lists of long tags
    */
    final public static void initLongTags() throws Exception {
        Statement stmt = LocalSQL.createStatement();

        longNTags = new ArrayList<String>();
        longCTags = new ArrayList<String>();
        nTagIndex = new HashMap<String,Integer>();
        cTagIndex = new HashMap<String,Integer>();

        ResultSet rs = stmt.executeQuery("select raf_get_body(r.id), t.tag_start, t.tag_end, t.pdb_chain_diff_id from pdb_chain_tag t, raf r where t.tag_end-t.tag_start+1 >= "+MIN_TAG_LENGTH+" and r.pdb_chain_id=t.pdb_chain_id and r.first_release_id is null and r.last_release_id is null");
        while (rs.next()) {
            String rafBody = rs.getString(1);
            int tagStart = rs.getInt(2);
            int tagEnd = rs.getInt(3);
            int diffID = rs.getInt(4);

            RAF.SequenceFragment sf = RAF.partialChainSeq(rafBody,2,tagStart,tagEnd);
            if (sf.xCount > 0)
                continue;

            String tagSeq = sf.getSequence();
            if (tagSeq.length() < MIN_TAG_LENGTH)
                continue;

            if (tagStart==0) {
                if (!longNTags.contains(tagSeq)) {
                    longNTags.add(tagSeq);
                    nTagIndex.put(tagSeq,new Integer(diffID));
                }
            }
            else {
                if (!longCTags.contains(tagSeq)) {
                    longCTags.add(tagSeq);
                    cTagIndex.put(tagSeq,new Integer(diffID));
                }
            }
        }
        rs.close();
        stmt.close();
        
        // sort long tags by longest-first
        Collections.sort(longNTags,new Comparator<String>() {
            public int compare(String s1,String s2) {
                return s2.length() - s1.length();
            }
        });
        Collections.sort(longCTags,new Comparator<String>() {
            public int compare(String s1,String s2) {
                return s2.length() - s1.length();
            }
        });
        
        // debug: print out long tags
        /*
          for (String tag : longNTags)
          System.out.println("debug: longN "+tag);
          for (String tag : longCTags)
          System.out.println("debug: longC "+tag);
        */
    }

    /**
       Infers tags in an astral chain, and adds to corresponding
       pdb_chain_tag table.  If any tags already in table, returns
       those.
       Returns array containing nTag and cTag (either can be null
       if not found).
    */
    final public static Annotation[] findTagsInChain(int astralChainID) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs;

        rs = stmt.executeQuery("select c.sid, c.source_id, s.id, s.seq, r.pdb_chain_id from astral_seq s, astral_chain c, raf r where c.id="+astralChainID+" and s.id=c.seq_id and r.id=c.raf_id");
        rs.next();
        String sid = rs.getString(1);
        int sourceType = rs.getInt(2);
        sid += (sourceType==1 ? "-A" : "-S");
        int seqID = rs.getInt(3);
        String seq = rs.getString(4);
        int pdbChainID = rs.getInt(5);
        rs.close();

        Annotation[] rv = new Annotation[2];
        Annotation nTag = null;
        Annotation cTag = null;
        
        // look for previously annotated tags in chain
        rs = stmt.executeQuery("select tag_start, tag_end, pdb_chain_diff_id from pdb_chain_tag where pdb_chain_id = "+pdbChainID);
        while (rs.next()) {
            int tagStart = rs.getInt(1);
            int tagEnd = rs.getInt(2);
            Annotation a = new Annotation(Annotation.Source.UNKNOWN,
                                          -1,
                                          tagStart,
                                          tagEnd-tagStart+1);
            a.hitNodeID = rs.getInt(3);
            a.sortByStart = true;
            if (tagStart==0)
                nTag = a;
            else
                cTag = a;
        }
        rs.close();
        if ((nTag != null) || (cTag != null)) {
            stmt.close();
            rv[0] = nTag;
            rv[1] = cTag;
            return rv;
        }
        
        AnnotationSet asTags = new AnnotationSet(astralChainID);
        asTags.load(stmt);
        String rafBody = RAF.getRAFBody(asTags.rafLine);

        // load in all likely tags belonging to this chain
        rs = stmt.executeQuery("select d.id, d.diff_start, d.diff_end, d.diff_seq from pdb_chain_diff d, pdb_chain_diff_category dc where d.pdb_chain_id="+pdbChainID+" and d.category_id=dc.id and (dc.description like '%tag' or dc.description like '%clon%' or dc.description='LEADER SEQUENCE')");
        while (rs.next()) {
            int diffID = rs.getInt(1);
            int diffStart = rs.getInt(2);
            int diffEnd = rs.getInt(3);
            String diffSeq = rs.getString(4);

            Annotation a = new Annotation(Annotation.Source.UNKNOWN,
                                          -1,
                                          diffStart,
                                          diffEnd-diffStart+1);
            a.hitNodeID = diffID;
            a.sortByStart = true;
            asTags.annotations.add(a);
        }
        rs.close();

        // if there are none, infer tags from identical seqs or known long tag seqs
        boolean inferredTags = false;
        if (asTags.annotations.size()==0) {
            // make sure we can infer tags
            if (longNTags==null)
                initLongTags();
            
            // look for longest tag in an identical sequence
            boolean foundNTag = false;
            rs = stmt.executeQuery("select t.tag_start, t.tag_end, t.pdb_chain_diff_id from pdb_chain_diff d, pdb_chain_tag t, raf r, astral_chain ac where ac.seq_id="+seqID+" and ac.source_id="+sourceType+" and ac.raf_id=r.id and r.pdb_chain_id=t.pdb_chain_id and t.pdb_chain_diff_id=d.id and d.pdb_chain_id=r.pdb_chain_id and t.tag_start=0 order by t.tag_end desc limit 1");
            if (rs.next()) {
                int tagStart = rs.getInt(1);
                int tagEnd = rs.getInt(2);
                Annotation a = new Annotation(Annotation.Source.UNKNOWN,
                                              -1,
                                              tagStart,
                                              tagEnd-tagStart+1);
                a.hitNodeID = rs.getInt(3);
                a.sortByStart = true;
                asTags.annotations.add(a);
                foundNTag = true;
                inferredTags = true;
            }
            rs.close();

            // same for C-tag
            boolean foundCTag = false;
            rs = stmt.executeQuery("select t.tag_start, t.tag_end, t.pdb_chain_diff_id from pdb_chain_diff d, pdb_chain_tag t, raf r, astral_chain ac where ac.seq_id="+seqID+" and ac.source_id="+sourceType+" and ac.raf_id=r.id and r.pdb_chain_id=t.pdb_chain_id and t.pdb_chain_diff_id=d.id and d.pdb_chain_id=r.pdb_chain_id and t.tag_start>0 order by (t.tag_end-t.tag_start) desc limit 1");
            if (rs.next()) {
                int tagStart = rs.getInt(1);
                int tagEnd = rs.getInt(2);
                Annotation a = new Annotation(Annotation.Source.UNKNOWN,
                                              -1,
                                              tagStart,
                                              tagEnd-tagStart+1);
                a.hitNodeID = rs.getInt(3);
                a.sortByStart = true;
                asTags.annotations.add(a);
                foundCTag = true;
                inferredTags = true;
            }
            rs.close();

            // as last resort, look for matching long tags
            if (!foundNTag) {
                for (String tag : longNTags) {
                    if (seq.startsWith(tag)) {
                        int tagStart = RAF.translateIndex(rafBody,
                                                          0,
                                                          sourceType);
                        int tagEnd = tagStart + tag.length()-1;
                        Annotation a = new Annotation(Annotation.Source.UNKNOWN,
                                                      -1,
                                                      tagStart,
                                                      tagEnd-tagStart+1);
                        a.hitNodeID = nTagIndex.get(tag).intValue();
                        a.sortByStart = true;
                        asTags.annotations.add(a);
                            
                        foundNTag = true;
                        inferredTags = true;
                        break;
                    }
                }
            }

            if (!foundCTag) {
                for (String tag : longCTags) {
                    if (seq.endsWith(tag)) {
                        int tagEnd = RAF.translateIndex(rafBody,
                                                        seq.length()-1,
                                                        sourceType);
                        int tagStart = tagEnd - tag.length()+1;
                        Annotation a = new Annotation(Annotation.Source.UNKNOWN,
                                                      -1,
                                                      tagStart,
                                                      tagEnd-tagStart+1);
                        a.hitNodeID = cTagIndex.get(tag).intValue();
                        a.sortByStart = true;
                        asTags.annotations.add(a);
                            
                        foundCTag = true;
                        inferredTags = true;
                        break;
                    }
                }
            }
        }

        // find nTag and cTag: annotations at beginning and end
        // of ATOM records
        int firstIndex = RAF.translateIndex(rafBody,
                                            0,
                                            2);
        firstIndex = RAF.findNearestATOM(rafBody,
                                         firstIndex,
                                         true);
        int lastIndex = RAF.translateIndex(rafBody,
                                           seq.length()-1,
                                           2);
        lastIndex = RAF.findNearestATOM(rafBody,
                                        lastIndex,
                                        false);

        nTag = new Annotation(Annotation.Source.UNKNOWN,
                              -1,
                              firstIndex,
                              1);
        nTag = asTags.getBestMatch(nTag);

        cTag = new Annotation(Annotation.Source.UNKNOWN,
                              -1,
                              lastIndex,
                              1);
        cTag = asTags.getBestMatch(cTag);

        // extend annotations to ends of sequence
        if (nTag != null) {
            AnnotationRegion r = nTag.regions.elementAt(0);
            if (r.start != 0) {
                r.length += r.start;
                r.start = 0;
            }
        }
        if (cTag != null) {
            int rafLength = RAF.getSeqLength(rafBody);
            AnnotationRegion r = cTag.regions.elementAt(0);
            if (r.start+r.length != rafLength) {
                r.length = rafLength-r.start;
            }
        }

        // check that inferred tags are not in sequence annotated as uniprot
        if (inferredTags) {
            AnnotationSet asUniprot = new AnnotationSet(astralChainID);
            asUniprot.load(stmt);
            rs = stmt.executeQuery("select id, db_code, pdb_align_start, pdb_align_end from pdb_chain_dbref where pdb_chain_id="+pdbChainID+" and db_name='UNP' and (pdb_align_start is not null or pdb_align_end is not null)");
            while (rs.next()) {
                int refID = rs.getInt(1);
                String refCode = rs.getString(2);
                int start = rs.getInt(3);
                if (rs.wasNull())
                    start = 0;
                int end = rs.getInt(4);
                if (rs.wasNull())
                    end = RAF.getSeqLength(rafBody)-1;
                Annotation a = new Annotation(Annotation.Source.UNKNOWN,
                                              -1,
                                              start,
                                              end-start+1);
                a.hitNodeID = refID;
                a.info = refID+" "+refCode+" annot";
                asUniprot.annotations.add(a);
            }
            rs.close();

            if (asUniprot.annotations.size()==0) {
                // infer uniprot annotations from chains with same sequence
                rs = stmt.executeQuery("select d.id, d.db_code, d.pdb_align_start, d.pdb_align_end from pdb_chain_dbref d, raf r, astral_chain ac where ac.seq_id="+seqID+" and ac.source_id=2 and ac.raf_id=r.id and r.pdb_chain_id=d.pdb_chain_id and d.db_name='UNP' and (pdb_align_start is not null or pdb_align_end is not null)");
                while (rs.next()) {
                    int refID = rs.getInt(1);
                    String refCode = rs.getString(2);
                    int start = rs.getInt(3);
                    if (rs.wasNull())
                        start = 0;
                    int end = rs.getInt(4);
                    if (rs.wasNull())
                        end = RAF.getSeqLength(rafBody)-1;
                    Annotation a = new Annotation(Annotation.Source.UNKNOWN,
                                                  -1,
                                                  start,
                                                  end-start+1);
                    a.hitNodeID = refID;
                    a.info = refID+" "+refCode+" sameseq";
                    asUniprot.annotations.add(a);
                }
                rs.close();
            }

            // shrink proposed tags where they overlap with uniprot seq
            Annotation a = null;
            if (nTag != null) 
                a = asUniprot.getBestMatch(nTag);
            if (a != null) {
                int olap = a.nOverlap(nTag);
                AnnotationRegion r = nTag.regions.elementAt(0);
                boolean killTag = true;
                if (olap < r.length) {
                    // can shrink tag
                    r.length -= olap;
                    // check that nothing else overlaps
                    Annotation a2 = asUniprot.getBestMatch(nTag);
                    if (a2 == null)
                        killTag = false;
                }

                System.out.println("\ndebug: "+pdbChainID+" "+seqID);
                if (killTag) {
                    System.out.println("debug: eliminating nTag "+nTag.toString());
                    System.out.println("debug: uniprot "+a.toString());
                    nTag = null;
                }
                else {
                    System.out.println("debug: shortening nTag "+nTag.toString());
                    System.out.println("debug: uniprot "+a.toString());
                }
                a = null;
            }
            if (cTag != null) 
                a = asUniprot.getBestMatch(cTag);
            if (a != null) {
                int olap = a.nOverlap(cTag);
                AnnotationRegion r = cTag.regions.elementAt(0);
                boolean killTag = true;
                if (olap < r.length) {
                    // can shrink tag
                    r.start += olap;
                    r.length -= olap;
                    // check that nothing else overlaps
                    Annotation a2 = asUniprot.getBestMatch(cTag);
                    if (a2 == null)
                        killTag = false;
                }
                
                System.out.println("\ndebug: "+pdbChainID+" "+seqID);
                if (killTag) {
                    System.out.println("debug: eliminating cTag "+cTag.toString());
                    System.out.println("debug: uniprot "+a.toString());
                    cTag = null;
                }
                else {
                    System.out.println("debug: shortening cTag "+cTag.toString());
                    System.out.println("debug: uniprot "+a.toString());
                }
            }
        }

        // get actual tag sequences
        String nTagSeq = "-";
        if (nTag != null) {
            AnnotationRegion r = nTag.regions.elementAt(0);
            nTagSeq = RAF.partialChainSeq(rafBody,
                                          sourceType,
                                          r.start,
                                          r.start+r.length-1).getSequence();
        }
        String cTagSeq = "-";
        if (cTag != null) {
            AnnotationRegion r = cTag.regions.elementAt(0);
            cTagSeq = RAF.partialChainSeq(rafBody,
                                          sourceType,
                                          r.start,
                                          r.start+r.length-1).getSequence();
        }

        if ((nTag != null) || (cTag != null))
            System.out.println((inferredTags? 2 : 1)+" "+sid+" "+seq+" "+nTagSeq+" "+cTagSeq);

        // record tags in table
        if ((seq.startsWith(nTagSeq)) && (nTag != null)) {
            AnnotationRegion r = nTag.regions.elementAt(0);
            stmt.executeUpdate("delete from pdb_chain_tag where pdb_chain_id="+pdbChainID+" and tag_start=0");
            stmt.executeUpdate("insert into pdb_chain_tag values (null, "+
                               pdbChainID+", 0, "+
                               (r.length-1)+", "+
                               nTag.hitNodeID+")");
        }
        else if (nTag != null)
            throw new Exception("error in ntag");

        if ((seq.endsWith(cTagSeq)) && (cTag != null)) {
            AnnotationRegion r = cTag.regions.elementAt(0);
            stmt.executeUpdate("delete from pdb_chain_tag where pdb_chain_id="+pdbChainID+" and tag_start>0");
            stmt.executeUpdate("insert into pdb_chain_tag values (null, "+
                               pdbChainID+", "+
                               r.start+", "+
                               (r.start+r.length-1)+", "+
                               cTag.hitNodeID+")");
        }
        else if (cTag != null)
            throw new Exception("error in ctag");
        stmt.close();

        rv[0] = nTag;
        rv[1] = cTag;
        return rv;
    }

    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            Statement stmt2 = LocalSQL.createStatement();
            ResultSet rs, rs2;

            // don't clear out old tag table
            // stmt.executeUpdate("truncate table pdb_chain_tag");

            // do last release, public or not
            int scopReleaseID = LocalSQL.getLatestSCOPRelease(false);
            int lastPublicRelease = LocalSQL.getLatestSCOPRelease(true);
            
            // If new class added, and not public release, make changes live
            int tagFamID = LocalSQL.lookupNodeBySCCS("l.1.1.1",
                                                     scopReleaseID);
            if ((lastPublicRelease == scopReleaseID) &&
                (argv.length == 0))
                tagFamID = 0;  // avoid changing public release

            if (tagFamID>0)
                startLiveChanges();

            // work on a specific chain
            if (argv.length > 0) {
                if (argv[0].startsWith("N")) {
                    int nodeID = StringUtil.atoi(argv[0],1);
                    rs = stmt.executeQuery("select old_node_id from scop_history where change_type_id=12 and release_id="+scopReleaseID+" and old_node_id="+nodeID);
                    if (!rs.next()) {
                        throw new Exception("Node must have been promoted in current release");
                    }
                    rs.close();
                    rs = stmt.executeQuery("select ac.id from astral_chain ac, raf r, link_pdb l where l.node_id="+nodeID+" and l.pdb_chain_id=r.pdb_chain_id and r.last_release_id="+scopReleaseID+" and ac.raf_id=r.id and ac.source_id=1");
                    if (!rs.next()) {
                        throw new Exception("Node must have astral chain already");
                    }
                    int astralChainID = rs.getInt(1);
                    rs.close();
                    System.out.println("Trying to find tags for node "+nodeID);
                    HashSet<Integer> tags = splitTagDomains(astralChainID);
                    for (Integer i : tags) {
                        System.out.println("Created tag node "+i);
                        stmt.executeUpdate("update scop_node set sunid="+LocalSQL.getNextSunid()+" where id="+i);
                    }
                }
                else
                    throw new Exception("Must specify node ID as Nxxxxx");
                System.exit(0);
            }
            
            // seqs in which tags have been found
            HashSet<Integer> doneChains = new HashSet<Integer>();

            // get all chain seqs with seqadv records
            // look in seqres first, then atom
            rs = stmt.executeQuery("select ac.id from astral_chain ac, raf r, pdb_chain_diff d, pdb_chain_diff_category dc where ac.source_id<3 and ac.raf_id=r.id and r.last_release_id="+scopReleaseID+" and r.pdb_chain_id=d.pdb_chain_id and d.category_id=dc.id and (dc.description like '%tag' or dc.description like '%clon%' or dc.description='LEADER SEQUENCE') group by ac.id order by ac.source_id desc");
            while (rs.next()) {
                int astralChainID = rs.getInt(1);

                splitTagDomains(astralChainID);

                doneChains.add(new Integer(astralChainID));
            }
            rs.close();

            // sort long tags
            initLongTags();

            // find unreported tags, same seq or long tag
            rs = stmt.executeQuery("select ac.id from astral_chain ac, raf r where ac.source_id<3 and ac.raf_id=r.id and r.last_release_id="+scopReleaseID+" order by ac.source_id desc");
            while (rs.next()) {
                int astralChainID = rs.getInt(1);

                if (doneChains.contains(new Integer(astralChainID)))
                    continue;

                splitTagDomains(astralChainID);
            }
            rs.close();
            stmt.close();
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
