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
   Dump out dir.cla file for a given release

   format 1.00:  mangle chain case to all caps

   format 1.01:  leave everything alone

   format 1.02:  new dates
*/
public class DumpDirCla {
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            ResultSet rs;

            int scopReleaseID = LocalSQL.lookupSCOPRelease(argv[0]);
            String dbName = LocalSQL.getDBName(scopReleaseID);

            rs = stmt.executeQuery("select release_date from scop_release where id="+scopReleaseID);
            rs.next();
            java.sql.Date d = rs.getDate(1);
            SimpleDateFormat df = new SimpleDateFormat ("yyyy-MM-dd");
            SimpleDateFormat dfYear = new SimpleDateFormat ("yyyy");

            String dateString = df.format(d);
	    
            if (scopReleaseID > 12) {
                java.sql.Date d2 = LocalSQL.getUpdateDate(scopReleaseID);
                if (d2 != null) {
                    d = d2;
                    dateString += ", last updated "+df.format(d);
                }
            }

            System.out.println("# dir.cla."+dbName.toLowerCase()+".txt");
	    
            System.out.println("# "+dbName+" release "+argv[0]+" ("+dateString+")  [File format version 1.02]");
            if (dbName.equals("SCOP")) {
                System.out.println("# http://scop.mrc-lmb.cam.ac.uk/scop/");
                System.out.println("# Copyright (c) 1994-"+dfYear.format(d)+" the scop authors; see http://scop.mrc-lmb.cam.ac.uk/scop/lic/copy.html");
            }
            else {
                System.out.println("# http://scop.berkeley.edu/");
                System.out.println("# Copyright (c) 1994-"+dfYear.format(d)+" the SCOP and SCOPe authors; see http://scop.berkeley.edu/about");
            }

            // sort by node id order
            String lastSCCS = "";
            String lastLongSCCS = null;
            rs = stmt.executeQuery("select id, sunid, sccs, sid, description, parent_node_id from scop_node where level_id=8 and release_id="+scopReleaseID+" order by id");
            while (rs.next()) {
                int id = rs.getInt(1);
                int sunid = rs.getInt(2);
                String sccs = rs.getString(3);
                String sid = rs.getString(4);
                String description = rs.getString(5);
                int spID = rs.getInt(6);

                // break up description
                String code = description.substring(0,4);
                description = description.substring(5);

                // get dm, sp info
                int spSunid = LocalSQL.getSunid(spID);
                int dmID = LocalSQL.findParent(spID,6);
                int dmSunid = LocalSQL.getSunid(dmID);

                String longSCCS = null;
                if (sccs.equals(lastSCCS)) {
                    longSCCS = lastLongSCCS;
                }
                else {
                    int faID = LocalSQL.findParent(dmID,5);
                    int sfID = LocalSQL.findParent(faID,4);
                    int cfID = LocalSQL.findParent(sfID,3);
                    int clID = LocalSQL.findParent(cfID,2);

                    int faSunid = LocalSQL.getSunid(faID);
                    int sfSunid = LocalSQL.getSunid(sfID);
                    int cfSunid = LocalSQL.getSunid(cfID);
                    int clSunid = LocalSQL.getSunid(clID);

                    longSCCS = "cl="+clSunid+",cf="+cfSunid+",sf="+sfSunid+",fa="+faSunid;
                }

                String hier = longSCCS+",dm="+dmSunid+",sp="+spSunid+",px="+sunid;
                System.out.println(sid+"\t"+code+"\t"+description+"\t"+sccs+"\t"+sunid+"\t"+hier);

                lastSCCS = sccs;
                lastLongSCCS = longSCCS;
            }
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
