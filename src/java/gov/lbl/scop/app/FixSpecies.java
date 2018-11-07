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
import gov.lbl.scop.local.LocalSQL;

/**
   Fix species descriptions to be consistent across all clades,
   where possible.
*/
public class FixSpecies {
    /**
       merge one species into another, updating all links.  Delete
       old, keep new.
    */
    final public static void mergeSpecies(int oldID, int newID) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select id from species where id="+oldID);
        if (!rs.next()) {
            rs.close();
            stmt.close();
            return;
        }
        rs.close();
        rs = stmt.executeQuery("select id from species where id="+newID);
        if (!rs.next()) {
            rs.close();
            stmt.close();
            return;
        }
        rs.close();
        stmt.executeUpdate("update link_species set species_id="+newID+" where species_id="+oldID);
        stmt.executeUpdate("update pdb_source_species set species_id="+newID+" where species_id="+oldID);
        stmt.executeUpdate("delete from species where id="+oldID);
        stmt.close();
    }
    
    /**
       set the descriptions in the scop_node table based on
       the species that they're linked to, for a given
       species
    */
    final public static void fixDescription(int speciesID) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        Statement stmt2 = LocalSQL.createStatement();
        ResultSet rs, rs2;

        rs = stmt.executeQuery("select max(id) from scop_release");
        rs.next();
        int scopReleaseID = rs.getInt(1);
        rs.close();
	    
        rs = stmt.executeQuery("select scientific_name, common_name, details, ncbi_taxid from species where id="+speciesID);
        rs.next();
        String sciName = rs.getString(1);
        String commonName = rs.getString(2);
        String details = rs.getString(3);
        int taxid = rs.getInt(4);
        if (rs.wasNull())
            taxid = 0;

        String description = null;
        if (commonName != null)
            description = commonName+" ("+sciName+")";
        else
            description = sciName;
        if (details != null)
            description += ", "+details;
        if (taxid != 0)
            description += " [TaxId: "+taxid+"]";

        description = StringUtil.replace(description,"\"","\\\"");

        rs = stmt.executeQuery("select id, description from scop_node where id in (select node_id from link_species where species_id="+speciesID+") and description != \""+description+"\" and release_id="+scopReleaseID);
        while (rs.next()) {
            int nodeID = rs.getInt(1);
            String oldDesc = StringUtil.replace(rs.getString(2),"\"","\\\"");
            System.out.println("difference for node "+nodeID);
            System.out.println("  "+oldDesc);
            System.out.println("  "+description);
            if (oldDesc.toLowerCase().equals(description.toLowerCase())) {
                // find most common case
                rs2 = stmt2.executeQuery("select count(*) from scop_node where id in (select node_id from link_species where species_id="+speciesID+") and description = \""+description+"\" and sunid>0 and release_id="+scopReleaseID);
                rs2.next();
                int newCount = rs2.getInt(1);
                rs2.close();
                rs2 = stmt2.executeQuery("select count(*) from scop_node where id in (select node_id from link_species where species_id="+speciesID+") and description = \""+oldDesc+"\" and sunid>0 and release_id="+scopReleaseID);
                rs2.next();
                int oldCount = rs2.getInt(1);
                rs2.close();
                System.out.println("  Counts: "+oldCount+" for former, "+newCount+" for latter.");
                if (newCount >= oldCount)
                    stmt2.executeUpdate("update scop_node set description=\""+description+"\" where id="+nodeID);
                else {
                    // fix case in species node
                    Vector<Integer> oldNodes = new Vector<Integer>();
                    rs2 = stmt2.executeQuery("select node_id from link_species where species_id="+speciesID);
                    while (rs2.next())
                        oldNodes.add(new Integer(rs2.getInt(1)));
		    
                    stmt2.executeUpdate("delete from link_species where species_id="+speciesID);
                    stmt2.executeUpdate("delete from species where id="+speciesID);
                    stmt2.executeUpdate("delete from species where id="+speciesID);
                    int newSpeciesID = MakeSpecies.processNode(nodeID, description);
                    for (Integer i : oldNodes) {
                        rs2 = stmt2.executeQuery("select description from scop_node where id="+i);
                        rs2.next();
                        String desc2 = rs2.getString(1);
                        rs2.close();
                        MakeSpecies.processNode(i.intValue(), desc2);
                    }
                    MakeSpecies.linkPDBSpecies(newSpeciesID);
                    stmt.close();
                    stmt2.close();
                    return;
                }
            }
            else {
                System.out.println("fixing");
                stmt2.executeUpdate("update scop_node set description=\""+description+"\" where id="+nodeID);
            }
        }
        rs.close();

        stmt.close();
        stmt2.close();
    }
    
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            Statement stmt2 = LocalSQL.createStatement();
            ResultSet rs, rs2;

            int scopReleaseID = LocalSQL.getLatestSCOPRelease(false);

            int lastPublicRelease = LocalSQL.getLatestSCOPRelease(true);
            if (scopReleaseID==lastPublicRelease)
                throw new Exception("can't fix a public release");

            if (argv.length > 0) {
                int species1 = StringUtil.atoi(argv[0]);
                int species2 = StringUtil.atoi(argv[1]);
                if ((species1==0) || (species2==0))
                    throw new Exception("can't merge species");
                System.out.println("Merging "+species1+" "+species2);
                mergeSpecies(species1, species2);
                System.exit(0);
            }

            // merge species where everything is the same
            rs = stmt.executeQuery("select s1.id, s2.id from species s1, species s2 where s1.scientific_name=s2.scientific_name and s1.details<=>s2.details and s1.ncbi_taxid<=>s2.ncbi_taxid and s1.common_name<=>s2.common_name and s1.id>s2.id");
            while (rs.next()) {
                int species1 = rs.getInt(1);
                int species2 = rs.getInt(2);
                System.out.println("Merging "+species1+" "+species2);
                mergeSpecies(species1, species2);
            }
	    
            // merge species where everything is the same but the common name
            rs = stmt.executeQuery("select s1.id, s2.id from species s1, species s2 where s1.scientific_name=s2.scientific_name and s1.details<=>s2.details and s1.ncbi_taxid<=>s2.ncbi_taxid and s1.common_name is null and s2.common_name is not null");
            while (rs.next()) {
                int species1 = rs.getInt(1);
                int species2 = rs.getInt(2);
                // check that there are no other common names
                rs2 = stmt2.executeQuery("select s3.id, s2.common_name, s3.common_name from species s2, species s3 where s2.id="+species2+" and s3.scientific_name=s2.scientific_name and s3.details<=>s2.details and s3.ncbi_taxid<=>s2.ncbi_taxid and s3.common_name is not null and s2.common_name is not null and s2.common_name!=s3.common_name");
                if (rs2.next()) {
                    int species3 = rs2.getInt(1);
                    String speciesName2 = rs2.getString(2);
                    String speciesName3 = rs2.getString(3);
                    // get counts:
                    rs2 = stmt2.executeQuery("select count(*) from scop_node where id in (select node_id from link_species where species_id="+species2+") and sunid>0 and release_id="+scopReleaseID);
                    rs2.next();
                    int count2 = rs2.getInt(1);
                    rs2 = stmt2.executeQuery("select count(*) from scop_node where id in (select node_id from link_species where species_id="+species3+") and sunid>0 and release_id="+scopReleaseID);
                    rs2.next();
                    int count3 = rs2.getInt(1);
                    if ((count2 > count3+5) && (count2>9)) {
                        System.out.println("Merging "+species1+" "+species2);
                        mergeSpecies(species1, species2);
                    }
                    else {
                        System.out.println("Not merging:");
                        System.out.println("  "+species2+" "+speciesName2+" "+count2);
                        System.out.println("  "+species3+" "+speciesName3+" "+count3);
                    }
                }
                else {
                    System.out.println("Merging "+species1+" "+species2);
                    mergeSpecies(species1, species2);
                }
            }

            // merge species where everything is the same but the taxid
            rs = stmt.executeQuery("select s1.id, s2.id from species s1, species s2 where s1.scientific_name=s2.scientific_name and s1.details<=>s2.details and s1.common_name<=>s2.common_name and s1.ncbi_taxid is null and s2.ncbi_taxid is not null");
            while (rs.next()) {
                int species1 = rs.getInt(1);
                int species2 = rs.getInt(2);
                // check that there are no other taxids
                rs2 = stmt2.executeQuery("select s3.id, s2.ncbi_taxid, s3.ncbi_taxid from species s2, species s3 where s2.id="+species2+" and s3.scientific_name=s2.scientific_name and s3.details<=>s2.details and s3.common_name<=>s2.common_name and s3.ncbi_taxid is not null and s2.ncbi_taxid is not null and s2.ncbi_taxid!=s3.ncbi_taxid");
                if (rs2.next()) {
                    int species3 = rs2.getInt(1);
                    int speciesTax2 = rs2.getInt(2);
                    int speciesTax3 = rs2.getInt(3);
                    // get counts:
                    rs2 = stmt2.executeQuery("select count(*) from scop_node where id in (select node_id from link_species where species_id="+species2+") and sunid>0 and release_id="+scopReleaseID);
                    rs2.next();
                    int count2 = rs2.getInt(1);
                    rs2 = stmt2.executeQuery("select count(*) from scop_node where id in (select node_id from link_species where species_id="+species3+") and sunid>0 and release_id="+scopReleaseID);
                    rs2.next();
                    int count3 = rs2.getInt(1);
                    if ((count2 > count3+4) && (count3==0)) {
                        System.out.println("Merging "+species1+" "+species2);
                        mergeSpecies(species1, species2);
                    }
                    else {
                        System.out.println("Not merging (tax):");
                        System.out.println("  "+species2+" "+speciesTax2+" "+count2);
                        System.out.println("  "+species3+" "+speciesTax3+" "+count3);
                    }
                }
                else {
                    System.out.println("Merging "+species1+" "+species2);
                    mergeSpecies(species1, species2);
                }
            }

            // merge same species at same level
            rs = stmt.executeQuery("select n1.id, n2.id from scop_node n1, scop_node n2, link_species l1, link_species l2 where n1.id=l1.node_id and n2.id=l2.node_id and (n1.sunid=0 or n1.sunid > n2.sunid) and n1.id != n2.id and n1.parent_node_id=n2.parent_node_id and l1.species_id=l2.species_id and n1.release_id="+scopReleaseID);
            while (rs.next()) {
                int node1 = rs.getInt(1);
                int node2 = rs.getInt(2);
                try {
                    System.out.println("Merging nodes "+node1+" and "+node2);
                    ManualEdit.mergeNode(node1,node2,true);
                }
                catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            }
	    
            // check all species in current release
            rs = stmt.executeQuery("select distinct(species_id) from link_species where node_id in (select id from scop_node where release_id="+scopReleaseID+")");
            while (rs.next())
                fixDescription(rs.getInt(1));
            rs.close();

            stmt.close();
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
