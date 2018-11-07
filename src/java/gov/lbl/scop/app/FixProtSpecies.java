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
   Fix protein and species inconsistencies.
*/
public class FixProtSpecies {
    /**
       if a family has multiple proteins, are there any gene annotations
       that are consistent between different proteins in the family?
       if so, warn about them
    */
    final public static void checkFamily(int familyID) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        Statement stmt2 = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select count(*) from scop_node where parent_node_id="+familyID);
        if (!rs.next()) {
            rs.close();
            stmt.close();
            stmt2.close();
            return;
        }
        int nProt = rs.getInt(1);
        rs.close();
        if (nProt<=1) {
            stmt.close();
            stmt2.close();
            return;
        }
        rs = stmt.executeQuery("select sccs, parent_node_id, description from scop_node where id="+familyID);
        rs.next();
        String sccs = rs.getString(1);
        int sfID = rs.getInt(2);
        String fdesc = rs.getString(3);
        rs.close();
        System.out.println("Checking family "+familyID+" "+sccs+" "+fdesc);
        boolean ok = true;
        rs = stmt.executeQuery("select nd1.id, nd2.id, np1.id, np2.id, nd1.sid, nd2.sid, np1.description, np2.description, g.gene_name, np1.sunid, np2.sunid, ns1.id, ns2.id from pdb_gene g, pdb_chain_gene pcs1, pdb_chain_gene pcs2, pdb_release prn1, pdb_release pro1, pdb_release prn2, pdb_release pro2, pdb_chain pcn1, pdb_chain pcn2, pdb_chain pco1, pdb_chain pco2, link_pdb l1, link_pdb l2, scop_node nd1, scop_node ns1, scop_node np1, scop_node nd2, scop_node ns2, scop_node np2 where np1.parent_node_id="+familyID+" and np2.parent_node_id=np1.parent_node_id and np1.id < np2.id and ns1.parent_node_id=np1.id and ns2.parent_node_id=np2.id and nd1.parent_node_id=ns1.id and nd2.parent_node_id=ns2.id and l1.node_id=nd1.id and l2.node_id=nd2.id and l1.pdb_chain_id=pco1.id and l2.pdb_chain_id=pco2.id and pco1.pdb_release_id=pro1.id and pco2.pdb_release_id=pro2.id and pro1.pdb_entry_id=prn1.pdb_entry_id and pro2.pdb_entry_id=prn2.pdb_entry_id and pcn1.pdb_release_id=prn1.id and pcn2.pdb_release_id=prn2.id and prn1.replaced_by is null and prn2.replaced_by is null and pcn1.chain=pco1.chain and pcn2.chain=pco2.chain and pcs1.pdb_chain_id=pcn1.id and pcs2.pdb_chain_id=pcn2.id and pcs1.pdb_gene_id=g.id and pcs2.pdb_gene_id=g.id group by nd1.id, nd2.id");
        while (rs.next()) {
            ok = false;
            int node1 = rs.getInt(1);
            int node2 = rs.getInt(2);
            int nodeP1 = rs.getInt(3);
            int nodeP2 = rs.getInt(4);
            String sid1 = rs.getString(5);
            String sid2 = rs.getString(6);
            String prot1 = rs.getString(7);
            String prot2 = rs.getString(8);
            String gene = rs.getString(9);
            int sunid1 = rs.getInt(10);
            int sunid2 = rs.getInt(11);
            int nodeS1 = rs.getInt(12);
            int nodeS2 = rs.getInt(13);

            System.out.println("Warning:  sids "+sid1+" and "+sid2+" share gene "+gene);
            System.out.println("  "+sid1+" = "+prot1+" http://strgen.org/~jmc/scop-newui/?node="+node1);
            System.out.println("  "+sid2+" = "+prot2+" http://strgen.org/~jmc/scop-newui/?node="+node2);
            if (sunid2==0) {
                ResultSet rs2 = stmt2.executeQuery("select nd1.sid, np1.description, g.gene_name, nd1.id from pdb_gene g, pdb_chain_gene pcs1, pdb_chain_gene pcs2, pdb_release prn1, pdb_release pro1, pdb_release prn2, pdb_release pro2, pdb_chain pcn1, pdb_chain pcn2, pdb_chain pco1, pdb_chain pco2, link_pdb l1, link_pdb l2, scop_node nd1, scop_node ns1, scop_node np1, scop_node nd2, scop_node ns2, scop_node np2, scop_node nf1, scop_node nf2 where np1.parent_node_id=nf1.id and nf1.id!="+familyID+" and nf1.parent_node_id="+sfID+" and np2.parent_node_id=nf1.id and nf1.parent_node_id=nf2.parent_node_id and nf1.id!=nf2.id and np1.id < np2.id and ns1.parent_node_id=np1.id and ns2.parent_node_id=np2.id and nd1.parent_node_id=ns1.id and nd2.parent_node_id=ns2.id and l1.node_id=nd1.id and l2.node_id=nd2.id and l1.pdb_chain_id=pco1.id and l2.pdb_chain_id=pco2.id and pco1.pdb_release_id=pro1.id and pco2.pdb_release_id=pro2.id and pro1.pdb_entry_id=prn1.pdb_entry_id and pro2.pdb_entry_id=prn2.pdb_entry_id and pcn1.pdb_release_id=prn1.id and pcn2.pdb_release_id=prn2.id and prn1.replaced_by is null and prn2.replaced_by is null and pcn1.chain=pco1.chain and pcn2.chain=pco2.chain and pcs1.pdb_chain_id=pcn1.id and pcs2.pdb_chain_id=pcn2.id and pcs1.pdb_gene_id=g.id and pcs2.pdb_gene_id=g.id and nd2.id="+node2+" group by nd1.sid");
                while (rs2.next()) {
                    String sid1B = rs2.getString(1);
                    String prot1B = rs2.getString(2);
                    String geneB = rs2.getString(3);
                    int id1B = rs2.getInt(4);
                    System.out.println("  different family, same protein "+geneB+":");
                    System.out.println("    "+sid1B+" = "+prot1B+" http://strgen.org/~jmc/scop-newui/?node="+id1B);
                }
                rs2.close();
            }
            System.out.println("  move "+sid1+": java gov.lbl.scop.app.ManualEdit mv "+nodeS1+" "+nodeP2);
            System.out.println("  move "+sid2+": java gov.lbl.scop.app.ManualEdit mv "+nodeS2+" "+nodeP1);
        }
        rs.close();
        if (ok)
            System.out.println("No common proteins found");
        stmt.close();
        stmt2.close();
    }

    /**
       if a protein has multiple species, are there any species annotations
       that are consistent between different species nodes?
       if so, warn about them
    */
    final public static void checkProtein(int proteinID) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select count(*) from scop_node where parent_node_id="+proteinID);
        if (!rs.next()) {
            rs.close();
            stmt.close();
            return;
        }
        int nSpec = rs.getInt(1);
        rs.close();
        if (nSpec<=1) {
            stmt.close();
            return;
        }
        System.out.println("Checking protein "+proteinID);
        boolean ok = true;
        rs = stmt.executeQuery("select nd1.id, nd2.id, ns1.id, ns2.id, nd1.sid, nd2.sid, ns1.description, ns2.description, s1.scientific_name, ns1.sunid, ns2.sunid from pdb_source s1, pdb_source s2, pdb_chain_source pcs1, pdb_chain_source pcs2, pdb_release prn1, pdb_release pro1, pdb_release prn2, pdb_release pro2, pdb_chain pcn1, pdb_chain pcn2, pdb_chain pco1, pdb_chain pco2, link_pdb l1, link_pdb l2, scop_node nd1, scop_node ns1, scop_node nd2, scop_node ns2 where ns1.id < ns2.id and ns1.parent_node_id="+proteinID+" and ns1.parent_node_id=ns2.parent_node_id and nd1.parent_node_id=ns1.id and nd2.parent_node_id=ns2.id and l1.node_id=nd1.id and l2.node_id=nd2.id and l1.pdb_chain_id=pco1.id and l2.pdb_chain_id=pco2.id and pco1.pdb_release_id=pro1.id and pco2.pdb_release_id=pro2.id and pro1.pdb_entry_id=prn1.pdb_entry_id and pro2.pdb_entry_id=prn2.pdb_entry_id and pcn1.pdb_release_id=prn1.id and pcn2.pdb_release_id=prn2.id and prn1.replaced_by is null and prn2.replaced_by is null and pcn1.chain=pco1.chain and pcn2.chain=pco2.chain and pcs1.pdb_chain_id=pcn1.id and pcs2.pdb_chain_id=pcn2.id and pcs1.pdb_source_id=s1.id and pcs2.pdb_source_id=s2.id and s1.scientific_name=s2.scientific_name");
        while (rs.next()) {
            ok = false;
            int node1 = rs.getInt(1);
            int node2 = rs.getInt(2);
            int nodeS1 = rs.getInt(3);
            int nodeS2 = rs.getInt(4);
            String sid1 = rs.getString(5);
            String sid2 = rs.getString(6);
            String spec1 = rs.getString(7);
            String spec2 = rs.getString(8);
            String species = rs.getString(9);
            int sunid1 = rs.getInt(10);
            int sunid2 = rs.getInt(11);

            System.out.println("Warning:  sids "+sid1+" and "+sid2+" share species "+species);
            System.out.println("  "+sid1+" = "+spec1+" http://strgen.org/~jmc/scop-newui/?node="+node1);
            System.out.println("  "+sid2+" = "+spec2+" http://strgen.org/~jmc/scop-newui/?node="+node2);
            System.out.println("  move "+sid1+": java gov.lbl.scop.app.ManualEdit mv "+node1+" "+nodeS2);
            System.out.println("  move "+sid2+": java gov.lbl.scop.app.ManualEdit mv "+node2+" "+nodeS1);
        }
        rs.close();
        if (ok)
            System.out.println("No common species found");
        stmt.close();
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

            // check consistency of proteins in families
            rs = stmt.executeQuery("select id from scop_node where level_id=5 and release_id="+scopReleaseID);
            while (rs.next())
                checkFamily(rs.getInt(1));
            rs.close();

            // check consistency of species in proteins
            rs = stmt.executeQuery("select id from scop_node where level_id=6 and release_id="+scopReleaseID);
            while (rs.next())
                checkProtein(rs.getInt(1));
            rs.close();

            stmt.close();
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
