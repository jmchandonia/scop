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
   Fill in SCOP node table from SCOP dir.des file
*/
public class ParseDirDes {
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

            // create root node for this release, if not present
            rs = stmt.executeQuery("select id from scop_node where level_id=1 and release_id="+scopReleaseID);
            if (!rs.next())
                LocalSQL.createNode(0,
                                    null,
                                    null,
                                    "SCOP root",
                                    1,
                                    0,
                                    scopReleaseID,
                                    1);

            // read dir.des from argv[0];
            BufferedReader infile = IO.openReader(argv[0]);
            String buffer = infile.readLine();
            while (buffer != null) {
                if (!buffer.startsWith("#")) {
                    StringTokenizer st = new StringTokenizer(buffer,"\t");
                    int sunid = StringUtil.atoi(st.nextToken());
                    String level = st.nextToken();
                    int levelID = LocalSQL.lookupLevelAbbrev(level);
                    String sccs = st.nextToken();
                    String sid = st.nextToken();
                    String quotedSid = "null";
                    String sidTest = " is null";
                    if (sid.length() > 1) {
                        quotedSid = "\""+sid+"\"";
                        sidTest = "="+quotedSid;
                    }
                    String description = st.nextToken();
                    description = StringUtil.replace(description,"\"","\\\"");

                    // see if appropriate node already exists
                    rs = stmt.executeQuery("select id from scop_node where sunid=\""+sunid+"\" and sccs=\""+sccs+"\" and sid"+sidTest+" and description=\""+description+"\" and level_id="+levelID+" and release_id="+scopReleaseID);
                    if (!rs.next())
                        LocalSQL.createNode(sunid,
                                            sccs,
                                            sid,
                                            description,
                                            levelID,
                                            0,
                                            scopReleaseID,
                                            1);
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
