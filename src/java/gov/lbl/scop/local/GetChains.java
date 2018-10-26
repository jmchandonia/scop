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
 Class to run getChains.pl
 */
public class GetChains extends Program {
    final public String programName() {
	return "/lab/proj/astral/bin/getChains.pl";
    }

    /**
       Returns a HashMap with the keys being the chain ids
       (Character) and the values being the chain types (String),
       or null if failure
    */
    final public static HashMap<Character,String> getChains(String fileName) {
	GetChains gc = new GetChains();
	HashMap<Character,String> rv = new HashMap<Character,String>();
	BufferedReader infile;
	String buffer;
	try {
	    File tmpFile = File.createTempFile("getch",null);
	    tmpFile.delete();

	    String[] input = new String[2];
	    input[0] = fileName;
	    input[1] = tmpFile.getPath();

	    gc.run(input);

	    // read output
	    infile = IO.openReader(tmpFile.getPath());
	    while ((buffer = infile.readLine()) != null) {
		StringTokenizer st = new StringTokenizer(buffer,"\t");
		char c = st.nextToken().charAt(0);
		String chainType = st.nextToken();
		rv.put(new Character(c),chainType);
	    }
	    infile.close();

	    tmpFile.delete();
	}
	catch (IOException e) {
	    rv = null;
	    e.printStackTrace();
	}
	return rv;
    }

    /**
       test:  prints chains for a PDBML file
    */
    final public static void main(String[] argv) {
	HashMap<Character,String> rv = getChains(argv[0]);
	if (rv==null)
	    System.out.println("no output");
	else {
	    for (Character c : rv.keySet()) {
		System.out.println(c+" "+rv.get(c));
	    }
	}
    }
}
