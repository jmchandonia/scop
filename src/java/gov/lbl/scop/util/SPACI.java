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
import org.strbio.io.Printf;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.Vector;

/**
   Utilities related to calculating SPACI scores
*/
public class SPACI {
    public static class SPACINode implements Comparable<SPACINode> {
        public int nodeID;
        public String sid;
        public double score;

        public SPACINode() {
            nodeID = 0;
            sid = null;
            score = 0.0;
        }

        public SPACINode(int id, String s, double d) {
            nodeID = id;
            sid = s;
            score = d;
        }

        public SPACINode(int id) {
            nodeID = id;
            sid = null;
            score = 0.0;
        }

        public SPACINode(String s) {
            nodeID = 0;
            sid = s;
            score = 0.0;
        }

        public boolean equals(SPACINode o) {
            if ((nodeID != 0) && (nodeID == o.nodeID))
                return true;
            if ((sid != null) && (sid.equals(o.sid)))
                return true;
            return false;
        }

        public int compareTo(SPACINode o) {
            if (score > o.score)
                return -1;
            else if (score < o.score)
                return 1;
            /*
              else if (nodeID < o.nodeID)
              return -1;
              else
              return 1;
            */
            else return (sid.compareTo(o.sid));
        }
    }

    /**
       sort a set of nodes by their aerospaci scores (lowest first).
       In case of ties, sort alphabetically by sid.
    */
    final public static Vector<SPACI.SPACINode> sortByScores(Vector<Integer> nodeID,
                                                             HashMap<Integer, String> nodesToSids,
                                                             HashMap<Integer, Double> nodesToScores) {
        Vector<SPACI.SPACINode> rv = new Vector<SPACI.SPACINode>();
        for (Integer id : nodeID) {
            // error if no sid
            String sid = nodesToSids.get(id);
            if (sid == null)
                throw new IllegalArgumentException("unmapped sid for " + id);
            Double score = nodesToScores.get(id);
            if (score != null) {
                rv.add(new SPACINode(id.intValue(),
                                     sid,
                                     score.doubleValue()));
            } else {
                rv.add(new SPACINode(id.intValue(),
                                     sid,
                                     0.0));
            }
        }
        Collections.sort(rv);
        return rv;
    }

    /**
       sort a set of nodes by their aerospaci scores (lowest first).
       In case of ties, sort alphabetically by sid.
    */
    final public static Vector<SPACI.SPACINode> sortByScores(Vector<Integer> nodeIDs,
                                                             Vector<String> sids,
                                                             Vector<Double> scores) {
        Vector<SPACI.SPACINode> rv = new Vector<SPACI.SPACINode>();
        for (int i = 0; i < nodeIDs.size(); i++) {
            // error if null nodeID
            Integer nodeID = nodeIDs.get(i);
            if (nodeID == null)
                throw new IllegalArgumentException("null nodeID in set, index " + i);
            // error if no sid
            String sid = sids.get(i);
            if (sid == null)
                throw new IllegalArgumentException("unmapped sid for " + nodeID);
            Double score = scores.get(i);
            if (score != null) {
                rv.add(new SPACINode(nodeID.intValue(),
                                     sid,
                                     score.doubleValue()));
            } else {
                rv.add(new SPACINode(nodeID.intValue(),
                                     sid,
                                     0.0));
            }
        }
        Collections.sort(rv);
        return rv;
    }

    /**
       class representing 1 line in a spaci/aerospaci file
    */
    public static class SPACILine implements Comparable<SPACILine> {
        public int pdbEntryID;
        public int pdbReleaseID;
        public String pdbCode;
        public int scopReleaseID;
        public double aerospaci;
        public double spaci;
        public String method;
        public double eResolution;
        public double eRFactor;
        public double whatcheckSummary;
        public double procheckSummary;
        public double resolution;
        public double rFactor;
        public double[] whatcheck;
        public int[] procheck;

        public SPACILine() {
            pdbEntryID = 0;
            pdbReleaseID = 0;
            pdbCode = null;
            scopReleaseID = 0;
            aerospaci = Double.NaN;
            spaci = Double.NaN;
            method = null;
            eResolution = Double.NaN;
            eRFactor = Double.NaN;
            whatcheckSummary = Double.NaN;
            procheckSummary = Double.NaN;
            resolution = Double.NaN;
            rFactor = Double.NaN;
            whatcheck = new double[4];
            for (int i = 0; i < 4; i++)
                whatcheck[i] = Double.NaN;
            procheck = new int[3];
            for (int i = 0; i < 3; i++)
                procheck[i] = -1;
        }

