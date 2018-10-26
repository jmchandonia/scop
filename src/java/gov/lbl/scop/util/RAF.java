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
import gov.lbl.scop.local.SCOP;
import org.strbio.IO;

import java.io.BufferedReader;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;

/**
   Utilities related to calculating sequence from RAF.
*/
public class RAF {
    public static class SequenceFragment {
        private StringBuffer seqBuffer;
        public int xCount = 0;

        public SequenceFragment() {
            seqBuffer = new StringBuffer();
        }

        public SequenceFragment(String seq) {
            seqBuffer = new StringBuffer(seq);
            int l = seqBuffer.length();
            for (int i = 0; i < l; i++)
                if (seqBuffer.charAt(i) == 'x')
                    xCount++;
        }

        public SequenceFragment(int expectedLength) {
            seqBuffer = new StringBuffer(expectedLength);
        }

        final public String getSequence() {
            return seqBuffer.toString();
        }

        final public boolean isReject() {
            int l = seqBuffer.length();
            if ((l < 20) || (xCount * 5) > l)
                return true;
            return false;
        }

        /**
           appends legal (no . or ") character to sequence
        */
        final public void append(char seqChar) {
            if ((seqChar != '.') &&
                (seqChar != '"')) {
                seqBuffer.append(seqChar);
                if (seqChar == 'x')
                    xCount++;
            }
        }

        /**
           appends another fragment
        */
        final public void append(SequenceFragment f) {
            seqBuffer.append(f.seqBuffer);
            xCount += f.xCount;
        }
    }

    /**
       returns sequence of entire chain, according to source:
       <p/>
       1 = ATOM
       2 = SEQRES
       3 = SEQRES, with first/last ATOM boundaries (pre-1.65)
       <p/>
       (as in astral_seq_source table)
    */
    final public static SequenceFragment wholeChainSeq(String body, int sourceType) {
        int l = body.length();
        SequenceFragment rv = new SequenceFragment(l / 7);
        boolean inSeq = true;
        if (sourceType == 3)
            inSeq = false;
        for (int i = 0; i < l; i += 7) {
            String resID = body.substring(i, i + 5).trim();
            if ((sourceType == 3) && (!inSeq) && (!resID.equals("B")))
                inSeq = true;
            if ((resID.equals("E")) && (sourceType != 2))
                return rv;
            char seqChar;
            if (sourceType == 3)
                seqChar = body.charAt(i + 6);
            else
                seqChar = body.charAt(i + 4 + sourceType);
            if (inSeq)
                rv.append(seqChar);
        }
        return rv;
    }


    /**
       Get the length of the sequence

       @param body
       @return
    */
    final public static int getSeqLength(String body) {
        return body.length() / 7;
    }

    /**
       find index of a particular residue id in RAF.
       If forward == false, returns last index.  Returns -1 if not found.
    */
    final public static int indexOf(String body, String resID, boolean forward) {
        int l = body.length();
        int direction = 1;
        if (!forward) direction = -1;

        for (int i = (forward ? 0 : l - 7);
             (forward && (i < l)) || (!forward && (i >= 0));
             i += 7 * direction) {
            String curResID = body.substring(i, i + 5).trim();
            if (curResID.equals(resID))
                return (i / 7);
        }
        return -1;
    }

    /**
       find resid at a particular index in RAF.  index starts at 0.
    */
    final public static String getResID(String body, int index) {
        int i = 7 * index;
        return body.substring(i, i + 5).trim();
    }

    /**
       Translate an index (0-based) in a whole chain seq (type depends on
       sourceType) into an index in the RAF (0-based).  -1 if not
       found.
    */
    final public static int translateIndex(String body,
                                           int index,
                                           int sourceType) {
        int l = body.length();
        boolean inSeq = true;
        if (sourceType == 3)
            inSeq = false;
        for (int i = 0; i < l; i += 7) {
            String resID = body.substring(i, i + 5).trim();
            if ((sourceType == 3) && (!inSeq) && (!resID.equals("B")))
                inSeq = true;
            if ((resID.equals("E")) && (sourceType != 2))
                return -1;
            char seqChar;
            if (sourceType == 3)
                seqChar = body.charAt(i + 6);
            else
                seqChar = body.charAt(i + 4 + sourceType);
            if (inSeq) {
                if ((seqChar != '.') &&
                    (seqChar != '"'))
                    if (index-- == 0)
                        return (i / 7);
            }
        }
        return -1;
    }

