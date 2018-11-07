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
package gov.lbl.scop.local;

import java.io.*;
import java.util.*;
import org.strbio.IO;
import org.strbio.io.*;
import org.strbio.local.*;
import org.xml.sax.*;
import org.xml.sax.helpers.*;
import javax.xml.parsers.*;

/**
   Class to interface with GridEngine.  Currently does not use
   DRMAA due to the Debian libraries not working.
*/
public class GridEngine {
    public static class JobStatus {
        public String id;  // usually numeric, but maybe not always?
        public String name;
        public String owner;
        public String state;
        public String queue;

        final public void delete() {
            File tmpDir = new File(System.getProperty("java.io.tmpdir"));
	    
            Program p = new Program("qdel");
            String[] input = new String[1];
            input[0] = id;
            p.setInput(null);
            p.setOutput(null);
            p.setError(null);
            p.run(input,null,tmpDir);
        }
    }
    
    public static class QSub extends Program {
        final public String programName() {
            return "qsub";
        }

        final public static void submit (String job) throws Exception {
            File tmpDir = new File(System.getProperty("java.io.tmpdir"));
	    
            String[] input = new String[3];
            input[0] = "-l";
            input[1] = "mf=2G";
            input[2] = job;
            QSub prog = new QSub();
            prog.setInput(null);
            prog.setOutput(null);
            prog.setError(null);
            prog.run(input,null,tmpDir);
        }

        final public static void submit (String[] params) throws Exception {
            File tmpDir = new File(System.getProperty("java.io.tmpdir"));
	    
            QSub prog = new QSub();
            prog.setInput(null);
            prog.setOutput(null);
            prog.setError(null);
            prog.run(params,null,tmpDir);
        }
    }

    public static class QStat extends Program {
        final public String programName() {
            return "qstat";
        }

        public static class QStatHandler extends DefaultHandler {
            public Vector<JobStatus> jobs = null;
            private String inElement = null;
            private JobStatus job = null;

            public QStatHandler() {
                jobs = new Vector<JobStatus>();
            }

            public void startElement(String uri,
                                     String localName,
                                     String qName,
                                     Attributes attributes) {
                inElement = qName;
                if (inElement.equals("job_list")) {
                    job = new JobStatus();
                    job.id = "";
                    job.name = "";
                    job.owner = "";
                    job.state = "";
                    job.queue = "";
                }
            }

            public void endElement(String uri,
                                   String localName,
                                   String qName) {
                if (qName.equals("job_list")) {
                    job.id = job.id.trim();
                    job.name = job.name.trim();
                    job.owner = job.owner.trim();
                    job.state = job.state.trim();
                    job.queue = job.queue.trim();
                    jobs.add(job);
                }
            }

            public void characters(char[] ch,
                                   int start,
                                   int length) {
                if ((inElement != null) && (length > 0)) {
                    if (inElement.equals("JB_job_number"))
                        job.id += new String(ch, start, length);
                    else if (inElement.equals("JB_name"))
                        job.name += new String(ch, start, length);
                    else if (inElement.equals("JB_owner"))
                        job.owner += new String(ch, start, length);
                    else if (inElement.equals("state"))
                        job.state += new String(ch, start, length);
                    else if (inElement.equals("queue_name"))
                        job.queue += new String(ch, start, length);
                }
            }
        }

        final public static Vector<JobStatus> getJobs() throws Exception {
            File tmpFile = File.createTempFile("qstat",null);
            tmpFile.delete();
            PrintfStream output = new PrintfStream(tmpFile.getPath());

            String[] input = new String[1];
            input[0] = "-xml";
            QStat prog = new QStat();
            prog.setInput(null);
            prog.setOutput(output);
            prog.setError(null);
            prog.run(input);
            output.close();

            SAXParserFactory factory
                = SAXParserFactory.newInstance();
            factory.setValidating(false);
            SAXParser parser = factory.newSAXParser();

            BufferedReader infile = IO.openReader(tmpFile.getPath());
            QStatHandler h = new QStatHandler();
            parser.parse(new InputSource(infile), h);
            infile.close();
	    
            tmpFile.delete();

            return h.jobs;
        }
    }

    /**
       submit a new job
    */
    final public static void submit(String job) throws Exception {
        QSub.submit(job);
    }

    /**
       submit a new job, with specified parameters
    */
    final public static void submit(String[] params) throws Exception {
        QSub.submit(params);
    }


    /**
       get list with status of all running jobs
    */
    final public static Vector<JobStatus> getJobs() throws Exception {
        return QStat.getJobs();
    }

    final public static void main(String argv[]) {
        try {
            Vector<JobStatus> jobs = getJobs();
            for (JobStatus j : jobs) {
                System.out.println("id "+j.id);
                System.out.println("name "+j.name);
                System.out.println("owner "+j.owner);
                System.out.println("state "+j.state);
                System.out.println("queue "+j.queue);
                System.out.println();
            }
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
