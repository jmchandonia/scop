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
   Find headers for PDB without headers, for which we have a local
   copy
*/
public class MakePDBHeaders {
    public static class PDBMLHandler extends DefaultHandler {
	private int inElement = 0;
	public String curTitle = "";
	public String curClass = "";
	public String curCompound = "";
	public String curCompoundSyn = "";
	public String curHet = "";
	public String curKeywords = "";
	public String curEntity = null;
	public String curChain = "";
	// maps entities to chains
	public HashMap<String,String> chainMap = new HashMap<String,String>();
	// list of heterogens found
	public Vector<String> hets = new Vector<String>();
	// maps entities to compounds
	public HashMap<String,String> compoundMap = new HashMap<String,String>();

	public void startElement(String uri,
				 String localName,
				 String qName,
				 Attributes attributes) {
	    if (qName.equals("PDBx:pdbx_poly_seq_scheme")) {
		curChain = "";
		if (attributes != null)
		    curEntity = attributes.getValue("entity_id");
		else
		    curEntity = null;
	    }
	    else if (qName.equals("PDBx:struct_keywords")) {
		curClass = "";
		curKeywords = "";
		inElement = 1;
	    }
	    else if ((qName.equals("PDBx:pdbx_keywords")) &&
		     (inElement==1)) {
		if (curClass.length() == 0)
		    inElement = 2;
	    }
	    else if ((qName.equals("PDBx:text")) &&
		     (inElement==1)) {
		if (curKeywords.length() == 0)
		    inElement = 3;
	    }
	    else if (qName.equals("PDBx:struct")) {
		curTitle = "";
		inElement = 4;
	    }
	    else if (qName.equals("PDBx:pdb_strand_id")) {
		if (curChain.length()==0)
		    inElement = 5;
	    }
	    else if ((qName.equals("PDBx:title")) &&
		     (inElement==4)) {
		if (curTitle.length() == 0)
		    inElement = 6;
	    }
	    else if ((qName.equals("PDBx:entity")) ||
		     (qName.equals("PDBx:entity_name_com"))) {
		if (attributes != null)
		    curEntity = attributes.getValue("id");
		else
		    curEntity = null;
		inElement = 7;
		curCompound = "";
		curCompoundSyn = "";
	    }
	    else if ((qName.equals("PDBx:pdbx_description")) &&
		     (inElement==7)) {
		if (curCompound.length() == 0)
		    inElement = 8;
	    }
	    else if ((qName.equals("PDBx:name")) &&
		     (inElement==7)) {
		if (curCompoundSyn.length() == 0)
		    inElement = 9;
	    }
	    else if (qName.equals("PDBx:comp_id")) {
		if (curHet.length() == 0)
		    inElement = 10;
	    }
	}

	public void endElement(String uri,
			       String localName,
			       String qName) {
	    if (qName.equals("PDBx:pdbx_poly_seq_scheme")) {
		inElement = 0;
		// System.out.println(curEntity);
		// System.out.println(curChain);
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
		curEntity = null;
	    }
	    else if (qName.equals("PDBx:struct_keywords")) {
		inElement = 0;
		curClass = curClass.trim();
		curKeywords = curKeywords.trim();
	    }
	    else if ((qName.equals("PDBx:pdbx_keywords")) &&
		     (inElement==2)) {
		inElement = 1;
	    }
	    else if ((qName.equals("PDBx:text")) &&
		     (inElement==3)) {
		inElement = 1;
	    }
	    else if (qName.equals("PDBx:struct")) {
		inElement = 0;
		curTitle = curTitle.trim();
	    }
	    else if (qName.equals("PDBx:pdb_strand_id")) {
		inElement = 0;
		curChain = curChain.trim();
	    }
	    else if ((qName.equals("PDBx:title")) &&
		     (inElement==6)) {
		inElement = 4;
	    }
	    else if ((qName.equals("PDBx:entity")) ||
		     (qName.equals("PDBx:entity_name_com"))) {
		if (curEntity != null) {
		    if (curCompoundSyn.length() > 0) {
			if (curCompound.length() > 0)
			    curCompound += ", ";
			curCompound += curCompoundSyn;
		    }
			
		    if (curCompound.length() > 0)		    
			compoundMap.put(curEntity, curCompound);
		}
		curCompound = "";
		curCompoundSyn = "";
		inElement = 0;
	    }
	    else if ((qName.equals("PDBx:pdbx_description")) &&
		     (inElement==8)) {
		inElement = 7;
	    }
	    else if ((qName.equals("PDBx:name")) &&
		     (inElement==9)) {
		inElement = 7;
	    }
	    else if (qName.equals("PDBx:comp_id")) {
		curHet = curHet.trim();
		if (curHet.length() > 0) {
		    if (!hets.contains(curHet))
			hets.add(curHet);
		}
		curHet = "";
		inElement = 0;
	    }
	}
	    
