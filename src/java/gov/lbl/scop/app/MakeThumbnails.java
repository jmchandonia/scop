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
import org.strbio.local.*;
import org.strbio.math.*;
import org.strbio.mol.*;
import org.strbio.util.*;
import org.strbio.IO;
import gov.lbl.scop.util.*;
import gov.lbl.scop.local.LocalSQL;

/**
   creates thumbnails for proteins
*/
public class MakeThumbnails {
    /**
       do 2 nodes have the same number of chains in their parent
       PDB files?
    */
    final public static boolean sameNChains(int node1, int node2) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs;
	
        rs = stmt.executeQuery("select count(distinct(c2.id)) from pdb_chain c1, pdb_chain c2, link_pdb l where l.node_id="+node1+" and l.pdb_chain_id=c1.id and c1.pdb_release_id=c2.pdb_release_id");
        rs.next();
        int nChains1 = rs.getInt(1);
        rs.close();
        rs = stmt.executeQuery("select count(distinct(c2.id)) from pdb_chain c1, pdb_chain c2, link_pdb l where l.node_id="+node2+" and l.pdb_chain_id=c1.id and c1.pdb_release_id=c2.pdb_release_id");
        rs.next();
        int nChains2 = rs.getInt(1);
        rs.close();
        stmt.close();
        return (nChains1==nChains2);
    }

    /**
       copy all thumbnail links from one node to another.
    */
    final public static void copyAllThumbnails(int node1, int node2) throws
        Exception {
        Thumbnail.copyThumb(node1,
                            node2,
                            false,
                            "domain_tiny",
                            "domain_tiny");
        Thumbnail.copyThumb(node1,
                            node2,
                            false,
                            "domain_small",
                            "domain_small");
        Thumbnail.copyThumb(node1,
                            node2,
                            false,
                            "domain_large",
                            "domain_large");
        Thumbnail.copyThumb(node1,
                            node2,
                            false,
                            "chain_small",
                            "chain_small");
        Thumbnail.copyThumb(node1,
                            node2,
                            false,
                            "chain_large",
                            "chain_large");
        Thumbnail.copyThumb(node1,
                            node2,
                            false,
                            "structure_small",
                            "structure_small");
        Thumbnail.copyThumb(node1,
                            node2,
                            false,
                            "structure_large",
                            "structure_large");
        Thumbnail.copyThumb(node1,
                            node2,
                            false,
                            "main",
                            "main");
    }

    /**
       copy thumbnails of scop domain nodes to the higher levels
       of the tree for which the domain is a representative.
    */
    final public static void copyRepThumbnails(int repNodeID,
                                               int levelNodeID) throws
                                                   Exception {
	    Thumbnail.copyThumb(repNodeID,
                            levelNodeID,
                            false,
                            "domain_tiny",
                            "domain_tiny");

	    Thumbnail.copyThumb(repNodeID,
                            levelNodeID,
                            false,
                            "domain_small",
                            "domain_small");
	    
	    Thumbnail.copyThumb(repNodeID,
                            levelNodeID,
                            false,
                            "domain_large",
                            "domain_large");

	    // fill in domain view everywhere:
	    Thumbnail.copyThumb(repNodeID,
                            levelNodeID,
                            false,
                            "domain_small",
                            "chain_small");

	    Thumbnail.copyThumb(repNodeID,
                            levelNodeID,
                            false,
                            "domain_large",
                            "chain_large");

	    Thumbnail.copyThumb(repNodeID,
                            levelNodeID,
                            false,
                            "domain_small",
                            "structure_small");

	    Thumbnail.copyThumb(repNodeID,
                            levelNodeID,
                            false,
                            "domain_large",
                            "structure_large");

	    // make domain view the default
	    Thumbnail.copyThumb(levelNodeID,
                            levelNodeID,
                            false,
                            "domain_tiny",
                            "main");
    }
    
    /**
       copy thumbnails of scop domain nodes to the higher levels
       of the tree for which the domain is a representative.  Must
       be done after the node level thumbnails are completed, and
       after the scop subsets by level are created
    */
    final public static void copyRepThumbnails(int scopReleaseID,
                                               boolean wait) throws
                                                   Exception {
        System.out.println("copying representative structures for release "+scopReleaseID);

        if (wait)
            WaitForJobs.waitFor(7);
	
        // once all nodes are done, copy representative structures'
        // domain views to the nodes of the clades they represent
        Statement stmt = LocalSQL.createStatement();
        Statement stmt2 = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select rep_node_id, level_node_id from scop_subset_level where level_node_id in (select id from scop_node where release_id="+scopReleaseID+")");
        while (rs.next()) {
            int repNodeID = rs.getInt(1);
            int levelNodeID = rs.getInt(2);

            int i = 0;

            // go down the list of potential reps until we find one with a thumbnail
            ResultSet rs2 = stmt2.executeQuery("select main from scop_node_thumbnail where node_id="+repNodeID+" and domain_tiny is not null");
            while (!rs2.next()) {
                rs2.close();
                i++;
                repNodeID = 0;
		
                Vector<SPACI.SPACINode> nodes = MakeSubsets.getSortedNodes(MakeSubsets.descendentsOf(levelNodeID,8));
                nodes = MakeSubsets.removeRejects(nodes);

                if ((nodes==null) || (nodes.size()<=i))
                    break;

                repNodeID = nodes.get(i).nodeID;

                rs2 = stmt2.executeQuery("select main from scop_node_thumbnail where node_id="+repNodeID+" and domain_tiny is not null");
            }
            rs2.close();
	    
            System.out.println("copying representative thumbs to node "+levelNodeID);
	    
            copyRepThumbnails(repNodeID, levelNodeID);
        }
        stmt2.close();
        stmt.close();
    }

    /**
       create thumbnails for a SCOP node
       need pdb-style file first, so wait for it.
    */
    final public static void makeThumbnail(int nodeID,
                                           boolean needsCPU) throws Exception {
	
        System.out.println("Making thumbnails for "+nodeID);
	
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select d.id from astral_domain d, scop_node n, astral_seq s where d.seq_id=s.id and d.node_id=n.id and length(s.seq) > 0 and d.source_id=2 and (d.style_id=1 or d.style_id=3) and n.id="+nodeID);
        while (rs.next()) {
            int domainID = rs.getInt(1);
            WaitForJobs.waitFor(17,domainID,0);
        }
        rs.close();
        stmt.close();
	
        Thumbnail.makeForDomain(nodeID,
                                false,
                                "domain",
                                needsCPU);

        // only need to create for chain if domains
        // don't span entire chain
        // also, make structures that show ligands
        boolean ligand = false;
        String comments = LocalSQL.getComments(nodeID);
        if ((comments != null) && (comments.indexOf("complexed with") != -1))
            ligand = true;
        if ((ASTRAL.domainIsEntireChain(nodeID)) &&
            (!ligand)) {
            System.out.println("Copying domain thumbnails to chain for "+nodeID);
            Thumbnail.copyThumb(nodeID,
                                nodeID,
                                false,
                                "domain_small",
                                "chain_small");

            Thumbnail.copyThumb(nodeID,
                                nodeID,
                                false,
                                "domain_large",
                                "chain_large");
        }
        else
            Thumbnail.makeForDomain(nodeID,
                                    false,
                                    "chain",
                                    needsCPU);

        // only need to create for PDB structure
        // if chain structure isn't whole thing
        if (ASTRAL.domainHasAllChains(nodeID)) {
            System.out.println("Copying chain thumbnails to structure for "+nodeID);
            Thumbnail.copyThumb(nodeID,
                                nodeID,
                                false,
                                "chain_small",
                                "structure_small");

            Thumbnail.copyThumb(nodeID,
                                nodeID,
                                false,
                                "chain_large",
                                "structure_large");
        }
        else 
            Thumbnail.makeForDomain(nodeID,
                                    false,
                                    "structure",
                                    needsCPU);

        // make the chain view the main view
        Thumbnail.copyThumb(nodeID,
                            nodeID,
                            false,
                            "chain_small",
                            "main");

        // link to all nodes from later releases w/ same thumbnail
        copyForward(nodeID);
    }

    /**
       create thumbnails for an ASTEROID
    */
    final public static void makeThumbnailASTEROID(int asteroidID,
                                                   boolean needsCPU) throws Exception {
        WaitForJobs.waitFor(18,asteroidID,0);
	
        Thumbnail.makeForDomain(asteroidID,
                                true,
                                "domain",
                                needsCPU);

        // only need to create for chain if domains
        // don't span entire chain
        if (ASTRAL.domainIsEntireChainASTEROID(asteroidID)) {
            Thumbnail.copyThumb(asteroidID,
                                asteroidID,
                                true,
                                "domain_small",
                                "chain_small");
	    
            Thumbnail.copyThumb(asteroidID,
                                asteroidID,
                                true,
                                "domain_large",
                                "chain_large");
        }
        else
            Thumbnail.makeForDomain(asteroidID,
                                    true,
                                    "chain",
                                    needsCPU);

        // only need to create for PDB structure
        // if chain structure isn't whole thing
        if (ASTRAL.domainHasAllChainsASTEROID(asteroidID)) {
            Thumbnail.copyThumb(asteroidID,
                                asteroidID,
                                true,
                                "chain_small",
                                "structure_small");
	    
            Thumbnail.copyThumb(asteroidID,
                                asteroidID,
                                true,
                                "chain_large",
                                "structure_large");
        }
        else 
            Thumbnail.makeForDomain(asteroidID,
                                    true,
                                    "structure",
                                    needsCPU);

        // make the chain view the main view
        Thumbnail.copyThumb(asteroidID,
                            asteroidID,
                            true,
                            "chain_small",
                            "main");
    }

    /**
       check that all thumbnail files actually exist
    */
    final public static void purgeDeletedThumbs() throws Exception {
        Statement stmt = LocalSQL.createStatement();
        Statement stmt2 = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select id, image_path from thumbnail");
        while (rs.next()) {
            int id = rs.getInt(1);
            String path = rs.getString(2);
            File f = new File(path);
            if (!f.exists())
                stmt2.executeUpdate("delete from thumbnail where id="+id);
        }
        rs.close();
        stmt.close();
        stmt2.close();
    }

    /**
       copy thumbnails forward to subsequent versions that have
       the same pdbstyle file and number of chains.
    */
    final public static void copyForward(int nodeID) throws Exception {

        Statement stmt = LocalSQL.createStatement();

        ResultSet rs = stmt.executeQuery("select n2.id from scop_node n1, scop_node n2, scop_node_pdbstyle p where n2.id=p.node_id and n2.sunid=n1.sunid and n2.release_id>n1.release_id and p.last_update_id=n1.release_id and n2.id not in (select node_id from scop_node_thumbnail where main is not null) and n1.id="+nodeID+" order by n2.release_id asc");
        // overwrites newer ones:  ResultSet rs = stmt.executeQuery("select n2.id from scop_node n1, scop_node n2, scop_node_pdbstyle p where n2.id=p.node_id and n2.sunid=n1.sunid and n2.release_id>n1.release_id and p.last_update_id=n1.release_id and n1.id="+nodeID+" order by n2.release_id asc");
        while (rs.next()) {
            int newNodeID = rs.getInt(1);
            if (sameNChains(nodeID,newNodeID))
                copyAllThumbnails(nodeID,newNodeID);
        }
        rs.close();
        stmt.close();
    }
    
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            Statement stmt2 = LocalSQL.createStatement();
            ResultSet rs, rs2;

            if (argv[0].startsWith("A")) {
                int asteroidID = StringUtil.atoi(argv[0],1);
                makeThumbnailASTEROID(asteroidID,
                                      true);
                System.exit(0);
            }
            else if (argv[0].startsWith("N")) {
                int nodeID = StringUtil.atoi(argv[0],1);
                makeThumbnail(nodeID,true);
                System.exit(0);
            }
            else if (argv[0].equals("rep")) {
                int scopReleaseID = LocalSQL.lookupSCOPRelease(argv[1]);
                copyRepThumbnails(scopReleaseID, false);
                System.exit(0);
            }
            else if (argv[0].equals("purge")) {
                purgeDeletedThumbs();
                System.exit(0);
            }
	    
            int scopReleaseID = LocalSQL.lookupSCOPRelease(argv[0]);
            if (scopReleaseID==0)
                throw new Exception("SCOP version not found: "+argv[0]);

            // copy thumbnails from previous release, where possible
            rs = stmt.executeQuery("select n1.id, n2.id, n1.sid from scop_node n1, scop_node n2, scop_node_thumbnail t, scop_node_pdbstyle s where n1.release_id=n2.release_id-1 and n2.release_id="+scopReleaseID+" and n1.sid=n2.sid and n1.sunid=n2.sunid and n1.level_id=8 and n2.level_id=8 and n1.id=t.node_id and t.structure_large is not null and t.main is not null and s.node_id=n2.id and s.last_update_id<=n1.release_id and n2.id not in (select node_id from scop_node_thumbnail where structure_large is not null and main is not null)");
            while (rs.next()) {
                int oldNodeID = rs.getInt(1);
                int nodeID = rs.getInt(2);
                System.out.println("copying old thumbnails for "+rs.getString(3));

                if (sameNChains(oldNodeID, nodeID))
                    copyAllThumbnails(oldNodeID, nodeID);
            }

            // finished domains must have both structure_large and main
            rs = stmt.executeQuery("select id, sid from scop_node where release_id="+scopReleaseID+" and level_id=8 and id not in (select node_id from scop_node_thumbnail where structure_large is not null and main is not null) and id in (select node_id from scop_node_pdbstyle)");
            while (rs.next()) {
                int nodeID = rs.getInt(1);
                System.out.println("starting thumbnail job for "+rs.getString(2));

                LocalSQL.newJob(7,
                                nodeID,
                                null,
                                stmt2);
            }
            LocalSQL.newJob(8,
                            scopReleaseID,
                            null,
                            stmt);
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