    /**
       Translate an a RAF (0-based) into into an index
       (0-based) in a whole chain seq (type depends on sourceType).
       -1 if not found.
    */
    final public static int rTranslateIndex(String body,
                                            int index,
                                            int sourceType) {
        int l = body.length();
        int rv = -1;
        boolean inSeq = true;
        if (sourceType == 3)
            inSeq = false;
        for (int i = 0; i <= (index * 7); i += 7) {
            if (i >= l)
                return -1;
            String resID = body.substring(i, i + 5).trim();
            if ((sourceType == 3) && (!inSeq) && (!resID.equals("B")))
                inSeq = true;
            if ((resID.equals("E")) && (sourceType != 2))
                return -1;
            char seqChar;
            if (sourceType == 3)
                seqChar = body.charAt(i + 6);
            else
                seqChar = body.charAt(i + 4 + sourceType);
            if (inSeq) {
                if ((seqChar != '.') &&
                    (seqChar != '"'))
                    rv++;
            }
        }
        return rv;
    }

    /**
       Finds the index of the nearest residue in the sequence with
       atomic coordinates, starting from a given index in a given
       direction.  -1 if not found.
    */
    final public static int findNearestATOM(String body,
                                            int index,
                                            boolean forward) {
        int l = body.length();
        int direction = 1;
        if (!forward) direction = -1;

        for (int i = index * 7;
             (forward && (i < l)) || (!forward && (i >= 0));
             i += 7 * direction) {
            String resID = body.substring(i, i + 5).trim();
            if ((!resID.equals("B")) &&
                (!resID.equals("M")) &&
                (!resID.equals("E")))
                return (i / 7);
            if (resID.equals("E") && forward)
                return -1;
            if (resID.equals("B") && !forward)
                return -1;
        }
        return -1;
    }

    /**
       Move along the chain until either 1) the last residue, or
       2) the last residue prior to a missing ATOM, in a given
       direction.  -1 if not found.  Starting point is assumed to
       be on a residue with both SEQRES and ATOM records.  Returns
       index into RAF.
    */
    final public static int extendToGap(String body,
                                        int index,
                                        boolean forward) {
        int l = body.length();
        int direction = 1;
        if (!forward) direction = -1;

        for (int i = (index + direction) * 7;
             (forward && (i < l)) || (!forward && (i >= 0));
             i += 7 * direction) {
            String resID = body.substring(i, i + 5).trim();
            if ((resID.equals("B")) ||
                (resID.equals("M")) ||
                (resID.equals("E")))
                return (i / 7 - direction);
        }
        if (forward)
            return (l / 7 - 1);
        else
            return 0;
    }

    /**
       how many gaps (missing ATOMs) are there between two indices,
       inclusive?
    */
    final public static int nGaps(String body,
                                  int i1,
                                  int i2) {
        int rv = 0;
        for (int i = i1 * 7; i <= i2 * 7; i += 7) {
            String resID = body.substring(i, i + 5).trim();
            if ((resID.equals("B")) ||
                (resID.equals("M")) ||
                (resID.equals("E")))
                rv++;
        }
        return rv;
    }

    /**
       returns sequence of part of a chain, according to source:
       <p/>
       1 = ATOM
       2 = SEQRES
       <p/>
       If residue ids are duplicated in the RAF (common with older
       PDB files), the range returned will be that between the FIRST
       instance of firstRes and the LAST instance of lastRes.
       Returns null if anything was not found or range is reversed.
    */
    final public static SequenceFragment partialChainSeq(String body, int sourceType, String firstRes, String lastRes) {
        int firstResN = indexOf(body, firstRes, true);
        int lastResN = indexOf(body, lastRes, false);
        // System.out.println(firstRes+" "+firstResN);
        // System.out.println(lastRes+" "+lastResN);
        if ((firstResN == -1) || (lastResN == -1) || (firstResN > lastResN))
            return null;
        else
            return partialChainSeq(body, sourceType, firstResN, lastResN);
    }

