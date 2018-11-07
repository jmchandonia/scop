/*
 * Software to build and maintain SCOPe, https://scop.berkeley.edu/
 *
 * Copyright (C) 2012-2018 The Regents of the University of California
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
import org.strbio.local.*;
import org.strbio.math.*;
import org.strbio.mol.*;
import org.strbio.util.*;
import org.strbio.IO;
import gov.lbl.scop.util.*;
import gov.lbl.scop.local.*;

/**
   Hold until all jobs of a given type are finished.
*/
public class WaitForJobs {
    /**
       Wait for jobs of a certain type on a certain target
       maxWait is in minutes.  If 0, wait forever.
    */
    final public static void waitFor(int jobTypeID,
                                     int targetID,
                                     int maxWait) throws Exception {
        Object lock = new Object();
        PreparedStatement countJobs = LocalSQL.prepareStatement("select count(*) from job where job_type_id=? and target_id=?");
        System.out.println("Waiting for jobs of type "+jobTypeID+" for target "+targetID);
        countJobs.setInt(1,jobTypeID);
        countJobs.setInt(2,targetID);
        int timeout = 0;

        while (true) {
            // monitor queue
            ResultSet rs = countJobs.executeQuery();
            rs.next();
            int nTasks = rs.getInt(1);
            if (nTasks == 0)
                break;

            // check again in 1 minute
            synchronized (lock) {
                lock.wait(60000);
            }

            // see if we need to timeout
            if ((maxWait > 0) &&
                (++timeout >= maxWait))
                break;
        }
    }

    /**
       wait for all jobs of a certain type
    */
    final public static void waitFor(int jobTypeID) throws Exception {
        Object lock = new Object();
        PreparedStatement countJobs = LocalSQL.prepareStatement("select count(*) from job where job_type_id=?");
	
        System.out.println("Waiting for all jobs of type "+jobTypeID);
        countJobs.setInt(1,jobTypeID);

        while (true) {
            // monitor queue
            ResultSet rs = countJobs.executeQuery();
            rs.next();
            int nTasks = rs.getInt(1);
            if (nTasks == 0)
                break;

            // check again in 1 minute
            synchronized (lock) {
                lock.wait(60000);
            }
        }
    }
    
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();

            int i = StringUtil.atoi(argv[0]);
            waitFor(i);
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
        System.exit(0);
    }
}
