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
import org.strbio.local.*;
import org.strbio.math.*;
import org.strbio.mol.*;
import org.strbio.util.*;
import org.strbio.IO;
import gov.lbl.scop.local.LocalSQL;

/**
   links proteins to their pdb-style files
*/
public class FindPDBStyle {
    final public static void linkPDBStyle(int nodeID,
                                          String fileName) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        File pdbFile = new File(fileName);
        if (pdbFile.exists()) {
            stmt.executeUpdate("delete from scop_node_pdbstyle where node_id="+nodeID);

            // find last SCOP update
            BufferedReader infile = IO.openReader(pdbFile.getPath());
            int lastUpdate = 0;
            String buffer = infile.readLine();
            while (buffer != null) {
                if (buffer.startsWith("REMARK  99 ASTRAL Data-updated-release:")) {
                    String ver = buffer.substring(40).trim();
                    lastUpdate = LocalSQL.lookupSCOPRelease(ver);
                    buffer = null;
                }
                else
                    buffer = infile.readLine();
            }
            stmt.executeUpdate("insert into scop_node_pdbstyle values ("+nodeID+", \""+pdbFile.getPath()+"\", "+lastUpdate+")");
        }
        stmt.close();
    }
    
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
	    
            int l = argv[0].length();
            if (argv[0].charAt(l-1)=='/')
                l--;
            int pos = argv[0].lastIndexOf('/',l-1);
            String ver = argv[0].substring(pos+1,l);
            int scopReleaseID = LocalSQL.lookupSCOPRelease(ver);
            if (scopReleaseID==0)
                throw new Exception("Can't determine SCOP version from "+argv[0]);

            ResultSet rs = stmt.executeQuery("select id, sid from scop_node where release_id="+scopReleaseID+" and level_id=8");
            while (rs.next()) {
                int nodeID = rs.getInt(1);
                String sid = rs.getString(2);
                String hash = sid.substring(2,4);

                File pdbFile = new File(argv[0]+File.separator+hash+File.separator+sid+".ent");
                if (pdbFile.exists()) {
                    MakePDBStyle.fixLastUpdate(nodeID,
                                               pdbFile.getPath());
					       
                    linkPDBStyle(nodeID,
                                 pdbFile.getPath());
                }
            }
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
