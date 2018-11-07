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
   Re-assign species of domains that Alexey classified automatically,
   if unambiguously wrong.
*/
public class FixProtSpecies3 {
    final public static void reassignAutomated(int domainID) throws Exception {
        Statement stmt = LocalSQL.createStatement();
	
        ResultSet rs = stmt.executeQuery("select n2.id, l1.pdb_chain_id, n2.parent_node_id, n2.description, s.ncbi_taxid from scop_node n1, scop_node n2, link_pdb l1, link_species l2, species s where l2.node_id=n2.id and s.id=l2.species_id and l1.node_id=n1.id and n1.parent_node_id=n2.id and n1.id="+domainID);
        rs.next();
        int speciesID = rs.getInt(1);
        int pdbChainID = rs.getInt(2);
        int proteinID = rs.getInt(3);
        String spDescription = rs.getString(4);
        int spTaxid = rs.getInt(5);
        rs.close();

        String pdbSpecies = PromoteASTEROIDS.getSpecies(pdbChainID);
        int pdbTaxid = PromoteASTEROIDS.getTaxid(pdbChainID);
        if ((pdbSpecies==null) ||
            ((spTaxid==pdbTaxid) && (spTaxid > 0))) {
            stmt.close();
            return;
        }

        int newSpeciesID = 0;
        String newDescription = null;

        // look up new species by name
        rs = stmt.executeQuery("select n.id, n.description from species s, link_species l, scop_node n where l.node_id=n.id and l.species_id=s.id and s.scientific_name=\""+StringUtil.replace(pdbSpecies,"\"","\\\"")+"\" and n.parent_node_id = "+proteinID+" limit 2");
        if (rs.next()) {
            newSpeciesID = rs.getInt(1);
            newDescription = rs.getString(2);
        }
        if (rs.next()) {
            newSpeciesID = 0; // ambiguous
        }
        rs.close();
        stmt.close();

        if ((newSpeciesID==0) || (newSpeciesID==speciesID))
            return;

        if ((pdbSpecies.toLowerCase().indexOf("synthetic") > -1) &&
            (newDescription.toLowerCase().indexOf("synthetic") > -1))
            return;
	    
        System.out.println("moving node "+domainID+" under "+newSpeciesID);
        System.out.println("  http://strgen.org/~jmc/scop-newui/?node="+domainID);
        if (pdbTaxid > 0)
            pdbSpecies += " ["+pdbTaxid+"]";
        System.out.println("  pdb says "+pdbSpecies);
        System.out.println("  current species is "+spDescription);
        System.out.println("  new species is "+newDescription);

        // move protein to new species
        ManualEdit.moveNode(domainID,
                            newSpeciesID,
                            true);
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
            rs = stmt.executeQuery("select n.id from scop_node n, scop_comment c where n.level_id=8 and n.release_id="+scopReleaseID+" and n.id=c.node_id and (c.description like \"automatically matched to %\" or c.description like \"automated match to %\")");
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
