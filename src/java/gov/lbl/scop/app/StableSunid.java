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
import java.text.*;
import org.strbio.io.*;
import org.strbio.math.*;
import org.strbio.mol.*;
import org.strbio.util.*;
import org.strbio.IO;
import gov.lbl.scop.local.LocalSQL;

/**
   Calculate stable sunid for a release, based on a
   previous release, and history.  Stable px must have already
   been assigned using StablePX.
*/
public class StableSunid {
    /**
       scop id in new release
    */
    public static int scopReleaseID;

    // maps from old and new releases
    public static HashMap<Integer,Integer> oldParentID;
    public static HashMap<Integer,String> oldChildIDs;
    public static HashMap<Integer,Integer> oldIDToSunid;
    public static HashMap<Integer,Integer> oldSunidToID;
    public static HashMap<Integer,String> oldIDToDescription;
    public static HashMap<Integer,Integer> newIDToSunid;
    public static HashMap<String,Integer> newDescriptionToID;
    public static HashMap<Integer,Integer> newSunidToID;
    public static HashMap<Integer,Integer> newParentID;
    public static HashMap<Integer,String> newChildIDs;
    public static HashMap<Integer,String> fwdMapAll;
    public static HashMap<Integer,String> rvsMapAll;
    public static int maxOldID;
    public static int maxOldSunid;
    public static HashSet<String> history;

    /**
       keep map of ids to ids, from old to new version
       format is oldID_newID -> number of common children
    */
    private static HashMap<String,Integer> idMap;

    /**
       get all the mappings for a particular id in the id map.
    */
    final public static Vector<Integer> getMappings(int id, boolean forward) {
        Vector<Integer> rv = new Vector<Integer>();
        for (String map : idMap.keySet()) {
            int pos = map.indexOf('_');
            int oldID = StringUtil.atoi(map);
            int newID = StringUtil.atoi(map,pos+1);

            if ((forward) && (oldID==id))
                rv.add(new Integer(newID));
            if ((!forward) && (newID==id))
                rv.add(new Integer(oldID));
        }
        return rv;
    }

    /**
       get all old or new children of a node
    */
    final public static Vector<Integer> getChildIDs(int nodeID) throws Exception {
        Vector<Integer> rv = new Vector<Integer>();
        Integer id = new Integer(nodeID);
        String allChildren;
        if (id<=maxOldID)
            allChildren = oldChildIDs.get(id);
        else
            allChildren = newChildIDs.get(id);
        if (allChildren==null)
            return rv;
        String[] child = allChildren.split("_");
        for (String c : child)
            rv.add(new Integer(StringUtil.atoi(c)));
        return rv;
    }

    /**
       convert list of ids from old release to new (or back).
       not 1-1 mapping due to split/merge
    */
    final public static HashSet<Integer> convert(Collection<Integer> ids,
                                                 boolean forward) throws Exception {
        HashSet<Integer> rv = new HashSet<Integer>();
        for (Integer id : ids) {
            String map;
            if (forward)
                map = fwdMapAll.get(id);
            else
                map = rvsMapAll.get(id);
            if (map != null) {
                String[] ans = map.split("_");
                for (String s : ans)
                    rv.add(new Integer(s));
            }
        }
        return rv;
    }

    /**
       convert ids to sunids
    */
    final public static Vector<Integer> idToSunid(Vector<Integer>ids) throws Exception {
        Vector<Integer> rv = new Vector<Integer>();
        for (Integer id : ids) {
            if (id.intValue() <= maxOldID)
                rv.add(oldIDToSunid.get(id));
            else
                rv.add(newIDToSunid.get(id));
        }
        return rv;
    }

