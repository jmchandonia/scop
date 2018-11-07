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
   Parses history.obs
*/
public class ParseHistory {
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            ResultSet rs;

            // get history.obs file from argv[0]
            BufferedReader infile = IO.openReader(argv[0]);
	    
            while (infile.ready()) {
                String buffer = infile.readLine();

                StringTokenizer st = new StringTokenizer(buffer);
                try {
                    String oldID = st.nextToken();
                    String oldIDType = st.nextToken();
                    String newID = st.nextToken();
                    String vers = st.nextToken();
                    int oldReleaseID = LocalSQL.lookupSCOPRelease(vers.substring(1,5));
                    int newReleaseID = LocalSQL.lookupSCOPRelease(vers.substring(6,10));
                    int oldNodeID = 0;

                    if (oldIDType.equals("sid"))
                        oldNodeID = LocalSQL.lookupNodeBySid(oldID,oldReleaseID);
                    else {
                        if (Character.isDigit(oldID.charAt(0))) {
                            int i = StringUtil.atoi(oldID);
                            oldNodeID = LocalSQL.lookupNodeBySunid(i,oldReleaseID);
                        }
                        else
                            oldNodeID = LocalSQL.lookupNodeBySCCS(oldID,oldReleaseID);
                    }

                    if (oldNodeID == 0) {
                        System.out.println("skipping(2) line: '"+buffer+"'");
                        continue;
                    }
		    
                    int pos = newID.indexOf(':');
                    int moveType = LocalSQL.lookupHistoryTypeAbbrev(newID.substring(1,pos));
                    newID = newID.substring(pos+1,newID.length()-1);
                    st = new StringTokenizer(newID,",");
                    while (st.hasMoreTokens()) {
                        newID = st.nextToken();
                        int newNodeID = 0;
                        if (moveType > 3) {
                            if ((oldIDType.equals("sid")) || (moveType==10))
                                newNodeID = LocalSQL.lookupNodeBySid(newID,newReleaseID);
                            else {
                                if (Character.isDigit(newID.charAt(0))) {
                                    int i = StringUtil.atoi(newID);
                                    newNodeID = LocalSQL.lookupNodeBySunid(i,newReleaseID);
                                }
                                else {
                                    if (oldIDType.equals("px"))
                                        newNodeID = LocalSQL.lookupNodeBySid(newID,newReleaseID);
                                    else
                                        newNodeID = LocalSQL.lookupNodeBySCCS(newID,newReleaseID);
                                }
                            }
                        }

                        rs = stmt.executeQuery("select id from scop_history where old_node_id="+oldNodeID+" and new_node_id "+(newNodeID==0 ? "is null" : "="+newNodeID)+" and release_id="+newReleaseID+" and change_type_id="+moveType);
                        if (!rs.next()) {
                            stmt.executeUpdate("insert into scop_history values (null,"+
                                               oldNodeID+", "+
                                               (newNodeID==0 ? "null" : newNodeID)+", "+
                                               newReleaseID+", "+
                                               moveType+")");
                        }
                    }
                }
                catch (NoSuchElementException nse) {
                    System.out.println("skipping(1) line: '"+buffer+"'");
                }
            }
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
