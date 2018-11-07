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
import gov.lbl.scop.util.RAF;
import gov.lbl.scop.util.ASTEROIDS;
import gov.lbl.scop.local.LocalSQL;

/**
   Perform manual edits - moves, deletions, replacement of obs
*/
public class ManualEdit {
    /**
       "undelete" a node.  The nodeID must exist in the prior
       release, and a new node with the same description and parent
       will be created in the current release.  Any history showing the
       node was deleted will be removed.  Any comments will be copied
       from old to new release.
    */
    final public static int undeleteNode(int oldNodeID) throws Exception {
        Statement stmt = LocalSQL.createStatement();

        int scopReleaseID = LocalSQL.getLatestSCOPRelease(false);
	
        int lastPublicRelease = LocalSQL.getLatestSCOPRelease(true);
        if (scopReleaseID!=lastPublicRelease+1)
            throw new Exception("can only undelete node into non-public release");

        ResultSet rs = stmt.executeQuery("select description, parent_node_id, release_id, curation_type_id from scop_node where id="+oldNodeID);
        rs.next();
        String description = rs.getString(1);
        int oldParentID = rs.getInt(2);
        int releaseID = rs.getInt(3);
        int curationTypeID = rs.getInt(4);
        rs.close();

        if (releaseID != lastPublicRelease)
            throw new Exception("can only undelete node from last public release, into non-public version");

        int oldParentSun = LocalSQL.getSunid(oldParentID);
        if (oldParentSun == -1)
            throw new Exception("Failed to find parent in old release");
	
        int newParentID = LocalSQL.lookupNodeBySunid(oldParentSun,
                                                     scopReleaseID);
        if (newParentID <= 0) {
            // see if it moved/merged
            rs = stmt.executeQuery("select new_node_id from scop_history where old_node_id="+oldParentID+" and release_id="+scopReleaseID+" and (change_type_id=6 or change_type_id=4)");
            if (rs.next())
                newParentID = rs.getInt(1);
            rs.close();
        }

        if (newParentID <= 0)
            throw new Exception("Failed to find parent sunid in new release");

        int newNodeID = newNode(newParentID, description);

        // must have same curation type
        stmt.executeUpdate("update scop_node set curation_type_id="+curationTypeID+" where id="+newNodeID);

        // copy all old comments
        rs = stmt.executeQuery("select description from scop_comment where node_id="+oldNodeID);
        while (rs.next()) {
            String comment = rs.getString(1);
            addComment(newNodeID, comment);
        }
        rs.close();

        // delete history of deletion
        stmt.executeUpdate("delete from scop_history where old_node_id="+oldNodeID+" and release_id="+scopReleaseID+" and change_type_id<4");	

        stmt.close();

        return newNodeID;
    }
    
