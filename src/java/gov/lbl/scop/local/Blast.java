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
   Runs a local copy of blastp.  This requires that blastall be
   in your path (it should be in /pub/share/blast/bin), and
   that you have a ~/.ncbirc.
   <p>
   <pre>
   Version 3.0, 4/16/12 - updated to use new table format
   Version 2.0, 9/23/11 - updated to save gaps, if requested
   Version 1.5, 7/7/11 - fixed to be bug-compatible with Sasum; see 6/15/10 nb
   Version 1.4, 6/28/11 - more processing to deal with style on domain output
   Version 1.3, 6/21/10 - reads pba file as well as blast
   Version 1.2, 6/16/10 - revert some of 1.1, since we need multiple hits
   for bg file.
   Version 1.1, 5/27/10 - updated to only save first hit per query/subject pair
   Version 1.0, 11/26/08 - based on org.strbio.local.BlastP 2.2
   </pre>
   @version 3.0, 4/16/12
   @author JMC
*/
public class Blast extends Program {
    public String version = null;
    public String[] inputs = null;
    public File baseDir = null;
    
    public String programName() {
        if (version == null)
            return "blastpgp";
        else
            return "blastpgp-"+version;
    }

    public Blast(String ver) {
        version = ver;
    }

    public Blast() {
        version = null;
    }

    /**
       Run on current database, assuming inputs[] has been
       set up correctly
    */
    final public void process(File outFile) throws IOException {
        OutputStream os = new GZIPOutputStream(new FileOutputStream(outFile.getPath()));
        setOutput(os);
        run(inputs, null, baseDir);

        os.flush();
        os.close();
    }

    /**
       Run on the current database, saving everything into a
       directory named after the has of the last 2 letters of
       the name of the polymer.  inputIndex and outputIndex
       refers to the indices of inputs[] that will be updated
       with the name of the input file and PBA output file.
    */
    final public File[] process(Polymer p,
                                String dbName,
                                int inputIndex,
                                int outputIndex) throws Exception {
        File tmpFile = File.createTempFile("blast",null);
        tmpFile.delete();

        // create input
        PrintfWriter ow = new PrintfWriter(tmpFile.getPath());
        String tmpPName = p.name;
        p.name = "Q"+tmpPName;
        p.writeFasta(ow);
        p.name = tmpPName;
        ow.close();

        inputs[inputIndex] = tmpFile.getPath();

        // set up hash output dir
        int p1 = dbName.indexOf(".fa");
        String dbPart = ".";
        if (p1 > -1)
            dbPart = dbName.substring(0,p1);
        int p2 = p.name.length();
        p1 = p2-2;
        if (p1 < 0)
            p1 = 0;
        File hashDir = new File(baseDir.getPath()+File.separator+dbPart+File.separator+p.name.substring(p1,p2));
        if (!hashDir.isDirectory())
            hashDir.mkdirs();
        File outFile = new File(hashDir+File.separator+p.name+".bla.gz");
        File outFile2 = new File(hashDir+File.separator+p.name+".pba");
        File outFile3 = new File(hashDir+File.separator+p.name+".pba.gz");

        if (outFile.exists())
            outFile.delete();
        if (outFile2.exists())
            outFile2.delete();
        if (outFile3.exists())
            outFile3.delete();

        inputs[outputIndex] = outFile2.getPath();

        process(outFile);

        if (!outFile2.exists()) {
            throw new Exception("Failed to generate file " + outFile2.toString());
        }


        // compress pba file
        Program gz = new Program("gzip");
        String[] inputs2 = new String[2];
        inputs2[0] = "-9";
        inputs2[1] = outFile2.getPath();
        gz.setInput(null);
        gz.setOutput(null);
        gz.setError(null);
        gz.run(inputs2,null,hashDir);

        if (outFile2.exists())
            throw new Exception("PBA output failed to zip");

        tmpFile.delete();


        File[] rv = new File[2];
        rv[0] = outFile;
        rv[1] = outFile3;
        return rv;
    }

