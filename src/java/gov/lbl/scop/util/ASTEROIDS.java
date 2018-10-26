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
package gov.lbl.scop.util;

import gov.lbl.scop.local.LocalSQL;
import org.strbio.util.StringUtil;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities related to ASTEROIDS creation
 */
public class ASTEROIDS {
    /**
       class representing a continuous region of a
       match to a known SCOP domain/family
    */
    public static class AnnotationRegion implements Comparable<AnnotationRegion> {
        /**
         * start is a 0-indexed location of the start of this
         * annotation, or -1 for undefined
         */
        public int start;

        /**
         * length is 0 for undefined, otherwise positive
         */
        public int length;

        public AnnotationRegion() {
            start = -1;
            length = 0;
        }

        /**
         * create an annotation region with a given start,
         * length
         */
        public AnnotationRegion(int s, int l) {
            start = s;
            length = l;
        }

        /**
         * copy another annotation region
         */
        public AnnotationRegion(AnnotationRegion b) {
            start = b.start;
            length = b.length;
        }

        /**
         * get a sequence corresponding to this region, to use in
         * constructing the full sequence of an ASTEROIDS hit
         */
        final public String getSequence(String seq) {
            return seq.substring(start, start + length);
        }

        /**
         * get header for this fragment, using indexing starting at 1,
         * as in classic ASTEROIDS
         */
        final public String getHeader() {
            return (start + 1) + "-" + (start + length);
        }

        /**
         * get header for this fragment, using RAF indexing
         * as in SCOP.  Note that SCOP boundaries must be on
         * residues that have ATOM records and residue ids,
         * so the region may be truncated to reflect that.
         * If no such residues are in this fragment, returns
         * null.
         */
        final public String getHeader(String rafLine) {
            char chain = rafLine.charAt(4);
            String rafBody = rafLine.substring(38);

            int indexStart = RAF.translateIndex(rafBody,
                                                start,
                                                2);
            if (indexStart == -1)
                return null;
            indexStart = RAF.findNearestATOM(rafBody,
                                             indexStart,
                                             true);
            if (indexStart == -1)
                return null;
            int indexEnd = RAF.translateIndex(rafBody,
                                              start + length - 1,
                                              2);
            if (indexEnd == -1)
                return null;
            indexEnd = RAF.findNearestATOM(rafBody,
                                           indexEnd,
                                           false);
            if (indexEnd == -1)
                return null;
            if (indexStart > indexEnd)
                return null;

            String resIDStart = RAF.getResID(rafBody, indexStart);
            String resIDEnd = RAF.getResID(rafBody, indexEnd);

            String firstResID = rafLine.substring(28, 33).trim();
            String lastResID = rafLine.substring(33, 38).trim();

            if (resIDStart.equals(firstResID) &&
                resIDEnd.equals(lastResID)) {
                // just chain; e.g., A: or -
                if (chain == '_')
                    return "-";
                else
                    return chain + ":";
            }
            if (chain != '_')
                resIDStart = chain + ":" + resIDStart;

            return resIDStart + "-" + resIDEnd;
        }

        /**
         * Does the region span the entire chain?
         *
         * Checks if start is 0 or the residue at index (start-1) has a resID of 'B'.
         * Checks if end is length-1 or residue at index length has a resID of 'E'
         *
         * TODO: add test
         *
         * @param rafLine
         * @return true if the region spans the chain, false otherwise
         */
        final public boolean spansChain(String rafLine) {
            char chain = rafLine.charAt(4);
            String rafBody = rafLine.substring(38);

            int seqLength = RAF.getSeqLength(rafBody);

            boolean startsAtNTerminus = false;
            boolean endsAtCTerminus = false;

            // either the index starts at 0 or the preceding residue is missing labeled with a "B" in RAF

            if (start == 0)
                startsAtNTerminus = true;
            else {
                String startResID = RAF.getResID(rafBody, start - 1);
                if (startResID.equals("B"))
                    startsAtNTerminus = true;
            }

            // either the length is the entire sequence, the next residue is missing and labeled with a "B" in RAF
            if (start + length - 1 == seqLength)
                endsAtCTerminus = true;
            else {
                String endResID = RAF.getResID(rafBody, start + length);
                if (endResID.equals("E"))
                    endsAtCTerminus = true;
            }
            return startsAtNTerminus && endsAtCTerminus;
        }

        /**
         * expand one end of this region until we hit missing ATOM or end
         * if (forward), extends length.  if (!forward), reduces start
         * while extending length
         */
        final public void extendToNearEnd(String rafLine,
                                          boolean forward) {
            char chain = rafLine.charAt(4);
            String rafBody = rafLine.substring(38);

            int indexStart = RAF.translateIndex(rafBody,
                                                start,
                                                2);
            if (indexStart == -1)
                return;

            if (!forward) {
                indexStart = RAF.extendToGap(rafBody,
                                             indexStart,
                                             false);

                // NKF - 25-Aug-2014, commented out these lines
                //                String resIDStart = rafLine.substring(28, 33).trim();
                //                int indexStart2 = RAF.indexOf(rafBody, resIDStart, true);
                //
                //                int diff = indexStart - indexStart2;
                //                int nGaps = RAF.nGaps(rafBody,
                //                        indexStart2,
                //                        indexStart);
                //                int nNonGaps = diff + 1 - nGaps;
                //                if (nNonGaps > 3)
                //                    indexStart = indexStart2;


            }
            if (indexStart == -1)
                return;

            int indexEnd = RAF.translateIndex(rafBody,
                                              start + length - 1,
                                              2);
            if (indexEnd == -1)
                return;

            if (forward) {
                indexEnd = RAF.extendToGap(rafBody,
                                           indexEnd,
                                           true);

                // NKF - 25-Aug-2014, commented out these lines
                //                String resIDEnd = rafLine.substring(33, 38).trim();
                //                int indexEnd2 = RAF.indexOf(rafBody, resIDEnd, false);
                //
                //                int diff = indexEnd2 - indexEnd;
                //                int nGaps = RAF.nGaps(rafBody,
                //                        indexEnd,
                //                        indexEnd2);
                //                int nNonGaps = diff + 1 - nGaps;
                //                if (nNonGaps > 3)
                //                    indexEnd = indexEnd2;
            }
            if (indexEnd == -1)
                return;

            if (indexStart > indexEnd)
                return;

            start = RAF.rTranslateIndex(rafBody,
                                        indexStart,
                                        2);
            if (start == -1)
                return;

            int end = RAF.rTranslateIndex(rafBody,
                                          indexEnd,
                                          2);

            if (end >= start)
                length = end - start + 1;
        }


        /**
         * is the region isolated, or are there adjacent ATOMRES residues?
         *
         * @param rafLine
         * @return
         */
        public boolean isRegionIsolated(String rafLine) {
            String rafBody = RAF.getRAFBody(rafLine);
            int indexStart = RAF.extendToGap(rafBody, start, false);
            int indexEnd = RAF.extendToGap(rafBody, start + length - 1, true);
            int newLength = indexEnd - indexStart + 1;
            // these don't work
            //region2.extendToNearEnd(rafLine, true);
            //region2.extendToNearEnd(rafLine, false);
            if (newLength == length) {
                return true;
            }
            return false;
        }

