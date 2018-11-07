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
   Utility for manually resubmitting failed jobs, for debugging.
*/
public class ResubmitFailedJob {
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            Statement stmt2 = LocalSQL.createStatement();
            ResultSet rs;

            String query = "select id, job_type_id, target_id, args from job_done where n_failures=10";
            if (argv.length > 0)
                query += " and status like \"%"+argv[0]+"%\"";
            rs = stmt.executeQuery(query);
            while (rs.next()) {
                int jobID = rs.getInt(1);
                int jobTypeID = rs.getInt(2);
                int targetID = rs.getInt(3);
                String args = rs.getString(4);
                System.out.println("resubmitting job "+jobID);
                stmt2.executeUpdate("delete from job_done where id="+jobID);
                LocalSQL.newJob(jobTypeID,
                                targetID,
                                args,
                                stmt2);
            }
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
