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
   Calculate stable sccs for a release, based on a
   previous release.  Stable sunid must have already been assigned.
*/
public class StableSCCS {
    /**
       scop id in new release
    */
    public static int scopReleaseID;

    // maps from old and new releases
    public static HashMap<Integer,String> oldSunidToSCCS;
    public static HashMap<Integer,Integer> oldSunidToID;
    public static HashMap<Integer,Integer> newIDToSunid;
    public static HashMap<Integer,Integer> newParentID;
    public static HashMap<Integer,String> newIDToSCCS;
    public static HashMap<Integer,String> newIDToDescription;
    public static HashMap<String,Integer> oldSCCSMaxSuffix;

    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            ResultSet rs;

            int oldScopID = LocalSQL.lookupSCOPRelease(argv[0]);
            scopReleaseID = LocalSQL.lookupSCOPRelease(argv[1]);
            boolean checkOnly = false;

            if (oldScopID != scopReleaseID-1)
                throw new Exception("only works for consecutive releases");

            rs = stmt.executeQuery("select is_public from scop_release where id="+scopReleaseID);
            rs.next();
            boolean isPublic = (rs.getInt(1)==1);
            rs.close();
            if (isPublic)
                checkOnly = true;

            rs = stmt.executeQuery("select max(id) from scop_release");
            rs.next();
            int maxID = rs.getInt(1);
            rs.close();
            if (scopReleaseID != maxID)
                checkOnly = true;

            // clear out previously assigned, possibly wrong sccs
            if (!checkOnly) {
                rs = stmt.executeQuery("select max(sunid) from scop_node where release_id="+oldScopID);
                rs.next();
                int nextSunid = rs.getInt(1);
                nextSunid++;
		
                stmt.executeUpdate("update scop_node set sccs='' where release_id="+scopReleaseID+" and sunid>="+nextSunid);
            }

            // map old tree, and sunids <-> sccs
            System.out.println("getting old mappings");
            oldSunidToID = new HashMap<Integer,Integer>();
            oldSunidToSCCS = new HashMap<Integer,String>();
            oldSCCSMaxSuffix = new HashMap<String,Integer>();
            rs = stmt.executeQuery("select id, sunid, sccs from scop_node where release_id="+oldScopID);
            while (rs.next()) {
                int oldID = rs.getInt(1);
                int sunid = rs.getInt(2);
                String sccs = rs.getString(3);
                oldSunidToID.put(new Integer(sunid), new Integer(oldID));
                oldSunidToSCCS.put(new Integer(sunid), sccs);
            }
            // map largest suffix for every sccs ever used
            rs = stmt.executeQuery("select distinct(sccs) from scop_node where sccs like '%.%' and release_id<="+oldScopID);
            while (rs.next()) {
                String sccs = rs.getString(1);
                int pos = sccs.lastIndexOf(".");
                if (pos > -1) {
                    String prefix = sccs.substring(0,pos);
                    String suffix = sccs.substring(pos+1);
                    int s = StringUtil.atoi(suffix);
                    Integer oldMax = oldSCCSMaxSuffix.get(prefix);
                    if ((oldMax==null) ||
                        (oldMax.intValue() < s))
                        oldSCCSMaxSuffix.put(prefix, new Integer(s));
                }
            }
            rs.close();
            System.out.println(oldSunidToSCCS.size()+" nodes in old version");

            // map new ids
            newIDToSCCS = new HashMap<Integer,String>();
            newIDToSunid = new HashMap<Integer,Integer>();
            newIDToDescription = new HashMap<Integer,String>();
            newParentID = new HashMap<Integer,Integer>();
            HashSet<String> usedSCCS = new HashSet<String>();
            rs = stmt.executeQuery("select id, parent_node_id, description, sunid from scop_node where release_id="+scopReleaseID);
            while (rs.next()) {
                int id = rs.getInt(1);
                int parentID = rs.getInt(2);
                String description = rs.getString(3);
                int sunid = rs.getInt(4);
                newIDToSunid.put(new Integer(id), new Integer(sunid));
                newIDToDescription.put(new Integer(id), description);
                newParentID.put(new Integer(id), new Integer(parentID));
            }
            rs.close();
            System.out.println(newIDToSunid.size()+" nodes in new version");
	    