        public SPACILine(int pdbReleaseID) {
            this();
            this.pdbReleaseID = pdbReleaseID;
        }

        public SPACILine(int pdbEntryID, int scopReleaseID, String pdbCode) throws Exception {
            this();
            this.pdbEntryID = pdbEntryID;
            this.scopReleaseID = scopReleaseID;
            this.pdbCode = pdbCode;
        }

        public SPACILine(int pdbEntryID, int scopReleaseID) throws Exception {
            this(pdbEntryID, scopReleaseID, LocalSQL.getPDBCode(pdbEntryID));
        }

        public void lookupPDBRelease(Statement stmt,
                                     boolean isWeeklyUpdate) throws Exception {
            String query = null;
            if ((scopReleaseID == 0) || isWeeklyUpdate)
                query = "select r.id from pdb_release r, pdb_local l where r.pdb_entry_id=" + pdbEntryID + " and l.pdb_release_id=r.id and l.pdb_path is not null and r.replaced_by is null order by l.snapshot_date desc limit 1";
            else
                query = "select r.id from pdb_release r, scop_release s, pdb_local l where s.id=" + scopReleaseID + " and r.file_date <= s.freeze_date and r.revision_date <= s.freeze_date and l.snapshot_date <= s.freeze_date and r.pdb_entry_id=" + pdbEntryID + " and l.pdb_release_id=r.id and l.pdb_path is not null order by l.snapshot_date desc limit 1";

            ResultSet rs = stmt.executeQuery(query);
            if (rs.next())
                pdbReleaseID = rs.getInt(1);
            else
                throw new Exception("Error - pdb release for " + pdbCode + " not found");
            rs.close();
        }

        public int compareTo(SPACILine o) {
            if (aerospaci > o.aerospaci)
                return -1;
            else if (aerospaci < o.aerospaci)
                return 1;
            else return (pdbCode.compareTo(o.pdbCode));
        }

        /**
           lookup in aerospaci table:
        */
        public void lookupFromID(int id, Statement stmt) throws Exception {
            ResultSet rs = stmt.executeQuery("select a.pdb_entry_id, a.release_id, a.aerospaci, a.spaci, a.method_summary, a.effective_resolution, a.effective_r_factor, a.summary_whatcheck, a.summary_procheck, a.resolution, a.r_factor, a.procheck1, a.procheck2, a.procheck3, a.whatcheck1, a.whatcheck2, a.whatcheck3, a.whatcheck4, e.code from aerospaci a, pdb_entry e where a.pdb_entry_id=e.id and a.id=" + id);
            rs.next();
            pdbEntryID = rs.getInt(1);
            scopReleaseID = rs.getInt(2);
            aerospaci = rs.getDouble(3);
            spaci = rs.getDouble(4);
            method = rs.getString(5);
            eResolution = rs.getDouble(6);
            eRFactor = rs.getDouble(7);
            whatcheckSummary = rs.getDouble(8);
            procheckSummary = rs.getDouble(9);

            resolution = rs.getDouble(10);
            if (rs.wasNull()) resolution = Double.NaN;
            rFactor = rs.getDouble(11);
            if (rs.wasNull()) rFactor = Double.NaN;

            procheck[0] = rs.getInt(12);
            if (rs.wasNull()) procheck[0] = -1;
            procheck[1] = rs.getInt(13);
            if (rs.wasNull()) procheck[1] = -1;
            procheck[2] = rs.getInt(14);
            if (rs.wasNull()) procheck[2] = -1;

            whatcheck[0] = rs.getDouble(15);
            if (rs.wasNull()) whatcheck[0] = Double.NaN;
            whatcheck[1] = rs.getDouble(16);
            if (rs.wasNull()) whatcheck[1] = Double.NaN;
            whatcheck[2] = rs.getDouble(17);
            if (rs.wasNull()) whatcheck[2] = Double.NaN;
            whatcheck[3] = rs.getDouble(18);
            if (rs.wasNull()) whatcheck[3] = Double.NaN;

            pdbCode = rs.getString(19);
            rs.close();
        }

