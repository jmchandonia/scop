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
   Re-assign domains in the "automated" category, if possible
   based on new PDB annotations since they were first assigned
*/
public class FixProtSpecies2 {
    final public static void reassignAutomated(int proteinID) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        Statement stmt2 = LocalSQL.createStatement();
	
        ResultSet rs = stmt.executeQuery("select sccs, parent_node_id, release_id from scop_node where id="+proteinID);
        rs.next();
        String sccs = rs.getString(1);
        int familyID = rs.getInt(2);
        int scopReleaseID = rs.getInt(3);
        if (sccs.endsWith(".0"))
            return;

        System.out.println("Trying to reclassify proteins from "+proteinID);
	
        rs = stmt.executeQuery("select n1.id, n2.id, n2.description, l1.pdb_chain_id, l2.species_id from scop_node n1, scop_node n2, link_pdb l1, link_species l2 where l2.node_id=n2.id and l1.node_id=n1.id and n1.parent_node_id=n2.id and n2.parent_node_id="+proteinID);
        while (rs.next()) {
            int domainID = rs.getInt(1);
            int speciesID = rs.getInt(2);
            String spDescription = rs.getString(3);
            int pdbChainID = rs.getInt(4);
            int speciesTableID = rs.getInt(5);

            String pdbGene = PromoteASTEROIDS.getGene(pdbChainID);
            if (pdbGene==null)
                continue;

            // does gene name match exactly 1 protein from same family?
            // (besides the automated matches)
            int newProteinID = 0;
            ResultSet rs2 = stmt2.executeQuery("select distinct(n3.id) from pdb_gene g, pdb_chain_gene pcg, link_pdb l, scop_node n1, scop_node n2, scop_node n3 where n3.parent_node_id="+familyID+" and n3.id!="+proteinID+" and n2.parent_node_id=n3.id and n1.parent_node_id=n2.id and l.node_id=n1.id and l.pdb_chain_id=pcg.pdb_chain_id and pcg.pdb_gene_id=g.id and g.gene_name=\""+StringUtil.replace(pdbGene,"\"","\\\"")+"\"");
            if (rs2.next())
                newProteinID = rs2.getInt(1);
            else {
                rs2.close();
                continue;
            }
            if (rs2.next()) {
                System.out.println("gene "+pdbGene+" matches multiple proteins; can't automatically assign");
                rs2.close();
                continue;
            }
            rs2.close();

            int newSpeciesID = 0;
            boolean hasMultiple = false;
            // do any matching species exist under that protein?
            rs2 = stmt2.executeQuery("select distinct(n1.id) from scop_node n1, link_species l1, link_species l2, species s1, species s2 where n1.parent_node_id="+newProteinID+" and l1.node_id=n1.id and l2.node_id="+speciesID+" and l1.species_id=s1.id and l2.species_id=s2.id and (s1.scientific_name=s2.scientific_name) and (s1.ncbi_taxid=s2.ncbi_taxid) and s2.ncbi_taxid is not null");
            if (rs2.next())
                newSpeciesID = rs2.getInt(1);
            if (rs2.next())
                hasMultiple = true;
            rs2.close();

            if (newSpeciesID==0) {
                // look by taxid instead
                rs2 = stmt2.executeQuery("select distinct(n1.id) from scop_node n1, link_species l1, link_species l2, species s1, species s2 where n1.parent_node_id="+newProteinID+" and l1.node_id=n1.id and l2.node_id="+speciesID+" and l1.species_id=s1.id and l2.species_id=s2.id and s1.ncbi_taxid=s2.ncbi_taxid and s2.ncbi_taxid is not null");
                if (rs2.next())
                    newSpeciesID = rs2.getInt(1);
                if (rs2.next())
                    hasMultiple = true;
                rs2.close();
            }

            if (newSpeciesID==0) {
                // look up by name only
                rs2 = stmt2.executeQuery("select distinct(n1.id) from scop_node n1, link_species l1, link_species l2, species s1, species s2 where n1.parent_node_id="+newProteinID+" and l1.node_id=n1.id and l2.node_id="+speciesID+" and l1.species_id=s1.id and l2.species_id=s2.id and (s1.scientific_name=s2.scientific_name)");
                if (rs2.next())
                    newSpeciesID = rs2.getInt(1);
                if (rs2.next())
                    hasMultiple = true;
                rs2.close();
            }
	    
            if (hasMultiple) {
                // does only one of the species match this gene?
                rs2 = stmt2.executeQuery("select distinct(n2.id) from pdb_gene g, pdb_chain_gene pcg, link_pdb l, scop_node n1, scop_node n2 where n2.parent_node_id="+newProteinID+" and n1.parent_node_id=n2.id and l.node_id=n1.id and l.pdb_chain_id=pcg.pdb_chain_id and pcg.pdb_gene_id=g.id and g.gene_name=\""+StringUtil.replace(pdbGene,"\"","\\\"")+"\"");
                if (rs2.next())
                    newSpeciesID = rs2.getInt(1);
                if (rs2.next()) {
                    System.out.println("gene "+pdbGene+" matches multiple species under 1 protein; can't automatically assign");
                    rs2.close();
                    continue;
                }
                rs2.close();
            }

            if (newSpeciesID==0) {
                // duplicate the species under new protein
                newSpeciesID = LocalSQL.createNode(0,
                                                   sccs,
                                                   null,
                                                   spDescription,
                                                   7,
                                                   newProteinID,
                                                   scopReleaseID,
                                                   3);
                stmt2.executeUpdate("insert into link_species values ("+newSpeciesID+", "+speciesTableID+")");
            }

            System.out.println("moving node "+domainID+" under "+newSpeciesID);

            // move protein to new species
            ManualEdit.moveNode(domainID,
                                newSpeciesID,
                                true);
        }
        rs.close();
        stmt.close();
        stmt2.close();
    }

    
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            ResultSet rs;

            int scopReleaseID = LocalSQL.getLatestSCOPRelease(false);

            int lastPublicRelease = LocalSQL.getLatestSCOPRelease(true);
            if (scopReleaseID==lastPublicRelease)
                throw new Exception("can't fix a public release");

            // check automated proteins
            rs = stmt.executeQuery("select id from scop_node where level_id=6 and release_id="+scopReleaseID+" and description=\"automated matches\"");
            while (rs.next())
                reassignAutomated(rs.getInt(1));
            rs.close();

            stmt.close();
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
