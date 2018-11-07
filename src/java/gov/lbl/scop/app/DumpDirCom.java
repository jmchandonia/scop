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
   Dump out dir.com file for a given release
*/
public class DumpDirCom {
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

            System.out.println("# dir.com."+dbName.toLowerCase()+".txt");
	    
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
            int lastSunid = 0;
            rs = stmt.executeQuery("select n.sunid, c.description from scop_node n, scop_comment c where c.node_id=n.id and release_id="+scopReleaseID+" order by n.id,c.id");
            while (rs.next()) {
                int sunid = rs.getInt(1);
                String description = rs.getString(2);

                if (sunid != lastSunid) {
                    if (lastSunid != 0)
                        System.out.println();
                    System.out.print(sunid);
                }
		
                System.out.print(" ! "+description);
                lastSunid = sunid;
            }
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
