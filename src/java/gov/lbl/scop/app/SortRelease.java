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
   Sort scop nodes within a release by SCCS, then by AEROSPACI score.
   Put auto-assigned clades last.

   Can sort all old releases, or else latest release.  Now edits
   public releases, since we want to do that just prior to copying an
   old release to a new one.
*/
public class SortRelease {
    /**
       moves a node, then its children.  returns next new node id available
    */
    final public static int moveNodes(int oldNodeID, int newNodeID) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs;

        rs = stmt.executeQuery("select level_id, release_id from scop_node where id="+oldNodeID);
        rs.next();
        int levelID = rs.getInt(1);
        int scopReleaseID = rs.getInt(2);

        stmt.executeUpdate("update scop_node set id="+newNodeID+" where id="+oldNodeID);
        if (levelID < 8)
            stmt.executeUpdate("update scop_node set parent_node_id="+newNodeID+" where parent_node_id="+oldNodeID);
        newNodeID++;
	
        // update all children, in order
        if (levelID < 8) {
            String query = "select id, description from scop_node where parent_node_id="+(newNodeID-1);
            if (levelID==1)
                query += " order by sccs asc";
            else if (levelID < 5)
                query += " order by CONVERT(SUBSTRING_INDEX(sccs, '.', -1), UNSIGNED) asc";
            else if (levelID < 7)
                query += " order by upper(description) asc";
            else {
                query = "select distinct(n.id), n.description from scop_node n, link_pdb l, pdb_chain c, pdb_release r left join aerospaci a on (a.pdb_entry_id=r.pdb_entry_id and a.release_id="+scopReleaseID+") where n.parent_node_id="+(newNodeID-1)+" and l.node_id=n.id and l.pdb_chain_id=c.id and c.pdb_release_id=r.id order by a.aerospaci desc, n.sid asc";
            }
            rs = stmt.executeQuery(query);
            Vector<Integer> saveForLast = new Vector<Integer>();
            while (rs.next()) {
                int nodeID = rs.getInt(1);
                String description = rs.getString(2);
                if (description.equals("automated matches")) {
                    saveForLast.add(new Integer(nodeID));
                }
                else {
                    System.out.println("moving "+nodeID+" "+newNodeID+" "+description);
                    newNodeID = moveNodes(nodeID, newNodeID);
                }
            }
            rs.close();
            if (saveForLast.size() > 0) {
                for (Integer nodeID : saveForLast)
                    newNodeID = moveNodes(nodeID.intValue(), newNodeID);
            }
        }
        stmt.close();
        return newNodeID;
    }
    
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            ResultSet rs;

            int scopReleaseID = LocalSQL.lookupSCOPRelease(argv[0]);
            if (argv[0].equals("all"))
                scopReleaseID = -1;

            if (scopReleaseID==0)
                throw new Exception("Syntax:  SortRelease [all|version_string]");

            if (scopReleaseID > 0) {
                rs = stmt.executeQuery("select max(release_id) from scop_node");
                rs.next();
                int scopMaxReleaseID = rs.getInt(1);
                rs.close();
                if (scopReleaseID != scopMaxReleaseID)
                    throw new Exception("can't edit anything but last release");
            }

            // check that all nodes are higher than any other release
            String query = "select min(id), max(id) from scop_node";
            if (scopReleaseID>0)
                query += " where release_id="+scopReleaseID;
            rs = stmt.executeQuery(query);
            rs.next();
            int minID = rs.getInt(1);
            int maxID = rs.getInt(2);
            int nOffset = maxID-minID+1;

            int newMinID = 1;
            if (scopReleaseID > 0) {
                rs = stmt.executeQuery("select max(id) from scop_node where release_id!="+scopReleaseID);
                rs.next();
                int maxOldID = rs.getInt(1);
                if (minID <= maxOldID)
                    throw new Exception("Can't unscramble SCOP table");
                newMinID = maxOldID+1;
            }

            // work around mysql bug #5103 by temporarily disabling keys
            stmt.executeUpdate("alter table scop_node drop foreign key scop_node_ibfk_3");

            // move all nodes out of the way
            query = "update scop_node set id=id+"+nOffset;
            if (scopReleaseID > 0)
                query += " where release_id="+scopReleaseID;
            stmt.executeUpdate(query);
            // because of mysql bug (above), need to cascade parent
            query = "update scop_node set parent_node_id=parent_node_id+"+nOffset;
            if (scopReleaseID > 0)
                query += " where release_id="+scopReleaseID;
            stmt.executeUpdate(query);

            // move each release of SCOP
            int minReleaseID = scopReleaseID;
            int maxReleaseID = scopReleaseID;
            if (scopReleaseID==-1) {
                rs = stmt.executeQuery("select min(id), max(id) from scop_release");
                rs.next();
                minReleaseID = rs.getInt(1);
                maxReleaseID = rs.getInt(2);
            }

            for (scopReleaseID=minReleaseID;
                 scopReleaseID<=maxReleaseID;
                 scopReleaseID++) {
                System.out.println("moving SCOP release "+scopReleaseID);
		
                rs = stmt.executeQuery("select id from scop_node where release_id="+scopReleaseID+" and level_id=1 and sunid=0");
                rs.next();
                int scopRootID = rs.getInt(1);

                // move nodes from the top down
                newMinID = moveNodes(scopRootID, newMinID);
            }

            // re-enable the constraint, dropped above
            stmt.executeUpdate("alter table scop_node add CONSTRAINT `scop_node_ibfk_3` FOREIGN KEY (`parent_node_id`) REFERENCES `scop_node` (`id`) ON UPDATE CASCADE");

            // re-index everything
            MakeIndex.rebuildIndex();
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
