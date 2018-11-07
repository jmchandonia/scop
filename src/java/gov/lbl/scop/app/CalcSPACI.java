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
import org.xml.sax.*;
import org.xml.sax.helpers.*;
import javax.xml.parsers.*;
import gov.lbl.scop.util.*;
import gov.lbl.scop.local.LocalSQL;

/**
   Get/calculate all data related to SPACI, for PDB entries that don't
   have one calculated yet.
*/
public class CalcSPACI {
    public static class PDBMLHandler extends DefaultHandler {
        private int inElement = 0;
        public String curRes = "";
        public String curR = "";

        public void startElement(String uri,
                                 String localName,
                                 String qName,
                                 Attributes attributes) {
            if (qName.equals("PDBx:ls_d_res_high")) {
                inElement = 1;
                curRes = "";
            }
            else if ((qName.equals("PDBx:ls_R_factor_all")) ||
                     (qName.equals("PDBx:R_factor_all_no_cutoff"))) {
                inElement = 2;
                curR = "";
            }
            else if ((qName.equals("PDBx:ls_R_factor_obs")) ||
                     (qName.equals("PDBx:R_factor_obs_no_cutoff")) ||
                     (qName.equals("PDBx:ls_R_factor_R_work"))) {
                if (curR.length() == 0) {
                    // use these only as backup
                    inElement = 2;
                    curR = "";
                }
            }
        }

        public void endElement(String uri,
                               String localName,
                               String qName) {
            if (qName.equals("PDBx:ls_d_res_high")) {
                inElement = 0;
                curRes = curRes.trim();
            }
            else if ((qName.equals("PDBx:ls_R_factor_all")) ||
                     (qName.equals("PDBx:ls_R_factor_obs")) ||
                     (qName.equals("PDBx:R_factor_obs_no_cutoff")) ||
                     (qName.equals("PDBx:ls_R_factor_R_work"))) {
                inElement = 0;
                curR = curR.trim();
            }
        }
	    
        public void characters(char[] ch,
                               int start,
                               int length) {
            if (inElement==1)
                curRes += new String(ch, start, length);
            else if (inElement==2)
                curR += new String(ch, start, length);
        }
    }

    /**
       parse an individual XML file, returning resolution and R,
       or Double.NaN if missing.
    */
    final public static double[] parseXMLFile(String fileName) throws Exception {
        BufferedReader infile = IO.openReader(fileName);
        PDBMLHandler h = new PDBMLHandler();

        SAXParserFactory factory
            = SAXParserFactory.newInstance();
        factory.setValidating(false);
        SAXParser parser = factory.newSAXParser();
	
        parser.parse(new InputSource(infile), h);

        double res = Double.NaN;
        if (h.curRes.length() > 0) {
            res = StringUtil.atod(h.curRes);
            res = Math.round(res*1000000.0)/1000000.0;
        }

        double r = Double.NaN;
        if (h.curR.length() > 0) {
            r = StringUtil.atod(h.curR);
            r = Math.round(r*1000000.0)/1000000.0;
        }

        double[] rv = { res, r };
        return rv;
    }
    
    /**
       get resolution for a PDB file.  Updates database.
    */
    final public static double getResolution(int pdbReleaseID) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select l.xml_path from pdb_release r, pdb_local l where l.pdb_release_id=r.id and r.id="+pdbReleaseID);
        if (!rs.next()) {
            throw new IllegalArgumentException("Local PDB path not found for "+pdbReleaseID);
        }
        String pdbPath = rs.getString(1);
        rs.close();

        double resolution = Double.NaN;

        // try XML file first
        /*
          to be deployed in 2.06
          if (pdbPath != null) {
          System.out.println("Getting resolution for "+pdbPath);

          double[] rv = parseXMLFile(fileName);
          resolution = rv[0];
          }
        */

