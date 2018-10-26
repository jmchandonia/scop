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
   Runs a local copy of HMMER.  This requires that hmmpfam and/or
   hmmscan are in your path.
   <p>
   <pre>
   Version 3.0, 7/20/12 - updated to deal with HMMER3
   Version 2.0, 9/23/11 - updated to save gaps, if requested
   Version 1.0, 11/26/08 - based on gov.lbl.scop.app.Blast 1.5
   </pre>
   @version 3.0, 7/20/12
   @author JMC
*/
public class HMMER extends Program {
    public String version = null;
    public String[] inputs = null;
    public File baseDir = null;
    
    public String programName() {
        if (version == null)
            return "hmmscan";
        else if (version.startsWith("2."))
            return "hmmpfam-"+version;
        else
            return "hmmscan-"+version;
    }

    public HMMER(String ver) {
        version = ver;
    }

    public HMMER() {
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

        File tmpFile = File.createTempFile("hmmer",null);
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
        outFile = new File(hashDir+File.separator+p.name+".hmmer.gz");

        process(outFile);
	    
        tmpFile.delete();

        return outFile;
    }

    /**
       Process HMMER output on a sequence ID; stores all results
       in given table.  Does not close stmt.  releaseID refers
       to the Pfam release if positive and the SCOP release (for
       ASTEROIDS) if negative.  Saves gaps of at least minGapLength
       (0 = don't save)
    */
    final public void processOutput(Statement stmt,
                                    File outFile,
                                    int seqID,
                                    int releaseID,
                                    int minGapLength) throws Exception {
        // read in the output
        if (!outFile.canRead())
            throw new Exception("no HMMER output");

        int hmmID=0;
        double log10E;
        double score;
        int start;
        int l;
        int hStart;
        int hLength;
        Vector<Integer> hits = null;
        Vector<Integer> lengths = null;
        Vector<Integer> starts = null;
        String tableName = "astral_seq_hmm_";
        if (releaseID <= -1)
            tableName += "asteroids";
        else if (releaseID == 0)
            throw new IllegalArgumentException();
        else
            tableName += "pfam";

        if (minGapLength > 0) {
            hits = new Vector<Integer>();
            lengths = new Vector<Integer>();
            starts = new Vector<Integer>();
        }
		
        BufferedReader infile = IO.openReader(outFile.getPath());
        if (infile==null)
            throw new Exception("failed to open HMMER output");

        while (infile.ready()) {
            String buffer = infile.readLine();
            if (buffer==null) {
                infile.close();
                return;
            }

            if (buffer.startsWith("Query sequence:")) {
                // hmmer2
                String queryName = buffer.substring(16);
                int queryID = StringUtil.atoi(queryName);
                if (queryID != seqID)
                    throw new Exception ("HMMER output contains wrong query");
            }
            else if (buffer.startsWith("Query:")) {
                // hmmer3
                String queryName = buffer.substring(7);
                int queryID = StringUtil.atoi(queryName);
                if (queryID != seqID)
                    throw new Exception ("HMMER output contains wrong query");
            }
            else if (buffer.startsWith("Parsed for domains:")) {
                // hmmer2
                buffer = infile.readLine();
                buffer = infile.readLine();
                buffer = infile.readLine();

                while ((buffer.length() > 0) && (buffer.charAt(0) != ' ')
                       && (buffer.charAt(0) != '\t')) {
                    StringTokenizer st = new StringTokenizer(buffer);
                    try {
                        String modelName = st.nextToken();
                        // System.out.println("model: "+modelName);
                        hmmID = 0;
                        hLength = 0;
                        if (releaseID > 0) {
                            ResultSet rs = stmt.executeQuery("select id, length from pfam where name=\""+modelName+"\" and release_id="+releaseID);
                            if (rs.next()) {
                                hmmID = rs.getInt(1);
                                hLength = rs.getInt(2);
                            }
                            rs.close();
                        }
                        else if (releaseID < 0) {
                            ResultSet rs = stmt.executeQuery("select id from scop_node where sccs=\""+modelName+"\" and (level_id=4 or level_id=5) and release_id="+(0-releaseID));
                            if (rs.next())
                                hmmID = rs.getInt(1);
                            rs.close();
                        }

                        if (hmmID==0)
                            throw new Exception("No recognized domain in line '"+buffer+"'");

                        st.nextToken();
                        start = StringUtil.atoi(st.nextToken()) - 1;
                        l = StringUtil.atoi(st.nextToken()) - start;
                        st.nextToken();
                        st.nextToken();
                        st.nextToken();
			
                        String hmmBounds = st.nextToken();
                        if (!hmmBounds.equals("[]")) {
                            if (minGapLength > 0) {
                                hits.add(new Integer(0));
                                lengths.add(new Integer(0));
                                starts.add(new Integer(0));
                            }
                            continue;
                        }
			
                        score = StringUtil.atod(st.nextToken());
                        String eString = st.nextToken();
                        int expos = eString.indexOf("e-");
                        if (expos==-1) {
                            double e = StringUtil.atod(eString);
                            if (e==0.0)
                                log10E = -9999.0;
                            else {
                                log10E = Math.log(e)/Math.log(10.0);
                            }
                        }
                        else {
                            log10E = (double)StringUtil.atoi(eString, expos+1);
                            double coef = StringUtil.atod(eString,0,expos);
                            if (coef > 0.0) {
                                log10E += Math.log(coef)/Math.log(10.0);
                            }
                        }
                        stmt.executeUpdate("insert into "+tableName+" values (NULL, "+
                                           seqID+", "+
                                           hmmID+", "+
                                           log10E+", "+
                                           score+", "+
                                           start+", "+
                                           l+", 0, "+
                                           hLength+")",
                                           Statement.RETURN_GENERATED_KEYS);
                        if (minGapLength > 0) {
                            ResultSet rs = stmt.getGeneratedKeys();
                            rs.next();
                            hits.add(new Integer(rs.getInt(1)));
                            rs.close();
                            lengths.add(new Integer(l));
                            starts.add(new Integer(start));
                        }
                    }
                    catch (NoSuchElementException e) {
                        throw new Exception("Format error in line '"+buffer+"'");
                    }
                    buffer = infile.readLine();
                }
            }
            else if (buffer.startsWith("Alignments of top-scoring domains:")) {
                // hmmer2
                if (minGapLength == 0) {
                    infile.close();
                    return;
                }

                int nAlignments = hits.size();
                buffer = infile.readLine();
                for (int j=0; j<nAlignments; j++) {
                    String allQuery = "";
                    boolean done = false;
                    int hitID = hits.get(j).intValue();
                    int length1 = lengths.get(j).intValue();
                    int start1 = starts.get(j).intValue();
                    while (!done) {
                        String queryBuffer = null;
                        do {
                            queryBuffer = buffer;
                            buffer = infile.readLine();
                        } while (buffer.length() > 0);

                        if (hitID != 0) {
                            int lastQ = queryBuffer.length()-1;
                            int firstQ = 6;
                            while ((firstQ < lastQ-1) &&
                                   (!Residue.isRes(queryBuffer.charAt(firstQ))) &&
                                   (Character.toUpperCase(queryBuffer.charAt(firstQ))!='X'))
                                firstQ++;
                            while ((lastQ > firstQ) &&
                                   (!Residue.isRes(queryBuffer.charAt(lastQ))) &&
                                   (Character.toUpperCase(queryBuffer.charAt(lastQ))!='X'))
                                lastQ--;
                            // System.out.println("QB: '"+queryBuffer+"'");
                            // System.out.println("FQ: "+firstQ);
                            // System.out.println("LQ: "+lastQ);

                            if (lastQ < queryBuffer.length()-1) {
                                int lastNum = StringUtil.atoi(queryBuffer,lastQ+1);
                                if (lastNum > 0)
                                    allQuery += queryBuffer.substring(firstQ,lastQ+1);
                            }
                        }
			
                        buffer = infile.readLine();
                        if ((buffer.indexOf(": score ") > -1) ||
                            (buffer.indexOf("//") == 0))
                            done = true;
                    }

                    if (hitID > 0) {
                        // check both records to be sure length matches
                        int length1b = allQuery.length();
                        int length1c = 0;
                        for (int i=0; i<length1b; i++) {
                            if (allQuery.charAt(i) != '-')
                                length1c++;
                        }
                        if (length1c != length1)
                            throw new Exception("Query error: "+seqID+" "+hitID+" "+allQuery+" "+length1+" vs "+length1c);

                        // save gaps
                        int queryPos = start1;
                        int gapStart = 0;
                        int gapLength = 0;
                        for (int i=0; i<length1b; i++) {
                            char c = allQuery.charAt(i);
                            if (Character.isLowerCase(c)) {
                                if (gapLength==0)
                                    gapStart = queryPos;
                                gapLength++;
                            }
                            else {
                                if (gapLength >= minGapLength) {
                                    stmt.executeUpdate("insert into "+tableName+"_gap values (NULL, "+
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
            }
            else if (buffer.startsWith("Domain annotation for each model (and alignments):")) {
                // hmmer3
                buffer = infile.readLine();

                while (buffer.startsWith(">> ")) {
                    StringTokenizer st = new StringTokenizer(buffer);
                    try {
                        st.nextToken();
                        String modelName = st.nextToken();
                        // System.out.println("model: "+modelName);
                        hmmID = 0;
                        if (releaseID > 0) {
                            ResultSet rs = stmt.executeQuery("select id from pfam where accession=\""+modelName+"\" and release_id="+releaseID);
                            if (rs.next())
                                hmmID = rs.getInt(1);
                            rs.close();
                        }
                        else if (releaseID < 0) {
                            ResultSet rs = stmt.executeQuery("select id from scop_node where sccs=\""+modelName+"\" and (level_id=4 or level_id=5) and release_id="+(0-releaseID));
                            if (rs.next())
                                hmmID = rs.getInt(1);
                            rs.close();
                        }

                        if (hmmID==0)
                            throw new Exception("No recognized domain in line '"+buffer+"'");

                    }
                    catch (NoSuchElementException e) {
                        throw new Exception("Format error in line '"+buffer+"'");
                    }

                    buffer = infile.readLine();
                    buffer = infile.readLine();
                    buffer = infile.readLine();

                    if (buffer.startsWith(">> "))
                        continue;
		    
                    while (buffer.length() > 0) {
                        st = new StringTokenizer(buffer.substring(7));
                        try {
                            score = StringUtil.atod(st.nextToken());
                            st.nextToken(); // bias
                            st.nextToken(); // c-evalue
			
                            String eString = st.nextToken();  // i-evalue
                            int expos = eString.indexOf("e-");
                            if (expos==-1) {
                                double e = StringUtil.atod(eString);
                                if (e==0.0)
                                    log10E = -9999.0;
                                else {
                                    log10E = Math.log(e)/Math.log(10.0);
                                }
                            }
                            else {
                                log10E = (double)StringUtil.atoi(eString, expos+1);
                                double coef = StringUtil.atod(eString,0,expos);
                                if (coef > 0.0) {
                                    log10E += Math.log(coef)/Math.log(10.0);
                                }
                            }

                            hStart = StringUtil.atoi(st.nextToken()) - 1;
                            hLength = StringUtil.atoi(st.nextToken()) - hStart;

                            st.nextToken(); // bounds

                            start = StringUtil.atoi(st.nextToken()) - 1;
                            l = StringUtil.atoi(st.nextToken()) - start;
			
                            stmt.executeUpdate("insert into "+tableName+" values (NULL, "+
                                               seqID+", "+
                                               hmmID+", "+
                                               log10E+", "+
                                               score+", "+
                                               start+", "+
                                               l+", "+
                                               hStart+", "+
                                               hLength+")",
                                               Statement.RETURN_GENERATED_KEYS);
                            if (minGapLength > 0) {
                                ResultSet rs = stmt.getGeneratedKeys();
                                rs.next();
                                hits.add(new Integer(rs.getInt(1)));
                                rs.close();
                                lengths.add(new Integer(l));
                                starts.add(new Integer(start));
                            }
                        }
                        catch (NoSuchElementException e) {
                            throw new Exception("Format error in line '"+buffer+"'");
                        }
                        buffer = infile.readLine();
                    }

                    // read in alignments
                    buffer = infile.readLine();
                    buffer = infile.readLine();

                    int nAlignments = hits.size();
                    for (int j=0; j<nAlignments; j++) {
                        int hitID = hits.get(j).intValue();
                        int length1 = lengths.get(j).intValue();
                        int start1 = starts.get(j).intValue();

                        String allQuery = null;
                        String queryBuffer = null;
                        String buffer2 = null;
                        do {
                            queryBuffer = buffer2;
                            buffer2 = buffer;
                            buffer = infile.readLine();
                        } while (buffer.length() > 0);

                        if (hitID != 0) {
                            int lastQ = queryBuffer.length()-1;
                            int firstQ = 6;
                            while ((firstQ < lastQ-1) &&
                                   (!Residue.isRes(queryBuffer.charAt(firstQ))) &&
                                   (Character.toUpperCase(queryBuffer.charAt(firstQ))!='X'))
                                firstQ++;
                            while ((lastQ > firstQ) &&
                                   (!Residue.isRes(queryBuffer.charAt(lastQ))) &&
                                   (Character.toUpperCase(queryBuffer.charAt(lastQ))!='X'))
                                lastQ--;
                            // System.out.println("QB: '"+queryBuffer+"'");
                            // System.out.println("FQ: "+firstQ);
                            // System.out.println("LQ: "+lastQ);

                            if (lastQ < queryBuffer.length()-1) {
                                int lastNum = StringUtil.atoi(queryBuffer,lastQ+1);
                                if (lastNum > 0)
                                    allQuery = queryBuffer.substring(firstQ,lastQ+1);
                            }
                        }

                        if (hitID > 0) {
                            // check both records to be sure length matches
                            int length1b = allQuery.length();
                            int length1c = 0;
                            for (int i=0; i<length1b; i++) {
                                if (allQuery.charAt(i) != '-')
                                    length1c++;
                            }
                            if (length1c != length1)
                                throw new Exception("Query error: "+seqID+" "+hitID+" "+allQuery+" "+length1+" vs "+length1c);

                            // save gaps
                            int queryPos = start1;
                            int gapStart = 0;
                            int gapLength = 0;
                            for (int i=0; i<length1b; i++) {
                                char c = allQuery.charAt(i);
                                if (Character.isLowerCase(c)) {
                                    if (gapLength==0)
                                        gapStart = queryPos;
                                    gapLength++;
                                }
                                else {
                                    if (gapLength >= minGapLength) {
                                        stmt.executeUpdate("insert into "+tableName+"_gap values (NULL, "+
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
                    hits.clear();
                    lengths.clear();
                    starts.clear();
                    buffer = infile.readLine();
                }
            }
        }
        infile.close();
    }
}