    /**
       returns sequence of part of a chain, according to source:
       <p/>
       1 = ATOM
       2 = SEQRES
       <p/>
       Residue ids are numeric, and 0-indexed.  lastRes must be
       greater than or equal to firstRes.  Indices are not checked;
       this will cause an exception if off either end.
    */
    final public static SequenceFragment partialChainSeq(String body, int sourceType, int firstRes, int lastRes) {
        SequenceFragment rv = new SequenceFragment(lastRes - firstRes + 1);
        for (int i = firstRes * 7; i <= lastRes * 7; i += 7) {
            rv.append(body.charAt(i + 4 + sourceType));
        }
        return rv;
    }

    private static HashMap<String, String> chemDic = null;

    /**
       Set up chemical dictionary
    */
    final private static void setupChemDic() throws Exception {
        String chemDicFile = SCOP.getProperty("xml2raf.chem_dic");
        if (chemDicFile == null)
            throw new Exception("Error; must define local property 'xml2raf.chem_dic' with path to XML2RAF's chemical dictionary cache file");
        BufferedReader infile = IO.openReader(chemDicFile);
        String buffer;
        chemDic = new HashMap<String, String>();
        while ((buffer = infile.readLine()) != null) {
            String[] fields = buffer.split("\t");
            chemDic.put(fields[0], fields[1]);
        }
        infile.close();
        chemDic.put("ala", "a");
        chemDic.put("val", "v");
        chemDic.put("phe", "f");
        chemDic.put("pro", "p");
        chemDic.put("met", "m");
        chemDic.put("ile", "i");
        chemDic.put("leu", "l");
        chemDic.put("asp", "d");
        chemDic.put("glu", "e");
        chemDic.put("lys", "k");
        chemDic.put("arg", "r");
        chemDic.put("ser", "s");
        chemDic.put("thr", "t");
        chemDic.put("tyr", "y");
        chemDic.put("his", "h");
        chemDic.put("cys", "c");
        chemDic.put("asn", "n");
        chemDic.put("gln", "q");
        chemDic.put("trp", "w");
        chemDic.put("gly", "g");
        chemDic.put("glx", "z");
        chemDic.put("asx", "b");
        chemDic.put("unk", "x");

        chemDic.put("n/a", ".");
        chemDic.put("ace", ".");
        chemDic.put("ch3", ".");
        chemDic.put("nh2", ".");
        chemDic.put("for", ".");
        chemDic.put("fmt", ".");

        chemDic.put("a", "x");
        chemDic.put("t", "x");
        chemDic.put("g", "x");
        chemDic.put("c", "x");
        chemDic.put("u", "x");
        chemDic.put("n", "x");
        chemDic.put("da", "x");
        chemDic.put("dt", "x");
        chemDic.put("dg", "x");
        chemDic.put("dc", "x");
        chemDic.put("du", "x");
        chemDic.put("dn", "x");
    }

    /**
       translate a single modified residue, using
       chemical dictionary
    */
    final public static String translatePDBRes(String res) throws Exception {
        if (chemDic == null)
            setupChemDic();
        String translation = chemDic.get(res.toLowerCase());
        if (translation == null)
            return("x");
        if (translation.equals("."))
            return("");
        return translation;
    }

    /**
       translate chemically modified residues as in XML2RAF,
       where chemically modified residues are in ()
    */
    final public static String translatePDBSeq(String seq) throws Exception {
        StringBuffer sb = new StringBuffer();
        int pos1 = 0;
        do {
            int pos2 = seq.indexOf('(', pos1);
            if (pos2 == -1) {
                sb.append(seq.substring(pos1));
                if (sb.length() == 0)
                    return null;
                else
                    return (sb.toString());
            } else {
                sb.append(seq.substring(pos1, pos2));
                pos1 = seq.indexOf(')', pos2 + 1) + 1;
                if (pos1 == 0)
                    throw new Exception("Mismatched parentheses in " + seq);
                String modRes = seq.substring(pos2 + 1, pos1 - 1);
                sb.append(translatePDBRes(modRes));
            }
        } while (true);
    }

