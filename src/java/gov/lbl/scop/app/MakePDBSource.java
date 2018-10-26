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
   Find source of pdb chains w/o sources, for which we have a local
   copy
*/
public class MakePDBSource {
    public static class PDBMLHandler extends DefaultHandler {
	private int inElement = 0;
	public String curSci = "";
	public String curCom = "";
	public String curTax = "";
	public String curStr = "";
	public String curGen = "";
	public String curEntity = null;
	public String curChain = "";
	public String curDesc = "";
	public String curDetails = "";
	
	public HashMap<String,String> chainMap = new HashMap<String,String>();
	public HashMap<String,String> scientificName = new HashMap<String,String>();
	public HashMap<String,String> commonName = new HashMap<String,String>();
	public HashMap<String,String> taxid = new HashMap<String,String>();
	public HashMap<String,String> gene = new HashMap<String,String>();
	public HashMap<String,String> strain = new HashMap<String,String>();
	public HashMap<String,String> description = new HashMap<String,String>();
	public HashSet<String> isSynthetic = new HashSet<String>();

	public void startElement(String uri,
				 String localName,
				 String qName,
				 Attributes attributes) {
	    if ((qName.equals("PDBx:entity_src_gen")) ||
		(qName.equals("PDBx:entity_src_nat")) ||
		(qName.equals("PDBx:pdbx_entity_src_syn")) ||
		(qName.equals("PDBx:pdbx_poly_seq_scheme"))) {
		inElement = 0;
		if (attributes != null)
		    curEntity = attributes.getValue("entity_id");
		else
		    curEntity = null;
		curSci = "";
		curCom = "";
		curTax = "";
		curStr = "";
		curGen = "";
		curChain = "";
		curDesc = "";
		curDetails = "";
	    }
	    else if ((qName.equals("PDBx:pdbx_gene_src_scientific_name")) ||
		     (qName.equals("PDBx:pdbx_organism_scientific")) ||
		     (qName.equals("PDBx:organism_scientific")) ||
		     (qName.equals("PDBx:species")) ||
		     (qName.equals("PDBx:gene_src_species"))) {
		if (curSci.length() == 0)
		    inElement = 1;
	    }
	    else if ((qName.equals("PDBx:gene_src_common_name")) ||
		     (qName.equals("PDBx:organism_common_name")) ||
		     (qName.equals("PDBx:common_name"))) {
		if (curCom.length() == 0)
		    inElement = 2;
	    }
	    else if ((qName.equals("PDBx:pdbx_gene_src_ncbi_taxonomy_id")) ||
		     (qName.equals("PDBx:ncbi_taxonomy_id")) ||
		     (qName.equals("PDBx:pdbx_ncbi_taxonomy_id"))) {
		if (curTax.length()==0)
		    inElement = 3;
	    }
	    else if ((qName.equals("PDBx:gene_src_strain")) ||
		     (qName.equals("PDBx:strain"))) {
		if (curStr.length()==0)
		    inElement = 4;
	    }
	    else if (qName.equals("PDBx:pdb_strand_id")) {
		if (curChain.length()==0)
		    inElement = 5;
	    }
	    else if (qName.equals("PDBx:pdbx_gene_src_gene")) {
		if (curGen.length()==0)
		    inElement = 6;
	    }
	    else if (qName.equals("PDBx:pdbx_description")) {
		if (curDesc.length()==0)
		    inElement = 7;
	    }
	    else if (qName.equals("PDBx:details")) {
		if (curDetails.length()==0)
		    inElement = 8;
	    }
	}

