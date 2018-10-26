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
import java.util.regex.*;
import org.strbio.io.*;
import org.strbio.math.*;
import org.strbio.mol.*;
import org.strbio.util.*;
import org.strbio.IO;
import org.xml.sax.*;
import org.xml.sax.helpers.*;
import javax.xml.parsers.*;
import gov.lbl.scop.local.LocalSQL;

/**
   Link PDB releases to Uniprot accession numbers
*/
public class MakePDBUniprot {
    public static class PDBMLHandler extends DefaultHandler {
	private int inElement = 0;
	private boolean isUniprot = false;
	private String curDB = "";
	private String curAcc = "";
	public String curChain = "";
	public String curEntity = null;
        public HashMap<String,String> chainMap = new HashMap<String,String>();
	public HashMap<String,HashSet<String>> accMap = new HashMap<String,HashSet<String>>();

	public void startElement(String uri,
				 String localName,
				 String qName,
				 Attributes attributes) {
	    if (qName.equals("PDBx:struct_ref")) {
		inElement = 1;
		isUniprot = false;
		curDB = "";
		curAcc = "";
		curEntity = null;
	    }
	    else if ((qName.equals("PDBx:db_name")) && (inElement==1)) {
		inElement = 2;
		isUniprot = false;
		curDB = "";
	    }
	    else if ((qName.equals("PDBx:pdbx_db_accession")) && (inElement==1)) {
		inElement = 3;
		curAcc = "";
	    }
	    else if ((qName.equals("PDBx:entity_id")) && (inElement==1)) {
		inElement = 4;
		curEntity = "";
	    }
	    else if (qName.equals("PDBx:pdbx_poly_seq_scheme")) {
		if (attributes != null)
		    curEntity = attributes.getValue("entity_id");
		else
		    curEntity = null;
		curChain = "";
	    }
	    else if (qName.equals("PDBx:pdb_strand_id")) {
		if (curChain.length()==0)
		    inElement = 5;
	    }
	}

	public void endElement(String uri,
			       String localName,
			       String qName) {
	    if (qName.equals("PDBx:struct_ref")) {
		inElement = 0;
		if (curEntity != null) {
		    if (isUniprot && (Pattern.matches("[OPQ][0-9][A-Z0-9]{3}[0-9]|[A-NR-Z][0-9]([A-Z][A-Z0-9]{2}[0-9]){1,2}",curAcc))) {
			HashSet<String> accessions = accMap.get(curEntity);
			if (accessions == null) {
			    accessions = new HashSet<String>();
			    accMap.put(curEntity,accessions);
			}
			accessions.add(curAcc);
		    }
		}
	    }
	    else if ((qName.equals("PDBx:db_name")) && (inElement==2)) {
		inElement = 1;
		if (curDB.equals("UNP"))
		    isUniprot = true;
	    }
	    else if ((qName.equals("PDBx:pdbx_db_accession")) && (inElement==3)) {
		inElement = 1;
	    }
	    else if ((qName.equals("PDBx:entity_id")) && (inElement==4)) {
		inElement = 1;
	    }
	    else if (qName.equals("PDBx:pdbx_poly_seq_scheme")) {
		inElement = 0;
		if (curEntity != null) {
		    if (curChain.length() == 1) {
			String x = chainMap.get(curEntity);
			if (x==null)
			    x = "";
			if (x.indexOf(curChain) == -1)
			    x += curChain;
			chainMap.put(curEntity, x);
		    }
		}
	    }
	    else if (qName.equals("PDBx:pdb_strand_id")) {
		inElement = 0;
		curChain = curChain.trim();
	    }
	}
	
	public void characters(char[] ch,
			       int start,
			       int length) {
	    if (inElement==2) {
		curDB += new String(ch, start, length);
	    }
	    else if (inElement==3) {
		curAcc += new String(ch, start, length);
	    }
	    else if (inElement==4) {
		curEntity += new String(ch, start, length);
	    }
	    else if (inElement==5) {
		curChain += new String(ch, start, length);
	    }
	}
    }

    /**
       find uniprot accessions for an individual PDB release
    */
    final public static void findUniprot(int pdbReleaseID) throws Exception {
	Statement stmt = LocalSQL.createStatement();
	ResultSet rs = stmt.executeQuery("select xml_path from pdb_local where pdb_release_id="+pdbReleaseID);
	if (!rs.next()) {
	    stmt.close();
	    return;
	}
	String xml = rs.getString(1);
	rs.close();

	System.out.println("finding uniprot accessions for "+xml);
	System.out.flush();

	BufferedReader infile = IO.openReader(xml);
	PDBMLHandler h = new PDBMLHandler();

	SAXParserFactory factory
	    = SAXParserFactory.newInstance();
	factory.setValidating(false);
	SAXParser parser = factory.newSAXParser();
	
	parser.parse(new InputSource(infile), h);

	stmt.executeUpdate("delete from pdb_chain_uniprot where pdb_chain_id in (select id from pdb_chain where pdb_release_id="+pdbReleaseID+")");

	for (String entity : h.chainMap.keySet()) {
	    String tmp = h.chainMap.get(entity);
	    HashSet<String> accessions = h.accMap.get(entity);
	    if (accessions==null)
		continue;
	    
	    for (int i=0; i<tmp.length(); i++) {
		char chain = tmp.charAt(i);

		rs = stmt.executeQuery("select id from pdb_chain where pdb_release_id="+pdbReleaseID+" and chain=\""+chain+"\" limit 1");
		if (rs.next()) {
		    int pdbChainID = rs.getInt(1);
		
		    for (String s : accessions) {
			if ((s!=null) && (s.length() > 0)) {
			    stmt.executeUpdate("insert into pdb_chain_uniprot values ("+
					       pdbChainID+", \""+
					       s+"\")");
			}
		    }
		}
		rs.close();
	    }
	}
	
	stmt.close();
    }

    
    final public static void main(String argv[]) {
	try {
	    LocalSQL.connectRW();
	    Statement stmt = LocalSQL.createStatement();
	    
	    ResultSet rs = stmt.executeQuery("select pdb_release_id from pdb_local where xml_path is not null and pdb_release_id not in (select distinct pdb_release_id from pdb_chain where id in (select distinct pdb_chain_id from pdb_chain_uniprot))");
	    while (rs.next()) {
		int id = rs.getInt(1);
		findUniprot(id);
	    }
	    rs.close();
	    stmt.close();
	}
	catch (Exception e) {
	    System.out.println("Exception: "+e.getMessage());
	    e.printStackTrace();
	}
    }
}