	public void characters(char[] ch,
			       int start,
			       int length) {
	    if (inElement==2)
		curClass += new String(ch, start, length);
	    else if (inElement==3)
		curKeywords += new String(ch, start, length);
	    else if (inElement==5)
		curChain += new String(ch, start, length);
	    else if (inElement==6)
		curTitle += new String(ch, start, length);
	    else if (inElement==8)
		curCompound += new String(ch, start, length);
	    else if (inElement==9)
		curCompoundSyn += new String(ch, start, length);
	    else if (inElement==10)
		curHet += new String(ch, start, length);
	}
    }

    /**
       note:  pdb_heterogen is not case sensitive
    */
    final public static int lookupOrCreateHeterogen(String heterogen) throws Exception {
	int rv = 0;
	Statement stmt = LocalSQL.createStatement();
	heterogen = StringUtil.replace(heterogen,"\"","\\\"");
	ResultSet rs = stmt.executeQuery("select id from pdb_heterogen where description=\""+
					 heterogen+
					 "\"");

	if (rs.next())
	    rv = rs.getInt(1);
	else {
	    stmt.executeUpdate("insert into pdb_heterogen values (null, \""+
			       heterogen+"\")",
			       Statement.RETURN_GENERATED_KEYS);
	    rs = stmt.getGeneratedKeys();
	    rs.next();
	    rv = rs.getInt(1);
	}
	stmt.close();
	return rv;
    }

    /**
       note:  pdb_compound is not case sensitive
    */
    final public static int lookupOrCreateCompound(String compound) throws Exception {
	int rv = 0;
	Statement stmt = LocalSQL.createStatement();
	compound = StringUtil.replace(compound,"\"","\\\"");
	ResultSet rs = stmt.executeQuery("select id from pdb_compound where description=\""+
					 compound+
					 "\"");

	if (rs.next())
	    rv = rs.getInt(1);
	else {
	    stmt.executeUpdate("insert into pdb_compound values (null, \""+
			       compound+"\")",
			       Statement.RETURN_GENERATED_KEYS);
	    rs = stmt.getGeneratedKeys();
	    rs.next();
	    rv = rs.getInt(1);
	}
	stmt.close();
	return rv;
    }

    /**
       If header is all upper case, convert to (mostly) lower case
    */
    final public static String fixHeader(String header) {
	int l = header.length();
	boolean hasLower = false;
	for (int i=0; i<l; i++) {
	    if (Character.isLowerCase(header.charAt(i)))
		return header; // no need to fix.
	}
	String rv = header.toLowerCase();

	// fix common words
	rv = rv.replaceAll("\\v\\b","V");
	rv = rv.replaceAll("\\biv\\b","IV");
	rv = rv.replaceAll("\\biii\\b","III");
	rv = rv.replaceAll("\\bii\\b","II");
	rv = rv.replaceAll("\\bi\\b","I");
	rv = rv.replaceAll("\\bt7\\b","T7");
	rv = rv.replaceAll("\\batpase\\b","ATPase");
	rv = rv.replaceAll("\\batp\\b","ATP");
	rv = rv.replaceAll("\\badp\\b","ADP");
	rv = rv.replaceAll("\\bgtp\\b","GTP");
	rv = rv.replaceAll("\\bgdp\\b","GDP");
	rv = rv.replaceAll("\\bntp\\b","NTP");
	rv = rv.replaceAll("\\bdntp\\b","dNTP");
	rv = rv.replaceAll("\\btfiis\\b","TFIIS");
	rv = rv.replaceAll("\\bgroel\\b","groEL");
	rv = rv.replaceAll("\\bgroes\\b","groES");
	rv = rv.replaceAll("\\brnase\\b","RNAse");
	rv = rv.replaceAll("\\bdnase\\b","DNAse");
	rv = rv.replaceAll("\\bdna\\b","DNA");
	rv = rv.replaceAll("\\bssdna\\b","ssDNA");
	rv = rv.replaceAll("\\brna\\b","RNA");
	rv = rv.replaceAll("\\brrna\\b","rRNA");
	rv = rv.replaceAll("\\bmrna\\b","mRNA");
	rv = rv.replaceAll("\\btrna\\b","tRNA");
	rv = rv.replaceAll("\\bdsrna\\b","dsRNA");
	rv = rv.replaceAll("\\bmhc\\b","MHC");
	rv = rv.replaceAll("\\bt-cell\\b","T-cell");

	return rv;
    }
    