            // convert all new ids to sccs, based on old map/parents
            // start at top level
            for (int levelID = 2; levelID < 9; levelID++) {
                System.out.println("getting nodes at level "+levelID);

                Vector<Integer> newIDs = new Vector<Integer>(); // ordered
                rs = stmt.executeQuery("select id from scop_node where release_id="+scopReleaseID+" and level_id="+levelID);
                while (rs.next())
                    newIDs.add(new Integer(rs.getInt(1)));
                rs.close();
                System.out.println(newIDs.size()+" new clades at level "+levelID);

                for (Integer newID : newIDs) {
                    Integer sunid = newIDToSunid.get(newID);
                    String sccs = oldSunidToSCCS.get(sunid);
                    String description = newIDToDescription.get(newID);

                    if ((levelID == 2) && (sccs == null)) {
                        if (description.equals("Artifacts"))
                            sccs = "l";
                    }

                    if (levelID > 2) {
                        Integer parentID = newParentID.get(newID);
                        String parentSCCS = newIDToSCCS.get(parentID);

                        // System.out.println(newID+" "+sunid+" "+sccs+" "+parentSCCS);
			
                        // check consistency with parent
                        if (parentSCCS==null) {
                            Integer parentSunid = newIDToSunid.get(parentID);
                            parentSCCS = oldSunidToSCCS.get(parentSunid);
                        }

                        // assign next new suffix for new sunids
                        if (sccs==null) {
                            if (levelID > 5)
                                sccs = parentSCCS;
                            else {
                                int suffix = 0;
                                if (!description.equals("automated matches")) {
                                    Integer s = oldSCCSMaxSuffix.get(parentSCCS);
                                    if (s==null)
                                        suffix = 1;
                                    else
                                        suffix = s.intValue()+1;
                                    oldSCCSMaxSuffix.put(parentSCCS,new Integer(suffix));
                                }
                                sccs = parentSCCS+"."+suffix;
                                if (levelID < 5)
                                    oldSCCSMaxSuffix.put(sccs,new Integer(0));
                            }
                        }

                        // check consistency with parent
                        if (sccs != null) {
                            String shouldEqualParent = sccs;
                            if (levelID < 6) {
                                int pos = sccs.lastIndexOf('.');
                                if (pos==-1)
                                    throw new Exception("strange sccs: "+sccs+" for node "+newID);
                                shouldEqualParent = sccs.substring(0,pos);
                            }
                            if (!shouldEqualParent.equals(parentSCCS)) {
                                // check whether node moved
                                rs = stmt.executeQuery("select id from scop_history where new_node_id="+newID+" and change_type_id=6");
                                if (!rs.next()) {
                                    // make new history node
                                    Integer oldNodeID = oldSunidToID.get(sunid);
                                    stmt.executeUpdate("insert into scop_history values (null, "+oldNodeID+", "+newID+", "+scopReleaseID+", 6, now())");
                                    // throw new Exception("SCCS changed for node "+newID+" "+shouldEqualParent+" vs "+parentSCCS);
                                }
                                rs.close();
                                // fix, based on new sccs
                                if (levelID > 5)
                                    sccs = parentSCCS;
                            }
                        }
                    }

                    if (sccs == null)
                        throw new Exception("SCCS must be assigned by this point; node is "+newID);
		    
                    // sccs for level 2-5 must be unique
                    if (levelID < 6) {
                        if (usedSCCS.contains(sccs))
                            throw new Exception("SCCS "+sccs+" used twice; node is "+newID);
                        usedSCCS.add(sccs);
                    }
                    newIDToSCCS.put(newID, sccs);
                }
            }

            // update all nodes with new sccs, or just check
            for (Integer newID : newIDToSCCS.keySet()) {
                int id = newID.intValue();
                String sccs = newIDToSCCS.get(newID);

                if (checkOnly) {
                    rs = stmt.executeQuery("select sccs from scop_node where id="+id);
                    if (rs.next()) {
                        String oldSCCS = rs.getString(1);

                        if (!sccs.equals(oldSCCS))
                            System.out.println("sccs mapping error: "+sccs+" should be "+oldSCCS+" (id "+id+")");
                    }
                    rs.close();
                }
                else {
                    stmt.executeUpdate("update scop_node set sccs=\""+sccs+"\" where id="+id);
                }
            }
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