        // use old PDB file as fallback
        if (Double.isNaN(resolution)) {
            rs = stmt.executeQuery("select l.pdb_path from pdb_release r, pdb_local l where l.pdb_release_id=r.id and r.id="+pdbReleaseID);
            if (!rs.next()) {
                throw new IllegalArgumentException("Local PDB path not found for "+pdbReleaseID);
            }
            pdbPath = rs.getString(1);
            rs.close();

            if ((pdbPath != null) &&
                (pdbPath.endsWith(".pdb-bundle.tar.gz"))) {
                System.out.println("Getting resolution for "+pdbPath);

                resolution = PDB.getResolution(pdbPath);
            }
        }
	
        if (!Double.isNaN(resolution)) {
            stmt.executeUpdate("update pdb_release set resolution="+
                               resolution+
                               " where id="+
                               pdbReleaseID);
        }

        stmt.close();
        return resolution;
    }

    /**
       get R factor for a PDB file.  Updates database.
    */
    final public static double getRFactor(int pdbReleaseID) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select l.pdb_path from pdb_release r, pdb_local l where l.pdb_release_id=r.id and r.id="+pdbReleaseID);
        if (!rs.next()) {
            throw new IllegalArgumentException("Local PDB path not found for "+pdbReleaseID);
        }
        String pdbPath = rs.getString(1);
        rs.close();
	
        double rFactor = Double.NaN;

        // try XML file first
        /*
          to be deployed in 2.06
          if (pdbPath != null) {
          System.out.println("Getting R factor for "+pdbPath);

          double[] rv = parseXMLFile(fileName);
          rFactor = rv[1];
          }
        */

        // use old PDB file as fallback
        if (Double.isNaN(rFactor)) {
            rs = stmt.executeQuery("select l.pdb_path from pdb_release r, pdb_local l where l.pdb_release_id=r.id and r.id="+pdbReleaseID);
            if (!rs.next()) {
                throw new IllegalArgumentException("Local PDB path not found for "+pdbReleaseID);
            }
            pdbPath = rs.getString(1);
            rs.close();

            if ((pdbPath != null) &&
                (pdbPath.endsWith(".pdb-bundle.tar.gz"))) {
                System.out.println("Getting R factor for "+pdbPath);
                rFactor = PDB.getRFactor(pdbPath);
            }
        }
	
        if (!Double.isNaN(rFactor)) {
            stmt.executeUpdate("update pdb_release set r_factor="+
                               rFactor+
                               " where id="+
                               pdbReleaseID);
        }
	
        stmt.close();
        return rFactor;
    }

    /**
       run Whatcheck on a PDB entry and return true if valid.  Updates
       database with WC results, including resolution.
    */
    final public static boolean runWhatcheck(int pdbReleaseID) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select l.pdb_path from pdb_release r, pdb_local l where l.pdb_release_id=r.id and r.id="+pdbReleaseID);
        if (!rs.next()) {
            throw new IllegalArgumentException("Local PDB path not found for "+pdbReleaseID);
        }
        String pdbPath = rs.getString(1);
        rs.close();

        System.out.println("Running WHATCHECK for "+pdbPath);

        File unBundled = null;
        if (pdbPath.endsWith(".pdb-bundle.tar.gz")) {
            unBundled = ParsePDBXML.unBundle(pdbPath);
            pdbPath = unBundled.getAbsolutePath();
        }
	
        double[] rv = WhatCheck.getSummary(pdbPath);

        if (unBundled != null)
            unBundled.delete();
	
        boolean hasValidWhatcheck = false;
        double[] whatcheck = new double[4];
        if (rv != null) {
            if (!Double.isNaN(rv[0])) {
                stmt.executeUpdate("update pdb_release set resolution="+
                                   rv[0]+
                                   " where id="+
                                   pdbReleaseID);
            }
	    
            hasValidWhatcheck = true;
            for (int i=0; i<4; i++) {
                whatcheck[i] = rv[i+1];
                if (Double.isNaN(rv[i]))
                    hasValidWhatcheck = false;
            }
        }
        stmt.executeUpdate("update pdb_release set has_valid_whatcheck="+
                           (hasValidWhatcheck ? 1 : 0)+
                           " where id="+
                           pdbReleaseID);
        stmt.executeUpdate("update pdb_release set whatcheck1="+
                           (Double.isNaN(whatcheck[0]) ? "null" : whatcheck[0])+
                           " where id="+
                           pdbReleaseID);
        stmt.executeUpdate("update pdb_release set whatcheck2="+
                           (Double.isNaN(whatcheck[1]) ? "null" : whatcheck[1])+
                           " where id="+
                           pdbReleaseID);
        stmt.executeUpdate("update pdb_release set whatcheck3="+
                           (Double.isNaN(whatcheck[2]) ? "null" : whatcheck[2])+
                           " where id="+
                           pdbReleaseID);
        stmt.executeUpdate("update pdb_release set whatcheck4="+
                           (Double.isNaN(whatcheck[3]) ? "null" : whatcheck[3])+
                           " where id="+
                           pdbReleaseID);

        stmt.close();
        return hasValidWhatcheck;
    }
    
    /**
       run Procheck on a PDB entry and return true if valid.  Updates
       database with PC results.  Must know resolution in advance.
    */
    final public static boolean runProcheck(int pdbReleaseID) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select l.pdb_path, r.resolution from pdb_release r, pdb_local l where l.pdb_release_id=r.id and r.id="+pdbReleaseID);
        if (!rs.next()) {
            throw new IllegalArgumentException("Local PDB path not found for "+pdbReleaseID);
        }
        String pdbPath = rs.getString(1);
        double resolution = rs.getDouble(2);
        if (rs.wasNull()) {
            rs.close();
            stmt.close();
            return false;
        }
        rs.close();

        System.out.println("Running PROCHECK for "+pdbPath);

        File unBundled = null;
        if (pdbPath.endsWith(".pdb-bundle.tar.gz")) {
            unBundled = ParsePDBXML.unBundle(pdbPath);
            pdbPath = unBundled.getAbsolutePath();
        }

        int[] rv = ProCheck.getSummary(pdbPath,resolution);

        if (unBundled != null)
            unBundled.delete();
	
        boolean hasValidProcheck = false;
        int[] procheck = new int[3];
        if (rv != null) {
            hasValidProcheck = true;
            for (int i=0; i<3; i++) {
                procheck[i] = rv[i];
                if (Double.isNaN(rv[i]))
                    hasValidProcheck = false;
            }
        }
        stmt.executeUpdate("update pdb_release set has_valid_procheck="+
                           (hasValidProcheck ? 1 : 0)+
                           " where id="+
                           pdbReleaseID);
        stmt.executeUpdate("update pdb_release set procheck1="+
                           ((procheck[0]==-1) ? "null" : procheck[0])+
                           " where id="+
                           pdbReleaseID);
        stmt.executeUpdate("update pdb_release set procheck2="+
                           ((procheck[1]==-1) ? "null" : procheck[1])+
                           " where id="+
                           pdbReleaseID);
        stmt.executeUpdate("update pdb_release set procheck3="+
                           ((procheck[2]==-1) ? "null" : procheck[2])+
                           " where id="+
                           pdbReleaseID);	
        stmt.close();
        return hasValidProcheck;
    }

    /**
       calculate spaci, after getting resolution and r factor,
       and running whatcheck and procheck.  Updates database.
    */
    final public static void calcSPACI(int pdbReleaseID) throws Exception {
        Statement stmt = LocalSQL.createStatement();

        // get current data
        ResultSet rs = stmt.executeQuery("select r.resolution, r.r_factor, r.has_valid_procheck, r.has_valid_whatcheck, r.procheck1, r.procheck2, r.procheck3, r.whatcheck1, r.whatcheck2, r.whatcheck3, r.whatcheck4, l.pdb_path, r.method_id from pdb_release r, pdb_local l where l.pdb_release_id=r.id and r.id="+pdbReleaseID);
        if (!rs.next()) {
            rs.close();
            stmt.close();
            return;
        }
	
        double resolution = rs.getDouble(1);
        if (rs.wasNull()) resolution = Double.NaN;
        double rFactor = rs.getDouble(2);
        if (rs.wasNull()) rFactor = Double.NaN;
        boolean hasValidProcheck = (rs.getInt(3)==1);
        boolean hasValidWhatcheck = (rs.getInt(4)==1);
        int[] procheck = new int[3];
        procheck[0] = rs.getInt(5);
        if (rs.wasNull()) procheck[0] = -1;
        procheck[1] = rs.getInt(6);
        if (rs.wasNull()) procheck[1] = -1;
        procheck[2] = rs.getInt(7);
        if (rs.wasNull()) procheck[2] = -1;
        double[] whatcheck = new double[4];
        whatcheck[0] = rs.getDouble(8);
        if (rs.wasNull()) whatcheck[0] = Double.NaN;
        whatcheck[1] = rs.getDouble(9);
        if (rs.wasNull()) whatcheck[1] = Double.NaN;
        whatcheck[2] = rs.getDouble(10);
        if (rs.wasNull()) whatcheck[2] = Double.NaN;
        whatcheck[3] = rs.getDouble(11);
        if (rs.wasNull()) whatcheck[3] = Double.NaN;
        String pdbPath = rs.getString(12);
        int methodID = rs.getInt(13);
        if (rs.wasNull()) methodID = -1;
        rs.close();
	
        System.out.println("Calculating SPACI for "+pdbPath);
		
        // get method details
        boolean isTheory = false;
        String method = "";
        if (methodID > 0) {
            rs = stmt.executeQuery("select summary, is_theory from pdb_method where id="+methodID);
            if (rs.next()) {
                method = rs.getString(1);
                isTheory = (rs.getInt(2) == 1);
                if (isTheory) method = "THEORY";
            }
            rs.close();
        }
        double spaci = SPACI.calcSPACI(method,resolution,rFactor,whatcheck,procheck);
        stmt.executeUpdate("update pdb_release set spaci="+spaci+" where id="+pdbReleaseID);
        stmt.close();
    }

    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            Statement stmt2 = LocalSQL.createStatement();
            ResultSet rs, rs2;

            if ((argv.length > 0) && (argv[0].equals("check"))) {
                rs = stmt.executeQuery("select r.id, l.xml_path from pdb_local l, pdb_release r where l.xml_path is not null and l.pdb_release_id=r.id");
                while (rs.next()) {
                    int releaseID = rs.getInt(1);
                    String fileName = rs.getString(2);

                    double[] rv = parseXMLFile(fileName);

                    rs2 = stmt2.executeQuery("select resolution, r_factor from pdb_release where id="+releaseID);
                    rs2.next();
                    double realRes = rs2.getDouble(1);
                    if (rs2.wasNull())
                        realRes = Double.NaN;
                    double realR = rs2.getDouble(2);
                    if (rs2.wasNull())
                        realR = Double.NaN;
                    rs2.close();

                    if (Double.compare(rv[0],realRes)!=0)
                        System.out.println(fileName+" res "+realRes+" "+rv[0]);
                    if (Double.compare(rv[1],realR)!=0)
                        System.out.println(fileName+" R "+realR+" "+rv[1]);
                }
            }

            boolean done = false;
            while (!done) {
                // pick one spaci at a time to calculate
                rs = stmt.executeQuery("select id from pdb_release where spaci is null and id in (select pdb_release_id from pdb_local where pdb_path is not null) limit 1");
                int id = 0;
                if (rs.next()) {
                    id = rs.getInt(1);
                    rs.close();
                    stmt.executeUpdate("update pdb_release set spaci=-999.0 where id="+id);
                }
                else {
                    done = true;
                    break;
                }

                getResolution(id);
                getRFactor(id);
                runWhatcheck(id);
                runProcheck(id);
                calcSPACI(id);
            }
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
