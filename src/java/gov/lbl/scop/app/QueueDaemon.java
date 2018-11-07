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
   Monitor the job queue, and start JobDaemons if there are jobs
   to be run.  Delete crashed jobs.
*/
public class QueueDaemon {
    /**
       How many jobs to run on each host?
    */
    final public static HashMap<String,Integer> HOST_JOBS = new HashMap<String,Integer>() {
        {
            put("node0.jmcnet",new Integer(8));
            // put("node1.jmcnet",new Integer(6));
            put("node2.jmcnet",new Integer(6));
            put("node3.jmcnet",new Integer(6));
            put("node4.jmcnet",new Integer(6));
            put("node5.jmcnet",new Integer(6));
        }
    };

    /**
       How often to poll for hung jobs and start new daemons?
    */
    final public static int POLL_SECONDS = 60;

    /**
       How often (in numbers of polls) to start cleanup jobs?
       1440 = 1 day, if we poll once per minute
    */
    final public static int CLEANUP_INTERVAL = 1440;

    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Object timer = new Object();
            Statement stmt = LocalSQL.createStatement();
            PreparedStatement countJobs = LocalSQL.prepareStatement("select count(*) from job");

            // the JobDaemon is supposed to kill jobs after
            // MAX_TIME_SECONDS; this will free up the job to
            // run elsewhere if the JobDaemon itself is hung
            PreparedStatement restartCrashed = LocalSQL.prepareStatement("update job set running = null, time_started = null where time_started < date_sub(now(), interval "+(JobDaemon.MAX_TIME_SECONDS + 2*POLL_SECONDS)+" second)");

            // kill jobs that are marked as done
            PreparedStatement deleteDone = LocalSQL.prepareStatement("delete j from job j join job_done jd on j.id=jd.id");
            PreparedStatement deleteDone2 = LocalSQL.prepareStatement("delete jd from job_done jd join job j on j.id=jd.id");
            PreparedStatement lock = LocalSQL.prepareStatement("lock table job write, job_done write, job as j write, job_done as jd write");;
            PreparedStatement unlock = LocalSQL.prepareStatement("unlock tables");
	    
            lock.executeUpdate();
            deleteDone.executeUpdate();
            deleteDone2.executeUpdate();
            unlock.executeUpdate();
	    
            // count up total jobs we can run
            int MAX_JOBS = 0;
            for (Integer nJobs : HOST_JOBS.values()) {
                MAX_JOBS += nJobs.intValue();
            }

            // let admins know daemon was restarted
            stmt.executeUpdate("insert into notify_message_queue (template_id, user_id) values (4,1)");

            int lastNTasks = 0;
            int cleanup = 0;
            while (true) {
                // monitor queue
                ResultSet rs = countJobs.executeQuery();
                rs.next();
                int nTasks = rs.getInt(1);
                int nJobs = nTasks;
                if (nJobs > MAX_JOBS) nJobs = MAX_JOBS;
                if (nTasks != lastNTasks) {
                    if (nTasks == 0) {
                        stmt.executeUpdate("insert into notify_message_queue (template_id, user_id) values (2,1)");
                    }
                    if (lastNTasks == 0) {
                        stmt.executeUpdate("insert into notify_message_queue (template_id, user_id) values (3,1)");
                    }
                    System.out.println(new java.util.Date().toString());
                    System.out.println("Tasks left in queue: "+nTasks);
                }
                lastNTasks = nTasks;

                // check jobs that are running
                Vector<GridEngine.JobStatus> jobs =
                    GridEngine.getJobs();
                int scopJobs = 0;
                for (GridEngine.JobStatus j : jobs) {
                    if ((j.name.equals("scop_job_daemon.sh")) &&
                        (j.state.equals("r"))) {
                        int pos = j.queue.indexOf('@');
                        if (pos > -1) {
                            String machine = j.queue.substring(pos+1);
                            if (HOST_JOBS.keySet().contains(machine))
                                scopJobs++;
                        }
                    }
                }

                // start new jobs if required
                int jobsToStart = nJobs - scopJobs;
                for (int i=0; i<jobsToStart; i++) {
                    GridEngine.submit("/lab/proj/astral/bin/scop_job_daemon.sh");
                    System.out.println(new java.util.Date().toString());
                    System.out.println("Started new job");
                }

                // restart hung tasks
                try {
                    lock.executeUpdate();
                    restartCrashed.executeUpdate();
                    unlock.executeUpdate();
                }
                catch (Exception e2) {
                    System.out.println("Hangcheck failed");
                }

                // cleanup if required
                if (cleanup++ >= CLEANUP_INTERVAL) {
                    // delete finished jobs
                    lock.executeUpdate();
                    deleteDone.executeUpdate();
                    unlock.executeUpdate();
		    
                    System.out.println(new java.util.Date().toString());
                    System.out.println("Cleanup");
		    
                    // delete old cleanup jobs that haven't run yet
                    for (GridEngine.JobStatus j : jobs) {
                        if (j.name.equals("cleanup_host.sh"))
                            j.delete();
                    }

                    // submit new jobs
                    String[] parms = new String[8];
                    parms[0] = "-l";
                    parms[2] = "-pe";
                    parms[3] = "serial";
                    parms[5] = "-p";
                    parms[6] = "10";
                    parms[7] = "/lab/proj/astral/bin/cleanup_host.sh";
                    for (String host : HOST_JOBS.keySet()) {
                        parms[1] = "hostname="+host;
                        parms[4] = HOST_JOBS.get(host).toString();
                        GridEngine.submit(parms);
                    }
		    
                    cleanup = 0;  // reset timer
                }

                // check again after POLL_SECONDS
                synchronized (timer) {
                    timer.wait(1000 * POLL_SECONDS);
                }
            }
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
        System.exit(0);
    }
}
