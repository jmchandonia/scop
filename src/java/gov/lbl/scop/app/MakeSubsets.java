/*
 * Software to build and maintain SCOPe, https://scop.berkeley.edu/
 *
 * Copyright (C) 2008-2018 The Regents of the University of California
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
import gov.lbl.scop.util.*;
import gov.lbl.scop.local.LocalSQL;

/**
   Find SCOP nodes in a release with the best AEROSPACI scores.
*/
public class MakeSubsets {
    /**
       make percent id subsets for chains at these levels; must be in
       numerical order
    */
    final public static int[] makePctIDChain = {90, 95, 100};

    /**
       make percent id subsets for domains at these levels; must be
       in numerical order
    */
    final public static int[] makePctIDDomain = {10, 20, 25, 30, 35, 40, 50, 70, 90, 95, 100};

    /**
       make e-value subsets for domains at these levels; must be in
       numerical order.
    */
    final public static double[] makeLog10E = {-50.0, -25.0, -20.0, -15.0, -10.0, -5.0, -4.0, -3.0, -2.3, -2.0, -1.3, -1.0, -0.3, 0.0, 0.7, 1.0};

    /**
       translate node ids to aerospaci scores.  null on error.
       Some domains might not have aerospaci values (e.g., for
       non-PDB nodes); these are left unmapped.
       For backwards compatibility, rounds off scores to nearest
       hundreth.
    */
    final public static HashMap<Integer,Double> nodeIDToAerospaci(Collection<Integer> ids)
        throws Exception {
        Statement stmt = LocalSQL.createStatement();
        HashMap<Integer,Double> rv = new HashMap<Integer,Double>();
        for (Integer i : ids) {
            ResultSet rs = stmt.executeQuery("select a.aerospaci from scop_node n, aerospaci a, link_pdb l, pdb_chain c, pdb_release r where n.id="+i+" and a.release_id=n.release_id and n.id=l.node_id and l.pdb_chain_id=c.id and c.pdb_release_id=r.id and r.pdb_entry_id=a.pdb_entry_id limit 1");
            if (rs.next()) {
                double score = rs.getDouble(1);
                long iScore = Math.round(score*100.0);
                rv.put(i, new Double((double)iScore / 100.0));
            }
        }
        stmt.close();
        return rv;
    }

    /**
       translate domain ids to aerospaci scores.  null on error.
       Some domains might not have aerospaci values (e.g., for
       non-PDB nodes); these are left unmapped.
       For backwards compatibility, rounds off scores to nearest
       hundreth.
    */
    final public static HashMap<Integer,Double> domainIDToAerospaci(Collection<Integer> ids)
        throws Exception {
        Statement stmt = LocalSQL.createStatement();
        HashMap<Integer,Double> rv = new HashMap<Integer,Double>();
        for (Integer i : ids) {
            ResultSet rs = stmt.executeQuery("select a.aerospaci from astral_domain d, scop_node n, aerospaci a, link_pdb l, pdb_chain c, pdb_release r where d.id="+i+" and d.node_id=n.id and a.release_id=n.release_id and n.id=l.node_id and l.pdb_chain_id=c.id and c.pdb_release_id=r.id and r.pdb_entry_id=a.pdb_entry_id limit 1");
            if (rs.next()) {
                double score = rs.getDouble(1);
                long iScore = Math.round(score*100.0);
                rv.put(i, new Double((double)iScore / 100.0));
            }
        }
        stmt.close();
        return rv;
    }

    /**
       translate chain ids to aerospaci scores.  null on error.
       Some chains might not have aerospaci values (e.g., for
       non-PDB chains); these are left unmapped.
       For backwards compatibility, rounds off scores to nearest
       hundreth.
    */
    final public static HashMap<Integer,Double> astralChainIDToAerospaci(Collection<Integer> ids, int scopReleaseID)
        throws Exception {
        Statement stmt = LocalSQL.createStatement();
        HashMap<Integer,Double> rv = new HashMap<Integer,Double>();
        for (Integer i : ids) {
            ResultSet rs = stmt.executeQuery("select a.aerospaci from astral_chain ac, raf r, aerospaci a, pdb_chain c, pdb_release pr where ac.id="+i+" and ac.raf_id=r.id and a.release_id>=r.first_release_id and a.release_id<=r.last_release_id and r.pdb_chain_id=c.id and c.pdb_release_id=pr.id and pr.pdb_entry_id=a.pdb_entry_id and a.release_id="+scopReleaseID+" limit 1");
            if (rs.next()) {
                double score = rs.getDouble(1);
                long iScore = Math.round(score*100.0);
                rv.put(i, new Double((double)iScore / 100.0));
            }
        }
        stmt.close();
        return rv;
    }

