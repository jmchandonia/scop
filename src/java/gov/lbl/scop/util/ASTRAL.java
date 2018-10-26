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
package gov.lbl.scop.util;

import java.sql.*;
import java.io.*;
import java.util.*;
import java.text.*;
import org.strbio.io.*;
import org.strbio.local.*;
import org.strbio.math.*;
import org.strbio.mol.*;
import org.strbio.util.*;
import org.strbio.IO;
import gov.lbl.scop.local.LocalSQL;

/**
   Utilities related to ASTRAL features.
*/
public class ASTRAL {
    /**
       translate SCOP-style sids for gd to ASTRAL-style
    */
    public static String astralSid (String scopSid) {
	if (scopSid.charAt(5)=='.')
	    return "g"+scopSid.substring(1);
	return scopSid;
    }

    /**
       which nodes are from "not true scop classes"
    */
    public static Vector<Integer> findNTSC(Vector<Integer> nodeIDs)
    throws Exception {
	Statement stmt = LocalSQL.createStatement();
	
	Vector<Integer> rv = new Vector<Integer>();
	for (Integer nodeID : nodeIDs) {
	    ResultSet rs = stmt.executeQuery("select sccs from scop_node where id = "+nodeID);
	    if (rs.next()) {
		String sccs = rs.getString(1);
		if (sccs.charAt(0) > 'g')
		    rv.add(nodeID);
	    }
	}

	stmt.close();
	return rv;
    }

    /**
       Are all the chains in this domain all of the chains in
       the entire PDB structure?
    */
    public static boolean domainHasAllChains(int nodeID) throws Exception {
	Statement stmt = LocalSQL.createStatement();
	HashSet<Integer> chainsInDomain = new HashSet<Integer>();
	HashSet<Integer> chainsInStructure = new HashSet<Integer>();
	int pdbChainID = 0;
	ResultSet rs = stmt.executeQuery("select m.pdb_chain_id from link_pdb m where m.node_id="+nodeID);
	while (rs.next()) {
	    pdbChainID = rs.getInt(1);
	    chainsInDomain.add(new Integer(pdbChainID));
	}
	if (pdbChainID==0) {
	    stmt.close();
	    return true;
	}

	rs = stmt.executeQuery("select c2.id from pdb_chain c1, pdb_chain c2 where c1.id="+pdbChainID+" and c1.pdb_release_id=c2.pdb_release_id");
	while (rs.next()) {
	    chainsInStructure.add(new Integer(rs.getInt(1)));
	}

	stmt.close();
	
	if (chainsInDomain.size()==chainsInStructure.size())
	    return true;
	else
	    return false;
    }

    /**
       Are all the chains in this domain all of the chains in
       the entire PDB structure?
    */
    public static boolean domainHasAllChainsASTEROID(int asteroidID) throws Exception {
	Statement stmt = LocalSQL.createStatement();
	HashSet<Integer> chainsInDomain = new HashSet<Integer>();
	HashSet<Integer> chainsInStructure = new HashSet<Integer>();
	int pdbChainID = 0;
	ResultSet rs = stmt.executeQuery("select r.pdb_chain_id from asteroid a, astral_chain ac, raf r where a.id="+asteroidID+" and a.chain_id=ac.id and ac.raf_id=r.id");
	while (rs.next()) {
	    pdbChainID = rs.getInt(1);
	    chainsInDomain.add(new Integer(pdbChainID));
	}
	if (pdbChainID==0) {
	    stmt.close();
	    return true;
	}

	rs = stmt.executeQuery("select c2.id from pdb_chain c1, pdb_chain c2 where c1.id="+pdbChainID+" and c1.pdb_release_id=c2.pdb_release_id");
	while (rs.next()) {
	    chainsInStructure.add(new Integer(rs.getInt(1)));
	}

	stmt.close();
	
	if (chainsInDomain.size()==chainsInStructure.size())
	    return true;
	else
	    return false;
    }

