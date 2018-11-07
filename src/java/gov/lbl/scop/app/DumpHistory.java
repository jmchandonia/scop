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
import org.strbio.io.*;
import org.strbio.math.*;
import org.strbio.mol.*;
import org.strbio.util.*;
import org.strbio.IO;
import gov.lbl.scop.local.LocalSQL;

/**
   prints out history, for a given version and history type
*/
public class DumpHistory {
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            Statement stmt2 = LocalSQL.createStatement();
            ResultSet rs,rs2;
            int historyType = -1;  // sid, px, sunid, sccs

            int scopReleaseID = LocalSQL.lookupSCOPRelease(argv[0]);

            if (argv[1].equals("sid"))
                historyType = 0;
            else if (argv[1].equals("px"))
                historyType = 1;
            else if (argv[1].equals("sunid"))
                historyType = 2;
            else if (argv[1].equals("sccs"))
                historyType = 3;
            else
                throw new Exception("must specify type: sid, px, sunid, sccs");

            // map types to abbreviations
            HashMap<Integer,String> abbrev = new HashMap<Integer,String>();
            rs = stmt.executeQuery("select id, abbreviation from scop_history_type");
            while (rs.next()) {
                int id = rs.getInt(1);
                String s = rs.getString(2);
                if (s.length() > 0)
                    abbrev.put(new Integer(id),s);
            }
	    
            rs = stmt.executeQuery("select distinct old_node_id, change_type_id from scop_history where release_id="+scopReleaseID+" order by id");
            while (rs.next()) {
                int oldID = rs.getInt(1);
                int changeID = rs.getInt(2);

                String changeType = abbrev.get(new Integer(changeID));
                if (changeType==null)
                    continue;
		
                rs2 = stmt2.executeQuery("select n.sunid, n.sccs, n.sid, n.description, l.id, l.abbreviation from scop_node n, scop_level l where n.level_id=l.id and n.id="+oldID);
                rs2.next();
                int oldSunid = rs2.getInt(1);
                String oldSCCS = rs2.getString(2);
                String oldSid = rs2.getString(3);
                String oldDesc = rs2.getString(4);
                int oldLevelID = rs2.getInt(5);
                String oldLevel = rs2.getString(6);

                if (historyType==0) {
                    if (oldSid==null)
                        continue;
		    
                    System.out.print(oldSid+" sid ("+changeType+": ");
                    if (changeID < 4)
                        System.out.print(oldDesc.substring(0,4));
                    else {
                        boolean firstPrint = true;
                        rs2 = stmt2.executeQuery("select n.sid from scop_node n, scop_history h where h.old_node_id="+oldID+" and h.change_type_id="+changeID+" and h.new_node_id=n.id");
                        while (rs2.next()) {
                            if (!firstPrint)
                                System.out.print(",");
                            firstPrint = false;
                            System.out.print(rs2.getString(1));
                        }
                    }
                    System.out.println(")");
                }
                else if (historyType==1) {
                    if (oldLevelID != 8)
                        continue;
		    
                    System.out.print(oldSunid+" px ("+changeType+": ");
                    if (changeID < 4)
                        System.out.print(oldDesc.substring(0,4));
                    else {
                        boolean firstPrint = true;
                        rs2 = stmt2.executeQuery("select n.sid from scop_node n, scop_history h where h.old_node_id="+oldID+" and h.change_type_id="+changeID+" and h.new_node_id=n.id");
                        while (rs2.next()) {
                            if (!firstPrint)
                                System.out.print(",");
                            firstPrint = false;
                            System.out.print(rs2.getString(1));
                        }
                    }
                    System.out.println(")");
                }
            }
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