	public void endElement(String uri,
			       String localName,
			       String qName) {
	    if ((qName.equals("PDBx:entity_src_gen")) ||
		(qName.equals("PDBx:entity_src_nat")) ||
		(qName.equals("PDBx:pdbx_entity_src_syn"))) {
		inElement = 0;
		// System.out.println(curEntity);
		// System.out.println(curSci);
		// System.out.println(curCom);
		// System.out.println(curTax);
		// System.out.println(curStr);
		if (curEntity != null) {
		    if (curSci.length() > 0)
			scientificName.put(curEntity, curSci);
		    if (curCom.length() > 0)
			commonName.put(curEntity, curCom);
		    if (curTax.length() > 0)
			taxid.put(curEntity, curTax);
		    if (curStr.length() > 0)
			strain.put(curEntity, curStr);
		    if (curGen.length() > 0)
			gene.put(curEntity, curGen);
		    if (curDesc.length() > 0)
			description.put(curEntity, curDesc);
		    if (curDetails.length() > 0) {
			// look for phage display
			String d = curDetails.toLowerCase();
			if ((d.indexOf("selected") > -1) &&
			    (d.indexOf("phage display") > -1))
			    isSynthetic.add(curEntity);
		    }
		    if (qName.equals("PDBx:pdbx_entity_src_syn"))
			isSynthetic.add(curEntity);
		}
		curSci = "";
		curCom = "";
		curTax = "";
		curStr = "";
		curGen = "";
		curDesc = "";
		curDetails = "";
	    }
	    else if (qName.equals("PDBx:pdbx_poly_seq_scheme")) {
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
		curStr = "";
	    }
	    else if ((qName.equals("PDBx:pdbx_gene_src_scientific_name")) ||
		     (qName.equals("PDBx:pdbx_organism_scientific")) ||
		     (qName.equals("PDBx:organism_scientific")) ||
		     (qName.equals("PDBx:species")) ||
		     (qName.equals("PDBx:gene_src_species"))) {
		inElement = 0;
		curSci = curSci.trim();
	    }
	    else if ((qName.equals("PDBx:gene_src_common_name")) ||
		     (qName.equals("PDBx:organism_common_name")) ||
		     (qName.equals("PDBx:common_name"))) {
		inElement = 0;
		curCom = curCom.trim();
	    }
	    else if ((qName.equals("PDBx:pdbx_gene_src_ncbi_taxonomy_id")) ||
		     (qName.equals("PDBx:ncbi_taxonomy_id")) ||
		     (qName.equals("PDBx:pdbx_ncbi_taxonomy_id"))) {
		inElement = 0;
		curTax = curTax.trim();
	    }
	    else if ((qName.equals("PDBx:gene_src_strain")) ||
		     (qName.equals("PDBx:strain"))) {
		inElement = 0;
		curStr = curStr.trim();
	    }
	    else if (qName.equals("PDBx:pdb_strand_id")) {
		inElement = 0;
		curChain = curChain.trim();
	    }
	    else if (qName.equals("PDBx:pdbx_gene_src_gene")) {
		inElement = 0;
		curGen = curGen.trim();
	    }
	    else if (qName.equals("PDBx:pdbx_description")) {
		inElement = 0;
		curDesc = curDesc.trim();
	    }
	    else if (qName.equals("PDBx:details")) {
		inElement = 0;
		curDetails = curDetails.trim();
	    }
	}
	    
	public void characters(char[] ch,
			       int start,
			       int length) {
	    if (inElement==1)
		curSci += new String(ch, start, length);
	    else if (inElement==2)
		curCom += new String(ch, start, length);
	    else if (inElement==3)
		curTax += new String(ch, start, length);
	    else if (inElement==4)
		curStr += new String(ch, start, length);
	    else if (inElement==5)
		curChain += new String(ch, start, length);
	    else if (inElement==6)
		curGen += new String(ch, start, length);
	    else if (inElement==7)
		curDesc += new String(ch, start, length);
	    else if (inElement==8)
		curDetails += new String(ch, start, length);
	}
    }

    /**
       note:  pdb_source is not case sensitive
    */
    final public static int lookupOrCreateSource(String scientificName,
						 String commonName,
						 String strain,
						 String taxid,
						 boolean isSynthetic) throws Exception {
	int rv = 0;
	Statement stmt = LocalSQL.createStatement();
	scientificName = StringUtil.replace(scientificName,"\"","\\\"");
	if (commonName != null)
	    commonName = StringUtil.replace(commonName,"\"","\\\"");
	if (strain != null)
	    strain = StringUtil.replace(strain,"\"","\\\"");
	if (taxid != null)
	    taxid = StringUtil.replace(taxid,"\"","\\\"");
	ResultSet rs = stmt.executeQuery("select id from pdb_source where scientific_name=\""+
					 scientificName+
					 "\" and common_name "+
					 (commonName==null? "is null" : "=\""+commonName+"\"")+
					 " and strain_name "+
					 (strain==null? "is null" : "=\""+strain+"\"")+
					 " and ncbi_taxid "+
					 (taxid==null? "is null" : "=\""+taxid+"\"")+
					 " and is_synthetic="+
					 (isSynthetic? "1":"0"));

	if (rs.next())
	    rv = rs.getInt(1);
	else {
	    String n = scientificName.toLowerCase();
	    stmt.executeUpdate("insert into pdb_source values (null, \""+
			       scientificName+"\", "+
			       (commonName==null? "null" : "\""+commonName+"\"")+
			       ", "+
			       (strain==null? "null" : "\""+strain+"\"")+
			       ", "+
			       (taxid==null? "null" : "\""+taxid+"\"")+
			       ", "+
			       (isSynthetic? "1" : "0")+
			       ")",
			       Statement.RETURN_GENERATED_KEYS);
	    rs = stmt.getGeneratedKeys();
	    rs.next();
	    rv = rs.getInt(1);
	}
	stmt.close();
	return rv;
    }

    /**
       note:  pdb_gene is not case sensitive
    */
    final public static int lookupOrCreateGene(String geneName) throws Exception {
	int rv = 0;
	Statement stmt = LocalSQL.createStatement();
	geneName = StringUtil.replace(geneName,"\"","\\\"");
	ResultSet rs = stmt.executeQuery("select id from pdb_gene where gene_name=\""+
					 geneName+
					 "\"");

	if (rs.next())
	    rv = rs.getInt(1);
	else {
	    stmt.executeUpdate("insert into pdb_gene values (null, \""+
			       geneName+"\")",
			       Statement.RETURN_GENERATED_KEYS);
	    rs = stmt.getGeneratedKeys();
	    rs.next();
	    rv = rs.getInt(1);
	}
	stmt.close();
	return rv;
    }