    /**
       convert sunids to ids; user has to specify old or new release.
       If some sunids don't exist in the release, these positions are
       returned as 0.
    */
    final public static Vector<Integer> sunidToID(Vector<Integer>sunids, boolean old) throws Exception {
        Vector<Integer> rv = new Vector<Integer>();
        for (Integer sunid : sunids) {
            Integer id;
            if (old)
                id = oldSunidToID.get(sunid);
            else
                id = newSunidToID.get(sunid);
            if (id == null)
                rv.add(new Integer(0));
            else
                rv.add(id);
        }
        return rv;
    }
    
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

            // what is last id/sunid from last release?
            rs = stmt.executeQuery("select max(id), max(sunid) from scop_node where release_id="+oldScopID);
            rs.next();
            maxOldID = rs.getInt(1);
            maxOldSunid = rs.getInt(2);
            rs.close();

            // keep track of next new sunid to assign
            rs = stmt.executeQuery("select max(sunid) from scop_node where release_id="+scopReleaseID+" and level_id=8");
            rs.next();
            int nextSunid = rs.getInt(1);
            rs.close();
            nextSunid++;
            if (nextSunid<=1)
                throw new Exception("must have assigned stable px first; run StablePX");

            // get history of px, and all old history if checking
            history = new HashSet<String>();
            fwdMapAll = new HashMap<Integer,String>();
            rvsMapAll = new HashMap<Integer,String>();
            HashSet<String> oldHistory = null;
            if (checkOnly)
                oldHistory = new HashSet<String>();
            rs = stmt.executeQuery("select h.old_node_id, h.new_node_id, h.change_type_id, n.level_id from scop_history h, scop_node n where h.release_id="+scopReleaseID+" and h.old_node_id=n.id");
            while (rs.next()) {
                int oldID = rs.getInt(1);
                int newID = rs.getInt(2); // should be 0 for null
                int changeID = rs.getInt(3);
                int levelID = rs.getInt(4);

                if (newID != 0) {
                    String s = fwdMapAll.get(oldID);
                    if (s == null)
                        s = ""+newID;
                    else
                        s += "_"+newID;
                    fwdMapAll.put(oldID,s);

                    s = rvsMapAll.get(newID);
                    if (s == null)
                        s = ""+oldID;
                    else
                        s += "_"+oldID;
                    rvsMapAll.put(newID,s);
                }

                if (levelID==8)
                    history.add(oldID+"_"+newID+"_"+changeID+"_"+levelID);
                if (checkOnly && (levelID>=7))
                    oldHistory.add(oldID+"_"+newID+"_"+changeID+"_"+levelID);
            }
            rs.close();
    
            // map old tree, and sunids <-> ids
            System.out.println("getting old mappings");
            oldParentID = new HashMap<Integer,Integer>();
            oldChildIDs = new HashMap<Integer,String>();
            oldIDToSunid = new HashMap<Integer,Integer>();
            oldIDToDescription = new HashMap<Integer,String>();
            oldSunidToID = new HashMap<Integer,Integer>();
            rs = stmt.executeQuery("select id, sunid, parent_node_id, description from scop_node where release_id="+oldScopID);
            while (rs.next()) {
                int id = rs.getInt(1);
                int sunid = rs.getInt(2);
                int parentID = rs.getInt(3);
                String description = rs.getString(4);
		
                oldParentID.put(new Integer(id), new Integer(parentID));
                oldIDToSunid.put(new Integer(id), new Integer(sunid));
                oldSunidToID.put(new Integer(sunid), new Integer(id));
                oldIDToDescription.put(new Integer(id), description);
            }
            rs.close();
            for (Integer childID : oldParentID.keySet()) {
                Integer parentID = oldParentID.get(childID);
                String children = oldChildIDs.get(parentID);
                if (children==null)
                    children = childID.toString();
                else {
                    children += "_"+childID;
                }
                oldChildIDs.put(parentID,children);
            }
            System.out.println(oldParentID.size()+" nodes in old version");

            // assign new sunids, starting at bottom level
            newIDToSunid = new HashMap<Integer,Integer>();
            newSunidToID = new HashMap<Integer,Integer>();
            newParentID = new HashMap<Integer,Integer>();
            newChildIDs = new HashMap<Integer,String>();
            newDescriptionToID = new HashMap<String,Integer>();
	    