    /**
       find headers for an individual PDB release
    */
    final public static void findHeaders(int pdbReleaseID) throws Exception {
	Statement stmt = LocalSQL.createStatement();
	ResultSet rs = stmt.executeQuery("select xml_path from pdb_local where pdb_release_id="+pdbReleaseID);
	if (!rs.next()) {
	    stmt.close();
	    return;
	}
	String xml = rs.getString(1);
	rs.close();

	System.out.println("reading headers for "+xml);
	System.out.flush();

	stmt.executeUpdate("delete from pdb_headers where pdb_release_id="+pdbReleaseID);
	stmt.executeUpdate("delete from pdb_release_heterogen where pdb_release_id="+pdbReleaseID);
	stmt.executeUpdate("delete from pdb_chain_compound where pdb_chain_id in (select id from pdb_chain where pdb_release_id="+pdbReleaseID+")");

	BufferedReader infile = IO.openReader(xml);
	PDBMLHandler h = new PDBMLHandler();

	SAXParserFactory factory
	    = SAXParserFactory.newInstance();
	factory.setValidating(false);
	SAXParser parser = factory.newSAXParser();
	
	parser.parse(new InputSource(infile), h);

	h.curTitle = fixHeader(h.curTitle);
	h.curClass = fixHeader(h.curClass);
	h.curKeywords = fixHeader(h.curKeywords);

	stmt.executeUpdate("insert into pdb_headers values (null,"+
			   pdbReleaseID+", \""+
			   StringUtil.replace(h.curTitle,"\"","\\\"")+"\", \""+
			   StringUtil.replace(h.curClass,"\"","\\\"")+"\", \""+
			   StringUtil.replace(h.curKeywords,"\"","\\\"")+"\")");
	
	for (String heterogen : h.hets) {
	    if (heterogen != null) {
		int heterogenID = lookupOrCreateHeterogen(heterogen);
		if (heterogenID > 0) 
		    stmt.executeUpdate("insert into pdb_release_heterogen values ("+
				       pdbReleaseID+", "+
				       heterogenID+")");
	    }
	}
	for (String entity : h.chainMap.keySet()) {
	    String tmp = h.chainMap.get(entity);
	    for (int i=0; i<tmp.length(); i++) {
		char chain = tmp.charAt(i);

		rs = stmt.executeQuery("select id from pdb_chain where pdb_release_id="+pdbReleaseID+" and chain=\""+chain+"\" limit 1");
		if (rs.next()) {
		    int pdbChainID = rs.getInt(1);

		    String compound = h.compoundMap.get(entity);

		    if (compound != null) {
			compound = fixHeader(compound);
			int compoundID = lookupOrCreateCompound(compound);
			if (compoundID > 0) 
			    stmt.executeUpdate("insert into pdb_chain_compound values ("+
					       pdbChainID+", "+
					       compoundID+")");
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
	    Statement stmt2 = LocalSQL.createStatement();

	    ResultSet rs;

	    if (argv.length==0) {
		rs = stmt.executeQuery("select pdb_release_id from pdb_local where xml_path is not null and pdb_release_id not in (select pdb_release_id from pdb_headers)");
		// rs = stmt.executeQuery("select pdb_release_id from pdb_local where xml_path is not null and pdb_release_id in (select distinct(c.pdb_release_id) from pdb_chain c, link_pdb l, scop_node n where c.id=l.pdb_chain_id and l.node_id=n.id and n.release_id=12) and pdb_release_id not in (select pdb_release_id from pdb_headers)");
	    }
	    else
		rs = stmt.executeQuery("select pdb_release_id from pdb_local where xml_path = \""+argv[0]+"\"");
	    while (rs.next()) {
		int id = rs.getInt(1);
		findHeaders(id);
	    }
	}
	catch (Exception e) {
	    System.out.println("Exception: "+e.getMessage());
	    e.printStackTrace();
	}
    }
}