    /**
       returns all descendents of a node, at a given level
    */
    final public static Vector<Integer> descendentsOf(int nodeID, int levelID) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        Vector<Integer> rv = new Vector<Integer>();
        ResultSet rs = stmt.executeQuery("select id, level_id from scop_node where parent_node_id="+nodeID+" order by id");
        while (rs.next()) {
            int childID = rs.getInt(1);
            int childLevelID = rs.getInt(2);
            if (childLevelID == levelID) {
                Integer i = new Integer(childID);
                if (!rv.contains(i))
                    rv.add(i);
            }
            else if (childLevelID < levelID) {
                Vector<Integer> desc = descendentsOf(childID, levelID);
                desc.removeAll(rv); // to avoid duplicates in future SCOP
                rv.addAll(desc);
            }
        }
        stmt.close();
        return rv;
    }

    /**
       Sets up a cache table, if one doesn't already exist; returns
       cache table name.
    */
    final public static String setupCache(int scopReleaseID,
                                          boolean isChain,
                                          boolean isGD,
                                          boolean tagless) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        String tableName = "astral_seq_blast_cache_"+scopReleaseID+"_"+(isChain ? ("chain"+(tagless ? "_tg" : "")) : ("domain_"+(isGD ? "gd" : "os")));
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS "+tableName+" (" +
                           "id1 integer unsigned not null,"+
                           "id2 integer unsigned not null,"+
                           "blast_log10_e double NOT NULL,"+
                           "pct_identical double NOT NULL,"+
                           "index (id1),"+
                           "index (id2)) engine=myisam");
        ResultSet rs = stmt.executeQuery("select id1 from "+tableName+" limit 1");
        if (rs.next()) {
            rs.close();
            stmt.close();
            return tableName;
        }
        rs.close();

        if (isChain) {
            int sourceID = 2;
            if (tagless)
                sourceID = 4;
            String rafQuery;
            if (scopReleaseID==0)
                rafQuery = "and r1.first_release_id is null and r2.first_release_id is null and r1.last_release_id is null and r2.last_release_id is null";
            else
                rafQuery = "and r1.first_release_id <= "+scopReleaseID+" and r2.first_release_id <= "+scopReleaseID+" and r1.last_release_id >= "+scopReleaseID+" and r2.last_release_id >= "+scopReleaseID;
            stmt.executeUpdate("insert into "+tableName+" (id1, id2, blast_log10_e, pct_identical) select c1.id, c2.id, b.blast_log10_e, b.pct_identical from astral_seq_blast b, astral_chain c1, astral_chain c2, raf r1, raf r2 where c1.id!=c2.id and c1.seq_id=b.seq1_id and c2.seq_id=b.seq2_id and b.seq1_length > 0 and b.seq2_length > 0 and c1.raf_id=r1.id and c2.raf_id=r2.id and c1.source_id=c2.source_id and c1.source_id=b.source_id and c1.source_id="+sourceID+" and b.style1_id=1 and b.style2_id=1 and b.release_id="+scopReleaseID+" "+rafQuery);
        }
        else {
            int styleID = 2;
            if (isGD)
                styleID = 3;

            stmt.executeUpdate("insert into "+tableName+" (id1, id2, blast_log10_e, pct_identical) select d1.id, d2.id, b.blast_log10_e, b.pct_identical from astral_seq_blast b, astral_domain d1, astral_domain d2, scop_node n1, scop_node n2 where d1.id!=d2.id and b.seq1_length > 0 and b.seq2_length > 0 and b.seq1_id=d1.seq_id and b.seq2_id=d2.seq_id and d1.node_id=n1.id and d2.node_id=n2.id and b.style1_id="+styleID+" and b.style2_id=b.style1_id and b.release_id=n1.release_id and n1.release_id=n2.release_id and b.source_id=d1.source_id and d2.source_id=d1.source_id and d1.source_id=2 and b.release_id="+scopReleaseID);
        }
        stmt.close();
        return tableName;
    }

    /**
       returns all peers of a domain, at a given percent identity
       and style
    */
    final public static Vector<Integer> getDomainPeers(int domainID,
                                                       int pctID,
                                                       int styleID) throws Exception {
        Statement stmt = LocalSQL.createStatement();

        ResultSet rs = stmt.executeQuery("select n.release_id from scop_node n, astral_domain d where d.node_id=n.id and d.id="+domainID);
        rs.next();
        int scopReleaseID = rs.getInt(1);
        rs.close();
        String tableName = setupCache(scopReleaseID,false,(styleID==3),false);
	
        Vector<Integer> rv = new Vector<Integer>();
        // uncached: rs = stmt.executeQuery("select d2.id from astral_seq_blast b, astral_domain d1, astral_domain d2, scop_node n1, scop_node n2 where d1.id!=d2.id and b.seq1_id=d1.seq_id and b.seq2_id=d2.seq_id and d1.node_id=n1.id and d2.node_id=n2.id and d1.id="+domainID+" and b.pct_identical>="+pctID+" and b.style1_id="+styleID+" and b.style2_id=b.style1_id and b.release_id=n1.release_id and n1.release_id=n2.release_id and b.source_id=d1.source_id and d2.source_id=d1.source_id");
        rs = stmt.executeQuery("select id2 from "+tableName+" where id1="+domainID+" and pct_identical>="+pctID);
        while (rs.next()) {
            Integer i = new Integer(rs.getInt(1));
            if (!rv.contains(i))
                // DON'T do the "worse score" check below, since
                // original perl code used the highest %ID for clustering
                rv.add(i);
        }
        rs.close();
        // uncached: rs = stmt.executeQuery("select d1.id from astral_seq_blast b, astral_domain d1, astral_domain d2, scop_node n1, scop_node n2 where d1.id!=d2.id and b.seq1_id=d1.seq_id and b.seq2_id=d2.seq_id and d1.node_id=n1.id and d2.node_id=n2.id and d2.id="+domainID+" and b.pct_identical>="+pctID+" and b.style1_id="+styleID+" and b.style2_id=b.style1_id and b.release_id=n1.release_id and n1.release_id=n2.release_id and b.source_id=d1.source_id and d2.source_id=d1.source_id");
        rs = stmt.executeQuery("select id1 from "+tableName+" where id2="+domainID+" and pct_identical>="+pctID);	
        while (rs.next()) {
            Integer i = new Integer(rs.getInt(1));
            if (!rv.contains(i))
                rv.add(i);
        }
        rs.close();
        stmt.close();
        return rv;
    }

    /**
       returns all peers of a chain, at a given percent identity
    */
    final public static Vector<Integer> getChainPeers(int astralChainID,
                                                      int pctID,
                                                      int scopReleaseID) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs;
        rs = stmt.executeQuery("select source_id from astral_chain where id="+astralChainID);
        rs.next();
        int sourceID = rs.getInt(1);
        rs.close();
        
        String tableName = setupCache(scopReleaseID,true,true,(sourceID==4));
	
        Vector<Integer> rv = new Vector<Integer>();

        /* uncached:
	  
           String rafQuery;
           if (scopReleaseID==0)
           rafQuery = "and r1.first_release_id is null and r2.first_release_id is null and r1.last_release_id is null and r2.last_release_id is null";
           else
           rafQuery = "and r1.first_release_id <= "+scopReleaseID+" and r2.first_release_id <= "+scopReleaseID+" and r1.last_release_id >= "+scopReleaseID+" and r2.last_release_id >= "+scopReleaseID;
           rs = stmt.executeQuery("select c2.id from astral_seq_blast b, astral_chain c1, astral_chain c2, raf r1, raf r2 where c1.id!=c2.id and c1.id="+astralChainID+" and c1.seq_id=b.seq1_id and c2.seq_id=b.seq2_id and c1.raf_id=r1.id and c2.raf_id=r2.id and c1.source_id=c2.source_id and c1.source_id=b.source_id and b.style1_id=1 and b.style2_id=1 and b.release_id="+scopReleaseID+" and b.pct_identical>="+pctID+" "+rafQuery);
        */
        rs = stmt.executeQuery("select id2 from "+tableName+" where id1="+astralChainID+" and pct_identical>="+pctID);
        while (rs.next()) {
            Integer i = new Integer(rs.getInt(1));
            if (!rv.contains(i))
                // DON'T do the "worse score" check below, since
                // original perl code used the highest %ID for clustering
                rv.add(i);
        }
        rs.close();
        // uncached: rs = stmt.executeQuery("select c1.id from astral_seq_blast b, astral_chain c1, astral_chain c2, raf r1, raf r2 where c1.id!=c2.id and c2.id="+astralChainID+" and c1.seq_id=b.seq1_id and c2.seq_id=b.seq2_id and c1.raf_id=r1.id and c2.raf_id=r2.id and c1.source_id=c2.source_id and c1.source_id=b.source_id and b.style1_id=1 and b.style2_id=1 and b.release_id="+scopReleaseID+" and b.pct_identical>="+pctID+" "+rafQuery);
        rs = stmt.executeQuery("select id1 from "+tableName+" where id2="+astralChainID+" and pct_identical>="+pctID);
        while (rs.next()) {
            Integer i = new Integer(rs.getInt(1));
            if (!rv.contains(i))
                rv.add(i);
        }
        rs.close();
        stmt.close();
        return rv;
    }

    /**
       returns all peers of a domain, at a given E-value and style
    */
    final public static Vector<Integer> getDomainPeers(int domainID,
                                                       double log10E,
                                                       int styleID) throws Exception {
        Statement stmt = LocalSQL.createStatement();

        ResultSet rs = stmt.executeQuery("select n.release_id from scop_node n, astral_domain d where d.node_id=n.id and d.id="+domainID);
        rs.next();
        int scopReleaseID = rs.getInt(1);
        rs.close();
        String tableName = setupCache(scopReleaseID,false,(styleID==3),false);
	
        Statement stmt2 = LocalSQL.createStatement();
        Vector<Integer> rv = new Vector<Integer>();
        // uncached: rs = stmt.executeQuery("select d2.id from astral_seq_blast b, astral_domain d1, astral_domain d2, scop_node n1, scop_node n2 where d1.id!=d2.id and b.seq1_id=d1.seq_id and b.seq2_id=d2.seq_id and d1.node_id=n1.id and d2.node_id=n2.id and d1.id="+domainID+" and round(b.blast_log10_e,8)<="+log10E+" and b.seq1_length > 0 and b.seq2_length > 0 and b.style1_id="+styleID+" and b.style2_id=b.style1_id and b.release_id=n1.release_id and n1.release_id=n2.release_id and b.source_id=d1.source_id and d2.source_id=d1.source_id");
        rs = stmt.executeQuery("select id2 from "+tableName+" where id1="+domainID+" and round(blast_log10_e,8)<="+log10E);
        while (rs.next()) {
            Integer i = new Integer(rs.getInt(1));
            if (!rv.contains(i)) {
                // check that it doesn't exist with a worse score
                // uncached: ResultSet rs2 = stmt2.executeQuery("select b.id from astral_seq_blast b, astral_domain d1, astral_domain d2, scop_node n1, scop_node n2 where d1.node_id=n1.id and d2.node_id=n2.id and b.seq2_id=d2.seq_id and d2.id="+domainID+" and b.seq1_id=d1.seq_id and d1.id="+i+" and round(b.blast_log10_e,8) > "+log10E+" and b.seq1_length > 0 and b.seq2_length > 0 and b.style1_id="+styleID+" and b.style2_id=b.style1_id and b.release_id=n1.release_id and n1.release_id=n2.release_id and b.source_id=d1.source_id and d2.source_id=d1.source_id");
                ResultSet rs2 = stmt2.executeQuery("select id1 from "+tableName+" where id2="+domainID+" and id1="+i+" and round(blast_log10_e,8)>"+log10E);
                if (!rs2.next()) {
                    rs2.close();
                    // uncached: rs2 = stmt2.executeQuery("select b.id from astral_seq_blast b, astral_domain d1, astral_domain d2, scop_node n1, scop_node n2 where d1.node_id=n1.id and d2.node_id=n2.id and b.seq2_id=d2.seq_id and d1.id="+domainID+" and b.seq1_id=d1.seq_id and d2.id="+i+" and round(b.blast_log10_e,8) > "+log10E+" and b.seq1_length > 0 and b.seq2_length > 0 and b.style1_id="+styleID+" and b.style2_id=b.style1_id and b.release_id=n1.release_id and n1.release_id=n2.release_id and b.source_id=d1.source_id and d2.source_id=d1.source_id");
                    rs2 = stmt2.executeQuery("select id1 from "+tableName+" where id1="+domainID+" and id2="+i+" and round(blast_log10_e,8)>"+log10E);
                    if (!rs2.next())
                        rv.add(i);
                }
                rs2.close();
            }
        }
        // uncached: rs = stmt.executeQuery("select d1.id from astral_seq_blast b, astral_domain d1, astral_domain d2, scop_node n1, scop_node n2 where d1.id!=d2.id and b.seq1_id=d1.seq_id and b.seq2_id=d2.seq_id and d1.node_id=n1.id and d2.node_id=n2.id and d2.id="+domainID+" and round(b.blast_log10_e,8)<="+log10E+" and b.seq1_length > 0 and b.seq2_length > 0 and b.style1_id="+styleID+" and b.style2_id=b.style1_id and b.release_id=n1.release_id and n1.release_id=n2.release_id and b.source_id=d1.source_id and d2.source_id=d1.source_id");
        rs = stmt.executeQuery("select id1 from "+tableName+" where id2="+domainID+" and round(blast_log10_e,8)<="+log10E);
        while (rs.next()) {
            Integer i = new Integer(rs.getInt(1));
            if (!rv.contains(i)) {
                // check that it doesn't exist with a worse score
                // uncached: ResultSet rs2 = stmt2.executeQuery("select b.id from astral_seq_blast b, astral_domain d1, astral_domain d2, scop_node n1, scop_node n2 where d1.node_id=n1.id and d2.node_id=n2.id and b.seq2_id=d2.seq_id and d2.id="+domainID+" and b.seq1_id=d1.seq_id and d1.id="+i+" and round(b.blast_log10_e,8) > "+log10E+" and b.seq1_length > 0 and b.seq2_length > 0 and b.style1_id="+styleID+" and b.style2_id=b.style1_id and b.release_id=n1.release_id and n1.release_id=n2.release_id and b.source_id=d1.source_id and d2.source_id=d1.source_id");
                ResultSet rs2 = stmt2.executeQuery("select id1 from "+tableName+" where id2="+domainID+" and id1="+i+" and round(blast_log10_e,8)>"+log10E);
                if (!rs2.next()) {
                    rs2.close();
                    // uncached: rs2 = stmt2.executeQuery("select b.id from astral_seq_blast b, astral_domain d1, astral_domain d2, scop_node n1, scop_node n2 where d1.node_id=n1.id and d2.node_id=n2.id and b.seq2_id=d2.seq_id and d1.id="+domainID+" and b.seq1_id=d1.seq_id and d2.id="+i+" and round(b.blast_log10_e,8) > "+log10E+" and b.seq1_length > 0 and b.seq2_length > 0 and b.style1_id="+styleID+" and b.style2_id=b.style1_id and b.release_id=n1.release_id and n1.release_id=n2.release_id and b.source_id=d1.source_id and d2.source_id=d1.source_id");
                    rs2 = stmt2.executeQuery("select id1 from "+tableName+" where id1="+domainID+" and id2="+i+" and round(blast_log10_e,8)>"+log10E);
                    if (!rs2.next())
                        rv.add(i);
                }
                rs2.close();
            }
        }
        rs.close();
        stmt2.close();
        stmt.close();
        return rv;
    }

    /**
       returns all peers of a chain, at a given E-value
    */
    final public static Vector<Integer> getChainPeers(int astralChainID,
                                                      double log10E,
                                                      int scopReleaseID) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs;
        rs = stmt.executeQuery("select source_id from astral_chain where id="+astralChainID);
        rs.next();
        int sourceID = rs.getInt(1);
        rs.close();

        String tableName = setupCache(scopReleaseID,true,true,(sourceID==4));
	
        Statement stmt2 = LocalSQL.createStatement();
        Vector<Integer> rv = new Vector<Integer>();

        /*
          uncached:
          String rafQuery;
          if (scopReleaseID==0)
          rafQuery = "and r1.first_release_id is null and r2.first_release_id is null and r1.last_release_id is null and r2.last_release_id is null";
          else
          rafQuery = "and r1.first_release_id <= "+scopReleaseID+" and r2.first_release_id <= "+scopReleaseID+" and r1.last_release_id >= "+scopReleaseID+" and r2.last_release_id >= "+scopReleaseID;
          ResultSet rs = stmt.executeQuery("select c2.id from astral_seq_blast b, astral_chain c1, astral_chain c2, raf r1, raf r2 where c1.id!=c2.id and c1.id="+astralChainID+" and c1.seq_id=b.seq1_id and c2.seq_id=b.seq2_id and c1.raf_id=r1.id and c2.raf_id=r2.id and c1.source_id=c2.source_id and c1.source_id=b.source_id and b.style1_id=1 and b.style2_id=1 and b.release_id="+scopReleaseID+" and round(b.blast_log10_e,8)<="+log10E+" and b.seq1_length > 0 and b.seq2_length > 0 "+rafQuery);
        */
        rs = stmt.executeQuery("select id2 from "+tableName+" where id1="+astralChainID+" and round(blast_log10_e,8)<="+log10E);
        while (rs.next()) {
            Integer i = new Integer(rs.getInt(1));
            if (!rv.contains(i)) {
                // check that it doesn't exist with a worse score
                // uncached: ResultSet rs2 = stmt2.executeQuery("select b.id from astral_seq_blast b, astral_chain c1, astral_chain c2, raf r1, raf r2 where c1.raf_id=r1.id and c2.raf_id=r2.id and b.seq2_id=c2.seq_id and c2.id="+astralChainID+" and b.seq1_id=c1.seq_id and c1.id="+i+" and round(b.blast_log10_e,8) > "+log10E+" and b.seq1_length > 0 and b.seq2_length > 0 and b.style1_id=1 and b.style2_id=1 and b.release_id="+scopReleaseID+" and b.source_id=c1.source_id and c2.source_id=c1.source_id "+rafQuery);
                ResultSet rs2 = stmt2.executeQuery("select id1 from "+tableName+" where id2="+astralChainID+" and id1="+i+" and round(blast_log10_e,8)>"+log10E);
                if (!rs2.next()) {
                    rs2.close();
                    // uncached: rs2 = stmt2.executeQuery("select b.id from astral_seq_blast b, astral_chain c1, astral_chain c2, raf r1, raf r2 where c1.raf_id=r1.id and c2.raf_id=r2.id and b.seq2_id=c2.seq_id and c1.id="+astralChainID+" and b.seq1_id=c1.seq_id and c2.id="+i+" and round(b.blast_log10_e,8) > "+log10E+" and b.seq1_length > 0 and b.seq2_length > 0 and b.style1_id=1 and b.style2_id=1 and b.release_id="+scopReleaseID+" and b.source_id=c1.source_id and c2.source_id=c1.source_id "+rafQuery);
                    rs2 = stmt2.executeQuery("select id1 from "+tableName+" where id1="+astralChainID+" and id2="+i+" and round(blast_log10_e,8)>"+log10E);
                    if (!rs2.next())
                        rv.add(i);
                }
                rs2.close();
            }
        }
        // uncached: rs = stmt.executeQuery("select c1.id from astral_seq_blast b, astral_chain c1, astral_chain c2, raf r1, raf r2 where c1.id!=c2.id and c2.id="+astralChainID+" and c1.seq_id=b.seq1_id and c2.seq_id=b.seq2_id and c1.raf_id=r1.id and c2.raf_id=r2.id and c1.source_id=c2.source_id and c1.source_id=b.source_id and b.style1_id=1 and b.style2_id=1 and b.release_id="+scopReleaseID+" and round(b.blast_log10_e,8)<="+log10E+" and b.seq1_length > 0 and b.seq2_length > 0 "+rafQuery);
        rs = stmt.executeQuery("select id1 from "+tableName+" where id2="+astralChainID+" and round(blast_log10_e,8)<="+log10E);	
        while (rs.next()) {
            Integer i = new Integer(rs.getInt(1));
            if (!rv.contains(i)) {
                // check that it doesn't exist with a worse score
                // uncached: ResultSet rs2 = stmt2.executeQuery("select b.id from astral_seq_blast b, astral_chain c1, astral_chain c2, raf r1, raf r2 where c1.raf_id=r1.id and c2.raf_id=r2.id and b.seq2_id=c2.seq_id and c2.id="+astralChainID+" and b.seq1_id=c1.seq_id and c1.id="+i+" and round(b.blast_log10_e,8) > "+log10E+" and b.seq1_length > 0 and b.seq2_length > 0 and b.style1_id=1 and b.style2_id=1 and b.release_id="+scopReleaseID+" and b.source_id=c1.source_id and c2.source_id=c1.source_id "+rafQuery);
                ResultSet rs2 = stmt2.executeQuery("select id1 from "+tableName+" where id2="+astralChainID+" and id1="+i+" and round(blast_log10_e,8)>"+log10E);				
                if (!rs2.next()) {
                    rs2.close();
                    // uncached: rs2 = stmt2.executeQuery("select b.id from astral_seq_blast b, astral_chain c1, astral_chain c2, raf r1, raf r2 where c1.raf_id=r1.id and c2.raf_id=r2.id and b.seq2_id=c2.seq_id and c1.id="+astralChainID+" and b.seq1_id=c1.seq_id and c2.id="+i+" and round(b.blast_log10_e,8) > "+log10E+" and b.seq1_length > 0 and b.seq2_length > 0 and b.style1_id=1 and b.style2_id=1 and b.release_id="+scopReleaseID+" and b.source_id=c1.source_id and c2.source_id=c1.source_id "+rafQuery);
                    rs2 = stmt2.executeQuery("select id1 from "+tableName+" where id1="+astralChainID+" and id2="+i+" and round(blast_log10_e,8)>"+log10E);						    
                    if (!rs2.next())
                        rv.add(i);
                }
                rs2.close();
            }
        }
        rs.close();
        stmt2.close();
        stmt.close();
        return rv;
    }


    /**
       prints selection log for a set of nodes
    */
    final public static void printVerboseLog(Vector<SPACI.SPACINode> nodes,
                                             Printf outfile) throws Exception {
        boolean first = true;
        for (SPACI.SPACINode n : nodes) {
            String sid = n.sid;
            double score = n.score;
            if (first) {
                outfile.printf("Rep: %s", sid);
                outfile.printf(" (score = %0.2f)\n",score);
                first = false;
            }
            else {
                outfile.printf("     %s", sid);
                outfile.printf(" (score = %0.2f)\n",score);
            }
        }
    }

    /**
       get all peers of a node for a given SCOP level
    */
    public static Vector<Integer> getNodePeers(int nodeID, int levelID)
        throws Exception {
        int parentID = LocalSQL.findParent(nodeID, levelID);
        return descendentsOf(parentID, 8);
    }

    /**
       get a sorted set of SPACI-anotated domains, for a set of
       SCOP node ids.
    */
    public static Vector<SPACI.SPACINode>
        getSortedNodes(Vector<Integer> nodeIDs) throws Exception {
        HashMap<Integer,String> allSids = ASTRAL.nodeIDToSid(nodeIDs);
        if (allSids==null)
            throw new Exception("error getting sids");
        HashMap<Integer,Double> allScores = nodeIDToAerospaci(nodeIDs);
        if (allScores==null)
            throw new Exception("error getting aerospacis");
        Vector<SPACI.SPACINode> nodes = SPACI.sortByScores(nodeIDs, allSids, allScores);
        return nodes;
    }

    /**
       Remove nodes where all the ASTRAL sequences are rejects.
    */
    public static Vector<SPACI.SPACINode>
        removeRejects(Vector<SPACI.SPACINode> nodes) throws Exception {
        if (nodes==null)
            return null;
        Statement stmt = LocalSQL.createStatement();
        Vector<SPACI.SPACINode> rv = new Vector<SPACI.SPACINode>();
        for (SPACI.SPACINode n : nodes) {
            int id = n.nodeID;
            // must have at least 1 non-reject OS sequence to qualify
            ResultSet rs = stmt.executeQuery("select d.id from astral_domain d, astral_seq s where d.source_id=2 and (d.style_id=1 or d.style_id=2) and s.id=d.seq_id and s.is_reject=0 and d.node_id="+id+" limit 1");
            if (rs.next())
                rv.add(n);
            rs.close();
        }
        stmt.close();
        return rv;
    }
    
    /**
       get a sorted set of SPACI-anotated domains, for a set of
       domain ids.
    */
    public static Vector<SPACI.SPACINode>
        getSortedDomains(Vector<Integer> nodeIDs) throws Exception {
        HashMap<Integer,String> allSids = ASTRAL.domainIDToSid(nodeIDs);
        if (allSids==null)
            throw new Exception("error getting sids");
        HashMap<Integer,Double> allScores = domainIDToAerospaci(nodeIDs);
        if (allScores==null)
            throw new Exception("error getting aerospacis");
        Vector<SPACI.SPACINode> nodes = SPACI.sortByScores(nodeIDs, allSids, allScores);
        return nodes;
    }

    /**
       get a sorted set of SPACI-anotated domains, for a set of
       chain ids.
    */
    public static Vector<SPACI.SPACINode>
        getSortedChains(Vector<Integer> nodeIDs,
                        int scopReleaseID) throws Exception {
        HashMap<Integer,String> allSids = ASTRAL.astralChainIDToSid(nodeIDs);
        if (allSids==null)
            throw new Exception("error getting sids");
        HashMap<Integer,Double> allScores = astralChainIDToAerospaci(nodeIDs,scopReleaseID);
        if (allScores==null)
            throw new Exception("error getting aerospacis");
        Vector<SPACI.SPACINode> nodes = SPACI.sortByScores(nodeIDs, allSids, allScores);
        return nodes;
    }
    
    /**
       get a set of sorted sets of SPACI-anotated chains, for a set of
       chain ids and a pct id.
    */
    public static Vector<Vector<SPACI.SPACINode>>
        getSortedChainSets(Vector<Integer> nodeIDs,
                           int pctID,
                           int scopReleaseID) throws Exception {
        Vector<Vector<SPACI.SPACINode>> rv =
            new Vector<Vector<SPACI.SPACINode>>();
        Vector<SPACI.SPACINode> remainingNodes = getSortedChains(nodeIDs,
                                                                 scopReleaseID);
        while (remainingNodes.size() > 0) {
            Vector<SPACI.SPACINode> newSet = new Vector<SPACI.SPACINode>();
            int firstID = remainingNodes.get(0).nodeID;
            Vector<Integer> peers = getChainPeers(firstID,
                                                  pctID,
                                                  scopReleaseID);
            peers.add(new Integer(firstID));
            for (SPACI.SPACINode node : remainingNodes) {
                if (peers.contains(new Integer(node.nodeID))) {
                    newSet.add(node);
                }
            }
            rv.add(newSet);
            remainingNodes.removeAll(newSet);
        }
        return rv;
    }
    
    /**
       get a set of sorted sets of SPACI-anotated domains, for a set
       of domain ids and a pct id, and a given style.
    */
    public static Vector<Vector<SPACI.SPACINode>>
        getSortedDomainSets(Vector<Integer> nodeIDs,
                            int pctID,
                            int styleID) throws Exception {
        Vector<Vector<SPACI.SPACINode>> rv =
            new Vector<Vector<SPACI.SPACINode>>();
        Vector<SPACI.SPACINode> remainingNodes = getSortedDomains(nodeIDs);
        while (remainingNodes.size() > 0) {
            Vector<SPACI.SPACINode> newSet = new Vector<SPACI.SPACINode>();
            int firstID = remainingNodes.get(0).nodeID;
            Vector<Integer> peers = getDomainPeers(firstID, pctID, styleID);
            peers.add(new Integer(firstID));
            for (SPACI.SPACINode node : remainingNodes) {
                if (peers.contains(new Integer(node.nodeID))) {
                    newSet.add(node);
                }
            }
            rv.add(newSet);
            remainingNodes.removeAll(newSet);
        }
        return rv;
    }
    
    /**
       get a set of sorted sets of SPACI-anotated chains, for a set
       of chain ids and an E-value.
    */
    public static Vector<Vector<SPACI.SPACINode>>
        getSortedChainSets(Vector<Integer> nodeIDs,
                           double log10E,
                           int scopReleaseID) throws Exception {
        Vector<Vector<SPACI.SPACINode>> rv =
            new Vector<Vector<SPACI.SPACINode>>();
        Vector<SPACI.SPACINode> remainingNodes = getSortedChains(nodeIDs,
                                                                 scopReleaseID);
        while (remainingNodes.size() > 0) {
            Vector<SPACI.SPACINode> newSet = new Vector<SPACI.SPACINode>();
            int firstID = remainingNodes.get(0).nodeID;
            Vector<Integer> peers = getChainPeers(firstID,
                                                  log10E,
                                                  scopReleaseID);
            peers.add(new Integer(firstID));
            for (SPACI.SPACINode node : remainingNodes) {
                if (peers.contains(new Integer(node.nodeID))) {
                    newSet.add(node);
                }
            }
            rv.add(newSet);
            remainingNodes.removeAll(newSet);
        }
        return rv;
    }
    
    /**
       get a set of sorted sets of SPACI-anotated domains, for a set
       of domain ids and an E-value, and a given style.
    */
    public static Vector<Vector<SPACI.SPACINode>>
        getSortedDomainSets(Vector<Integer> nodeIDs,
                            double log10E,
                            int styleID) throws Exception {
        Vector<Vector<SPACI.SPACINode>> rv =
            new Vector<Vector<SPACI.SPACINode>>();
        Vector<SPACI.SPACINode> remainingNodes = getSortedDomains(nodeIDs);
        while (remainingNodes.size() > 0) {
            // System.out.println("remaining: "+remainingNodes.size());
            Vector<SPACI.SPACINode> newSet = new Vector<SPACI.SPACINode>();
            int firstID = remainingNodes.get(0).nodeID;
            Vector<Integer> peers = getDomainPeers(firstID, log10E, styleID);
            peers.add(new Integer(firstID));
            for (SPACI.SPACINode node : remainingNodes) {
                if (peers.contains(new Integer(node.nodeID))) {
                    newSet.add(node);
                }
            }
            rv.add(newSet);
            remainingNodes.removeAll(newSet);
        }
        return rv;
    }
    
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            Statement stmt2 = LocalSQL.createStatement();

            int scopReleaseID = LocalSQL.lookupSCOPRelease(argv[0]);
            if (scopReleaseID==0)
                throw new Exception("SCOP version not found: "+argv[0]);
	    
            PrintfStream outfile = new PrintfStream(System.out);

            // get root node at this level
            ResultSet rs = stmt.executeQuery("select id from scop_node where release_id="+scopReleaseID+" and level_id=1");
            rs.next();
            int rootID = rs.getInt(1);

            Vector<Integer> allDomains = null;
            Vector<Integer> allChains = null;
            int[] ids = null;

            outfile.printf("Doing SCOP level subsets\n");
            // make subsets at each scop level from 2-7
            for (int level=2; level<=7; level++) {
                outfile.printf("doing level %d\n",level);
                outfile.flush();

                // get all nodes descended from root at that level
                Vector<Integer> nodesLevel = descendentsOf(rootID,level);

                // for each of these, get all domain descendents
                for (Integer parentID : nodesLevel) {
                    stmt.executeUpdate("delete from scop_subset_level where level_node_id="+parentID);
		    
                    Vector<SPACI.SPACINode> nodes = getSortedNodes(descendentsOf(parentID,8));
                    nodes = removeRejects(nodes);
			
                    // skip if no eligible nodes at this level
                    if ((nodes==null) || (nodes.size()==0))
                        continue;
		    
                    int repID = nodes.get(0).nodeID;

                    if (repID==122099)
                        System.out.println("debug - rep is d0lpc_2");

                    stmt.executeUpdate("insert into scop_subset_level values ("+repID+", "+parentID+")");
                    // printVerboseLog(nodes, outfile);
                }
            }

            // chain pct id
            outfile.printf("\nDoing chain %%ID subsets\n");
            allChains = DumpSeqs.getSeqs(scopReleaseID,
                                         true, // chain
                                         0, // no rejects
                                         0, // no ntc
                                         false, // no sort
                                         2, // seqres
                                         0, // gd (ignored)
                                         false); // unique
            for (int pctID : makePctIDChain) {
                outfile.printf("Doing %d%% id subsets\n",pctID);
                outfile.flush();
                stmt.executeUpdate("delete from astral_chain_subset_id where release_id="+scopReleaseID+" and pct_identical="+pctID+" and astral_chain_id in (select id from astral_chain where source_id=2)");
                Vector<Vector<SPACI.SPACINode>> allSets = getSortedChainSets(allChains, pctID, scopReleaseID);
                for (Vector<SPACI.SPACINode> nodes : allSets) {
                    int repID = nodes.get(0).nodeID;
                    stmt.executeUpdate("insert into astral_chain_subset_id values ("+scopReleaseID+", "+pctID+", "+repID+")");
                    // printVerboseLog(nodes, outfile);
                }
            }

            // chain-tagless pct id
            outfile.printf("\nDoing chain-tagless %%ID subsets\n");
            allChains = DumpSeqs.getSeqs(scopReleaseID,
                                         true, // chain
                                         0, // no rejects
                                         0, // no ntc
                                         false, // no sort
                                         4, // seqres-tagless
                                         0, // gd (ignored)
                                         false); // unique
            for (int pctID : makePctIDChain) {
                outfile.printf("Doing %d%% id subsets\n",pctID);
                outfile.flush();
                stmt.executeUpdate("delete from astral_chain_subset_id where release_id="+scopReleaseID+" and pct_identical="+pctID+" and astral_chain_id in (select id from astral_chain where source_id=4)");
                Vector<Vector<SPACI.SPACINode>> allSets = getSortedChainSets(allChains, pctID, scopReleaseID);
                for (Vector<SPACI.SPACINode> nodes : allSets) {
                    int repID = nodes.get(0).nodeID;
                    stmt.executeUpdate("insert into astral_chain_subset_id values ("+scopReleaseID+", "+pctID+", "+repID+")");
                    // printVerboseLog(nodes, outfile);
                }
            }

            // domain OS pct id
            outfile.printf("\nDoing OS domain %%ID subsets\n");
            allDomains = DumpSeqs.getSeqs(scopReleaseID,
                                          false, // chain
                                          0, // no rejects
                                          0, // no ntc
                                          false, // no sort
                                          2, // seqres
                                          0, // os+single
                                          false); // unique
            for (int pctID : makePctIDDomain) {
                outfile.printf("Doing %d%% id OS subsets\n",pctID);
                outfile.flush();
                stmt.executeUpdate("delete from astral_domain_subset_id where release_id="+scopReleaseID+" and pct_identical="+pctID+" and style_id=2");
                Vector<Vector<SPACI.SPACINode>> allSets = getSortedDomainSets(allDomains, pctID, 2);
                for (Vector<SPACI.SPACINode> nodes : allSets) {
                    int repID = nodes.get(0).nodeID;
                    stmt.executeUpdate("insert into astral_domain_subset_id values ("+scopReleaseID+", "+pctID+", 2, "+repID+")");
                    // printVerboseLog(nodes, outfile);
                }
            }

            // domain OS E-value
            outfile.printf("\nDoing OS domain E-value subsets\n");
            for (double log10E : makeLog10E) {
                outfile.printf("Doing E=10^%.1f OS subsets\n",log10E);
                outfile.flush();
                stmt.executeUpdate("delete from astral_domain_subset_blast_e where release_id="+scopReleaseID+" and blast_log10_e="+log10E+" and style_id=2");
                Vector<Vector<SPACI.SPACINode>> allSets = getSortedDomainSets(allDomains, log10E, 2);
                for (Vector<SPACI.SPACINode> nodes : allSets) {
                    int repID = nodes.get(0).nodeID;
                    stmt.executeUpdate("insert into astral_domain_subset_blast_e values ("+scopReleaseID+", "+log10E+", 2, "+repID+")");
                    // printVerboseLog(nodes, outfile);
                }
            }
	   
            // domain GD pct id
            outfile.printf("\nDoing GD domain %%ID subsets\n");
            allDomains = DumpSeqs.getSeqs(scopReleaseID,
                                          false, // chain
                                          0, // no rejects
                                          0, // no ntc
                                          false, // no sort
                                          2, // seqres
                                          1, // gd+single
                                          false); // unique
            for (int pctID : makePctIDDomain) {
                outfile.printf("Doing %d%% id GD subsets\n",pctID);
                outfile.flush();
                stmt.executeUpdate("delete from astral_domain_subset_id where release_id="+scopReleaseID+" and pct_identical="+pctID+" and style_id=3");
                Vector<Vector<SPACI.SPACINode>> allSets = getSortedDomainSets(allDomains, pctID, 3);
                for (Vector<SPACI.SPACINode> nodes : allSets) {
                    int repID = nodes.get(0).nodeID;
                    stmt.executeUpdate("insert into astral_domain_subset_id values ("+scopReleaseID+", "+pctID+", 3, "+repID+")");
                    // printVerboseLog(nodes, outfile);
                }
            }

            // domain GD E-value
            outfile.printf("\nDoing GD domain E-value subsets\n");
            for (double log10E : makeLog10E) {
                outfile.printf("Doing E=10^%.1f GD subsets\n",log10E);
                outfile.flush();
                stmt.executeUpdate("delete from astral_domain_subset_blast_e where release_id="+scopReleaseID+" and blast_log10_e="+log10E+" and style_id=3");
                Vector<Vector<SPACI.SPACINode>> allSets = getSortedDomainSets(allDomains, log10E, 3);
                for (Vector<SPACI.SPACINode> nodes : allSets) {
                    int repID = nodes.get(0).nodeID;
                    stmt.executeUpdate("insert into astral_domain_subset_blast_e values ("+scopReleaseID+", "+log10E+", 3, "+repID+")");
                    // printVerboseLog(nodes, outfile);
                }
            }

            outfile.flush();
            outfile.close();
    	}
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
