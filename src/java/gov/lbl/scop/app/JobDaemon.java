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

import gov.lbl.scop.local.LocalSQL;
import gov.lbl.scop.util.ASTEROIDS;
import gov.lbl.scop.util.annotation.ExactSequenceMatchChainAnnotator;
import org.strbio.util.StringUtil;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Monitor the job queue, and run the highest priority job that
 * hasn't been run already.  Quit after running a certain number of jobs,
 * to allow fair access to the DRM queue.  This process is
 * started as needed by the QueueDaemon.
 */
public class JobDaemon {
    /**
       how many jobs should each daemon run before quitting
       (to allow fair access to the DRM queue)
    */
    final public static int JOBS_PER_DAEMON = 1000;

    /**
     * how many times a job can fail before it fails permanently
     */
    final public static int MAX_FAIL = 10;

    /**
     * what is largest priority of any job?
     */
    final public static int MAX_PRIORITY = 6;

    /**
     * max time to allow job to run before calling it crashed
     * WHAT_CHECK can run for up to 12 hrs, so max time is 24h.
     */
    final public static int MAX_TIME_SECONDS = 86400;

    /**
     * run jobs in their own thread
     */
    static class Job extends Thread {
        int jobTypeID;
        int targetID;
        String args;
        int nFailures;
        String status;

        Job(int jobTypeID, int targetID, String args, int nFailures) {
            this.jobTypeID = jobTypeID;
            this.targetID = targetID;
            this.args = args;
            this.nFailures = nFailures;
            status = null;
        }

        public void run() {
            try {
                String arg[] = null;
                if (args != null)
                    arg = args.split(" ");
                switch (jobTypeID) {
                case 1:
                    MakeNewRAF.makeRAF(targetID);
                    MakePDBAuthor.findAuthors(targetID);
                    MakePDBSource.findSources(targetID);
                    MakePDBHeaders.findHeaders(targetID);
                    MakePDBSeqadv.findSeqadv(targetID);
                    MakePDBUniprot.findUniprot(targetID);
                    break;
                case 2:
                    double rv = CalcSPACI.getResolution(targetID);
                    if (Double.isNaN(rv) && (nFailures < MAX_FAIL - 1))
                        throw new Exception("Invalid Resolution");
                    rv = CalcSPACI.getRFactor(targetID);
                    if (Double.isNaN(rv) && (nFailures < MAX_FAIL - 1))
                        throw new Exception("Invalid R Factor");
                    LocalSQL.newJob(3, targetID, null);
                    break;
                case 3:
                    boolean rv2 = CalcSPACI.runWhatcheck(targetID);
                    if (!rv2 && (nFailures < MAX_FAIL - 1))
                        throw new Exception("Invalid WC");
                    LocalSQL.newJob(4, targetID, null);
                    break;
                case 4:
                    rv2 = CalcSPACI.runProcheck(targetID);
                    if (!rv2 && (nFailures < MAX_FAIL - 1))
                        throw new Exception("Invalid PC");
                    LocalSQL.newJob(5, targetID, null);
                    break;
                case 5:
                    CalcSPACI.calcSPACI(targetID);
                    break;
                case 6:
                    MakeChainSeq.makeChainSeq(targetID);
                    break;
                case 7:
                    MakeThumbnails.makeThumbnail(targetID,
                                                 (nFailures > 0));
                    break;
                case 8:
                    MakeThumbnails.copyRepThumbnails(targetID,true);
                    break;
                case 11:
                    HMMERSeqs.hmmerSeq(targetID,
                                       StringUtil.atoi(arg[0]));
                    break;
                case 12:
                    HMMERSeqs.hmmerSeq(targetID,
                                       0 - StringUtil.atoi(arg[0]));
                    break;
                case 16:
                    int pfamReleaseID = StringUtil.atoi(arg[0]);
                    int scopReleaseID = StringUtil.atoi(arg[1]);
                    MakeASTEROIDS.makeASTEROIDS(targetID,
                                                pfamReleaseID,
                                                scopReleaseID);
                    break;
                case 17:
                    MakePDBStyle.makePDBStyle(targetID);
                    break;
                case 18:
                    MakePDBStyle.makePDBStyleASTEROID(targetID);
                    break;
                case 19:
                    MakeThumbnails.makeThumbnailASTEROID(targetID,
                                                         (nFailures > 1));
                    break;
                case 20:
                    BlastSeqs.blastSeq(targetID,
                                       StringUtil.atoi(arg[0]),
                                       StringUtil.atoi(arg[1]),
                                       StringUtil.atoi(arg[2]),
                                       StringUtil.atoi(arg[3]));
                    break;
                case 21:
                    PromoteASTEROIDS.processChain(targetID,
                                                  StringUtil.atoi(arg[0]),
                                                  StringUtil.atoi(arg[1]),
                                                  StringUtil.atoi(arg[2]));
                    break;
                case 22:
                    CDDSeqs.cddSeq(targetID,
                                   StringUtil.atoi(arg[0]));
                    break;
                }
            } catch (Exception e) {
                e.printStackTrace();
                status = e.getMessage();
            }
        }
    }

    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();

