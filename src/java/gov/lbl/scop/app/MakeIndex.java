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
import java.text.*;
import org.strbio.io.*;
import org.strbio.math.*;
import org.strbio.mol.*;
import org.strbio.util.*;
import org.strbio.IO;
import gov.lbl.scop.local.LocalSQL;

/**
   Generates full-text index to search SCOP.
*/
public class MakeIndex {
    /**
       index a given node
    */
    final public static void indexNode(int nodeID) throws Exception {
        Statement stmt = LocalSQL.createStatement();
	
        ResultSet rs = stmt.executeQuery("select l.id, l.description, n.description, n.sccs, n.sunid, n.sid from scop_node n, scop_level l where n.level_id=l.id and n.level_id>1 and n.id="+nodeID);
        rs.next();
        int levelID = rs.getInt(1);
        String levelDesc = rs.getString(2);
        String desc = rs.getString(3);
        String sccs = rs.getString(4);
        int sunid = rs.getInt(5);
        String sid = rs.getString(6);

        String shortKeys = null;
        String longKeys = null;
        if (levelID==8) {
            shortKeys = "Domain: "+sid+" "+desc;
            longKeys = "Domain: "+sid+" "+desc;
            if (sid != null) {
                shortKeys+=" ! sid="+sid+" ! sid:"+sid;
                longKeys+=" ! sid="+sid+" ! sid:"+sid;
            }
            rs = stmt.executeQuery("select distinct(e.description) from pdb_entry e, pdb_release r, pdb_chain c, link_pdb l where l.node_id="+nodeID+" and l.pdb_chain_id=c.id and c.pdb_release_id=r.id and r.pdb_entry_id=e.id");
            while (rs.next())
                longKeys += " ! "+rs.getString(1);
            rs.close();
            rs = stmt.executeQuery("select e.code, c.chain from pdb_entry e, pdb_release r, pdb_chain c, link_pdb l where l.node_id="+nodeID+" and l.pdb_chain_id=c.id and c.pdb_release_id=r.id and r.pdb_entry_id=e.id");
            boolean addedCode = false;
            while (rs.next()) {
                String code = rs.getString(1);
                String chain = rs.getString(2);
                if (!addedCode) {
                    shortKeys += " ! pdb:"+code;
                    longKeys += " ! pdb:"+code;
                    addedCode = true;
                }
                longKeys += " ! pdb:"+code+chain;
                shortKeys += " ! pdb:"+code+chain;
            }
            rs.close();
            rs = stmt.executeQuery("select s.scientific_name, s.common_name, s.strain_name, s.is_synthetic, s.ncbi_taxid from pdb_chain_source pcs, pdb_source s, link_pdb l where l.node_id="+nodeID+" and l.pdb_chain_id=pcs.pdb_chain_id and pcs.pdb_source_id=s.id group by s.id");
            while (rs.next()) {
                longKeys += " ! "+rs.getString(1);
                String s = rs.getString(2);
                if (s != null)
                    longKeys += " "+s;
                s = rs.getString(3);
                if (s != null)
                    longKeys += " "+s;
                if (rs.getInt(4)==1)
                    longKeys += " synthetic";
                int taxid = rs.getInt(5);
                if (taxid > 0)
                    longKeys += " ! taxid="+taxid+" ! taxid:"+taxid;
            }
            rs.close();
            rs = stmt.executeQuery("select g.gene_name from pdb_chain_gene pcg, pdb_gene g, link_pdb l where l.node_id="+nodeID+" and l.pdb_chain_id=pcg.pdb_chain_id and pcg.pdb_gene_id=g.id group by g.id");
            while (rs.next())
                longKeys += " ! gene="+rs.getString(1);
            rs.close();
        }
        else {
            shortKeys = levelDesc+": "+desc;
            longKeys = levelDesc+": "+desc;
        }

        if (levelID==7) {
            rs = stmt.executeQuery("select s.scientific_name, s.common_name, s.details, s.ncbi_taxid from species s, link_species l where l.node_id="+nodeID+" and l.species_id=s.id");
            if (rs.next()) {
                longKeys += " ! "+rs.getString(1);
                String s = rs.getString(2);
                if (s != null)
                    longKeys += " "+s;
                s = rs.getString(3);
                if (s != null)
                    longKeys += " "+s;
                int taxid = rs.getInt(4);
                if (taxid > 0)
                    longKeys += " ! taxid="+taxid+" ! taxid:"+taxid;
            }
            rs.close();
        }

        HashSet<String> uniprots = new HashSet<String>();
        rs = stmt.executeQuery("select uniprot_accession from link_uniprot where node_id="+nodeID);
        while (rs.next()) {
            String acc = rs.getString(1);
            if (!uniprots.contains(acc)) {
                longKeys += " ! uniprot:"+acc;
                uniprots.add(acc);
            }
        }


        rs = stmt.executeQuery("select r.db_code, r.db_accession from pdb_chain_dbref r, link_pdb l where l.node_id="+nodeID+" and l.pdb_chain_id=r.pdb_chain_id and r.db_name='UNP'");
        while (rs.next()) {
            String acc = rs.getString(1);
            if (!uniprots.contains(acc)) {
                longKeys += " ! uniprot:"+acc;
                uniprots.add(acc);
            }
            acc = rs.getString(2);
            if (!uniprots.contains(acc)) {
                longKeys += " ! uniprot:"+acc;
                uniprots.add(acc);
            }
        }

        rs = stmt.executeQuery("select pfam_accession from link_pfam where node_id="+nodeID);
        while (rs.next()) {
            String acc = rs.getString(1);
            longKeys += " ! pfam:"+acc;
        }
		
        if (sunid>0) {
            shortKeys += " ! sunid="+sunid+" ! sunid:"+sunid;
            longKeys += " ! sunid="+sunid+" ! sunid:"+sunid;
        }
        if ((levelID < 6) && (sccs != null)) {
            shortKeys += " ! sccs="+sccs+" ! sccs:"+sccs;
            longKeys += " ! sccs="+sccs+" ! sccs:"+sccs;
        }
			
        rs = stmt.executeQuery("select description from scop_comment where node_id="+nodeID);
        while (rs.next()) {
            shortKeys += " ! "+rs.getString(1);
            longKeys += " ! "+rs.getString(1);
        }
        rs.close();

        shortKeys = StringUtil.replace(shortKeys,"\"","\\\"");
        longKeys = StringUtil.replace(longKeys,"\"","\\\"");
        // System.out.println(shortKeys);
        stmt.executeUpdate("insert into scop_index values ("+nodeID+", \""+shortKeys+"\", \""+longKeys+"\")");
        stmt.close();
    }

    /**
       rebuild entire index (all nodes other than root node)
    */
    final public static void rebuildIndex() throws Exception {
        LocalSQL.connectRW();
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs;
        stmt.executeUpdate("truncate table scop_index");
        stmt.executeUpdate("alter table scop_index disable keys");
        rs = stmt.executeQuery("select id from scop_node where level_id>1");
        while (rs.next()) {
            int nodeID = rs.getInt(1);
            indexNode(nodeID);
        }
        rs.close();
        stmt.executeUpdate("alter table scop_index enable keys");
        stmt.close();
    }
    
    
    final public static void main(String argv[]) {
        try {
            rebuildIndex();
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