    /**
       Format a database
    */
    final public void formatDB(String fileName) {
        String programName = "formatdb";
        if (version != null)
            programName += "-"+version;
        Program p = new Program(programName);
        String[] i = new String[4];
        i[0] = "-o";
        i[1] = "T";
        i[2] = "-i";
        i[3] = fileName;
        p.run(i, null, baseDir);
    }

    /**
       Process BLAST output on a sequence ID; stores all results
       in given table.  Does not close stmt.  Saves gaps of at
       least minGapLength (0 = don't save)
    */
    final public void processOutput(Statement stmt,
                                    File outFile,
                                    File seqAlnFile,
                                    int seqID1,
                                    int sourceID,
                                    int styleID1,
                                    int styleID2,
                                    int scopReleaseID,
                                    int minGapLength) throws Exception {

        // read in the output
        if (!outFile.canRead())
            throw new Exception("no BLAST output");

        if ((seqAlnFile != null) && (!seqAlnFile.canRead()))
            throw new Exception("no BLAST SeqAlign output");

        int lenQ = 0;
        int lenT = 0;
        int seqID2 = 0;
        double log10E = Double.NaN;
        double pctID = Double.NaN;
        boolean firstBlank = true;
        int start1 = -1;
        int start2 = -1;
        int length1 = -1;
        int length2 = -1;
        String allQuery = "";
        String allHit = "";
		
        BufferedReader infile = IO.openReader(outFile.getPath());
        if (infile==null)
            throw new Exception("failed to open BLAST output");

        BufferedReader infile2 = null;
        if (seqAlnFile != null) {
            infile2 = IO.openReader(seqAlnFile.getPath());
            if (infile2==null)
                throw new Exception("failed to open BLAST SeqAlign output");
        }
	
        readBlast:
        while (infile.ready()) {
            String buffer = infile.readLine();
            if (buffer==null) {
                infile.close();
                if (infile2 != null)
                    infile2.close();
                return;
            }
            int l = buffer.length();

            if (l>0)
                firstBlank = true;

            if ((buffer.startsWith("Matrix:")) ||
                (buffer.startsWith("Lambda"))) {
                infile.close();
                if (infile2 != null)
                    infile2.close();
                return;
            }
            int p = buffer.indexOf(" letters)");
            if (p>0) {
                p = buffer.lastIndexOf('(',p);
                lenQ = StringUtil.atoi(buffer,p+1);
                lenT = 0;
                seqID2 = 0;
                log10E = Double.NaN;
                pctID = Double.NaN;
                start1 = -1;
                start2 = -1;
                length1 = -1;
                length2 = -1;
                allQuery = "";
                allHit = "";
                continue readBlast;
            }
	    
            p = buffer.indexOf(" Length =");
            if (p>0) {
                lenT = StringUtil.atoi(buffer,p+10);
                log10E = Double.NaN;
                pctID = Double.NaN;
                start1 = -1;
                start2 = -1;
                length1 = -1;
                length2 = -1;
                allQuery = "";
                allHit = "";
                continue readBlast;
            }
		    
            if ((l>0) && (buffer.charAt(0)=='>')) {
                seqID2 = StringUtil.atoi(buffer,1);
                // System.out.println(" hit "+seqID2);
                lenT = 0;
                log10E = Double.NaN;
                pctID = Double.NaN;
                start1 = -1;
                start2 = -1;
                length1 = -1;
                length2 = -1;
                allQuery = "";
                allHit = "";
                continue readBlast;
            }

            // read content
            if (buffer.startsWith(" Score =")) {
                start1 = -1;
                start2 = -1;
                length1 = -1;
                length2 = -1;
                allQuery = "";
                allHit = "";

                int expect = buffer.indexOf("Expect =");
                if (expect != -1) {
                    // look for exponential notation vs normal notation
                    int pos = buffer.indexOf("e-",expect);
                    if (pos==-1) {
                        // for example, Expect = 0.001
                        double E = StringUtil.atod(buffer,expect+9);
                        if (E==0.0)
                            log10E = -9999.0;
                        else
                            log10E = (Math.log(E) / Math.log(10.0));
                    }
                    else {
                        // for example, Expect = 9e-20, or Expect = e-100
                        log10E = StringUtil.atoi(buffer,pos);
                        int coef = StringUtil.atoi(buffer,expect+9,pos);
                        if (coef > 0)
                            log10E += (Math.log((double)coef) / Math.log(10.0));
                    }
                }
                else {
                    throw new Exception("BLAST problem:  E-value missing");
                }
                if (infile2 != null) {
                    // get more precise E-value out of seqalign file
                    boolean foundE = false;
                    boolean foundID = false;
                    double fallbackE = log10E;
                    // System.out.println("fallback E: "+log10E);
                    readAln:
                    while (!foundID) {
                        String buffer2 = infile2.readLine();
                        if (buffer2 == null) {
                            // use less precise E-value
                            log10E = fallbackE;
                            foundID = true;
                            continue readAln;
                        }
                        else if (buffer2.indexOf("str \"e_value\"") > -1) {
                            buffer2 = infile2.readLine();
                            buffer2 = infile2.readLine();
                            int pos = buffer2.indexOf("{");
                            long E = StringUtil.atol(buffer2,pos+2);
                            if (E==0)
                                log10E = -9999.0;
                            else
                                log10E = Math.log((double)E) / Math.log(10.0);
                            pos = buffer2.indexOf(",",pos+1);
                            if (StringUtil.atoi(buffer2,pos+1) != 10)
                                throw new Exception("SeqAlign problem:  wrong base");
                            pos = buffer2.indexOf(",",pos+1);
                            log10E += (double)StringUtil.atoi(buffer2,pos+1);
                            foundE = true;
                            continue readAln;
                        }
                        else if (foundE && (buffer2.indexOf("ids {") > -1)) {
                            buffer2 = infile2.readLine();
                            buffer2 = infile2.readLine();
                            int id1 = 0;
                            int pos = buffer2.indexOf("id");
                            if (pos > -1) 
                                id1 = StringUtil.atoi(buffer2,pos+3);
                            else {
                                pos = buffer2.indexOf("str \"Q");
                                if (pos > -1) 
                                    id1 = StringUtil.atoi(buffer2,pos+6);
                            }
                            buffer2 = infile2.readLine();
                            buffer2 = infile2.readLine();
                            pos = buffer2.indexOf("id");
                            int id2 = StringUtil.atoi(buffer2,pos+3);
                            if ((id1 == seqID1) &&
                                (id2 == seqID2)) {
                                foundID = true;
                            }
                            else {
                                foundE = false;  // start over
                            }
                        }
                    }
                }
            }
            else if (buffer.startsWith(" Identities =")) {
                start1 = -1;
                start2 = -1;
                length1 = -1;
                length2 = -1;
                allQuery = "";
                allHit = "";
                if (lenT+lenQ == 0)
                    pctID = Double.NaN;
                else {
                    if ((scopReleaseID > 0) &&
                        (scopReleaseID < 12)) {
                        // do double round off for bug-compatibility
                        // with sasum, as per 6/15/10 notebook entry
                        int pos = buffer.indexOf("/");
                        int alignLength = StringUtil.atoi(buffer,pos+1);
                        int nID = StringUtil.atoi(buffer,13);
                        long blastPctID = Math.round((double)nID*10000.0/(double)alignLength);
                        pctID = (double)(2*blastPctID*alignLength)/(double)((lenT+lenQ)*100);
                        // System.out.println("debug - pctID with "+seqID2+" = "+pctID);
                    }
                    else {
                        // do the calculation correctly
                        int nID = StringUtil.atoi(buffer,13);
                        pctID = ((double)(nID*2)/(double)(lenT+lenQ))*100.0;
                    }
                }
            }
            else if (buffer.startsWith("Query:")) {
                String queryBuffer = buffer;
                infile.readLine();
                String hitBuffer = infile.readLine();
                int lastQ = queryBuffer.lastIndexOf(' ');
                int lastH = hitBuffer.lastIndexOf(' ');
                if (lastH < lastQ) lastQ = lastH;

                if (lastQ < 10)
                    throw new Exception("BLAST problem: query/sbjct lines too short");

                if (start1 == -1)
                    start1 = StringUtil.atoi(queryBuffer,6) - 1;
                if (start2 == -1)
                    start2 = StringUtil.atoi(hitBuffer,6) - 1;
                length1 = StringUtil.atoi(queryBuffer,lastQ+1) - start1;
                length2 = StringUtil.atoi(hitBuffer,lastH+1) - start2;

                // save entire hit sequence if we're looking for gaps
                if (minGapLength > 0) {
                    // find first residue in query sequence;
                    int firstQ = 6;
                    while ((firstQ < lastQ-1) &&
                           (!Residue.isRes(queryBuffer.charAt(firstQ))) &&
                           (queryBuffer.charAt(firstQ)!='X'))
                        firstQ++;

                    allQuery += queryBuffer.substring(firstQ,lastQ);
                    allHit += hitBuffer.substring(firstQ,lastQ);
                }
            }

            if (seqID2==0)
                continue readBlast;

            if (l==0) {
                if (firstBlank) {
                    firstBlank = false;
                    continue readBlast;
                }
                if ((!Double.isNaN(log10E)) &&
                    (!Double.isNaN(pctID)) &&
                    (start1 >= 0) &&
                    (start2 >= 0) &&
                    (length1 > 0) &&
                    (length2 > 0)) {

                    stmt.executeUpdate("insert into astral_seq_blast values (NULL, "+
                                       seqID1+", "+
                                       seqID2+", "+
                                       sourceID+", "+
                                       styleID1+", "+
                                       styleID2+", "+
                                       (scopReleaseID==0 ? "null" : scopReleaseID)+", "+
                                       log10E+", "+
                                       pctID+", "+
                                       start1+", "+
                                       length1+", "+
                                       start2+", "+
                                       length2+")",
                                       Statement.RETURN_GENERATED_KEYS);

                    if (minGapLength > 0) {
                        ResultSet rs = stmt.getGeneratedKeys();
                        rs.next();
                        int hitID = rs.getInt(1);
                        rs.close();

                        // check both records to be sure length matches
                        int length1b = allQuery.length();
                        int length1c = 0;
                        for (int i=0; i<length1b; i++) {
                            if (allQuery.charAt(i) != '-')
                                length1c++;
                        }
                        if (length1c != length1)
                            throw new Exception("Query error: "+seqID1+" "+seqID2+" "+allQuery+" "+length1+" vs "+length1c);
                        int length2b = allHit.length();
                        int length2c = 0;
                        for (int i=0; i<length2b; i++) {
                            if (allHit.charAt(i) != '-')
                                length2c++;
                        }
                        if (length2c != length2)
                            throw new Exception("Hit error: "+seqID1+" "+seqID2+" "+allHit+" "+length2+" vs "+length2c);

                        // save gaps
                        int queryPos = start1;
                        int gapStart = 0;
                        int gapLength = 0;
                        for (int i=0; i<length1b; i++) {
                            if (allHit.charAt(i) == '-') {
                                if (gapLength==0)
                                    gapStart = queryPos;
                                gapLength++;
                            }
                            else {
                                if (gapLength >= minGapLength) {
                                    stmt.executeUpdate("insert into astral_seq_blast_gap values (NULL, "+
                                                       hitID+", "+
                                                       gapStart+", "+
                                                       gapLength+")");
                                }
                                gapLength = 0;
                            }
                            if (allQuery.charAt(i) != '-')
                                queryPos++;
                        }
                    }
                }
                else
                    throw new Exception("Error: "+seqID1+" "+seqID2+" "+log10E+" "+pctID+" "+start1+" "+start2+" "+length1+" "+length2);
                firstBlank = true;
            }
        }
        infile.close();
        if (infile2 != null)
            infile2.close();
    }
}

