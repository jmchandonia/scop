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
import org.strbio.io.*;
import org.strbio.math.*;
import org.strbio.mol.*;
import org.strbio.util.*;
import org.strbio.IO;
import gov.lbl.scop.local.LocalSQL;

/**
   Import old SPACI/AEROSPACI files, after running ParsePDB.
   Need to import spaci, then aerospaci.
*/
public class ParseSPACI {
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            ResultSet rs;
            boolean isAero = false;

            // figure out what release we're parsing, and whether it's spaci
            if (argv[0].startsWith("aerospaci"))
                isAero = true;
            int pos = argv[0].indexOf('.');
            String date = ParseRAF.convertDate(argv[0].substring(pos+1,pos+7));
            int scopReleaseID = 0;
            rs = stmt.executeQuery("select id from scop_release where freeze_date<=adddate(\""+date+"\",7) and freeze_date>=adddate(\""+date+"\",-7)");
            if (rs.next()) {
                scopReleaseID = rs.getInt(1);
            }
            else {
                throw new Exception("Can't determine SCOP version from "+argv[0]);
            }

            // if spaci, delete old spaci scores for this release
            if (!isAero)
                stmt.executeUpdate("delete from aerospaci where release_id="+scopReleaseID);

            // read spaci from argv[0];
            BufferedReader infile = IO.openReader(argv[0]);
            String buffer = infile.readLine();
            while (buffer != null) {
                if (!buffer.startsWith("#")) {
                    StringTokenizer st = new StringTokenizer(buffer,"\t");

                    String pdbCode = st.nextToken();
                    int pdbID = LocalSQL.lookupPDB(pdbCode);
                    if (pdbID==0)
                        throw new Exception("never heard of PDB '"+pdbCode+"'");
		    
                    double spaci = StringUtil.atod(st.nextToken());
                    if (isAero) {
                        rs = stmt.executeQuery("select id from aerospaci where pdb_entry_id="+pdbID+" and release_id="+scopReleaseID);
                        if (rs.next()) {
                            int id = rs.getInt(1);
                            stmt.executeUpdate("update aerospaci set aerospaci="+spaci+" where id="+id);
                        }
                        else {
                            throw new Exception("error - must import spaci scores before aerospaci scores for same version");
                        }
                    }
                    else {
                        String method = st.nextToken();
                        double eResolution = StringUtil.atod(st.nextToken());
                        double eRFactor = StringUtil.atod(st.nextToken());
                        double whatcheck = StringUtil.atod(st.nextToken());
                        double procheck = StringUtil.atod(st.nextToken());
                        String tmpS = st.nextToken();
                        double resolution;
                        if (tmpS.equals("NA"))
                            resolution = Double.NaN;
                        else
                            resolution = StringUtil.atod(tmpS);
                        if (resolution==0.0) resolution = Double.NaN;
                        tmpS = st.nextToken();
                        double rFactor;
                        if (tmpS.equals("-"))
                            rFactor = Double.NaN;
                        else
                            rFactor = StringUtil.atod(tmpS);
                        if (rFactor==0.0) rFactor = Double.NaN;
                        tmpS = st.nextToken();
                        double whatcheck1;
                        if (tmpS.equals("NA"))
                            whatcheck1 = Double.NaN;
                        else
                            whatcheck1 = StringUtil.atod(tmpS);
                        tmpS = st.nextToken();
                        double whatcheck2;
                        if (tmpS.equals("NA"))
                            whatcheck2 = Double.NaN;
                        else
                            whatcheck2 = StringUtil.atod(tmpS);
                        tmpS = st.nextToken();
                        double whatcheck3;
                        if (tmpS.equals("NA"))
                            whatcheck3 = Double.NaN;
                        else
                            whatcheck3 = StringUtil.atod(tmpS);
                        tmpS = st.nextToken();
                        double whatcheck4;
                        if (tmpS.equals("NA"))
                            whatcheck4 = Double.NaN;
                        else
                            whatcheck4 = StringUtil.atod(tmpS);
                        tmpS = st.nextToken();
                        int procheck1;
                        if (tmpS.equals("NA"))
                            procheck1 = -1;
                        else
                            procheck1 = StringUtil.atoi(tmpS);
                        tmpS = st.nextToken();
                        int procheck2;
                        if (tmpS.equals("NA"))
                            procheck2 = -1;
                        else
                            procheck2 = StringUtil.atoi(tmpS);
                        tmpS = st.nextToken();
                        int procheck3;
                        if (tmpS.equals("NA"))
                            procheck3 = -1;
                        else
                            procheck3 = StringUtil.atoi(tmpS);
			
                        stmt.executeUpdate("insert into aerospaci values (null,"+
                                           pdbID+", "+
                                           scopReleaseID+", "+
                                           spaci+", "+
                                           spaci+", \""+
                                           method+"\", "+
                                           eResolution+", "+
                                           eRFactor+", "+
                                           whatcheck+", "+
                                           procheck+", "+
                                           (Double.isNaN(resolution) ? "null" : resolution)+", "+
                                           (Double.isNaN(rFactor) ? "null" : rFactor)+", "+
                                           (Double.isNaN(whatcheck1) ? "null" : whatcheck1)+", "+
                                           (Double.isNaN(whatcheck2) ? "null" : whatcheck2)+", "+
                                           (Double.isNaN(whatcheck3) ? "null" : whatcheck3)+", "+
                                           (Double.isNaN(whatcheck4) ? "null" : whatcheck4)+", "+
                                           ((procheck1 == -1) ? "null" : procheck1)+", "+
                                           ((procheck2 == -1) ? "null" : procheck2)+", "+
                                           ((procheck3 == -1) ? "null" : procheck3)+")");
                    }
                }
                buffer = infile.readLine();
            }
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