        /**
           get necessary info from pdb release, calculating AEROSPACI
           based on SCOP node comments linked to the release.
        */
        public void lookupFromRelease(Statement stmt) throws Exception {
            ResultSet rs = stmt.executeQuery("select r.resolution, r.r_factor, r.has_valid_procheck, r.has_valid_whatcheck, r.procheck1, r.procheck2, r.procheck3, r.whatcheck1, r.whatcheck2, r.whatcheck3, r.whatcheck4, r.method_id, e.code, e.id from pdb_release r, pdb_entry e where r.pdb_entry_id=e.id and r.id=" + pdbReleaseID);
            rs.next();
            resolution = rs.getDouble(1);
            if (rs.wasNull()) resolution = Double.NaN;
            rFactor = rs.getDouble(2);
            if (rs.wasNull()) rFactor = Double.NaN;

            // not actually used:
            boolean hasValidProcheck = (rs.getInt(3) == 1);
            boolean hasValidWhatcheck = (rs.getInt(4) == 1);

            procheck[0] = rs.getInt(5);
            if (rs.wasNull()) procheck[0] = -1;
            procheck[1] = rs.getInt(6);
            if (rs.wasNull()) procheck[1] = -1;
            procheck[2] = rs.getInt(7);
            if (rs.wasNull()) procheck[2] = -1;

            whatcheck[0] = rs.getDouble(8);
            if (rs.wasNull()) whatcheck[0] = Double.NaN;
            whatcheck[1] = rs.getDouble(9);
            if (rs.wasNull()) whatcheck[1] = Double.NaN;
            whatcheck[2] = rs.getDouble(10);
            if (rs.wasNull()) whatcheck[2] = Double.NaN;
            whatcheck[3] = rs.getDouble(11);
            if (rs.wasNull()) whatcheck[3] = Double.NaN;

            int methodID = rs.getInt(12);
            if (rs.wasNull()) methodID = 0;

            pdbCode = rs.getString(13);
            pdbEntryID = rs.getInt(14);

            rs.close();

            boolean isTheory = false;
            if (methodID == 0) {
                method = "-";
            } else {
                rs = stmt.executeQuery("select summary, is_theory from pdb_method where id=" + methodID);
                if (rs.next()) {
                    method = rs.getString(1);
                    if (rs.getInt(2) == 1) method = "THEORY";
                    if (method.equals("THEORY"))
                        isTheory = true;
                }
                rs.close();
            }

            whatcheckSummary = whatcheck(whatcheck);
            procheckSummary = procheck(procheck);

            eResolution = eResolution(method, resolution, rFactor);
            eRFactor = eRFactor(method, resolution, rFactor);
            spaci = calcSPACI(method, resolution, rFactor, whatcheck, procheck);

            String comments = "";
            rs = stmt.executeQuery("select sc.description from scop_comment sc, link_pdb l, pdb_chain c where sc.is_autogenerated=0 and sc.node_id=l.node_id and l.pdb_chain_id=c.id and c.pdb_release_id=" + pdbReleaseID);
            while (rs.next())
                comments += rs.getString(1) + "; ";
            if ((comments.length() > 0) || (isTheory))
                aerospaci = penalize(spaci, comments.toLowerCase(), isTheory);
            else
                aerospaci = spaci;
        }

        public void print(Printf outfile, boolean isAero) throws Exception {
            outfile.printf("%s", pdbCode);
            if (isAero)
                outfile.printf("\t%0.2f", aerospaci);
            else
                outfile.printf("\t%0.2f", spaci);
            outfile.printf("\t%s", method);
            outfile.printf("\t%0.2f", eResolution);
            outfile.printf("\t%0.3f", eRFactor);
            outfile.printf("\t%0.2f", whatcheckSummary);
            outfile.printf("\t%0.2f", procheckSummary);
            if (Double.isNaN(resolution))
                outfile.printf("\tNA");
            else
                outfile.printf("\t%0.2f", resolution);
            if (Double.isNaN(rFactor))
                outfile.printf("\t-");
            else
                outfile.printf("\t%0.3f", rFactor);
            for (double w : whatcheck) {
                if (Double.isNaN(w))
                    outfile.printf("\tNA");
                else
                    outfile.printf("\t%0.3f", w);
            }
            for (int p : procheck) {
                if (p == -1)
                    outfile.printf("\tNA");
                else
                    outfile.printf("\t%d", p);
            }
            outfile.printf("\n");
        }

