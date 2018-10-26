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
import java.sql.*;
import java.util.*;
import java.util.zip.*;
import org.strbio.IO;
import org.strbio.io.*;
import org.strbio.mol.*;
import org.strbio.util.*;
import org.strbio.local.*;

/**
   Runs a local copy of RPS-BLAST.
   <p>
   <pre>
   Version 1.0, 7/12/15 - based on gov.lbl.scop.local.HMMER 3.0
   </pre>
   @version 1.0, 7/12/15
   @author JMC
*/
public class RPSBlast extends Program {
    public String version = null;
    public String[] inputs = null;
    public File baseDir = null;
    
    public String programName() {
        if (version == null)
            return "rpsblast";
        else
            return "rpsblast-"+version;
    }

    public RPSBlast(String ver) {
        version = ver;
    }

    public RPSBlast() {
        version = null;
    }

    /**
       Run on current database, assuming inputs[] has been
       set up correctly
    */
    final public void process(File outFile) throws IOException {
        OutputStream os = null;
        if (outFile != null)
            os = new GZIPOutputStream(new FileOutputStream(outFile.getPath()));
        setOutput(os);
        run(inputs, null, baseDir);
        if (os != null) {
            os.flush();
            os.close();
        }
    }

    /**
       Run on the current database, saving everything into a
       directory named after the has of the last 2 letters of
       the name of the polymer.  inputIndex
       refers to the indices of inputs[] that will be updated
       with the name of the input file.  returns output file.
    */
    final public File process(Polymer p, int inputIndex) throws Exception {
        File outFile = null;

        File tmpFile = File.createTempFile("rps",null);
        tmpFile.delete();

        // create input
        PrintfWriter ow = new PrintfWriter(tmpFile.getPath());
        p.writeFasta(ow);
        ow.close();

        inputs[inputIndex] = tmpFile.getPath();

        // set up hash output dir
        int p2 = p.name.length();
        int p1 = p2-2;
        if (p1 < 0)
            p1 = 0;
        File hashDir = new File(baseDir.getPath()+File.separator+p.name.substring(p1,p2));
        if (!hashDir.isDirectory())
            hashDir.mkdir();
        outFile = new File(hashDir+File.separator+p.name+".txt.gz");

        process(outFile);
	    
        tmpFile.delete();

        return outFile;
    }

    /**
       Process RPSBlast output on a sequence ID; stores all results
       in given table.  Does not close stmt.
    */
    final public void processOutput(Statement stmt,
                                    File outFile,
                                    int seqID,
                                    int releaseID)  throws Exception {
        // read in the output
        if (!outFile.canRead())
            throw new Exception("no RPS-BLAST output");

        BufferedReader infile = IO.openReader(outFile.getPath());
        while (infile.ready()) {
            String buffer = infile.readLine();
            if (buffer==null) {
                infile.close();
                return;
            }

            String[] fields = buffer.split("\t");
            int pssmID = StringUtil.atoi(fields[0],3);
            ResultSet rs = stmt.executeQuery("select id from cdd where pssm_id="+pssmID+" and release_id="+releaseID);
            int cddID = 0;
            if (rs.next())
                cddID = rs.getInt(1);
            rs.close();

            if (cddID==0)
                throw new Exception("No recognized domain in line '"+buffer+"'");

            double log10E = Double.NaN;
            int pos = fields[1].indexOf("e-");
            if (pos==-1) {
                double E = StringUtil.atod(fields[1]);
                if (E==0.0)
                    log10E = -9999.0;
                else
                    log10E = (Math.log(E) / Math.log(10.0));
            }
            else {
                // for example, Expect = 9e-20, or Expect = e-100
                log10E = StringUtil.atoi(fields[1],pos);
                int coef = StringUtil.atoi(fields[1],0,pos);
                if (coef > 0)
                    log10E += (Math.log((double)coef) / Math.log(10.0));
            }
            
            int start = StringUtil.atoi(fields[2]) - 1;
            int l = StringUtil.atoi(fields[3]) - start;
            int pStart = StringUtil.atoi(fields[4]) - 1;
            int pL = StringUtil.atoi(fields[5]) - pStart;
			
            stmt.executeUpdate("insert into astral_seq_cdd values (NULL, "+
                               seqID+", "+
                               cddID+", "+
                               log10E+", "+
                               start+", "+
                               l+", "+
                               pStart+", "+
                               pL+")");
        }
        infile.close();
    }
}
