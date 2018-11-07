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

/**
   Class to run CheckRAF utility
*/
public class CheckRAF extends Program {
    final public String programName() {
        return "/lab/proj/astral/bin/checkraf1.pl";
    }

    /**
       returns all output of checking a string
    */
    final public static String checkRAF(String rafLine) {
        String rv = "";
        String buffer;
        try {
            String[] input = new String[3];
            File[] tmpFile = new File[3];
            for (int i=0; i<3; i++) {
                tmpFile[i] = File.createTempFile("raf",null);
                tmpFile[i].delete();
                input[i] = tmpFile[i].getPath();
            }

            // create input
            PrintfWriter ow = new PrintfWriter(input[0]);
            ow.write(rafLine);
            ow.close();	    

            CheckRAF cr = new CheckRAF();
            cr.run(input);

            // read output
            BufferedReader infile = IO.openReader(input[2]);
            while ((buffer = infile.readLine()) != null) {
                rv += buffer+"\n";
            }
            infile.close();

            for (int i=0; i<3; i++) {
                tmpFile[i].delete();
            }
        }
        catch (IOException e) {
            e.printStackTrace();
            rv = null;
        }
        return rv;
    }

    /**
       test:  prints RAF for a PDBML file
    */
    final public static void main(String[] argv) {
        String rv = checkRAF(argv[0]);
        if (rv==null)
            System.out.println("no output");
        else {
            System.out.println(rv);
        }
    }
}
