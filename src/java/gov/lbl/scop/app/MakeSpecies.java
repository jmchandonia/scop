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
import org.xml.sax.*;
import org.xml.sax.helpers.*;
import javax.xml.parsers.*;
import gov.lbl.scop.local.LocalSQL;

/**
   Populate species table and links to it
*/
public class MakeSpecies {
    /**
       note:  species is not case sensitive
    */
    final public static int lookupOrCreateSpecies(String scientificName,
                                                  String commonName,
                                                  String details,
                                                  int taxid) throws Exception {
        int rv = 0;
        Statement stmt = LocalSQL.createStatement();
        scientificName = StringUtil.replace(scientificName,"\"","\\\"");
        if (commonName != null)
            commonName = StringUtil.replace(commonName,"\"","\\\"");
        if (details != null)
            details = StringUtil.replace(details,"\"","\\\"");
        ResultSet rs = stmt.executeQuery("select id from species where scientific_name=\""+
                                         scientificName+
                                         "\" and common_name "+
                                         (commonName==null? "is null" : "=\""+commonName+"\"")+
                                         " and details "+
                                         (details==null? "is null" : "=\""+details+"\"")+
                                         " and ncbi_taxid "+
                                         (taxid==0? "is null" : "="+taxid));
        if (rs.next())
            rv = rs.getInt(1);
        else {
            rs.close();
            stmt.executeUpdate("insert into species values (null, \""+
                               scientificName+"\", "+
                               (commonName==null? "null" : "\""+commonName+"\"")+
                               ", "+
                               (details==null? "null" : "\""+details+"\"")+
                               ", "+
                               (taxid==0? "null" : taxid)+
                               ")",
                               Statement.RETURN_GENERATED_KEYS);
            rs = stmt.getGeneratedKeys();
            rs.next();
            rv = rs.getInt(1);
        }
        rs.close();
        stmt.close();
        return rv;
    }

    /**
       process a species node, and return an entry in the
       species table
    */
    final public static int processNode(int nodeID,
                                        String description) throws Exception {
        Statement stmt = LocalSQL.createStatement();
	
        int taxid = 0;
        int pos = description.indexOf("[TaxId: ");
        if (pos > -1) {
            taxid = StringUtil.atoi(description,pos+8);
            description = description.substring(0,pos).trim();
        }
        pos = description.indexOf("[TaxId:");
        if (pos > -1) {
            taxid = StringUtil.atoi(description,pos+7);
            description = description.substring(0,pos).trim();
        }
        String commonName = null;
        String details = null;
        pos = description.indexOf(",");
        if (pos > -1) {
            details = description.substring(pos+1);
            details = details.trim();
            if (details.length()==0)
                details = null;
            description = description.substring(0,pos).trim();
        }
        pos = description.indexOf("(");
        int pos2 = description.lastIndexOf("(");
        if ((pos > -1) && (pos==pos2) && (description.endsWith(")"))) {
            pos2 = description.indexOf(" ",pos+1);
            if ((pos2 > pos) &&
                (Character.isUpperCase(description.charAt(pos+1))) &&
                (Character.isLowerCase(description.charAt(pos2+1)))) {
                commonName = description.substring(0,pos).trim();
                if (commonName.length()==0)
                    commonName = null;
                description = description.substring(pos+1,description.length()-1).trim();
            }
        }
        int speciesID = lookupOrCreateSpecies(description,
                                              commonName,
                                              details,
                                              taxid);
        stmt.executeUpdate("delete from link_species where node_id="+nodeID);
        stmt.executeUpdate("insert into link_species values ("+nodeID+","+speciesID+")");
        stmt.close();
        return speciesID;
    }

    final public static void linkPDBSpecies(int speciesID) throws Exception {
        Statement stmt2 = LocalSQL.createStatement();
        Statement stmt3 = LocalSQL.createStatement();

        ResultSet rs2 = stmt2.executeQuery("select distinct(pcs.pdb_source_id) from pdb_chain_source pcs, link_pdb lp, link_species ls where lp.node_id=ls.node_id and lp.pdb_chain_id=pcs.pdb_chain_id and ls.species_id="+speciesID);
        while (rs2.next()) {
            int sourceID = rs2.getInt(1);
            stmt3.executeUpdate("delete from pdb_source_species where pdb_source_id="+sourceID);
            stmt3.executeUpdate("insert into pdb_source_species values ("+sourceID+","+speciesID+")");
        }
        rs2.close();
        stmt2.close();
        stmt3.close();
    }					    

    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            Statement stmt2 = LocalSQL.createStatement();
            Statement stmt3 = LocalSQL.createStatement();
	    
            ResultSet rs = stmt.executeQuery("select id, description from scop_node where level_id=7 and id not in (select node_id from link_species)");
            while (rs.next()) {
                int nodeID = rs.getInt(1);
                String description = rs.getString(2);

                int speciesID = processNode(nodeID, description);

                linkPDBSpecies(speciesID);
            }
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
