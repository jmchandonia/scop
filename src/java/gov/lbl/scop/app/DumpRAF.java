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
   Dump out the RAF file used in a given release
*/
public class DumpRAF {
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            ResultSet rs;

            int scopReleaseID = LocalSQL.lookupSCOPRelease(argv[0]);

            rs = stmt.executeQuery("select v.version, v.header_length from raf_version v, raf r where r.raf_version_id=v.id and first_release_id<="+scopReleaseID+" and last_release_id>="+scopReleaseID+" limit 1");
            rs.next();
            String version = rs.getString(1);
            int headerLength = rs.getInt(2);

            System.out.println("# ASTRAL Atom-Seqres Rapid Access Format");
            System.out.println("# Version "+version);
            System.out.println("# Header: "+headerLength+" Bytes");
            System.out.println("# http://scop.berkeley.edu/");
            System.out.println("# http://astral.berkeley.edu/");

            // "binary" order necessary for case sensitivity
            rs = stmt.executeQuery("select line from raf where first_release_id<="+scopReleaseID+" and last_release_id>="+scopReleaseID+" order by binary line");
            while (rs.next()) {
                System.out.println(rs.getString(1));
            }
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