            rs = stmt.executeQuery("select id, parent_node_id, description from scop_node where release_id="+scopReleaseID);
            while (rs.next()) {
                int id = rs.getInt(1);
                int parentID = rs.getInt(2);
                String description = rs.getString(3);
		
                newParentID.put(new Integer(id), new Integer(parentID));
                newDescriptionToID.put(parentID+"_"+description,new Integer(id));
            }
            rs.close();
            for (Integer childID : newParentID.keySet()) {
                Integer parentID = newParentID.get(childID);
                String children = newChildIDs.get(parentID);
                if (children==null)
                    children = childID.toString();
                else {
                    children += "_"+childID;
                }
                newChildIDs.put(parentID,children);
            }
	    
            // get assigned px
            rs = stmt.executeQuery("select id, sunid from scop_node where release_id="+scopReleaseID+" and level_id=8");
            while (rs.next()) {
                int id = rs.getInt(1);
                int sunid = rs.getInt(2);
		
                newIDToSunid.put(new Integer(id), new Integer(sunid));
                newSunidToID.put(new Integer(sunid), new Integer(id));
                Integer i = oldSunidToID.get(new Integer(sunid));
                if (i != null) {
                    String s = fwdMapAll.get(i);
                    if (s == null)
                        s = ""+id;
                    else
                        s += "_"+id;
                    fwdMapAll.put(i,s);

                    s = rvsMapAll.get(new Integer(id));
                    if (s == null)
                        s = ""+i;
                    else
                        s += "_"+i;
                    rvsMapAll.put(new Integer(id),s);
                }
            }
            rs.close();
            System.out.println(newParentID.size()+" nodes in new version");

            System.out.println(newIDToSunid.size()+" px assigned, nextSunid="+nextSunid);

            // assign other sunids, level by level:
            for (int levelID = 2; levelID < 8; levelID++) {
                System.out.println("getting nodes at level "+levelID);
                Vector<Integer> oldIDs = new Vector<Integer>(); // ordered
                rs = stmt.executeQuery("select id from scop_node where release_id="+oldScopID+" and level_id="+levelID);
                while (rs.next())
                    oldIDs.add(new Integer(rs.getInt(1)));
                rs.close();
                System.out.println(oldIDs.size()+" old clades at level "+levelID);

                Vector<Integer> newIDs = new Vector<Integer>(); // ordered
                rs = stmt.executeQuery("select id from scop_node where release_id="+scopReleaseID+" and level_id="+levelID);
                while (rs.next())
                    newIDs.add(new Integer(rs.getInt(1)));
                rs.close();
                System.out.println(newIDs.size()+" new clades at level "+levelID);

                // give new sunids to all split/moved nodes
                rs = stmt.executeQuery("select new_node_id from scop_history where release_id="+scopReleaseID+" and change_type_id in (5,6) and new_node_id in (select id from scop_node where level_id="+levelID+" and release_id="+scopReleaseID+")");
                while (rs.next()) {
                    int id = rs.getInt(1);
                    int sunid = nextSunid++;
                    newIDToSunid.put(new Integer(id),new Integer(sunid));
                    newSunidToID.put(new Integer(sunid),new Integer(id));
                    newIDs.remove(new Integer(id));
                }

                System.out.println(newIDToSunid.size()+" assigned after split/move, nextSunid="+nextSunid);

                // assign cases where child is unique description, and
                // parent is same sunid
                for (Integer oldID : oldIDs) {
                    Integer sunid = oldIDToSunid.get(oldID);
                    Integer oldParent = oldParentID.get(oldID);
                    Integer oldParentSunid = oldIDToSunid.get(oldParent);
                    String oldDescription = oldIDToDescription.get(oldID);
                    Integer newParent = newSunidToID.get(oldParentSunid);
                    if (newParent != null) {
                        Integer newID = newDescriptionToID.get(newParent+"_"+oldDescription);
                        if (newID != null) {
                            // System.out.println("mapping same: "+newID);
			    
                            // skip if already assigned
                            if (!newIDs.contains(newID))
                                continue;

                            newIDToSunid.put(newID, sunid);
                            if (newSunidToID.containsKey(sunid)) {
                                throw new Exception("Error mapping sunids:  "+sunid+" used twice!");
                            }
                            newSunidToID.put(sunid,newID);
                            newIDs.remove(newID);
                        }
                    }
                }

                // get already assigned sunids at this level
                rs = stmt.executeQuery("select id, sunid from scop_node where release_id="+scopReleaseID+" and sunid>0 and level_id="+levelID);
                while (rs.next()) {
                    int id = rs.getInt(1);
                    int sunid = rs.getInt(2);

                    // already assigned via split/move
                    if (!newIDs.contains(new Integer(id)))
                        continue;
		    
                    newIDToSunid.put(new Integer(id), new Integer(sunid));
                    if (newSunidToID.containsKey(new Integer(sunid))) {
                        throw new Exception("Error mapping sunids:  "+sunid+" used twice!");
                    }
                    newSunidToID.put(new Integer(sunid), new Integer(id));
                    newIDs.remove(new Integer(id));
                }
                rs.close();

                System.out.println(newIDToSunid.size()+" assigned after previous, nextSunid="+nextSunid);

                // everything not mapped is new clade, and should get new sunid
                Vector<Integer> tmpI = new Vector<Integer>(newIDs);
                for (Integer id : tmpI) {
                    int sunid = nextSunid++;
                    newIDToSunid.put(id,new Integer(sunid));
                    newSunidToID.put(new Integer(sunid),id);
                }
                System.out.println(newIDToSunid.size()+" done after assigning new clades, nextSunid="+nextSunid);
            }