        public void store(Statement stmt) throws Exception {
            stmt.executeUpdate("insert into aerospaci values (null," +
                               pdbEntryID + ", " +
                               scopReleaseID + ", " +
                               aerospaci + ", " +
                               spaci + ", \"" +
                               method + "\", " +
                               eResolution + ", " +
                               eRFactor + ", " +
                               whatcheckSummary + ", " +
                               procheckSummary + ", " +
                               (Double.isNaN(resolution) ? "null" : resolution) + ", " +
                               (Double.isNaN(rFactor) ? "null" : rFactor) + ", " +
                               (Double.isNaN(whatcheck[0]) ? "null" : whatcheck[0]) + ", " +
                               (Double.isNaN(whatcheck[1]) ? "null" : whatcheck[1]) + ", " +
                               (Double.isNaN(whatcheck[2]) ? "null" : whatcheck[2]) + ", " +
                               (Double.isNaN(whatcheck[3]) ? "null" : whatcheck[3]) + ", " +
                               ((procheck[0] == -1) ? "null" : procheck[0]) + ", " +
                               ((procheck[1] == -1) ? "null" : procheck[1]) + ", " +
                               ((procheck[2] == -1) ? "null" : procheck[2]) + ")");
        }
    }

    /**
       returns procheck summary score from array of 3 procheck summary
       integers.  If any are -1, they're considered undefined, which
       will result in a procheck summary score of 1.0.
    */
    final public static double procheck(int[] p) {
        if ((p == null) || (p.length != 3)) {
            throw new IllegalArgumentException("procheck requires an array of 3 integers");
        }
        if ((p[0] == -1) ||
            (p[1] == -1) ||
            (p[2] == -1))
            return 1.0;

        return (((double) (p[0] + p[1] + p[2]) - 3.0) / 6.0);
    }

    /**
       returns whatcheck summary score from array of 4 whatcheck summary
       doubles.  If any are NaN, they're considered undefined, which
       will result in a whatcheck summary score of 1.0.
    */
    final public static double whatcheck(double[] w) {
        if ((w == null) || (w.length != 4)) {
            throw new IllegalArgumentException("whatcheck requires an array of 4 doubles");
        }
        if ((Double.isNaN(w[0])) ||
            (Double.isNaN(w[1])) ||
            (Double.isNaN(w[2])) ||
            (Double.isNaN(w[3])))
            return 1.0;

        double rv = 0.0;
        rv += (w[0] + 8.0) / 16.0;
        rv += (w[1] + 8.0) / 12.0;
        rv += (w[2] + 5.0) / 7.0;
        rv += (w[3] + 10.0) / 15.0;
        rv /= 4.0;
        rv = 1.0 - rv;

        return rv;
    }

    /**
       effective resolution, based on method
    */
    final public static double eResolution(String method, double resolution, double rFactor) {
        double rv = resolution;
        if (method.startsWith("NMR")) {
            if ((method.length() > 3) &&
                (Character.isDigit(method.charAt(3)))) {
                rv = 3.8;
            } else
                rv = 4.0;
        } else if (method.startsWith("THEORY"))
            rv = 20.0;
        else if ((Double.isNaN(resolution)) || (resolution == 0.0))
            rv = 5.0;
        return rv;
    }

    /**
       effective r factor, based on method
    */
    final public static double eRFactor(String method, double resolution, double rFactor) {
        double rv = rFactor;
        if (method.startsWith("NMR"))
            rv = 0.25;
        else if (method.startsWith("THEORY"))
            rv = 1.0;
        else if ((Double.isNaN(resolution)) || (resolution == 0.0))
            rv = 0.5;
        else if (Double.isNaN(rFactor))
            rv = 0.25;
        return rv;
    }

    /**
       calculates SPACI score
    */
    final public static double calcSPACI(String method, double resolution, double rFactor, double[] whatcheck, int[] procheck) {
        double eResolution = eResolution(method, resolution, rFactor);
        double eRFactor = eRFactor(method, resolution, rFactor);
        double p = procheck(procheck);
        double w = whatcheck(whatcheck);

        return (0.1 + (1.0 / eResolution) - (eRFactor - 0.1) - (0.1 * w) - (0.1 * p));
    }

    /**
       words in comments that (each) cause AEROSPACI score to be lowered by 2
    */
    final public static String badWords = "chimer circular disorder error incorrect interrupted misfolded missing mistraced mutant permut truncat";

    /**
       calculates AEROSPACI score, based on comments (must be lower case)
    */
    final public static double penalize(double spaci, String comments, boolean isTheory) {
        double rv = spaci;
        String[] bad = badWords.split(" ");
        for (String b : bad) {
            if (comments.indexOf(b) > -1)
                rv -= 2.0;
        }
        if (isTheory)
            rv -= 5.0;
        return rv;
    }
}