    /**
       checks whether sequence is probably nucleotide.
       Same algorithm as MUSCLE; see if at least 95% of the first
       100 letters (fewer if there are fewer chars) look like nucleic acids.
    */
    final public static boolean isNucleotide(String seq) throws Exception {
        if (seq == null)
            return false;
        seq = seq.toLowerCase();
        int pos = 0;
        int total = 0;
        int totalNuc = 0;
        String tranSeq = translatePDBSeq(seq);
        while ((pos < 100) && (pos < tranSeq.length()) && (pos != -1)) {
            char c = tranSeq.charAt(pos++);
            if (c != 'x')
                total++;
            if ((c == 'a') ||
                (c == 'c') ||
                (c == 'g') ||
                (c == 't') ||
                (c == 'u') ||
                (c == 'n'))
                totalNuc++;
        }
        if (total == 0) {
            if ((seq.indexOf("(a)") > -1) ||
                (seq.indexOf("(t)") > -1) ||
                (seq.indexOf("(g)") > -1) ||
                (seq.indexOf("(c)") > -1) ||
                (seq.indexOf("(u)") > -1) ||
                (seq.indexOf("(n)") > -1) ||
                (seq.indexOf("(da)") > -1) ||
                (seq.indexOf("(dt)") > -1) ||
                (seq.indexOf("(dg)") > -1) ||
                (seq.indexOf("(dc)") > -1) ||
                (seq.indexOf("(du)") > -1) ||
                (seq.indexOf("(dn)") > -1))
                return true;
            return false;
        }
        double ratio = (double) totalNuc / (double) total;
        if (ratio >= 0.95)
            return true;
        return false;
    }

    /**
       checks sequences that have already been calculated
    */
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();

            for (int sourceID = 1; sourceID <= 3; sourceID++) {
                ResultSet rs = stmt.executeQuery("select s.seq, s.is_reject, raf_get_body(r.id) from astral_chain c, astral_seq s, raf r where c.seq_id=s.id and c.raf_id=r.id and c.source_id=" + sourceID + " limit 10000");
                while (rs.next()) {
                    String seq = rs.getString(1);
                    int reject = rs.getInt(2);
                    String body = rs.getString(3);

                    SequenceFragment sf = wholeChainSeq(body, sourceID);

                    String seq2 = sf.getSequence();
                    int reject2 = 0;
                    if (sf.isReject()) reject2 = 1;

                    if (!seq.equals(seq2)) {
                        System.out.println("SEQ:");
                        System.out.println(" old: " + seq);
                        System.out.println(" new: " + seq2);
                    }
                    if (reject != reject2) {
                        System.out.println("REJECT");
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
       Get the body from the raf line

       @param rafLine
       @return
    */
    public static String getRAFBody(String rafLine) {
        return rafLine.substring(38);
    }

    /**
     * Check that the residues are sequentially numbered
     *
     * @param rafLine
     * @return
     * @throws Exception
     */
    public static boolean areResIDsSequential(String rafLine) throws Exception {
        String rafBody = rafLine.substring(38);
        int numResidues = rafBody.length() / 7;

        // keeps track of the current residue numbering
        int currentRes = 0;

        boolean encounteredFirstNumberedResidue = false;
        for (int i = 0; i < numResidues; i++) {
            String resID = RAF.getResID(rafBody, i);

            // if we haven't encountered the numbered residue, continue
            if (!encounteredFirstNumberedResidue && resID.equals("B")) {
                // continue
            }
            // if we haven't encountered a numbered residue, but we get an M or E, we have a problem
            else if (!encounteredFirstNumberedResidue && (resID.equals("M") || resID.equals("E"))) {
                throw new Exception("Badly formatted RAF line: " + rafLine);
            }
            // we have encountered a non-numbered residue, but we should keep incrementing
            else if (resID.equals("M") || resID.equals("E")) {
                currentRes++;
            }
            // we have encountered the first numbered residue.  Very good.
            else if (!encounteredFirstNumberedResidue) {
                char lastChar = resID.charAt(resID.length() - 1);
                if (Character.isLetter(lastChar)) {  //handle insertion code
                    resID = resID.substring(0, resID.length() - 1);
                }
                //initialize currentRes
                currentRes = Integer.parseInt(resID);
                encounteredFirstNumberedResidue = true;
            }
            // we have encountered a numbered residue.  Make sure that the number matches with the current residue
            else {
                char lastChar = resID.charAt(resID.length() - 1);
                if (Character.isLetter(lastChar)) {
                    //handle alternate atoms
                    int resNum = Integer.parseInt(resID.substring(0, resID.length() - 1));
                    if (resNum != currentRes && resNum != currentRes + 1) {
                        return false;
                    }
                    currentRes = resNum;
                } else {
                    int resNum = Integer.parseInt(resID);
                    currentRes++;
                    if (resNum != currentRes) {
                        return false;
                    }
                }
            }
        }
        return true;
    }


}