            Statement stmt = LocalSQL.createStatement();
            PreparedStatement clearJobs = LocalSQL.prepareStatement("update job set running = null, time_started = null where running = ?");
            PreparedStatement takeJobs = LocalSQL.prepareStatement("update job set running = ?, time_started = now() where running is null and priority = ? and n_failures = ? order by id limit ?");
            PreparedStatement getJobs = LocalSQL.prepareStatement("select id, job_type_id, target_id, args, status from job where running = ?");
            PreparedStatement finished1 = LocalSQL.prepareStatement("insert into job_done (select id, job_type_id, time_created, time_started, now(), target_id, args, n_failures, running, priority, status from job where id = ?)");
            PreparedStatement finished2 = LocalSQL.prepareStatement("delete from job where id = ?");
            PreparedStatement fail = LocalSQL.prepareStatement("update job set running=null, time_started=null, status = ?, n_failures = ? where id = ?");
            PreparedStatement lock = LocalSQL.prepareStatement("lock table job write, job_done write");
            ;
            PreparedStatement unlock = LocalSQL.prepareStatement("unlock tables");

            // get my process name
            RuntimeMXBean rtb = ManagementFactory.getRuntimeMXBean();
            String processName = rtb.getName();
            processName = processName.replaceAll("\\W", ".");
            takeJobs.setString(1, processName);
            getJobs.setString(1, processName);
            clearJobs.setString(1, processName);

            // initial number of jobs at a time
            int nJobs = 1;
            takeJobs.setInt(4,nJobs);

            // run jobs
            for (int i = 0; i <= JOBS_PER_DAEMON; i++) {
                // clear out old jobs under my name
                clearJobs.executeUpdate();

                // pick N jobs and run them
                int nFailures = 0;
                String s = (new java.util.Date()).toString();
                TAKE_JOB:
                for (int priority = 0; priority <= MAX_PRIORITY; priority++) {
                    takeJobs.setInt(2, priority);
                    for (nFailures = 0; nFailures < MAX_FAIL; nFailures++) {
                        System.out.println("getting jobs priority " + priority + " failures " + nFailures + " " + s);
                        takeJobs.setInt(3, nFailures);
                        int n = 0; // jobs taken
                        try {
                            long t1 = new java.util.Date().getTime();
                            lock.executeUpdate();
                            n = takeJobs.executeUpdate();
                            unlock.executeUpdate();
                            long t2 = new java.util.Date().getTime();
                            if (t2 > t1+10L) { // 0.1 seconds
                                if (nJobs < 10) {
                                    nJobs++;
                                    System.out.println("nJobs = "+nJobs);
                                    takeJobs.setInt(4,nJobs);
                                }
                            }
                            else if (t2 < 2L) {
                                if (nJobs > 1) {
                                    nJobs--;
                                    System.out.println("nJobs = "+nJobs);
                                    takeJobs.setInt(4,nJobs);
                                }
                            }
                        } catch (Exception e2) {
                            System.out.println("Transaction failed");
                            n = 0;
                        }
                        if (n >= 1)
                            break TAKE_JOB;
                    }
                }
                s = (new java.util.Date()).toString();
                System.out.println("getting job details " + s);
                ResultSet rs = getJobs.executeQuery();
                boolean gotJob = false;
                while (rs.next()) {
                    gotJob = true;
                    int jobID = rs.getInt(1);
                    int jobTypeID = rs.getInt(2);
                    int targetID = rs.getInt(3);
                    String args = rs.getString(4);
                    String status = rs.getString(5);
                    if (status == null)
                        status = "";

                    s = (new java.util.Date()).toString();
                    System.out.println("running job " + jobID + " " + s);
                    boolean failed = false;
                    try {
                        Job j = new Job(jobTypeID, targetID, args, nFailures);
                        j.start();
                        j.join(1000 * MAX_TIME_SECONDS);
                        if (j.isAlive()) {
                            j.interrupt();
                            stmt.executeUpdate("insert into notify_message_queue (template_id, user_id) values (1,1)");
                            stmt.executeUpdate("set @message_id=last_insert_id();");
                            stmt.executeUpdate("insert into notify_parameter (message_id, parameter) values (@message_id, \"" + jobID + "\")");
                            stmt.executeUpdate("insert into notify_parameter (message_id, parameter) values (@message_id, \"" + processName + "\")");
                            throw new Exception("hung");
                        }
                        if (j.status != null) {
                            throw new Exception(j.status);
                        }
                    } catch (Exception e) {
                        System.out.println("failed job " + jobID);
                        e.printStackTrace();
                        status += e.getMessage() + "; ";
                        failed = true;
                    }
                    if (failed) {
                        nFailures++;
                        fail.setString(1, status);
                        fail.setInt(2, nFailures);
                        fail.setInt(3, jobID);
                        fail.executeUpdate();
                    }
                    if ((!failed) ||
                        (nFailures >= MAX_FAIL)) {
                        finished1.setInt(1, jobID);
                        finished2.setInt(1, jobID);
                        lock.executeUpdate();
                        finished1.executeUpdate();
                        finished2.executeUpdate();
                        unlock.executeUpdate();
                    }
                }
                rs.close();
                if (!gotJob) {
                    System.out.println("JobDaemon: no more jobs to take");
                    System.exit(0);
                }
            }
            System.out.println("normal finish");
        } catch (Exception e) {
            System.out.println("Exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
