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
   Calculate stable px (domain sunid) for a release, based on a
   previous release, and history.

   This must be done after sids are assigned (StableSid)
*/
public class StablePX {
    /**
       scop id in new release
    */
    public static int scopReleaseID;

    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            ResultSet rs;

            int oldScopID = LocalSQL.lookupSCOPRelease(argv[0]);
            scopReleaseID = LocalSQL.lookupSCOPRelease(argv[1]);
            boolean checkOnly = false;

            rs = stmt.executeQuery("select is_public from scop_release where id="+scopReleaseID);
            rs.next();
            boolean isPublic = (rs.getInt(1)==1);
            if (isPublic)
                checkOnly = true;

            rs = stmt.executeQuery("select max(id) from scop_release");
            rs.next();
            int maxID = rs.getInt(1);
            if (scopReleaseID != maxID)
                checkOnly = true;

            // keep track of next new sunid to assign
            rs = stmt.executeQuery("select max(sunid) from scop_node where release_id="+oldScopID);
            rs.next();
            int nextSunid = rs.getInt(1);
            nextSunid++;
    
            // clear out previously assigned sunids, that may need fixing
            if (!checkOnly) {
                stmt.executeUpdate("update scop_node set sunid=0 where release_id="+scopReleaseID+" and level_id=8");
                stmt.executeUpdate("update scop_node set sunid=0 where release_id="+scopReleaseID+" and sunid>="+nextSunid);
            }

            // map old sids to sunids
            System.out.println("getting old mappings");
            HashMap<String,Integer> oldSidToSunid = new HashMap<String,Integer>();
            HashMap<Integer,Integer> oldIDToSunid = new HashMap<Integer,Integer>();
            rs = stmt.executeQuery("select id, sid, sunid from scop_node where release_id="+oldScopID+" and level_id=8");
            while (rs.next()) {
                int nodeID = rs.getInt(1);
                String sid = rs.getString(2);
                int sunid = rs.getInt(3);
                oldSidToSunid.put(sid, new Integer(sunid));
                oldIDToSunid.put(new Integer(nodeID), new Integer(sunid));
            }
            System.out.println(oldSidToSunid.size()+" domains in old version");

            // map new sids to sunids
            System.out.println("getting new sids");
            HashMap<Integer,Integer> newIDToSunid = new HashMap<Integer,Integer>();
            HashMap<String,Integer> newSidToID = new HashMap<String,Integer>();
            HashMap<Integer,String> newIDToSid = new HashMap<Integer,String>();
            Vector<String> newSid = new Vector<String>(); // orderered
            String orderBy;
            if (scopReleaseID < 5 ) orderBy = "order by n.id";
            else orderBy = "order by n.sid";
            rs = stmt.executeQuery("select n.id, n.sid from scop_node n where n.release_id="+scopReleaseID+" and n.level_id=8 "+orderBy);
            while (rs.next()) {
                int nodeID = rs.getInt(1);
                String sid = rs.getString(2);

                newSidToID.put(sid, new Integer(nodeID));
                newIDToSid.put(new Integer(nodeID), sid);
                newSid.add(sid);
            }
            System.out.println(newSid.size()+" domains in new version");

            // map all identical nodes first
            Vector<String> tmpS = new Vector<String>(newSid);
            for (String sid : tmpS) {
                if (oldSidToSunid.containsKey(sid)) {
                    // System.out.println("mapped(1) "+sid);
                    Integer sunid = oldSidToSunid.get(sid);
                    Integer id = newSidToID.get(sid);

                    newIDToSunid.put(id,new Integer(sunid));

                    // remove these from old map
                    // and to-do list as they're done
                    oldSidToSunid.remove(sid);
                    newSid.remove(sid);
                }
            }
            System.out.println(newIDToSunid.size()+" domains matched exactly, nextSunid="+nextSunid);

            // map all nodes in history with unchanged sid
            rs = stmt.executeQuery("select old_node_id, new_node_id from scop_history where release_id="+scopReleaseID+" and (change_type_id=10 or change_type_id=11)");
            while (rs.next()) {
                int oldID = rs.getInt(1);
                int newID = rs.getInt(2);
                Integer sunid = oldIDToSunid.get(new Integer(oldID));
                String sid = newIDToSid.get(new Integer(newID));

                newIDToSunid.put(new Integer(newID),sunid);
                newSid.remove(sid);
            }

            System.out.println(newIDToSunid.size()+" domains matched after assigning unchanged sid");

            // map all merge/split/mod, plus new chains from old pdb
            // first, find all merge/split/mod:
            HashSet<String> msmSids = new HashSet<String>();
            rs = stmt.executeQuery("select h.new_node_id from scop_history h, scop_node n where h.release_id="+scopReleaseID+" and h.new_node_id=n.id and (change_type_id!=10 and change_type_id!=11) and n.level_id=8");
            while (rs.next()) {
                int newID = rs.getInt(1);
                String sid = newIDToSid.get(new Integer(newID));
                msmSids.add(sid);
            }

            // find all old pdb codes
            HashSet<String> oldPDBs = new HashSet<String>();
            rs = stmt.executeQuery("select distinct(substring(description,1,4)) from scop_node where level_id=8 and release_id="+oldScopID);
            while (rs.next()) {
                String pdb = rs.getString(1);
                oldPDBs.add(pdb);
            }
		   
            // map all these
            tmpS = new Vector<String>(newSid);
            for (String sid : tmpS) {
                String pdb = sid.substring(1,5);
                if (msmSids.contains(sid) ||
                    oldPDBs.contains(pdb)) {
                    int sunid = nextSunid++;
                    Integer id = newSidToID.get(sid);
                    newIDToSunid.put(id,new Integer(sunid));
                    newSid.remove(sid);
                }
            }
            System.out.println(newIDToSunid.size()+" domains matched after assigning merged/split/mod/oldpdb, nextSunid="+nextSunid);

            // everything else is new pdb, and should get new sunid
            tmpS = new Vector<String>(newSid);
            for (String sid : tmpS) {
                int sunid = nextSunid++;
                Integer id = newSidToID.get(sid);

                newIDToSunid.put(id,new Integer(sunid));
            }

            LocalSQL.setNextSunid(nextSunid);

            System.out.println(newIDToSunid.size()+" domains matched after assigning new sid, nextSunid="+nextSunid);
	    
            // update all nodes with new sid, or just check
            for (Integer newID : newIDToSunid.keySet()) {
                int id = newID.intValue();
                int sunid = newIDToSunid.get(newID).intValue();

                if (checkOnly) {
                    rs = stmt.executeQuery("select sunid from scop_node where id="+id);
                    if (rs.next()) {
                        int oldSunid = rs.getInt(1);

                        if (sunid != oldSunid) 
                            System.out.println("sunid mapping error: "+sunid+" should be "+oldSunid+" (id "+id+")");
                    }
                }
                else {
                    stmt.executeUpdate("update scop_node set sunid="+sunid+" where id="+id);
                }
            }
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