    /**
       find sources for an individual PDB release
    */
    final public static void findSources(int pdbReleaseID) throws Exception {
	Statement stmt = LocalSQL.createStatement();
	ResultSet rs = stmt.executeQuery("select xml_path from pdb_local where pdb_release_id="+pdbReleaseID);
	if (!rs.next()) {
	    stmt.close();
	    return;
	}
	String xml = rs.getString(1);
	rs.close();

	System.out.println("finding sources for "+xml);
	System.out.flush();

	stmt.executeUpdate("delete from pdb_chain_source where pdb_chain_id in (select id from pdb_chain where pdb_release_id="+pdbReleaseID+")");

	BufferedReader infile = IO.openReader(xml);
	PDBMLHandler h = new PDBMLHandler();

	SAXParserFactory factory
	    = SAXParserFactory.newInstance();
	factory.setValidating(false);
	SAXParser parser = factory.newSAXParser();
	
	parser.parse(new InputSource(infile), h);

	for (String entity : h.chainMap.keySet()) {
	    String tmp = h.chainMap.get(entity);
	    for (int i=0; i<tmp.length(); i++) {
		char chain = tmp.charAt(i);

		rs = stmt.executeQuery("select id from pdb_chain where pdb_release_id="+pdbReleaseID+" and chain=\""+chain+"\" limit 1");
		if (rs.next()) {
		    int pdbChainID = rs.getInt(1);

		    String scientificName = h.scientificName.get(entity);
		    String commonName = h.commonName.get(entity);
		    String strain = h.strain.get(entity);
		    String taxid = h.taxid.get(entity);
		    String gene = h.gene.get(entity);
		    String desc = h.description.get(entity);

		    if ((h.isSynthetic.contains(entity)) &&
			(scientificName==null))
			scientificName = "synthetic";

		    if (scientificName != null) {
			boolean isSynthetic = false;
			if (h.isSynthetic.contains(entity))
			    isSynthetic = true;
			String n = scientificName.toLowerCase();
			if (desc != null)
			    n += desc.toLowerCase();
			if ((n.indexOf("synthetic") > -1) ||
			    (n.indexOf("designed") > -1) ||
			    ((n.indexOf("phage display") > -1) &&
			     (n.indexOf("select") > -1)) ||
			    (n.indexOf("construct") > -1))
			    isSynthetic = true;

			// System.out.println(n);
		    
			int sourceID = lookupOrCreateSource(scientificName,
							    commonName,
							    strain,
							    taxid,
							    isSynthetic);
			if (sourceID > 0) 
			    stmt.executeUpdate("insert into pdb_chain_source values ("+
					       pdbChainID+", "+
					       sourceID+")");
		    }
		    if (gene != null) {
			int geneID = lookupOrCreateGene(gene);
			if (geneID > 0) 
			    stmt.executeUpdate("insert into pdb_chain_gene values ("+
					       pdbChainID+", "+
					       geneID+")");
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
		rs = stmt.executeQuery("select pdb_release_id from pdb_local where xml_path is not null and pdb_release_id in (select distinct pdb_release_id from pdb_chain where id not in (select pdb_chain_id from pdb_chain_source)) and pdb_release_id in (select distinct pdb_release_id from pdb_chain where is_polypeptide=1)");
		// rs = stmt.executeQuery("select pdb_release_id from pdb_local where xml_path is not null and pdb_release_id in (select distinct(c.pdb_release_id) from pdb_chain c, link_pdb l, scop_node n where n.release_id=12 and c.id=l.pdb_chain_id and l.node_id=n.id) and pdb_release_id not in (select distinct(c.pdb_release_id) from pdb_chain c, link_pdb l, scop_node n, scop_node p where c.id=l.pdb_chain_id and l.node_id=n.id and n.parent_node_id=p.id and p.description='automated matches')");
		// rs = stmt.executeQuery("select pdb_release_id from pdb_local where xml_path is not null and pdb_release_id in (select distinct(c.pdb_release_id) from pdb_chain c, link_pdb l, scop_node n, scop_node p where c.id=l.pdb_chain_id and l.node_id=n.id and n.parent_node_id=p.id and p.description='automated matches' and c.id not in (select pdb_chain_id from pdb_chain_source))");
		// rs = stmt.executeQuery("select pdb_release_id from pdb_local where xml_path is not null and pdb_release_id in (select distinct(c.pdb_release_id) from pdb_chain c, link_pdb l, scop_node n where c.id=l.pdb_chain_id and l.node_id=n.id and n.release_id=12 and c.id not in (select pdb_chain_id from pdb_chain_source))");
	    }
	    else
		rs = stmt.executeQuery("select pdb_release_id from pdb_local where xml_path = \""+argv[0]+"\"");
	    while (rs.next()) {
		int id = rs.getInt(1);
		findSources(id);
	    }
	}
	catch (Exception e) {
	    System.out.println("Exception: "+e.getMessage());
	    e.printStackTrace();
	}
    }
}