        /**
         * parse region header for this fragment, using RAF indexing
         * as in SCOP.  Sets start/length appropriately.  Leave
         * them both unchanged if error.
         *
         * @param region                  example "4-106"
         * @param rafLine                 the full RAF line
         * @param adjustForMissingATOMRes true if regions should be adjusted to include only residues are not in ATOM res
         * @throws Exception This will thrown an exception if the adjustForMissingATOMRes is false and a residue in the region is not included in the raf line
         */
        final public void parseRegion(String region,
                                      String rafLine,
                                      boolean adjustForMissingATOMRes
                                      ) throws Exception {
            Pattern regionPattern = Pattern.compile("\\s*(\\S+)-(\\S+)\\s*$");
            String rafBody = rafLine.substring(38);

            int pos = region.indexOf(':');
            if (pos > -1)
                region = region.substring(pos + 1);

            // figure out boundaries
            Matcher m = regionPattern.matcher(region);
            String resIDStart = null;
            String resIDEnd = null;
            if (m.matches()) {
                resIDStart = m.group(1);
                resIDEnd = m.group(2);
            }
            else if ((region.length() == 0) ||
                       (region.equals("-"))) {
                // use whole region
                resIDStart = rafLine.substring(28, 33).trim();
                resIDEnd = rafLine.substring(33, 38).trim();
            }
            else
                throw new Exception("Couldn't parse region " + region);

            int indexStart = RAF.indexOf(rafBody, resIDStart, true);
            int indexEnd = RAF.indexOf(rafBody, resIDEnd, false);
            start = RAF.rTranslateIndex(rafBody, indexStart, 2);

            // Check if start or end residues were not found
            if (start == -1) {  // the start index in the region parameter string does not have a matching ATOMRES residue.
                if (adjustForMissingATOMRes) {
                    int rafResidueIndex = RAF.translateIndex(rafBody, Integer.parseInt(resIDStart), 2);
                    if (rafResidueIndex < 0)
                        throw new Exception("Problem translating index " + resIDStart + " in RAF body " + rafBody);
                    indexStart = RAF.findNearestATOM(rafBody, rafResidueIndex, true);
                    start = RAF.rTranslateIndex(rafBody, indexStart, 2);
                }
                else {
                    throw new Exception("Problem with getting index for end residue " + region + " " + rafLine);
                }
            }

            int end = RAF.rTranslateIndex(rafBody, indexEnd, 2);
            if (end == -1) {
                if (adjustForMissingATOMRes) {
                    int rafResidueIndex = RAF.translateIndex(rafBody, Integer.parseInt(resIDEnd), 2);
                    if (rafResidueIndex < 0)
                        throw new Exception("Problem translating index " + resIDEnd + " in RAF body " + rafBody);
                    indexEnd = RAF.findNearestATOM(rafBody, rafResidueIndex, false);
                    end = RAF.rTranslateIndex(rafBody, indexEnd, 2);
                }
                else {
                    throw new Exception("Problem with getting index for end residue " + region + " " + rafLine);
                }
            }

            if (end >= start)
                length = end - start + 1;
        }

        /**
         * How many characters does this overlap with another
         * region?
         */
        final public int nOverlap(AnnotationRegion b) {
            int end1 = start + length - 1;
            int end2 = b.start + b.length - 1;
            int olap = Math.min(end1, end2) - Math.max(start, b.start) + 1;
            if (olap > 0)
                return olap;
            else
                return 0;
        }

        /**
         * How many characters do not overlap with another
         * region?
         */
        final public int nUnmatched(AnnotationRegion b) {
            return length + b.length - 2 * nOverlap(b);
        }

        /**
         * What is longest region that does not overlap with another
         * region (longest in either region)?
         */
        final public int maxUnmatched(AnnotationRegion b) {
            int end1 = start + length - 1;
            int end2 = b.start + b.length - 1;
            // limit on max length catches those that have
            // no overlap
            return Math.min(Math.max(length, b.length),
                            Math.max(Math.abs(end1 - end2),
                                     Math.abs(start - b.start)));
        }

