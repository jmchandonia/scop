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
import gov.lbl.scop.util.SPACI;

/**
   Class to run pdbfilter1.pl (to make PDB-style files)
*/
public class PDBFilter extends Program {
    final public String programName() {
        return "/lab/proj/astral/bin/pdbfilter1.pl";
    }

    /**
       Run on a domain, given all info
    */
    final public static void makePDBStyle(String inFileName,
                                          String outFileName,
                                          String rafFileName,
                                          String rafIndexFileName,
                                          String scopRelease,
                                          String sid,
                                          String description,
                                          int sunid,
                                          String sccs,
                                          String comments,
                                          SPACI.SPACILine spacis,
                                          String pfamRelease,
                                          String headerASTEROIDS) {
        PDBFilter pf = new PDBFilter();
        try {
            if (comments==null)
                comments = "";

            StringWriter sw = new StringWriter();
            PrintfWriter outfile = new PrintfWriter(sw);
            spacis.print(outfile,false);
            outfile.flush();
            String spaciString = sw.toString();
            // take out key (pdb code)
            int pos = spaciString.indexOf("\t");
            spaciString = spaciString.substring(pos+1);
	    
            String[] input;
            if (pfamRelease==null)
                input = new String[22];
            else
                input = new String[26];
            input[0] = "-p";
            input[1] = inFileName;
            input[2] = "-o";
            input[3] = outFileName;
            input[4] = "-r";
            input[5] = rafFileName;
            input[6] = "-s";
            input[7] = sid;
            input[8] = "-d";
            input[9] = description;
            input[10] = "-u";
            input[11] = ""+sunid;
            input[12] = "-c";
            input[13] = sccs;
            input[14] = "-a";
            input[15] = comments;
            input[16] = "-b";
            input[17] = spaciString;
            input[18] = "-v";
            input[19] = scopRelease;
            input[20] = "-i";
            input[21] = rafIndexFileName;
            if (pfamRelease != null) {
                input[22] = "-P";
                input[23] = pfamRelease;
                input[24] = "-A";
                input[25] = headerASTEROIDS;
            }

            pf.run(input);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