            LocalSQL.setNextSunid(nextSunid);
	    
            // update all nodes with new sunid, or just check
            for (Integer newID : newIDToSunid.keySet()) {
                int id = newID.intValue();
                int sunid = newIDToSunid.get(newID).intValue();

                if (checkOnly) {
                    rs = stmt.executeQuery("select sunid from scop_node where id="+id);
                    if (rs.next()) {
                        int oldSunid = rs.getInt(1);

                        if (sunid != oldSunid) {
                            /*
                            // only a problem if sunid is new
                            if (sunid <= maxOldSunid)
                            */
                            System.out.println("sunid mapping error: "+sunid+" should be "+oldSunid+" (id "+id+")");
                        }
                    }
                    rs.close();
                }
                else {
                    stmt.executeUpdate("update scop_node set sunid="+sunid+" where id="+id);
                }
            }

            /*
              Should already be done, although we might want to check it
	      
              System.out.println("updating history");

              // add new nodes to history, or check:
              for (String histLine : history) {
              String[] histVal = histLine.split("_");
              int oldID = StringUtil.atoi(histVal[0]);
              int newID = StringUtil.atoi(histVal[1]);
              int changeID = StringUtil.atoi(histVal[2]);

              if (checkOnly) {
              if (oldHistory.contains(histLine))
              oldHistory.remove(histLine);
              else
              System.out.println("old history missing "+histLine);
              }
              else {
              rs = stmt.executeQuery("select id from scop_history where old_node_id="+oldID+" and new_node_id "+(newID==0 ? "is null" : "="+newID)+" and release_id="+scopReleaseID+" and change_type_id="+changeID);
              if (!rs.next()) {
              rs.close();
              stmt.executeUpdate("insert into scop_history values (null,"+
              oldID+", "+
              (newID==0 ? "null" : newID)+", "+
              scopReleaseID+", "+
              changeID+", now())");
              }
              else
              rs.close();
              }
              }
              if ((oldHistory != null) && (checkOnly)) {
              for (String histLine : oldHistory) {
              System.out.println("missing history "+histLine);
              }
              }
            */
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
