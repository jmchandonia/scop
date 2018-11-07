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
   Fill in SCOP node table from SCOP dir.hie file, after running ParseDirDes
*/
public class ParseDirHie {
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            ResultSet rs;

            String ver = argv[0].substring(17);
            int scopReleaseID = LocalSQL.lookupSCOPRelease(ver);
            if (scopReleaseID==0) {
                throw new Exception("Can't determine SCOP version from "+argv[0]);
            }

            // read dir.hie from argv[0];
            BufferedReader infile = IO.openReader(argv[0]);
            String buffer = infile.readLine();
            while (buffer != null) {
                if (!buffer.startsWith("#")) {
                    StringTokenizer st = new StringTokenizer(buffer,"\t");
                    int child = StringUtil.atoi(st.nextToken());
                    int parent = StringUtil.atoi(st.nextToken());
                    // System.err.println(child+" "+parent);

                    int childID = LocalSQL.lookupNodeBySunid(child,scopReleaseID);
                    if (child != 0) {
                        int parentID = LocalSQL.lookupNodeBySunid(parent,scopReleaseID);
                        stmt.executeUpdate("update scop_node set parent_node_id="+parentID+" where id="+childID);
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