    /**
       delete a node.  If leaveHistory is true, marks it as deleted
       (obsPDB) in history.
    */
    final public static void deleteNode(int nodeID,
                                        boolean leaveHistory) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        stmt.executeUpdate("delete from scop_comment where node_id="+nodeID);
        stmt.executeUpdate("delete from astral_domain where node_id="+nodeID);
        stmt.executeUpdate("delete from link_pdb where node_id="+nodeID);
        stmt.executeUpdate("delete from link_uniprot where node_id="+nodeID);
        stmt.executeUpdate("delete from link_pfam where node_id="+nodeID);
        stmt.executeUpdate("delete from link_pubmed where node_id="+nodeID);
        stmt.executeUpdate("delete from link_species where node_id="+nodeID);
        stmt.executeUpdate("delete from scop_history where new_node_id="+nodeID);
        stmt.executeUpdate("delete from scop_history where old_node_id="+nodeID);
        if (leaveHistory) {
            ResultSet rs = stmt.executeQuery("select sunid, release_id from scop_node where id="+nodeID);
            if (rs.next()) {
                int sunid = rs.getInt(1);
                int scopReleaseID = rs.getInt(2);
                rs.close();
                if (sunid != 0) {
                    int oldNode = LocalSQL.lookupNodeBySunid(sunid,
                                                             scopReleaseID-1);
                    if (oldNode > 0) {
                        stmt.executeUpdate("insert into scop_history values (null, "+oldNode+", null, "+scopReleaseID+", 2, now())");
                    }
                }
            }
            else
                rs.close();
        }
        stmt.executeUpdate("delete from scop_node where id="+nodeID);
        stmt.close();
    }

    /**
       Moves a node to a new parent (must be in same SCOP release,
       one level up).  Fixes sccs if inconsistent, but only if the
       node is below the family level.  Does not allow move that
       would result in inconsistent sccs.
    */
    final public static void moveNode(int nodeID,
                                      int newParentID,
                                      boolean leaveHistory) throws Exception {
        Statement stmt = LocalSQL.createStatement();

        ResultSet rs = stmt.executeQuery("select sunid, release_id, parent_node_id, level_id, sccs from scop_node where id="+nodeID);
        if (!rs.next())
            throw new Exception("Node not found: "+nodeID);
        int sunid = rs.getInt(1);
        int scopReleaseID = rs.getInt(2);
        int oldParentID = rs.getInt(3);
        int levelID = rs.getInt(4);
        String oldSCCS = rs.getString(5);
        rs.close();

        if (oldParentID==newParentID) {
            stmt.close();
            return;
        }

        rs = stmt.executeQuery("select release_id, level_id, sccs from scop_node where id="+newParentID);
        if (!rs.next())
            throw new Exception("New parent node not found: "+newParentID);
        if (rs.getInt(1) != scopReleaseID)
            throw new Exception("New parent must be from same SCOP release");
        if (rs.getInt(2) != levelID-1)
            throw new Exception("New parent must be from adjacent SCOP level");
        String newParentSCCS = rs.getString(3);
        if ((levelID < 6) && (oldSCCS.indexOf(newParentSCCS+".")!=0)) {
            System.out.println("Warning: moving "+oldSCCS+" under parent "+newParentSCCS);
            newParentSCCS = "";
        }
        rs.close();

        stmt.executeUpdate("update scop_node set parent_node_id="+newParentID+" where id="+nodeID);
        if (!oldSCCS.equals(newParentSCCS)) {
            stmt.executeUpdate("update scop_node set sccs=\""+newParentSCCS+"\" where id="+nodeID);
            if (levelID<=7) {
                // update all kids
                Vector<Integer> kids = LocalSQL.findChildren(nodeID,0);
                for (Integer i : kids)
                    stmt.executeUpdate("update scop_node set sccs=\""+newParentSCCS+"\" where id="+i);
            }
        }

        if ((leaveHistory) && (sunid > 0)) {
            int oldNodeID = LocalSQL.lookupNodeBySunid(sunid,
                                                       scopReleaseID-1);
            if (oldNodeID > 0) {
                // check whether it really moved
                int oldParentNode = LocalSQL.findParent(oldNodeID,
                                                        levelID-1);
                int oldParentSun = LocalSQL.getSunid(oldParentNode);
                int newParentSun = LocalSQL.getSunid(newParentID);
                if (newParentSun != oldParentSun) {
                    stmt.executeUpdate("insert into scop_history values (null, "+oldNodeID+", "+nodeID+", "+scopReleaseID+", 6, now())");
                }
            }
        }

        stmt.close();
    }

    /**
       Merges a node into another (must be in same SCOP release, same
       level).  Moves all children under new parent, then deletes the
       1st node.
    */
    final public static void mergeNode(int nodeID,
                                       int newNodeID,
                                       boolean leaveHistory) throws Exception {
        Statement stmt = LocalSQL.createStatement();

        ResultSet rs = stmt.executeQuery("select sunid, release_id, level_id from scop_node where id="+nodeID);
        if (!rs.next())
            throw new Exception("Node not found: "+nodeID);
        int sunid = rs.getInt(1);
        int scopReleaseID = rs.getInt(2);
        int levelID = rs.getInt(3);
        rs.close();

        rs = stmt.executeQuery("select release_id, level_id from scop_node where id="+newNodeID);
        if (!rs.next())
            throw new Exception("Node to merge with not found: "+newNodeID);
        if (rs.getInt(1) != scopReleaseID)
            throw new Exception("Node to merge with must be from same SCOP release");
        if (rs.getInt(2) != levelID)
            throw new Exception("Node to merge with must be from same SCOP level");
        rs.close();

        stmt.executeUpdate("update scop_node set parent_node_id="+newNodeID+" where parent_node_id="+nodeID);
        if ((leaveHistory) && (sunid > 0)) {
            int oldNodeID = LocalSQL.lookupNodeBySunid(sunid,
                                                       scopReleaseID-1);
            if (oldNodeID > 0)
                stmt.executeUpdate("insert into scop_history values (null, "+oldNodeID+", "+newNodeID+", "+scopReleaseID+", 4, now())");
        }
        deleteNode(nodeID,false);

        stmt.close();
    }
    
    /**
       Edits the description of a node, sets curation type to manual
    */
    final public static void editNode(int nodeID,
                                      String newDesc) throws Exception {
        PreparedStatement stmt = LocalSQL.prepareStatement("update scop_node set description=?, curation_type_id=1 where id=?");
        stmt.setString(1,newDesc);
        stmt.setInt(2,nodeID);
        stmt.executeUpdate();
        // fix pdb links changed by the edit
        MakeLinks.linkPDB(nodeID);
        stmt.close();
    }

    /**
       Adds a new child node.  Will assign null SCCS if it can't
       determine from the parent.

       Some checks to try to eliminate human errors
    */
    final public static int newNode(int parentNodeID,
                                    String description) throws Exception {
        int levelID = LocalSQL.getLevel(parentNodeID);
        levelID++;
        String sccs = null;
        if (levelID > 5)
            sccs = LocalSQL.getSCCS(parentNodeID);
        int scopReleaseID = LocalSQL.getSCOPRelease(parentNodeID);

        // make it easier to paste in taxids from the website
        if ((description.indexOf("[TaxId:") > -1) &&
            (description.indexOf("[TaxId: ") == -1)) {
            description = StringUtil.replace(description,
                                             "[TaxId:",
                                             "[TaxId: ");
        }

        int nodeID = LocalSQL.lookupNodeByDescription(description,
                                                      scopReleaseID,
                                                      parentNodeID);
        if (nodeID != 0)
            throw new Exception("Node already in SCOP: "+nodeID);

        if ((levelID!=7) && (description.indexOf("[TaxId:") > -1))
            throw new Exception("TaxID only applicable to species level; must be an error");

        if ((levelID==8) && (description.indexOf(" ") != 4))
            throw new Exception("This does not look properly formatted for a domain entry");
	
        nodeID = LocalSQL.createNode(0,
                                     sccs,
                                     null,
                                     description,
                                     levelID,
                                     parentNodeID,
                                     scopReleaseID,
                                     1);

        // make appropriate links
        if (levelID==7)
            MakeSpecies.processNode(nodeID, description);
        if (levelID==8)
            MakeLinks.linkPDB(nodeID);
	
        return nodeID;
    }

    /**
       Split a node into several, with same level and parent.
       Moves all children (if any) of old node to first new node,
       then deletes the old node.  Returns the ids of the new nodes.
    */
    final public static int[] splitNode(int nodeID,
                                        String[] descriptions,
                                        boolean leaveHistory) throws Exception {
        if ((descriptions==null) ||
            (descriptions.length==0))
            return null;
	
        Statement stmt = LocalSQL.createStatement();

        int levelID = LocalSQL.getLevel(nodeID);
        int sunid = LocalSQL.getSunid(nodeID);
        int scopReleaseID = LocalSQL.getSCOPRelease(nodeID);
        int parentNodeID = LocalSQL.findParent(nodeID, levelID-1);

        int[] rv = new int[descriptions.length];
        for (int i=0; i<descriptions.length; i++)
            rv[i] = newNode(parentNodeID, descriptions[i]);

        stmt.executeUpdate("update scop_node set parent_node_id="+rv[0]+" where parent_node_id="+nodeID);
        if ((leaveHistory) && (sunid > 0)) {
            int oldNodeID = LocalSQL.lookupNodeBySunid(sunid,
                                                       scopReleaseID-1);
            for (int i=0; i<descriptions.length; i++)
                stmt.executeUpdate("insert into scop_history values (null, "+oldNodeID+", "+rv[i]+", "+scopReleaseID+", 5, now())");
        }
        deleteNode(nodeID,false);

        stmt.close();
        return rv;
    }

    /**
       rewrite history!  First argument is old node ID, 2nd argument
       is 1 or more new nodes, separated by spaces, 3rd argument is a
       history code (e.g., mov, spl) or numeric history type id.  The
       new nodes can be described by sids, sunids, or node ids (in the
       format Nxxxx), or 0 for null.
    */
    final public static void rewriteHistory(int oldNodeID,
                                            String newNodeList,
                                            String changeType) throws Exception {
        int changeTypeID = LocalSQL.lookupHistoryTypeAbbrev(changeType);
        if (changeTypeID==0)
            changeTypeID = StringUtil.atoi(changeType);
        if (changeTypeID==0)
            throw new IllegalArgumentException("invalid change type "+changeType);

        int scopReleaseID = LocalSQL.getSCOPRelease(oldNodeID);
        scopReleaseID++;

        int lastRelease = LocalSQL.getLatestSCOPRelease(false);
        if (scopReleaseID!=lastRelease)
            throw new Exception("can't edit an old release");

        int lastPublicRelease = LocalSQL.getLatestSCOPRelease(true);
        if (scopReleaseID==lastPublicRelease)
            throw new Exception("can't edit a public release");

        Statement stmt = LocalSQL.createStatement();
        stmt.executeUpdate("delete from scop_history where old_node_id="+oldNodeID+" and release_id="+scopReleaseID);

        String[] newNodes = newNodeList.split(" ");
        for (String newNode : newNodes) {
            int newNodeID = LocalSQL.lookupNode(newNode, scopReleaseID);
            stmt.executeUpdate("insert into scop_history values (null, "+oldNodeID+", "+(newNodeID==0 ? "null" : newNodeID)+", "+scopReleaseID+", "+changeTypeID+", now())");
        }
        stmt.close();
    }

    /**
       Add comment  First argument is node ID, 2nd is comment to add.
       Will not add existing comment.
    */
    final public static void addComment(int nodeID,
                                        String comment) throws Exception {
        int scopReleaseID = LocalSQL.getSCOPRelease(nodeID);

        int lastRelease = LocalSQL.getLatestSCOPRelease(false);
        if (scopReleaseID!=lastRelease)
            throw new Exception("can't edit an old release");

        int lastPublicRelease = LocalSQL.getLatestSCOPRelease(true);
        if (scopReleaseID==lastPublicRelease)
            throw new Exception("can't edit a public release");

        PreparedStatement stmt = LocalSQL.prepareStatement("select id from scop_comment where node_id=? and description=?");
        stmt.setInt(1,nodeID);
        stmt.setString(2,comment);
        ResultSet rs = stmt.executeQuery();
        if (rs.next()) {
            rs.close();
            stmt.close();
            return;
        }
        rs.close();
        stmt.close();
        stmt = LocalSQL.prepareStatement("insert into scop_comment values (null, ?, ?, ?)");
        stmt.setInt(1,nodeID);
        stmt.setString(2,comment);
        int isAuto = 0;
        if (comment.startsWith("automat"))
            isAuto = 1;
        stmt.setInt(3,isAuto);
        stmt.executeUpdate();
        stmt.close();
        ParseDirCom.linkPfam(nodeID,comment);
        ParseDirCom.linkPfamB(nodeID,comment);
        ParseDirCom.linkUniprot(nodeID,comment);
        ParseDirCom.linkPubMed(nodeID,comment);
        return;
    }

    /**
       Remove comment  First argument is node ID, 2nd is start of comment(s)
       to remove.
    */
    final public static void rmComment(int nodeID,
                                       String commentPrefix) throws Exception {
        int scopReleaseID = LocalSQL.getSCOPRelease(nodeID);

        int lastRelease = LocalSQL.getLatestSCOPRelease(false);
        if (scopReleaseID!=lastRelease)
            throw new Exception("can't edit an old release");

        int lastPublicRelease = LocalSQL.getLatestSCOPRelease(true);
        if (scopReleaseID==lastPublicRelease)
            throw new Exception("can't edit a public release");

        PreparedStatement stmt = LocalSQL.prepareStatement("delete from scop_comment where node_id=? and description like ?");
        stmt.setInt(1,nodeID);
        stmt.setString(2,commentPrefix+"%");
        stmt.executeUpdate();
        stmt.close();
        return;
    }
    
    /**
       replace domains from an obsolete node with domains from
       its replacement.  Checks that RAF body is the same.
    */
    final public static void replaceObs(int pdbEntryID) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        Statement stmt2 = LocalSQL.createStatement();
        ResultSet rs, rs2;

        int scopReleaseID = LocalSQL.getLatestSCOPRelease(false);
	
        int lastPublicRelease = LocalSQL.getLatestSCOPRelease(true);
        if (scopReleaseID==lastPublicRelease)
            throw new Exception("can't edit a public release");
	
        rs = stmt.executeQuery("select code from pdb_entry where id="+pdbEntryID);
        if (!rs.next())
            throw new Exception ("PDB entry not found: "+pdbEntryID);
        String oldCode = rs.getString(1);
        rs.close();

        System.out.println("Replacing domains from "+oldCode);

        rs = stmt.executeQuery("select id, code from pdb_entry where id in (select obsoleted_by from pdb_entry where id="+pdbEntryID+")");
        if (!rs.next())
            throw new Exception ("PDB entry has no replacement: "+oldCode);
        int newEntryID = rs.getInt(1);
        String newCode = rs.getString(2);

        rs = stmt.executeQuery("select sid, id, description from scop_node where release_id="+scopReleaseID+" and level_id=8 and description like \""+oldCode+"%\"");
        while (rs.next()) {
            String description = rs.getString(3);
            int id = rs.getInt(2);
            System.out.println("obs entry "+rs.getString(1)+" "+id+" "+description);
            rs2 = stmt2.executeQuery("select sunid from scop_node where id in (select parent_node_id from scop_node where id="+id+")");
            rs2.next();
            int sunid = rs2.getInt(1);
            rs2.close();
            description = StringUtil.replace(description,oldCode,newCode);
            System.out.println("java gov.lbl.scop.app.ManualEdit new "+sunid+" \""+description+"\"");
        }

        // make sure there are no domains already in SCOP
        rs = stmt.executeQuery("select sid, id, description from scop_node where release_id="+scopReleaseID+" and level_id=8 and description like \""+newCode+"%\"");
        boolean exists = false;
        while(rs.next()) {
            exists = true;
            System.out.println("new entry "+rs.getString(1)+" "+rs.getInt(2)+" "+rs.getString(3));
        }

        int newReleaseID = FreezeRAF.findPDBRelease(newEntryID,
                                                    scopReleaseID);
        if (newReleaseID==0)
            throw new Exception ("Replacement PDB entry after freeze date: "+newEntryID);

        // get all domains linked to old entry
        rs = stmt.executeQuery("select n.id, n.sid, n.description from scop_node n, pdb_entry e, pdb_release r, pdb_chain c, link_pdb l where l.node_id=n.id and l.pdb_chain_id=c.id and c.pdb_release_id=r.id and r.pdb_entry_id="+pdbEntryID+" and n.level_id=8 and n.release_id="+scopReleaseID+" group by n.id");
        while (rs.next()) {
            int nodeID = rs.getInt(1);
            String oldSid = rs.getString(2);
            String description = rs.getString(3);
            int nMatchingDomains = 0;
            rs2 = stmt2.executeQuery("select r1.line, r2.line from raf r1, raf r2, pdb_chain c1, pdb_chain c2, link_pdb l1 where l1.node_id="+nodeID+" and l1.pdb_chain_id=c1.id and r1.pdb_chain_id=c1.id and c2.pdb_release_id="+newReleaseID+" and c2.chain=c1.chain and r2.pdb_chain_id=c2.id and r1.last_release_id is null");
            while (rs2.next()) {
                String raf1 = rs2.getString(1);
                String raf2 = rs2.getString(2);
                if (raf1.substring(38).equals(raf2.substring(38)))
                    nMatchingDomains++;
                else
                    throw new Exception("RAF lines don't match for node "+nodeID+" "+oldSid+" "+newReleaseID);
            }
            rs2.close();
            rs2 = stmt2.executeQuery("select count(*) from link_pdb where node_id="+nodeID);
            rs2.next();
            if (rs2.getInt(1) != nMatchingDomains)
                throw new Exception("all domains don't match for node "+nodeID+" "+oldSid+" "+newReleaseID+" "+rs2.getInt(1)+" vs "+nMatchingDomains);
            rs2.close();

            // make new description
            description = StringUtil.replace(description,oldCode,newCode);
            if (exists) {
                rs2 = stmt2.executeQuery("select id from scop_node where release_id="+scopReleaseID+" and level_id=8 and description=\""+description+"\"");
                if (!rs2.next()) {
                    System.out.println("Check and replace "+oldSid+":  java gov.lbl.scop.app.ManualEdit edit N"+nodeID+" \""+description+"\"");
                }
                rs2.close();
            }
            else {
                System.out.println("Replacing: "+oldSid+" "+nodeID+" \""+description+"\"");
                editNode(nodeID,description);
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

            if (argv.length < 2) {
                System.out.println("syntax:  java ManualEdit rm|delete|mv|merge|split|flush|obs|edit|comment|rmcomment nodeID|PDBCode [newdata]");
                System.out.println("  nodeID = sunid, sid, or Nxxxxxxx for raw database ID");
                System.exit(0);
            }

            if (argv[0].equals("obs")) {
                rs = stmt.executeQuery("select id from pdb_entry where code=\""+argv[1]+"\"");
                if (rs.next())
                    replaceObs(rs.getInt(1));
                else
                    throw new Exception("PDB entry "+argv[1]+" not found");
                System.exit(0);
            }
	    
            int nodeID = 0;
            int scopReleaseID = LocalSQL.getLatestSCOPRelease(false);

            int lastPublicRelease = LocalSQL.getLatestSCOPRelease(true);
            if (scopReleaseID==lastPublicRelease)
                System.out.println("Warning - changing a public release");

            if (argv[0].equals("flush")) {
                int flushID = LocalSQL.lookupSCOPRelease(argv[1]);
                if (flushID != scopReleaseID)
                    throw new Exception("Can't modify old release");
            }
            else if (argv[0].equals("undelete")) {
                int oldReleaseID = LocalSQL.lookupSCOPRelease(argv[1]);
                if (oldReleaseID != lastPublicRelease)
                    throw new Exception("Can't undelete release");
                rs = stmt.executeQuery("select old_node_id from scop_history where release_id="+scopReleaseID+" and change_type_id=2");
                while (rs.next())
                    undeleteNode(rs.getInt(1));
                rs.close();
                stmt.close();
                System.exit(0);
            }
            else {
                if (argv[0].equals("history"))
                    scopReleaseID--;
                nodeID = LocalSQL.lookupNode(argv[1],scopReleaseID);
                if (nodeID == 0)
                    throw new Exception("Error - node "+argv[1]+" not found - use sunid, sid, or Nxxxx for node ID");
                if (nodeID == -1)
                    throw new Exception("Error - node "+argv[1]+" is ambiguous");
            }

            if (argv[0].equals("delete") ||
                argv[0].equals("rm")) {
                deleteNode(nodeID, true);
            }
            else if (argv[0].equals("mv")) {
                int newParent = LocalSQL.lookupNode(argv[2],scopReleaseID);
                moveNode(nodeID, newParent, true);
            }
            else if (argv[0].equals("new")) {
                int rv = newNode(nodeID, argv[2]);
                System.out.println(rv);
            }
            else if (argv[0].equals("edit")) {
                editNode(nodeID, argv[2]);
            }
            else if (argv[0].equals("merge")) {
                int newNodeID = LocalSQL.lookupNode(argv[2],scopReleaseID);
                mergeNode(nodeID, newNodeID, true);
            }
            else if (argv[0].equals("split")) {
                int[] rv = splitNode(nodeID,
                                     Arrays.copyOfRange(argv, 2, argv.length),
                                     true);
                for (int i : rv)
                    System.out.println(i);
            }
            else if (argv[0].equals("history")) {
                rewriteHistory(nodeID, argv[2], argv[3]);
            }
            else if (argv[0].equals("comment")) {
                addComment(nodeID, argv[2]);
            }
            else if (argv[0].equals("rmcomment")) {
                rmComment(nodeID, argv[2]);
            }
            else if (argv[0].equals("flush")) {
                rs = stmt.executeQuery("select id, sunid from scop_node where release_id="+scopReleaseID+" and level_id<8 and id not in (select parent_node_id from scop_node where level_id>1 and release_id="+scopReleaseID+")");
                while (rs.next()) {
                    int id = rs.getInt(1);
                    int sunid = rs.getInt(2);
                    if (sunid > 0) {
                        // check that sunid was in last release
                        rs2 = stmt2.executeQuery("select id from scop_node where release_id="+(scopReleaseID-1)+" and sunid="+sunid);
                        if (!rs2.next())
                            sunid = 0;
                        rs2.close();
                    }
                    if (sunid==0) {
                        System.out.println("deleting node "+id);
                        deleteNode(id, true);
                    }
                    else
                        System.out.println("check node: http://strgen.org/~jmc/scop-boot/?node="+id);
                }
            }
            stmt.close();
            stmt2.close();
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
