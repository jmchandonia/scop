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
   Dump out dir.hie file for a given release
*/
public class DumpDirHie {
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            Statement stmt2 = LocalSQL.createStatement();
            ResultSet rs,rs2;

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

            System.out.println("# dir.hie."+dbName.toLowerCase()+".txt");
	    
            System.out.println("# "+dbName+" release "+argv[0]+" ("+dateString+")  [File format version 1.01]");
            if (dbName.equals("SCOP")) {
                System.out.println("# http://scop.mrc-lmb.cam.ac.uk/scop/");
                System.out.println("# Copyright (c) 1994-"+dfYear.format(d)+" the scop authors; see http://scop.mrc-lmb.cam.ac.uk/scop/lic/copy.html");
            }
            else {
                System.out.println("# http://scop.berkeley.edu/");
                System.out.println("# Copyright (c) 1994-"+dfYear.format(d)+" the SCOP and SCOPe authors; see http://scop.berkeley.edu/about");
            }

            // sort by node id order
            rs = stmt.executeQuery("select id, sunid, parent_node_id from scop_node where release_id="+scopReleaseID+" order by id");
            while (rs.next()) {
                int id = rs.getInt(1);
                int sunid = rs.getInt(2);
                int parentID = rs.getInt(3);
                int parentSunid = -1;
                if (parentID > 0)
                    parentSunid = LocalSQL.getSunid(parentID);

                System.out.print(sunid+"\t"+(parentID==0 ? "-" :parentSunid)+"\t");
                boolean found = false;
                rs2 = stmt2.executeQuery("select sunid from scop_node where parent_node_id="+id+" order by id");
                while (rs2.next()) {
                    int childSunid = rs2.getInt(1);
                    if (found)
                        System.out.print(",");
                    System.out.print(childSunid);
                    found = true;
                }
                if (!found)
                    System.out.print("-");
                System.out.println();
            }
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