        /**
         * Check if region (or part of it) is in atom res
         *
         * @param rafBody body of rafLine for chain
         * @return
         */
        public boolean isRegionInAtomRes(String rafBody) {
            for (int i = 0; i < length; i++) {
                String currentRes = RAF.getResID(rafBody, start + i);
                if (currentRes.equals("B") || currentRes.equals("M") || currentRes.equals("E")) {
                    continue;
                }
                else {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            String outString = "AnnotationRegion: start: " + start + " length: " + length + "\n";
            return outString;
        }

        /**
         * Compare start with that of a second region
         *
         * @param o
         * @return
         */
        @Override
        public int compareTo(AnnotationRegion o) {
            return start - o.start;
        }

        /**
         * Get the regions with associated ATOMRES IDs inside a region
         *
         * @param rafBody
         * @return
         */
        public ArrayList<AnnotationRegion> getAtomResRegions(String rafBody) {
            ArrayList<AnnotationRegion> regions = new ArrayList<AnnotationRegion>();
            AnnotationRegion region = new AnnotationRegion();
            for (int i = 0; i < length; i++) {
                int currentResIndex = start + i;
                String currentRes = RAF.getResID(rafBody, currentResIndex);
                if (currentRes.equals("B") || currentRes.equals("M") || currentRes.equals("E")) {
                    // a new numbered region has begun
                    if (region.start >= 0) {
                        regions.add(region);
                        region = new AnnotationRegion();
                    }
                }
                else {
                    if (region.start == -1) {
                        region.start = currentResIndex;
                        region.length = 1;
                    }
                    else {
                        region.length++;
                    }
                }
            }
            if (region.start >= 0) {
                regions.add(region);
            }
            return regions;
        }
    }


    /**
     * class representing a possible match to a known SCOP domain/family
     */
    public static class Annotation implements Comparable<Annotation> {
        /**
         * regions of an annotation; these are assumed not to
         * overlap within a single annotation
         */
        public Vector<AnnotationRegion> regions;

        /**
         * Enum type for types of annotation sources
         */
        public enum Source {
            UNKNOWN, BLAST, PFAM, FAM, SF, SCOPSEQMATCH
                };

        /**
         * where did this annotation come from?
         */
        public Source source;

        /**
         * Confidence in the annotation
         * HIGH: Promote, highly confident in the annotation
         * MEDIUM: Promote, but should be checked by an expert in the future
         * LOW: Use as a "first-guess" for an expert to check
         */
        public enum ConfidenceLevel {
            HIGH, MEDIUM, LOW
                };

        /**
         * The confidence level for the annotation
         *
         * To convert to an int, use: confidenceLevel.ordinal()
         */
        public ConfidenceLevel confidenceLevel;

        /**
         * id of line in table that this came from
         * if source is 0 (blast),
         * then id is from astral_seq_blast table
         * if source is 1 (pfam),
         * then id is from astral_seq_hmm_pfam table
         * if source is 2 (fam),
         * then id is from ?
         * if source is 3 (sf),
         * then id is from ?
         * if source is 4 (scopseqmatch),
         * then id is from the scop_node from which annotation was taken
         */
        public int sourceID;

        /**
         * log10 of E-value of the annotation
         */
        public double log10E;

        /**
         * sccs for family that is assigned
         */
        public String family;

        /**
         * The scop_node ID for the protein level
         */
        public int proteinSourceID;

        /**
         * The scop_node ID for the species level
         */
        public int speciesSourceID;

        /**
         * more info about the hit
         */
        public String info;

        /**
         * The nodeID for the hit domain
         * listed in info
         */
        public int hitNodeID;

        /**
         * sid, if assigned
         */
        public String sid;

        /**
         * sort by start?  false = sort by log10E
         */
        public boolean sortByStart;

        /**
         * Constructor to create an empty annotation.
         * See method implementation for default settings for instance variables.
         */
        public Annotation() {
            regions = new Vector<AnnotationRegion>();
            source = Source.UNKNOWN;
            sourceID = -1;
            log10E = Double.NaN;
            family = "";
            speciesSourceID = -1;
            proteinSourceID = -1;
            info = "";
            sid = null;
            sortByStart = false;
            confidenceLevel = ConfidenceLevel.LOW;
        }

        /**
         * Constructor which creates a new empty annotations, initialized with source info
         * Should call the load() method after this to initialize the other instance variables
         */
        public Annotation(Source s, int sID) {
            regions = new Vector<AnnotationRegion>();
            source = s;
            sourceID = sID;
            log10E = Double.NaN;
            family = "";
            speciesSourceID = -1;
            proteinSourceID = -1;
            info = "";
            sid = null;
            sortByStart = false;
            confidenceLevel = ConfidenceLevel.LOW;
        }

        /**
         * Constructor to create an annotation initialized with source and region
         * This constructor is not meant to be used with the the load() method.
         */
        public Annotation(Source s, int sID, int start, int length) {
            regions = new Vector<AnnotationRegion>();
            regions.add(new AnnotationRegion(start, length));
            source = s;
            sourceID = sID;
            log10E = Double.NaN;
            family = "";
            speciesSourceID = -1;
            proteinSourceID = -1;
            info = "";
            sid = null;
            sortByStart = false;
            confidenceLevel = ConfidenceLevel.LOW;
        }

        /**
         * Copy constructor
         */
        public Annotation(Annotation b) {
            regions = new Vector<AnnotationRegion>();
            for (AnnotationRegion a : b.regions)
                regions.add(new AnnotationRegion(a));
            source = b.source;
            sourceID = b.sourceID;
            log10E = b.log10E;
            family = new String(b.family);
            speciesSourceID = b.speciesSourceID;
            proteinSourceID = b.proteinSourceID;
            info = new String(b.info);
            if (b.sid == null)
                sid = null;
            else
                sid = new String(b.sid);
            sortByStart = b.sortByStart;
            confidenceLevel = b.confidenceLevel;
        }

        /**
         * total length of this annotation
         */
        final public int length() {
            int l = 0;
            for (AnnotationRegion a : regions)
                l += a.length;
            return l;
        }

        /**
           number of observed residues in this annotation
        */
        final public int observedLength(String rafLine) {
            int l = 0;
            String rafBody = RAF.getRAFBody(rafLine);
            for (AnnotationRegion a : regions) {
                ArrayList<AnnotationRegion> observed =
                    a.getAtomResRegions(rafBody);
                for (AnnotationRegion b : observed)
                    l += b.length;
            }
            return l;
        }

        /**
         * get start of this annotation, 0-indexed
         */
        final public int getStart() {
            int s = -1;
            if (regions.size() >= 1) {
                AnnotationRegion a = regions.elementAt(0);
                s = a.start;
            }
            return s;
        }

        /**
         * get end of this annotation, 0-indexed
         */
        final public int getEnd() {
            int e = -1;
            int n;
            if ((n = regions.size()) >= 1) {
                AnnotationRegion a = regions.elementAt(n - 1);
                e = a.start + a.length - 1;
            }
            return e;
        }

        /**
         * total overlap with another annotation
         */
        final public int nOverlap(Annotation b) {
            int olap = 0;
            for (AnnotationRegion i : regions)
                for (AnnotationRegion j : b.regions)
                    olap += i.nOverlap(j);
            return olap;
        }

        /**
         * total unmatched (non-overlapping) with another annotation,
         * with same number of regions and same order
         */
        final public int nUnmatched(Annotation b) {
            int n = regions.size();
            if (b.regions.size() != n)
                return -1;

            int rv = 0;
            for (int i = 0; i < n; i++)
                rv += regions.elementAt(i).nUnmatched(b.regions.elementAt(i));
            return rv;
        }

        /**
         * Biggest non-overlapping area with another annotation,
         * with same number of regions and same order
         *
         * @return -1 if number of regions do not match,
         */
        final public int maxUnmatched(Annotation b) {
            int n = regions.size();
            if (b.regions.size() != n)
                return -1;

            int rv = 0;
            for (int i = 0; i < n; i++)
                rv = Math.max(rv,
                              regions.elementAt(i).maxUnmatched(b.regions.elementAt(i)));
            return rv;
        }

        /**
         * remove overlap with another annotation, by deleting the
         * minimum number of overlapping residues from this one
         */
        final public void removeOverlap(Annotation b) {
            // turn current annotation into a boolean array
            int oldStart = getStart();
            int oldEnd = getEnd();
            int length = oldEnd - oldStart + 1;
            boolean[] bits = new boolean[length];

            // set bits in current annotation to true
            for (AnnotationRegion a : regions)
                Arrays.fill(bits, a.start - oldStart, a.start + a.length - oldStart, true);

            // mask out bits from other annotation
            for (AnnotationRegion a : b.regions) {
                int start = a.start - oldStart;
                int end = a.start + a.length - oldStart;
                // make sure this is within array
                start = Math.max(start, 0);
                end = Math.min(end, length);
                if ((start < length) && (end > 0))
                    Arrays.fill(bits, start, end, false);
            }

            // translate array back to new set of regions
            regions = new Vector<AnnotationRegion>();
            AnnotationRegion a = new AnnotationRegion();
            for (int i = 0; i < length; i++) {
                if ((bits[i] == true) && (a.start == -1))
                    a.start = i + oldStart;
                else if ((bits[i] == false) && (a.start > -1)) {
                    a.length = i - a.start + oldStart;
                    regions.add(a);
                    a = new AnnotationRegion();
                }
            }
            if (a.start > -1) {
                a.length = length - a.start + oldStart;
                regions.add(a);
            }
        }

        /**
         * Get the largest gap size between regions
         */
        final public int maxGapSize() {
            if (regions.size() < 2)
                return 0;
            int maxGapLength = 0;

            AnnotationRegion a = null;
            for (AnnotationRegion b : regions) {
                if (a == null)
                    a = b;
                else {
                    int gapLength = b.start - (a.start + a.length);
                    if (gapLength > maxGapLength)
                        maxGapLength = gapLength;  // found larger gap
                }
                a = b;
            }
            return maxGapLength;
        }


        /**
         * Fill in gaps between regions, up to minGapLength
         * residues (minGapLength will be smallest remaining gap)
         */
        final public void fillGaps(int minGapLength) {
            if (regions.size() < 2)
                return;
            Vector<AnnotationRegion> newRegions =
                new Vector<AnnotationRegion>();
            AnnotationRegion a = null;
            for (AnnotationRegion b : regions) {
                if (a == null)
                    a = new AnnotationRegion(b);
                else {
                    int gapLength = b.start - (a.start + a.length);
                    if (gapLength < minGapLength)
                        a.length += b.length + gapLength;  // merge the gaps
                    else {
                        newRegions.add(a);
                        a = new AnnotationRegion(b);
                    }
                }
            }
            newRegions.add(a);
            regions = newRegions;
        }


        /**
         * expand regions in both directions to edge of gap (missing
         * ATOM) or to end of chain.  Only first and last regions are
         * affected, and regions must be in order (i.e., sorted by
         * start)
         */
        final public void expandToNearEnds(String rafLine) throws Exception {
            int n = regions.size();
            regions.get(0).extendToNearEnd(rafLine,
                                           false);
            regions.get(n - 1).extendToNearEnd(rafLine,
                                               true);
        }

        /**
         * get region(s) for header using indexing starting at 1,
         * as in classic ASTEROIDS
         */
        final public String getHeaderRegions() {
            String rv = "";
            for (AnnotationRegion a : regions)
                rv += a.getHeader() + ",";
            return rv.substring(0, rv.length() - 1);
        }

        /**
         * get header for this fragment, using RAF indexing
         * as in SCOP.  Note that SCOP boundaries must be on
         * residues that have ATOM records and residue ids,
         * so the region may be truncated to reflect that.
         * If no such residues are in this fragment, returns
         * null.
         */
        final public String getHeaderRegions(String rafLine) {
            String rv = null;
            for (AnnotationRegion a : regions) {
                String s = a.getHeader(rafLine);
                if (s != null) {
                    if (rv == null)
                        rv = s;
                    else
                        rv += "," + s;
                }
            }
            if (rv != null)
                rv = rafLine.substring(0, 4) + " " + rv;
            return rv;
        }

        /**
         * parse header for this fragment, using RAF indexing
         * as in SCOP.  Sets up regions.  Works only for a single
         * chain; no multi-chain regions.
         */
        final public void parseHeaderRegions(String description,
                                             String rafLine) throws Exception {
            regions.clear();
            String[] r = description.substring(5).split(",");
            for (String region : r) {
                AnnotationRegion a = new AnnotationRegion();
                a.parseRegion(region, rafLine, true);
                // System.err.println("debug region: "+region+" "+a.start+" "+a.length);
                if (a.start != -1)
                    regions.add(a);
                else {
                    throw new Exception("description \"" + description + "\" includes residues that are missing in the rafLine " + rafLine);
                }
            }
        }

        /**
         * get header used in ASTEROIDS merge files
         */
        final public String getHeaderName(boolean verbose) {
            if (source == Source.UNKNOWN)
                return "[UNMATCHED]";
            if (source == Source.BLAST) {
                int pos = info.indexOf(' ');
                if (pos == -1)
                    return "";
                String matchSid = info.substring(pos + 1);
                if (!verbose)
                    return matchSid + "-" + family;
                else {
                    String ver = info.substring(0, pos);
                    pos = family.lastIndexOf('.');
                    if (pos == -1)
                        return "";
                    String sf = family.substring(0, pos);
                    return "[ASTRAL-" + ver + "-BLAST-" + matchSid + "]" + sf;
                }
            }
            if (!verbose)
                return family;
            if (source == Source.PFAM)
                return "[PFAM-" + info + "]" + family;
            if (source == Source.FAM)
                return "[ASTRALfam-" + info + "]" + family;
            if (source == Source.SF)
                return "[ASTRALsf-" + info + "]" + family;
            if (source == Source.SCOPSEQMATCH) {
                try {
                    int pos = info.indexOf(' ');
                    if (pos == -1)
                        return "";
                    String matchSid = info.substring(pos + 1);
                    if (!verbose)
                        return matchSid + "-" + family;
                    else {
                        String ver = info.substring(0, pos);
                        pos = family.lastIndexOf('.');
                        //if (pos == -1)
                        //    return "";
                        //String sf = family.substring(0, pos);
                        info = info + CommonSCOPQueries.getScopNodeDescription(this.sourceID);
                        return "[ASTRAL-" + ver + "-SCOPSeqMatch-" + matchSid + "]";// + sf;
                    }

                } catch (Exception e) {//do nothing
                }
                //return "[ASTRAL-SCOPSeqMatch-" + info + "]" + family;
            }
            return "";
        }

        /**
         * get the SID from a header string like "(-) ASTEROIDS sf:[ASTRAL-1.75B-SCOPSeqMatch-d1cagb_] logE:0.00 (B:)"
         * Returns null if no sid is found
         *
         * @param header
         * @return
         */
        public static String getSidFromHeaderString(String header) {
            Pattern p = Pattern.compile("\\[ASTRAL-.*-.*-.*\\]");
            Matcher m = p.matcher(header);
            String matchedScopNodeDetails = "unknown";
            if (m.find()) {
                matchedScopNodeDetails = m.group();
            }
            else {
                return null;
            }
            // strip off the two square brackets
            matchedScopNodeDetails = matchedScopNodeDetails.substring(1, matchedScopNodeDetails.length() - 1);
            return matchedScopNodeDetails.split("-")[3];
        }

        /**
         * get a sequence
         */
        final public String getSequence(String seq) {
            String rv = "";
            for (AnnotationRegion a : regions)
                rv += a.getSequence(seq); // +"X";
            // return rv.substring(0,rv.length()-1);
            return rv;
        }

        /**
         * get FASTA header for asteroids
         */
        final public String getHeaderFull(String seq) {
            String E;
            if (source == Source.UNKNOWN)
                E = "UNMATCHED";
            else {
                char[] buffer = new char[10];
                int i = StringUtil.sprintf(buffer, "%.2f", log10E);
                E = new String(buffer).substring(0, i);
            }
            String regionString;
            if ((regions.size() == 1) && (regions.get(0).length == seq.length()))
                regionString = "(-)";
            else
                regionString = "(" + getHeaderRegions() + ")";

            String chainString = "(-)";
            if ((sid != null) && (sid.length() >= 6)) {
                char chain = sid.charAt(5);
                if (chain != '_')
                    chainString = "(" + chain + ":)";
            }

            return regionString + " ASTEROIDS sf:" + getHeaderName(true) + " logE:" + E + " " + chainString;
        }

        /**
         * load in details from database.  source and
         * sourceID must be set.  Fails silently on error.
         * Does not close statement.
         */
        final public void load(Statement stmt) throws Exception {
            String query, gapTable;

            if (source == Source.BLAST) {
                query = "select concat(r.version,' ',d.sid), n.sccs, m.seq1_start, m.seq1_length, m.blast_log10_e, n.id from "
                    + "astral_seq_blast m, astral_domain d, scop_node n, scop_release r where m.id=" + sourceID + " "
                    + "and d.source_id=2 and m.seq2_id=d.seq_id and (m.style2_id=d.style_id or d.style_id=1) "
                    + "and m.release_id = n.release_id and d.node_id=n.id and n.release_id=r.id and n.sccs regexp '^[a-h]' "
                    + "and d.source_id=2 limit 1";
                gapTable = "astral_seq_blast_gap";
            }
            else if (source == Source.PFAM) {
                query = "select r.version, p.accession, m.start, m.length, m.log10_e from astral_seq_hmm_pfam m, pfam p, pfam_release r where m.id=" + sourceID + " and m.pfam_id=p.id and p.release_id=r.id";
                gapTable = "astral_seq_hmm_pfam_gap";
            }
            else if ((source == Source.FAM) || (source == Source.SF)) {
                query = "select r.version, n.sccs, m.start, m.length, m.log10_e from astral_seq_hmm_asteroids m, scop_node n, scop_release r where m.id=" + sourceID + " and m.node_id=n.id and n.sccs regexp '^[a-g]' and n.release_id=r.id";
                gapTable = "astral_seq_hmm_asteroids_gap";
            }
            else if ((source == Source.SCOPSEQMATCH)) {
                throw new Exception("Unsupported for load() call when using SCOPSEQMATCH to annotate.  Must assign attribute values after calling the constructor.");
            }
            else
                return;

            ResultSet rs = stmt.executeQuery(query);
            if (rs.next()) {
                info = rs.getString(1);
                family = rs.getString(2);
                int start = rs.getInt(3);
                int end = rs.getInt(4) + start - 1;
                log10E = rs.getDouble(5);

                if (source == Source.BLAST) {
                    hitNodeID = rs.getInt(6);
                    speciesSourceID = LocalSQL.findParent(hitNodeID, 7);
                    proteinSourceID = LocalSQL.findParent(speciesSourceID, 6);
                }

                rs.close();
                rs = stmt.executeQuery("select gap_start, gap_length from " + gapTable + " where hit_id=" + sourceID);
                while (rs.next()) {
                    int gapStart = rs.getInt(1);
                    int gapEnd = rs.getInt(2) + gapStart - 1;
                    regions.add(new AnnotationRegion(start, gapStart - start));
                    start = gapEnd + 1;
                }
                rs.close();
                regions.add(new AnnotationRegion(start, end - start + 1));
            }
        }

        public int compareTo(Annotation b) {
            if (sortByStart) {
                int aStart = getStart();
                int bStart = b.getStart();
                if (aStart < bStart)
                    return -1;
                else if (aStart > bStart)
                    return 1;
            }
            else {
                // put pfam hits last
                if ((source == Source.PFAM) && (b.source != Source.PFAM))
                    return 1;
                else if ((source != Source.PFAM) && (b.source == Source.PFAM))
                    return -1;

                // otherwise, sort by log10E, lowest to highest
                if (log10E < b.log10E)
                    return -1;
                else if (log10E > b.log10E)
                    return 1;

                // if tie, longest gets priority
                int aLength = length();
                int bLength = b.length();
                if (aLength < bLength)
                    return 1;
                else if (aLength > bLength)
                    return -1;

                // if still a tie, BLAST hits get priority
                if ((source == Source.BLAST) && (b.source != Source.BLAST))
                    return -1;
                else if ((source != Source.BLAST) && (b.source == Source.BLAST))
                    return 1;
            }
            int c = info.compareTo(b.info);
            if (c != 0)
                return c;
            return (family.compareTo(b.family));
        }


        /**
         * Gets the number of residues that are included in the annotation.
         * Warning: If there is an overlap, that residue will count twice.
         *
         * @return the number of residues included in annotation
         */
        public int getNumberAnnotatedResidues() {
            int numRes = 0;
            for (AnnotationRegion region : regions) {
                numRes += region.length;
            }
            return numRes;
        }

        /**
         * Convert the source to a string
         *
         * @param source
         * @return
         */
        public static String sourceToString(Source source) {
            String sourceString = "unknown";
            switch (source) {
            case UNKNOWN:
                sourceString = "unknown";
                break;
            case BLAST:
                sourceString = "BLAST";
                break;
            case PFAM:
                sourceString = "Pfam";
                break;
            case FAM:
                sourceString = "Fam";
                break;
            case SF:
                sourceString = "SF";
                break;
            case SCOPSEQMATCH:
                sourceString = "SCOPSeqMatch";
                break;
            }
            return sourceString;
        }

        /**
         * Convert the string to an Annotation source
         *
         * @param sourceString
         * @return
         */
        public static Source parseSource(String sourceString) {
            Source source = Source.UNKNOWN;
            if (sourceString.equals("BLAST"))
                source = Source.BLAST;
            else if (sourceString.equals("Pfam"))
                source = Source.PFAM;
            else if (sourceString.equals("Fam"))
                source = Source.FAM;
            else if (sourceString.equals("SF"))
                source = Source.SF;
            else if (sourceString.equals("SCOPSeqMatch"))
                source = Source.SCOPSEQMATCH;
            return source;
        }

        /**
         * Intended only for debugging.
         *
         * Here, the contents of every field are placed into the result, with
         * one field per line.
         */
        @Override
        public String toString() {
            String outString = "Annotation: \n";
            outString += " Regions: \n";
            for (AnnotationRegion region : regions) {
                outString += "  " + region.toString();
            }
            outString += " source: " + source + "\n";
            outString += " sourceID: " + sourceID + "\n";
            outString += " log10E: " + log10E + "\n";
            outString += " family: " + family + "\n";
            outString += " info: " + info + "\n";
            outString += " sid: " + sid + "\n";
            outString += " sortByStart: " + sortByStart + "\n";
            outString += " description: " + this.getHeaderRegions() + "\n";
            return outString;
        }
    }

    /**
     * class representing a set of annotations to a chain seq
     */
    public static class AnnotationSet {
        /**
         * Set of annotations, filled by loadAnnotations method
         */
        public Vector<Annotation> annotations;

        /**
         * The astral_chain ID of the query chain
         */
        public int astralChainID;

        /**
         * The id of the hit chain (if there is only one).
         * In most cases, this attribute won't be set
         */
        public int hitChainID;

        /**
         * Set using astralChainID by the load() method
         */
        public String sid;

        /**
         * Set using astralChainID by the load() method
         */
        public String seq;

        /**
         * Set using astralChainID by the load() method
         */
        public String rafLine;

        /**
         * Can hold information on the annotation set and why it may be empty
         */
        public String reportString;

        /**
         * Default constructor:
         * creates an empty Vector for storing annotations,
         * sets astralChainID to -1, and
         * sets sid to a blank string
         */
        public AnnotationSet() {
            annotations = new Vector<Annotation>();
            astralChainID = -1;
            sid = "";
        }

        /**
         * Constructor takes chain ID as a parameter
         * creates an empty Vector for storing annotations,
         * sets astralChainID
         * sets sid to a blank string
         *
         * To collect BLAST annotations, call load() method
         */
        public AnnotationSet(int id) {
            annotations = new Vector<Annotation>();
            astralChainID = id;
            sid = "";
        }

        /**
         * Copy constructor
         *
         * @param as AnnotationSet to be copied
         */
        public AnnotationSet(AnnotationSet as) {
            annotations = new Vector<Annotation>();
            annotations.addAll(as.annotations);
            astralChainID = as.astralChainID;
            hitChainID = as.hitChainID;
            sid = as.sid;
            seq = as.seq;
            rafLine = as.rafLine;
            reportString = as.reportString;
        }


        /**
         * Defines the sid, seq, and rafLine attributes using the astralChainID
         *
         * @param stmt
         * @throws Exception
         */
        final public void load(Statement stmt) throws Exception {
            ResultSet rs = stmt.executeQuery("select ac.sid, s.seq, r.line from astral_chain ac, astral_seq s, raf r where r.id=ac.raf_id and ac.seq_id=s.id and ac.id=" + astralChainID);
            if (rs.next()) {
                sid = rs.getString(1);
                seq = rs.getString(2).toLowerCase();
                rafLine = rs.getString(3);
            }
            rs.close();
        }

        /**
         * try to apply a new annotation, according to the ASTEROIDS
         * overlap rules: cannot exceed maxOverlap residues with all
         * other accepted annotation, or 50% of the new annotation's
         * length.
         *
         * If successful, a new annotation corresponding to the
         * accepted part will be returned.  If not, returns null.
         */
        final public Annotation annotate(Annotation a, int maxOverlap) {
            Annotation rv;
            Annotation accepted = new Annotation();
            for (Annotation b : annotations)
                accepted.regions.addAll(b.regions);
            int olap = accepted.nOverlap(a);
            int length = a.length();
            if (olap > Math.min(maxOverlap, length / 2))
                return null; // reject
            rv = new Annotation(a);
            if (olap > 0)
                rv.removeOverlap(accepted);
            annotations.add(rv);
            return rv;
        }

        /**
         * fill in unfilled gaps, up to minGapLength residues
         */
        final public void fillGaps(int minGapLength) {
            Vector<Annotation> newAnnotations = new Vector<Annotation>();
            for (Annotation a : annotations) {
                Annotation filled = new Annotation(a);
                filled.fillGaps(minGapLength);
                boolean ok = true;
                for (Annotation b : annotations) {
                    if (!a.equals(b)) {
                        if (filled.nOverlap(b) > 0)
                            ok = false;
                    }
                }
                if (ok)
                    newAnnotations.add(filled);
                else
                    newAnnotations.add(a);
            }
            annotations = newAnnotations;
        }

        /**
         * Extend regions in ATOMRES, up to size maxDomainExtension
         *
         * @param maxDomainExtension the max number of residues to extend regions
         *
         * @throws Exception
         */
        public void extendRegionsInAtomRes(int maxDomainExtension) throws Exception {

            // just return if there are no annotations
            if (annotations.size() == 0) {
                return;
            }

            // Collect all regions in the chain, sorted by start
            Vector<ASTEROIDS.AnnotationRegion> regions = this.getAllRegions();

            // expand to beginning and end of chains (termini, or to gap).
            ASTEROIDS.AnnotationRegion firstRegion = regions.firstElement();
            ASTEROIDS.AnnotationRegion firstRegion2 = new ASTEROIDS.AnnotationRegion(firstRegion);
            firstRegion2.extendToNearEnd(rafLine, false);
            if (firstRegion2.length - firstRegion.length <= maxDomainExtension) {
                firstRegion.start = firstRegion2.start;
                firstRegion.length = firstRegion2.length;
            }

            ASTEROIDS.AnnotationRegion lastRegion = regions.lastElement();
            ASTEROIDS.AnnotationRegion lastRegion2 = new ASTEROIDS.AnnotationRegion(lastRegion);
            lastRegion2.extendToNearEnd(rafLine, true);

            if (lastRegion2.length - lastRegion.length <= maxDomainExtension) {
                lastRegion.length = lastRegion2.length;
            }

            // now, try to fill to gap
            for (int i = 0; i < regions.size() - 1; i++) {
                ASTEROIDS.AnnotationRegion region1 = regions.get(i);
                ASTEROIDS.AnnotationRegion region2 = regions.get(i + 1);
                ASTEROIDS.AnnotationRegion region1New = new ASTEROIDS.AnnotationRegion(region1);
                ASTEROIDS.AnnotationRegion region2New = new ASTEROIDS.AnnotationRegion(region2);
                region1New.extendToNearEnd(rafLine, true);
                region2New.extendToNearEnd(rafLine, false);
                if (((region1New.start + region1New.length - 1) < region2.start) && (region1New.length - region1.length <= maxDomainExtension)) {
                    region1.start = region1New.start;
                    region1.length = region1New.length;
                }
                if (((region1.start + region1.length - 1) < region2New.start) && (region2New.length - region2.length <= maxDomainExtension)) {
                    region2.start = region2New.start;
                    region2.length = region2New.length;
                }
            }
        }

        /**
         * Adds in "unmatched" annotations, for every gap of at least
         * minLength residues.
         *
         * @return list of regions that were found to not be annotated
         */
        final public ArrayList<AnnotationRegion> addUnmatched(int minLength) throws Exception {
            ArrayList<AnnotationRegion> unmatchedRegions = new ArrayList<AnnotationRegion>();
            if (seq == null)
                throw new Exception("seq must be set before calling addUnmatched");
            Annotation accepted = new Annotation();
            for (Annotation a : annotations)
                accepted.regions.addAll(a.regions);
            Annotation a = new Annotation(Annotation.Source.UNKNOWN, -1, 0, seq.length());
            a.removeOverlap(accepted);
            for (AnnotationRegion ar : a.regions) {
                if (ar.length >= minLength) {
                    unmatchedRegions.add(ar);
                    annotations.add(new Annotation(Annotation.Source.UNKNOWN, -1, ar.start, ar.length));
                }
            }
            return unmatchedRegions;
        }

        /**
         * What are all the regions with no annotation?
         *
         * @param atomResOnly if true, only the observed residues (in ATOMRES) are considered
         */
        public ArrayList<ASTEROIDS.AnnotationRegion> getNonAnnotatedRegions(boolean atomResOnly) throws Exception {
            Statement stmt = LocalSQL.createStatement();
            ASTEROIDS.AnnotationSet as = new ASTEROIDS.AnnotationSet(astralChainID);
            as.load(stmt);
            for (ASTEROIDS.Annotation annotation : this.annotations) {
                as.annotate(annotation, 0);
            }
            ArrayList<ASTEROIDS.AnnotationRegion> unmatchedRegions = as.addUnmatched(0);

            String rafBody = RAF.getRAFBody(rafLine);
            if (atomResOnly) { // remove regions not in atomres
                ArrayList<ASTEROIDS.AnnotationRegion> atomResUnmatchedRegions = new ArrayList<AnnotationRegion>();
                for (ASTEROIDS.AnnotationRegion annotationRegion : unmatchedRegions) {
                    if (annotationRegion.isRegionInAtomRes(rafBody)) {
                        ArrayList<AnnotationRegion> atomResRegions = annotationRegion.getAtomResRegions(rafBody);
                        for (AnnotationRegion region : atomResRegions)
                            atomResUnmatchedRegions.add(region);
                    }
                }
                unmatchedRegions = atomResUnmatchedRegions;
            }
            return unmatchedRegions;
        }

        /**
         * Sorts annotations and assigns sids
         *
         * the sids will be of the form:
         * u1jmca2
         *
         */
        final public void assignSids() throws Exception {
            assignSids("u");
        }

        /**
         * Sorts annotations and assigns sids
         * @param prefix the sid prefix
         */
        final public void assignSids(String prefix) throws Exception {
            int n = annotations.size();
            // if the annotation spans the chain, the sid ends with '_'
            if (n == 1 ) {
                String description = annotations.get(0).getHeaderRegions(rafLine);
                if (description.endsWith(":")) {
                    annotations.get(0).sid = prefix + sid + "_";
                    return;
                }
            }
            for (Annotation a : annotations)
                a.sortByStart = true;
            Collections.sort(annotations);
            char domID = '1';
            for (Annotation a : annotations) {
                a.sid = prefix + sid + domID;
                if (domID == '9')
                    domID = 'a';
                else if (domID == 'z')
                    domID = 'A';
                else if (domID == 'Z')
                    domID = '0';
                else if (domID == '0')
                    throw new Exception("Out of single letter domain ids");
                else
                    domID++;
            }
        }

        /**
         * load all annotations for a sequence, and sort them
         *
         * Currently only uses BLAST-based annotations.
         *
         * If using BLAST, the SCOP release ID is set to the release on which BLAST was run.
         * For example, to collect all BLAST hits against SCOP release 1.75, the ID should be set to 11.
         *
         * @param stmt          Statement for connection to SCOP DB.  Need read permissions only.
         * @param scopReleaseID The ID of the SCOP release, from the scop_release table
         * @param pfamReleaseID The ID of the PFAM release, from the pfam_release table
         * @throws Exception
         */
        final public void loadAnnotations(Statement stmt,
                                          int scopReleaseID,
                                          int pfamReleaseID) throws Exception {
            // for 1.75A/B:  BLAST only!
            for (int queryType = 0; queryType < 1; queryType++) {
                String query;
                Annotation.Source annotationSource = Annotation.Source.UNKNOWN;

                if (queryType == 0) {
                    annotationSource = Annotation.Source.BLAST;
                    // first, get the most recent release when blast was run
                    query = "select distinct m.id, m.seq2_id, d.style_id from astral_seq_blast m, astral_domain d, scop_node n, astral_chain ac where ac.id=" + astralChainID + " and m.seq1_id=ac.seq_id and m.seq2_id=d.seq_id and d.node_id=n.id and d.source_id=2 and m.source_id=2 and m.style1_id=1 and (m.style2_id=d.style_id or d.style_id=1) and m.blast_log10_e <= -4 and n.sccs regexp '^[a-h]' and m.release_id=n.release_id and n.release_id=" + scopReleaseID;
                }
                else if (queryType == 1) {
                    annotationSource = Annotation.Source.PFAM;
                    query = "select distinct(m.id) from astral_seq_hmm_pfam m, pfam p, astral_chain ac where ac.seq_id=m.seq_id and ac.id=" + astralChainID + " and m.pfam_id=p.id and m.log10_e <= -2 and p.release_id=" + pfamReleaseID;
                }
                else {
                    query = "select distinct(m.id) from astral_seq_hmm_asteroids m, scop_node n, astral_chain ac where ac.seq_id=m.seq_id and ac.id=" + astralChainID + " and m.node_id=n.id and m.log10_e <= -4 and n.sccs regexp '^[a-g]' and n.level_id=";
                    if (queryType == 2) {
                        annotationSource = Annotation.Source.FAM;
                        query += "5";
                    } else {
                        annotationSource = Annotation.Source.SF;
                        query += "4";
                    }
                    query += " and n.release_id=" + scopReleaseID;
                }

                // System.out.println(query);
                ResultSet rs = stmt.executeQuery(query);
                while (rs.next()) {
                    Annotation a = new ASTEROIDS.Annotation(annotationSource,
                                                            rs.getInt(1));
                    annotations.add(a);
                }
                rs.close();
            }

            for (Annotation a : annotations)
                a.load(stmt);

            Collections.sort(annotations);
        }

        /**
         * load all Pfam annotations for a sequence, and sort them
         *
         * @param stmt          Statement for connection to SCOP DB.  Need read permissions only.
         * @param pfamReleaseID The ID of the PFAM release, from the pfam_release table
         * @throws Exception
         */
        final public void loadPfamAnnotations(Statement stmt,
                                              int pfamReleaseID) throws Exception {
            String query = "SELECT distinct(m.id) ";
            query += "FROM astral_seq_hmm_pfam m, pfam p, astral_chain ac ";
            query += "WHERE ac.seq_id=m.seq_id ";
            query += " and m.pfam_id=p.id";
            query += " and m.score>=p.trusted_cutoff";  // collect significant pfam hits
            query += " and p.release_id=" + pfamReleaseID;
            query += " and ac.id=" + astralChainID ;
            ResultSet rs = stmt.executeQuery(query);
            while (rs.next()) {
                Annotation a = new ASTEROIDS.Annotation(Annotation.Source.PFAM, rs.getInt(1));
                annotations.add(a);
            }
            rs.close();
            for (Annotation a : annotations)
                a.load(stmt);
            Collections.sort(annotations);
        }

        /**
         * Remove all annotations to hits to genetic domains from set
         */
        final public void removeGeneticDomainHitAnnotations() {
            Vector<Annotation> annotations2 = new Vector<Annotation>();

            for (Annotation annotation : annotations) {
                // first, get the sid of the node hit by blast
                int pos = annotation.info.indexOf(' ');
                if (pos == -1)
                    continue;
                String matchSid = annotation.info.substring(pos + 1);
                // if the sid of the matched note is a genetic domain, remove
                // this annotation
                if (!matchSid.contains("."))
                    annotations2.add(annotation);
                //annotations.remove(annotation);

            }
            annotations = annotations2;
        }

        /**
         * Remove all annotations from hits to domains where
         * where domain size has changed by more than 10
         * residues between scopReleaseID and latest (public)
         * SCOP release.
         */
        final public void removeInvalidHitAnnotations(int scopReleaseID) throws Exception {
            Vector<Annotation> annotations2 = new Vector<Annotation>();

            for (Annotation annotation : annotations) {
                // first, get the hit scop_node from the blast ID
                String hitSid = annotation.info.split(" ")[1];
                if (hitSid.charAt(0) == 'g') {
                    hitSid = 'd' + hitSid.substring(1);
                }
                int hitScopNodeID = CommonSCOPQueries.getScopNodeBySidAndReleaseID(hitSid, scopReleaseID);
                int hitScopSunID = CommonSCOPQueries.getSunID(hitScopNodeID);

                int currentScopNodeID = CommonSCOPQueries.getScopNodeBySunIDAndReleaseID(hitScopSunID, LocalSQL.getLatestSCOPRelease(true));
                if (currentScopNodeID < 0) {
                    continue;  // node not found in current release
                }

                int seqLength1 = CommonSCOPQueries.getSeqLengthFromScopNodeID(hitScopNodeID);
                int seqLength2 = CommonSCOPQueries.getSeqLengthFromScopNodeID(currentScopNodeID);
                int seqDiff = seqLength2 - seqLength1;
                if (seqDiff > 10 || seqDiff < -10) {
                    continue;
                }

                /*
                  Replace next two queries with above

                  // second, get the sunid of the scop_node
                  int nextScopNodeID = CommonSCOPQueries.getScopNodeInNextRelease(hitScopNodeID);
                  if (nextScopNodeID < 0) {
                  // scop node not found, continue to next annotation
                  continue;
                  }


                  String sccs = CommonSCOPQueries.getSCCS(nextScopNodeID);
                  if (!sccs.equals(annotation.family)) {
                  continue;
                  }
                */

                // If there is a sunid in the next release,
                // get the sccs, and compare with the family of the annotation
                // if different, remove the annotation

                //else, add the annotation to annotations2
                annotations2.add(annotation);
            }
            annotations = annotations2;
        }

        /**
         * Collect annotations that resulted from hits to the same sequence, and only keep the bigger one
         *
         * @param annotationSet
         * @return
         * @throws Exception
         */
        public void removeHitsToSameSeq(ASTEROIDS.AnnotationSet annotationSet) throws Exception {

            // map of SeqID to annotation
            HashMap<Integer, Annotation> hitSeqIDToBestAnnotationTable = new HashMap<Integer, ASTEROIDS.Annotation>();

            for (ASTEROIDS.Annotation currentAnnotation : annotationSet.annotations) {
                Integer hitSeqID = CommonSCOPQueries.getHitSeqID(currentAnnotation.sourceID);
                if (hitSeqIDToBestAnnotationTable.containsKey(hitSeqID)) {
                    ASTEROIDS.Annotation previousAnnotation = hitSeqIDToBestAnnotationTable.get(hitSeqID);
                    if (previousAnnotation.getNumberAnnotatedResidues() < currentAnnotation.getNumberAnnotatedResidues()) {
                        hitSeqIDToBestAnnotationTable.put(hitSeqID, currentAnnotation);
                    }
                }
                else {
                    hitSeqIDToBestAnnotationTable.put(hitSeqID, currentAnnotation);
                }
            }
            annotations.removeAllElements();
            annotations.addAll(hitSeqIDToBestAnnotationTable.values());
        }

        /**
         * Get the annotation in annotation set with the best match
         * or return null if no overlapping region is found
         *
         * @param a the annotation being matched
         * @return the annotation that best matches a,
         * that has an overlap and smallest number of unmatched residues
         */
        public Annotation getBestMatch(Annotation a) {
            int bestMaxError = -1; // max error from a single region end
            Annotation bestAnnotation = null;

            for (Annotation a2 : annotations ) {
                int maxError = a2.maxUnmatched(a);
                int overlap = a2.nOverlap(a);

                if (overlap > 0 && ((bestAnnotation == null) || (maxError < bestMaxError))) {
                    bestAnnotation = a2;
                    bestMaxError = maxError;
                }
            }
            return bestAnnotation;
        }

        /**
         * Get all annotations in a set that overlap a given annotation
         * or return null if no overlapping region is found
         *
         * @param a the annotation being matched
         * @return an AnnotationSet containing only matches that overlap a
         */
        public AnnotationSet getAllMatches(Annotation a) {
            AnnotationSet rv = new AnnotationSet(astralChainID);
            boolean foundMatch = false;
            
            for (Annotation a2 : annotations ) {
                if (a2.nOverlap(a) > 0) {
                    foundMatch = true;
                    rv.annotations.add(a2);
                }
            }
            if (!foundMatch)
                rv = null;
            return rv;
        }
        
        public static class GeneticDomainException extends Exception {
        }

        /**
         * Get the set of SCOP Domains saved in an AnnotationSet
         * @param astralChainID
         * @param scopReleaseID
         * @return
         * @throws Exception
         */
        public static AnnotationSet getSCOPDomains(int astralChainID, int scopReleaseID) throws Exception {
            ArrayList<Integer> scopNodeIDS = CommonSCOPQueries.getScopNodeIDsForAstralChain(astralChainID, scopReleaseID);
            return getSCOPDomains(astralChainID, scopNodeIDS, scopReleaseID);
        }

        /**
         * Get the set of SCOP Domains saved in an AnnotationSet
         * @param astralChainID
         * @param scopNodeIDs
         * @param scopReleaseID
         * @return
         * @throws Exception
         */
        public static AnnotationSet getSCOPDomains(int astralChainID, Collection<Integer> scopNodeIDs, int scopReleaseID) throws Exception {
            Statement stmt = LocalSQL.createStatement();
            AnnotationSet as = new AnnotationSet(astralChainID);
            as.load(stmt);

            String rafLine = CommonSCOPQueries.getRAFLineForAstralChain(astralChainID);
            for (int nodeID : scopNodeIDs) {
                if (CommonSCOPQueries.isGeneticDomain(nodeID)) {
                    throw new GeneticDomainException(); //"Genetic domain found in chain " + astralChainID);
                }
                String description = CommonSCOPQueries.getScopNodeDescription(nodeID);
                ASTEROIDS.Annotation a = new ASTEROIDS.Annotation();
                try {
                    a.parseHeaderRegions(description, rafLine);
                }
                catch (Exception e) {
                    throw new Exception("Exception caught when attempt to parse header regions for chain ID " + astralChainID + " release " + scopReleaseID + " " + e.toString());
                }
                a.family = CommonSCOPQueries.getSCCS(nodeID);
                a.sid = CommonSCOPQueries.getScopNodeSid(nodeID);
                a.hitNodeID = nodeID;
                as.annotations.add(a);
            }
            stmt.close();
            return as;
        }

        /**
         * Remove annotations from the set that are smaller than some specified size
         *
         * @param minDomainSize
         */
        public void removeShortAnnotations(int minDomainSize) {
            AnnotationSet as2 = new AnnotationSet(astralChainID);
            for (Annotation annotation : annotations) {
                int numResidues = annotation.getNumberAnnotatedResidues();
                if (numResidues >= minDomainSize) {
                    as2.annotations.add(annotation);
                }
            }
            annotations = as2.annotations;
        }

        /**
         * @return first encountered Annotation with the minimum e-value
         */
        public Annotation getMinLog10E() {
            Annotation minLog10EAnnotation = null;
            for (Annotation annotation : annotations) {
                if (minLog10EAnnotation == null) {
                    minLog10EAnnotation = annotation;
                }
                else if (minLog10EAnnotation.log10E > annotation.log10E) {
                    minLog10EAnnotation = annotation;
                }
            }
            return minLog10EAnnotation;
        }

        /**
         * @return first encountered Annotation with the maximum e-value
         */
        public Annotation getMaxLog10E() {
            Annotation maxLog10EAnnotation = null;
            for (Annotation annotation : annotations) {
                if (maxLog10EAnnotation == null) {
                    maxLog10EAnnotation = annotation;
                }
                else if (maxLog10EAnnotation.log10E < annotation.log10E) {
                    maxLog10EAnnotation = annotation;
                }
            }
            return maxLog10EAnnotation;
        }

        /**
         *
         * @return sorted Vector of all AnnotationRegions in the AnnotationSet
         */
        final public Vector<AnnotationRegion> getAllRegions() {
            Vector<AnnotationRegion> regions = new Vector<AnnotationRegion>();
            for (Annotation a : annotations) {
                regions.addAll(a.regions);
            }
            Collections.sort(regions);
            return regions;
        }

        /**
         * expand regions in both directions to edge of gap (missing
         * ATOM) or to end of chain.  Only first and last regions are
         * affected, and regions must be in order (i.e., sorted by
         * start)
         */
        final public void expandToNearEnds(String rafLine) throws Exception {
            // Collect all regions in the chain
            Vector<AnnotationRegion> regions = getAllRegions();
            int n = regions.size();
            if (n > 0) {
                regions.get(0).extendToNearEnd(rafLine,
                                               false);
                regions.get(n - 1).extendToNearEnd(rafLine,
                                                   true);
            }
        }

        /**
         * Gets the number of residues that are included in the annotation.
         *
         * @return the number of residues included in annotation
         */
        public int getNumberAnnotatedResidues() {
            int numRes = 0;
            for (Annotation annotation : annotations) {
                numRes += annotation.getNumberAnnotatedResidues();
            }
            // subtract overlapping residues
            int countedTwiceResidues = 0;
            for (int i = 0; i < annotations.size(); i++ ) {
                for (int j = i+1; j < annotations.size(); j++ ) {
                    Annotation annotation1 = annotations.get(i);
                    Annotation annotation2 = annotations.get(j);
                    int overlap = annotation1.nOverlap(annotation2);
                    countedTwiceResidues += overlap;
                }
            }
            numRes -= countedTwiceResidues;
            return numRes;
        }

        /**
         * Get the total number of non annotated ATOMRES residues
         * @return
         * @throws Exception
         */
        public int getNumNonAnnotatedResidues() throws Exception {
            int numNonAnnotatedResidues = 0;
            ArrayList<ASTEROIDS.AnnotationRegion> nonAnnotatedRegions = this.getNonAnnotatedRegions(true);
            for (ASTEROIDS.AnnotationRegion nonAnnotatedRegion : nonAnnotatedRegions) {
                numNonAnnotatedResidues += nonAnnotatedRegion.length;
            }
            return numNonAnnotatedResidues;
        }

        /**
         * Used for debugging. Can be used to print a String of all the
         * annotations.
         *
         * @return a strung representation of the set of annotations
         */
        public String toString() {
            String outString = "AnnotationSet: ";
            for (Annotation a : this.annotations) {
                outString += a.toString();
            }
            return outString;
        }

        /**
         * Return the Annotation that the region belongs to.
         * Return null if not found.
         *
         * @param region
         * @return
         */
        public Annotation getAnnotation(AnnotationRegion region) {
            for (Annotation annotation : annotations) {
                for (AnnotationRegion regionInAnnotation : annotation.regions) {
                    if (regionInAnnotation == region) {
                        return annotation;
                    }
                }
            }
            return null;
        }

        /**
         * Get number of residues between region1 and region2
         * Assumption, regions have already been sorted
         */
        public static int getLinkerSize(AnnotationRegion region1, AnnotationRegion region2) {
            return region2.start-(region1.start + region1.length);
        }

        /**
         * Add linker region by splitting based on the hits on the subject domain sequences
         * @throws Exception
         */
        public void addLinkerRegions(int maxLinkerSize) throws Exception {
            // just return if there are no annotations
            if (annotations.size() == 0) {
                return;
            }

            // Collect all regions in the chain, sorted by start
            Vector<ASTEROIDS.AnnotationRegion> regions = this.getAllRegions();

            // now, try to fill to gap
            for (int i = 0; i < regions.size() - 1; i++) {
                ASTEROIDS.AnnotationRegion region1 = regions.get(i);
                ASTEROIDS.AnnotationRegion region2 = regions.get(i + 1);

                // check if there is a gap between the two regions
                int linkerSize = getLinkerSize(region1, region2);
                if (linkerSize < 1 || linkerSize > maxLinkerSize) {
                    continue;
                }

                // now check if there are unobserved residues) between the two regions
                ASTEROIDS.AnnotationRegion region1New = new AnnotationRegion(region1);
                region1New.extendToNearEnd(rafLine, true);
                if ((region1New.start + region1New.length) < region2.start) {
                    continue;
                }

                // try to fill in the linker
                Annotation annotation1 = getAnnotation(region1);
                Annotation annotation2 = getAnnotation(region2);

                if (annotation1.source != Annotation.Source.BLAST || annotation2.source != Annotation.Source.BLAST) {
                    throw new Exception("addLinkerRegions method only supported for BLAST annotations");
                }

                Statement stmt = LocalSQL.createStatement();
                CommonSCOPQueries.BlastInfo blastInfo1 = new CommonSCOPQueries.BlastInfo(annotation1.sourceID, stmt);
                int missedResiduesInSubject1 = blastInfo1.hitSeqLength - (blastInfo1.hitStart + blastInfo1.hitLength);

                CommonSCOPQueries.BlastInfo blastInfo2 = new CommonSCOPQueries.BlastInfo(annotation2.sourceID, stmt);
                int missedResiduesInSubject2 = blastInfo2.hitStart;

                // adjust the region ends
                double ratio = (double) missedResiduesInSubject1 / ((double) missedResiduesInSubject1 + (double) missedResiduesInSubject2);
                int residuesToAddToRegion1 = (int) Math.round(linkerSize * ratio);
                int residuesToAddToRegion2 = linkerSize - residuesToAddToRegion1;
                region1.length = region1.length + residuesToAddToRegion1;
                region2.start = region2.start - residuesToAddToRegion2;
                region2.length = region2.length + residuesToAddToRegion2;
            }
        }
    }
}