    /**
       Does this domain cover the entire chain (or all chains) that it
       is part of?  For each chain, "covering the entire chain" means
       that no range of residues is given, or that the range of
       residues in the domain is from the first residue with ATOM
       records to the last residue with ATOM records.

       The easiest way to do this is to check whether the original-style
       and chain sequences made from ATOM records match for every chain.
    */
    public static boolean domainIsEntireChain(int nodeID) throws Exception {
	boolean rv = true;
	Statement stmt = LocalSQL.createStatement();
	ResultSet rs, rs2;

	// shortcut:  sids ending in _ are automatically whole chain.
	rs = stmt.executeQuery("select sid from scop_node where id="+nodeID);
	rs.next();
	String sid = rs.getString(1);
	if (sid.endsWith("_")) {
	    stmt.close();
	    return true;
	}

	// if sid is ambiguous, we have to check further:
	Statement stmt2 = LocalSQL.createStatement();

	// count number of distinct chains
	rs = stmt.executeQuery("select count(*) from link_pdb where node_id="+nodeID);
	rs.next();
	int nChains = rs.getInt(1);
	rs.close();

	// find raf and chain ids for every chain in this domain
	rs = stmt.executeQuery("select c.id, r.id, c.chain from link_pdb l, scop_node n, raf r, pdb_chain c where c.id=l.pdb_chain_id and l.pdb_chain_id=r.pdb_chain_id and l.node_id=n.id and n.release_id>=r.first_release_id and n.release_id<=r.last_release_id and n.id="+nodeID);
	while (rs.next()) {
	    int pdbChainID = rs.getInt(1);
	    int rafID = rs.getInt(2);
	    char chain = rs.getString(3).charAt(0);

	    // get whole chain seq for that raf
	    int chainSeqID = 0;
	    rs2 = stmt2.executeQuery("select seq_id from astral_chain where raf_id="+rafID+" and source_id=1");
	    if (rs2.next())
		chainSeqID = rs2.getInt(1);

	    // get corresponding old-style or single domain seq
	    int domainSeqID = 0;
	    if (nChains==1)
		rs2 = stmt2.executeQuery("select seq_id from astral_domain where node_id="+nodeID+" and source_id=1 and style_id=1");
	    else
		rs2 = stmt2.executeQuery("select seq_id from astral_domain where node_id="+nodeID+" and source_id=1 and style_id=3 and sid like \"e%"+chain+"\"");
	    if (rs2.next())
		domainSeqID = rs2.getInt(1);
	    rs2.close();

	    if (chainSeqID != domainSeqID) {
		rv = false;
		break;
	    }
	}
	rs.close();
	
	stmt.close();
	stmt2.close();
	return rv;
    }

    /**
       Does this domain cover the entire chain (or all chains) that it
       is part of?
    */
    public static boolean domainIsEntireChainASTEROID(int asteroidID) throws Exception {
	Statement stmt = LocalSQL.createStatement();
	ResultSet rs;

	// shortcut:  sids ending in _ are automatically whole chain.
	rs = stmt.executeQuery("select sid from asteroid where id="+asteroidID);
	rs.next();
	String sid = rs.getString(1);
	rs.close();
	stmt.close();
	if (sid.endsWith("_")) {
	    return true;
	}
	return false;
    }
    
    /**
       translate node ids to sids.  null on error.
    */
    final public static HashMap<Integer,String> nodeIDToSid(Collection<Integer> ids)
	throws Exception {
	Statement stmt = LocalSQL.createStatement();
	HashMap<Integer,String> rv = new HashMap<Integer,String>();
	for (Integer i : ids) {
	    ResultSet rs = stmt.executeQuery("select sid from scop_node where id="+i);
	    if (rs.next())
		rv.put(i, rs.getString(1));
	    else {
		stmt.close();
		return null;
	    }
	}
	stmt.close();
	return rv;
    }

    /**
       translate SCOP node ids to domain ids.  All have to be from
       the same SCOP release.
    */
    final public static Vector<Integer> nodeIDToDomainID(Collection<Integer> ids,
							 boolean isAtom,
							 boolean isGD)
	throws Exception {
	Statement stmt = LocalSQL.createStatement();
	ResultSet rs;
	Vector<Integer> rv = new Vector<Integer>();
	int sourceID;
	if (isAtom)
	    sourceID = 1;
	else
	    sourceID = 2;
	for (Integer i : ids) {
	    String query = "select id from astral_domain where source_id="+sourceID+" and (style_id=1 or style_id="+(isGD?3:2)+") and node_id="+i;
	    rs = stmt.executeQuery(query);
	    while ((rs.next()))
		rv.add(new Integer(rs.getInt(1)));
	    rs.close();
	}
	stmt.close();
	return rv;
    }
    
    /**
       translate domain ids to sids.  null on error.
    */
    final public static HashMap<Integer,String> domainIDToSid(Collection<Integer> ids)
	throws Exception {
	Statement stmt = LocalSQL.createStatement();
	HashMap<Integer,String> rv = new HashMap<Integer,String>();
	for (Integer i : ids) {
	    ResultSet rs = stmt.executeQuery("select sid from astral_domain where id="+i);
	    if (rs.next())
		rv.put(i, rs.getString(1));
	    else {
		stmt.close();
		return null;
	    }
	}
	stmt.close();
	return rv;
    }

    /**
       translate chain ids to sids.  null on error.
    */
    final public static HashMap<Integer,String> astralChainIDToSid(Collection<Integer> ids)
	throws Exception {
	Statement stmt = LocalSQL.createStatement();
	HashMap<Integer,String> rv = new HashMap<Integer,String>();
	for (Integer i : ids) {
	    ResultSet rs = stmt.executeQuery("select sid from astral_chain where id="+i);
	    if (rs.next())
		rv.put(i, rs.getString(1));
	    else {
		stmt.close();
		return null;
	    }
	}
	stmt.close();
	return rv;
    }
}
