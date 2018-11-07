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

import gov.lbl.scop.local.LocalSQL;
import gov.lbl.scop.util.*;

import java.sql.*;
import java.util.*;

/**
   Check for various types of ID bugs
*/
public class CheckIDs {
    public static int scopReleaseID;
    
    // maps from old and new releases
    public static HashMap<Integer,Integer> oldIDToSunid;
    public static HashMap<Integer,String> oldSunidToSCCS;
    public static HashMap<Integer,String> oldSunidToSid;
    public static HashMap<Integer,Integer> oldSunidToID;
    public static HashMap<Integer,Integer> oldSunidToSeq;
    public static HashMap<Integer,String> oldIDToDescription;
    public static HashMap<Integer,Integer> oldSunidToCuration;
    public static HashMap<Integer,Integer> oldIDToLevel;
    public static HashMap<Integer,Integer> oldParentID;
    public static HashMap<Integer,Integer> newIDToSunid;
    public static HashMap<Integer,Integer> newSunidToSeq;
    public static HashMap<Integer,Integer> newSunidToID;
    public static HashMap<Integer,Integer> newIDToLevel;
    public static HashMap<Integer,String> newIDToDescription;
    public static HashMap<String,Integer> newDescriptionToID;
    public static HashMap<Integer,Integer> newParentID;
    public static HashMap<Integer,String> newSunidToSCCS;
    public static HashMap<Integer,String> newSunidToSid;
    public static HashSet<String> history;

    final public static void checkSid() throws Exception {
        System.out.println("checking Sid consistency");

        HashSet<String> usedSid = new HashSet<String>();

        Vector<Integer> newIDs = new Vector<Integer>();
        for (Integer newID : newIDToLevel.keySet())
            if (newIDToLevel.get(newID).intValue()==8)
                newIDs.add(newID);

        for (Integer newID : newIDs) {
            Integer sunid = newIDToSunid.get(newID);
            String oldSid = oldSunidToSid.get(sunid);
            String newSid = newSunidToSid.get(sunid);

            if (oldSid != null) {
                Integer oldID = oldSunidToID.get(sunid);
                // history changes that can change sid?
                if ((!history.contains(oldID+"_"+newID+"_4")) &&
                    (!history.contains(oldID+"_"+newID+"_5")) &&
                    (!history.contains(oldID+"_"+newID+"_6")) &&
                    (!history.contains(oldID+"_"+newID+"_7")) &&
                    (!history.contains(oldID+"_"+newID+"_8")) &&
                    (!history.contains(oldID+"_"+newID+"_9")) &&
                    (!history.contains(oldID+"_"+newID+"_10")) &&
                    (!history.contains(oldID+"_"+newID+"_11"))) {
                    if (!oldSid.equals(newSid))
                        System.out.println("Sid changed old->new for node "+newID+" "+oldSid+" vs "+newSid);
                }
            }

            if (usedSid.contains(newSid))
                System.out.println("Sid used twice; node is "+newID);
            usedSid.add(newSid);
        }
    }

    final public static void checkSunid() throws Exception {
        System.out.println("checking Sunid consistency");

        Vector<Integer> newIDs = new Vector<Integer>(newIDToLevel.keySet());

        for (Integer newID : newIDs) {
            Integer sunid = newIDToSunid.get(newID);
            Integer oldID = oldSunidToID.get(sunid);
            Integer levelID = newIDToLevel.get(newID);

            if (oldID != null) {
                // history changes that should change sunid
                if (((history.contains(oldID+"_"+newID+"_5")) ||
                     (history.contains(oldID+"_"+newID+"_7")) ||
                     (history.contains(oldID+"_"+newID+"_8"))) &&
                    (!history.contains(oldID+"_"+newID+"_11")))
                    System.out.println("Sunid should be obsolete for node "+newID+" due to history");
            }
        }

        Vector<Integer> oldIDs = new Vector<Integer>(oldIDToSunid.keySet());
        for (Integer oldID : oldIDs) {
            Integer sunid = oldIDToSunid.get(oldID);
            Integer newid = newSunidToID.get(sunid);
            if (newid == null) {
                if ((!history.contains(oldID+"_1")) &&
                    (!history.contains(oldID+"_2")) &&
                    (!history.contains(oldID+"_3")) &&
                    (!history.contains(oldID+"_4")) &&
                    (!history.contains(oldID+"_5")) &&
                    (!history.contains(oldID+"_6")) &&
                    (!history.contains(oldID+"_7")) &&
                    (!history.contains(oldID+"_8")) &&
                    (!history.contains(oldID+"_9")) &&
                    (!history.contains(oldID+"_13")))
                    System.out.println("Sunid should NOT be obsolete for node "+oldID+" - missing history");
            }
        }
    }

    final public static void checkMerges() throws Exception {
        System.out.println("checking for merges that erroneously change sunid");
        Vector<Integer> oldIDs = new Vector<Integer>(oldIDToSunid.keySet());

        for (Integer oldID : oldIDs) {
            if (history.contains(oldID+"_4")) {
                Integer sunid = oldIDToSunid.get(oldID);
                Integer oldParent = oldParentID.get(oldID);
                Integer oldParentSunid = oldIDToSunid.get(oldParent);
                String oldDescription = oldIDToDescription.get(oldID);
                Integer newParent = newSunidToID.get(oldParentSunid);
                if (newParent != null) {
                    Integer newID = newDescriptionToID.get(newParent+"_"+oldDescription);
                    if (newID != null) {
                        int newSunid = newIDToSunid.get(newID);
                        if (!sunid.equals(newSunid))
                            System.out.println("Sunid should NOT be obsolete for node "+oldID+" - bogus merge history");
                    }
                }
            }
        }
    }
    
    final public static void checkSCCS() throws Exception {
        System.out.println("checking SCCS consistency");

        HashSet<String> usedSCCS = new HashSet<String>();
	
        // convert all new ids to sccs, based on old map/parents
        // start at top level
        for (int levelID = 2; levelID < 9; levelID++) {
            Vector<Integer> newIDs = new Vector<Integer>(); // ordered
            for (Integer newID : newIDToLevel.keySet())
                if (newIDToLevel.get(newID).intValue()==levelID)
                    newIDs.add(newID);

            for (Integer newID : newIDs) {
                Integer sunid = newIDToSunid.get(newID);
                String oldSCCS = oldSunidToSCCS.get(sunid);
                String newSCCS = newSunidToSCCS.get(sunid);
                String description = newIDToDescription.get(newID);

                if (levelID > 2) {
                    // check consistency with parent
                    Integer parentID = newParentID.get(newID);
                    Integer parentSunid = newIDToSunid.get(parentID);
                    String parentSCCS = newSunidToSCCS.get(parentSunid);

                    if ((levelID==5) && (description.equals("automated matches"))) {
                        String correctSCCS = parentSCCS+".0";
                        if (!correctSCCS.equals(newSCCS)) {
                            System.out.println("SCCS for automated matches should end in .0 "+newID+" "+correctSCCS+" vs "+newSCCS);
                        }
                    }

                    String shouldEqualParent = newSCCS;
                    if (levelID < 6) {
                        int pos = newSCCS.lastIndexOf('.');
                        if (pos==-1)
                            System.out.println("strange sccs: "+newSCCS+" for node "+newID);
                        shouldEqualParent = newSCCS.substring(0,pos);
                    }
                    if (!shouldEqualParent.equals(parentSCCS)) {
                        System.out.println("SCCS changed for node "+newID+" "+shouldEqualParent+" vs "+parentSCCS);
                    }
                }

                // sccs for level 2-5 must be unique
                if (levelID < 6) {
                    if (usedSCCS.contains(newSCCS))
                        System.out.println("SCCS used twice; node is "+newID);
                    usedSCCS.add(newSCCS);
                }
            }
        }
    }

    final public static void checkDescriptions() {
        System.out.println("checking description uniqueness");

        HashMap<Integer,HashSet<Integer>> newChildIDs =
            new HashMap<Integer,HashSet<Integer>>();

        // map all parents to children
        for (Integer childID : newParentID.keySet()) {
            Integer parentID = newParentID.get(childID);
            HashSet<Integer> childSet = newChildIDs.get(parentID);
            if (childSet == null) {
                childSet = new HashSet<Integer>();
                newChildIDs.put(parentID, childSet);
            }
            childSet.add(childID);
        }

        // check uniqueness of child descriptions
        for (Integer parentID : newChildIDs.keySet()) {
            HashSet<String> usedDesc = new HashSet<String>();
            for (Integer childID : newChildIDs.get(parentID)) {
                String desc = newIDToDescription.get(childID);
                if (usedDesc.contains(desc))
                    System.out.println("Description '"+desc+"' used multiple times under "+parentID);
                usedDesc.add(desc);
            }
        }
    }

    final public static void checkHistory() {
        System.out.println("checking history");

        // check that each domain is accounted for
        Vector<Integer> oldDomains = new Vector<Integer>();
        for (Integer oldID : oldIDToSunid.keySet()) {
            if (oldIDToLevel.get(oldID).equals(8))
                oldDomains.add(oldID);
        }

        // filter out obs nodes
        for (Integer oldID : oldIDToSunid.keySet()) {
            if ((history.contains(oldID+"_1")) ||
                (history.contains(oldID+"_2")) ||
                (history.contains(oldID+"_3")))
                oldDomains.remove(oldID);
        }

        int nUnchanged = 0;
        int nSilent = 0;
        int nRECH = 0;
        int nMinorCurated = 0;
        int nMinorAutomatic = 0;
        int nMajorCurated = 0;
        int nMajorAutomatic = 0;
        int nUnmoved = 0;
        int nMovedCurated = 0;
        int nMovedAutomatic = 0;

        // These count the number of nodes
        // that were moved due to being 'wrong'
        // or just because they were overly
        // specific and we were not confident
        // in their classification
        int nMovedAutomaticOverlySpecific=0;
        int nMovedAutomaticIncorrect=0;

        // classify remaining domains
        for (Integer oldID : oldDomains) {
            Integer sunid = oldIDToSunid.get(oldID);
            String oldSid = oldSunidToSid.get(sunid);
            String newSid = newSunidToSid.get(sunid);
            Integer oldSeq = oldSunidToSeq.get(sunid);
            Integer newSeq = newSunidToSeq.get(sunid);
            Integer curationID = oldSunidToCuration.get(sunid);
            boolean isCurated = curationID.equals(1);
            Integer oldParent = oldParentID.get(oldID);
            Integer oldParentSunid = oldIDToSunid.get(oldParent);
            Integer oldGP = oldParentID.get(oldParent);
            Integer oldGPSunid = oldIDToSunid.get(oldGP);
	    
            String oldDescription = oldIDToDescription.get(oldID);
            Integer newID = newSunidToID.get(sunid);
            String newDescription = newIDToDescription.get(newID);
            if (newID != null) {
                Integer newParent = newParentID.get(newID);
                Integer newParentSunid = newIDToSunid.get(newParent);
                Integer newGP = newParentID.get(newParent);
                Integer newGPSunid = newIDToSunid.get(newGP);
                String newGPDescription = newIDToDescription.get(newGP);

                if (oldParentSunid.equals(newParentSunid) &&
                    oldGPSunid.equals(newGPSunid))
                    nUnmoved++;
                else {
                    if (isCurated)
                        nMovedCurated++;
                    else {
                        nMovedAutomatic++;
                        if (newGPDescription.equals("automated matches")) {
                            nMovedAutomaticOverlySpecific++;
                        }
                        else {
                            nMovedAutomaticIncorrect++;
                        }
                    }
                }
            }

            if ((oldDescription.equals(newDescription)) &&
                (oldSid.equals(newSid)))
                nUnchanged++;
            else {
                if (newID == null) {
                    if (isCurated)
                        nMajorCurated++;
                    else
                        nMajorAutomatic++;

                    boolean histFound = false;
                    for (int i=4; i<=14; i++) {
                        if (history.contains(oldID+"_"+i)) {
                            // System.out.println("history: "+oldID+"_"+i);
                            histFound = true;
                        }
                    }
                    if (!histFound)
                        System.out.println("Major change for "+oldSid+"; no history found");
		    
                }
                else {
                    if (!oldSid.equals(newSid)) {
                        if (newSid.endsWith("_")) {
                            if (oldSeq.equals(newSeq)) {
                                nRECH++;
                                if ((!history.contains(oldID+"_"+newID+"_"+10)) &&
                                    (!history.contains(oldID+"_"+newID+"_"+11)))
                                    System.out.println("add history? "+oldID+" "+newID+" RE->CH");
                            }
                            else {
                                if (isCurated)
                                    nMinorCurated++;
                                else
                                    nMinorAutomatic++;
                                if ((!history.contains(oldID+"_"+newID+"_"+11)) &&
                                    (!history.contains(oldID+"_"+newID+"_"+10)))
                                    System.out.println("add history? "+oldID+" "+newID+" minor");
                            }
                        }
                        else {
                            boolean histFound = false;
                            for (int i=4; i<=14; i++) {
                                if (history.contains(oldID+"_"+i))
                                    histFound = true;
                                // System.out.println("history: "+oldID+"_"+i);
                            }
                            if (!histFound)
                                System.out.println("check: "+oldSid+" -> "+newSid+"; no history found");
			    
                        }
                    }
                    else if (oldSeq.equals(newSeq))
                        nSilent++;
                    else {
                        if (oldSid.equals(newSid)) {
                            if (isCurated)
                                nMinorCurated++;
                            else
                                nMinorAutomatic++;
                        }
                    }
                }
            }
        }

        System.out.println(nMajorCurated+" major changes to curated domains");
        System.out.println(nMinorCurated+" minor changes to curated domains");
        System.out.println(nMajorAutomatic+" major changes to automatic domains");
        System.out.println(nMinorAutomatic+" minor changes to automatic domains");
        System.out.println(nRECH+" domains with RE->CH");
        System.out.println(nSilent+" silent changes");
        System.out.println(nUnchanged+" domains unchanged");
        System.out.println(nMovedCurated+" curated domains moved");
        System.out.println(nMovedAutomatic+" automatic domains moved");
        System.out.println(" " + nMovedAutomaticOverlySpecific+" automatic domains moved because overly specific");
        System.out.println(" " + nMovedAutomaticIncorrect+" automatic domains moved because incorrect");
        System.out.println(nUnmoved+" domains unmoved");
    }

    final public static void checkSubsets() throws Exception {
        System.out.println("checking scop level subsets");
        Statement stmt = LocalSQL.createStatement();

        // add all non-domain nodes
        Vector<Integer> newNodes = new Vector<Integer>();
        for (Integer newID : newIDToSunid.keySet()) {
            if ((!newIDToLevel.get(newID).equals(8)) &&
                (!newIDToLevel.get(newID).equals(1)))
                newNodes.add(newID);
        }

        // check that each one appears only once, and that
        // rep is an ancestor
        for (Integer newID : newNodes) {
            ResultSet rs = stmt.executeQuery("select rep_node_id from scop_subset_level where level_node_id="+newID);
            int nFound = 0;
            while (rs.next()) {
                nFound++;
                int repID = rs.getInt(1);
                if (!LocalSQL.isAncestor(newID.intValue(),repID))
                    System.out.println("Bad rep "+repID+" for level node "+newID);
            }
            rs.close();
            if (nFound==0) {
                // should any be found?
                Vector<SPACI.SPACINode> nodes = MakeSubsets.getSortedNodes(MakeSubsets.descendentsOf(newID,8));
                nodes = MakeSubsets.removeRejects(nodes);
                if ((nodes==null) || (nodes.size()==0))
                    continue;

                // only complain if there were non-reject nodes
                System.out.println("Missing rep for level node "+newID);
            }
            if (nFound > 1)
                System.out.println("Duplicate reps for level node "+newID);
        }

        // check that no level node/rep nodes are backwards
        ResultSet rs = stmt.executeQuery("select n.id, n.level_id from scop_subset_level r, scop_node n where n.id=r.rep_node_id and n.level_id<8");
        while (rs.next()) {
            int repID = rs.getInt(1);
            int levelID = rs.getInt(2);
            System.out.println("Bad rep "+repID+" level "+levelID+"; reps should be level 8");
        }
        rs.close();

        rs = stmt.executeQuery("select n.id from scop_subset_level r, scop_node n where n.id=r.level_node_id and n.level_id=8");
        while (rs.next()) {
            int nodeID = rs.getInt(1);
            System.out.println("Shouln't have representative for "+nodeID+" at level 8");
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

            scopReleaseID = LocalSQL.lookupSCOPRelease(argv[0]);
            int oldScopID = scopReleaseID-1;

            // map old tree
            System.out.println("getting old mappings");
            oldIDToSunid = new HashMap<Integer,Integer>();
            oldSunidToID = new HashMap<Integer,Integer>();
            oldSunidToSCCS = new HashMap<Integer,String>();
            oldSunidToSid = new HashMap<Integer,String>();
            oldSunidToSeq = new HashMap<Integer,Integer>();
            oldSunidToCuration = new HashMap<Integer,Integer>();
            oldIDToDescription = new HashMap<Integer,String>();
            oldIDToLevel = new HashMap<Integer,Integer>();
            oldParentID = new HashMap<Integer,Integer>();
            int maxOldSunid = 0;
            rs = stmt.executeQuery("select id, sunid, sccs, sid, description, level_id, curation_type_id, parent_node_id from scop_node where release_id="+oldScopID);
            while (rs.next()) {
                int oldID = rs.getInt(1);
                int sunid = rs.getInt(2);
                String sccs = rs.getString(3);
                String sid = rs.getString(4);
                String description = rs.getString(5);
                int levelID = rs.getInt(6);
                int curationID = rs.getInt(7);
                int parentID = rs.getInt(8);
		
                oldIDToSunid.put(new Integer(oldID), new Integer(sunid));
                oldSunidToID.put(new Integer(sunid), new Integer(oldID));
                oldIDToDescription.put(new Integer(oldID), description);

                oldSunidToCuration.put(new Integer(sunid), new Integer(curationID));
                oldIDToLevel.put(new Integer(oldID), new Integer(levelID));
                oldParentID.put(new Integer(oldID), new Integer(parentID));
		
                if (sccs != null)
                    oldSunidToSCCS.put(new Integer(sunid), sccs);
                if (sid != null)
                    oldSunidToSid.put(new Integer(sunid), sid);
                if (sunid > maxOldSunid)
                    maxOldSunid = sunid;

                rs2 = stmt2.executeQuery("select seq_id from astral_domain where node_id="+oldID+" and source_id=1 and (style_id=1 or style_id=3) limit 1");
                if (rs2.next())
                    oldSunidToSeq.put(new Integer(sunid), new Integer(rs2.getInt(1)));
                rs2.close();
            }
            rs.close();
            System.out.println(oldSunidToSCCS.size()+" nodes in old version");

            // map new ids
            newIDToSunid = new HashMap<Integer,Integer>();
            newSunidToID = new HashMap<Integer,Integer>();
            newIDToLevel = new HashMap<Integer,Integer>();
            newIDToDescription = new HashMap<Integer,String>();
            newDescriptionToID = new HashMap<String,Integer>();
            newSunidToSCCS = new HashMap<Integer,String>();
            newSunidToSid = new HashMap<Integer,String>();
            newSunidToSeq = new HashMap<Integer,Integer>();
            HashSet<String> newSids = new HashSet<String>();
            newParentID = new HashMap<Integer,Integer>();
            int maxNewSunid = 0;
            rs = stmt.executeQuery("select id, parent_node_id, description, sunid, sccs, sid, level_id from scop_node where release_id="+scopReleaseID);
            while (rs.next()) {
                int id = rs.getInt(1);
                int parentID = rs.getInt(2);
                String description = rs.getString(3);
                int sunid = rs.getInt(4);
                String sccs = rs.getString(5);
                String sid = rs.getString(6);
                int levelID = rs.getInt(7);
		
                newIDToSunid.put(new Integer(id), new Integer(sunid));
                if (newSunidToID.containsKey(new Integer(sunid)))
                    System.out.println("Sunid "+sunid+" used multiple times");
                newSunidToID.put(new Integer(sunid), new Integer(id));
                newIDToDescription.put(new Integer(id), description);
                newDescriptionToID.put(parentID+"_"+description,new Integer(id));
                newParentID.put(new Integer(id), new Integer(parentID));
                if (sccs != null)
                    newSunidToSCCS.put(new Integer(sunid), sccs);
                if (sid != null) {
                    newSunidToSid.put(new Integer(sunid), sid);
                    if (newSids.contains(sid))
                        System.out.println("Sid "+sid+" used multiple times");
                    newSids.add(sid);
                }
                newIDToLevel.put(new Integer(id), new Integer(levelID));
                if (sunid > maxNewSunid)
                    maxNewSunid = sunid;

                rs2 = stmt2.executeQuery("select seq_id from astral_domain where node_id="+id+" and source_id=1 and (style_id=1 or style_id=3) limit 1");
                if (rs2.next())
                    newSunidToSeq.put(new Integer(sunid), new Integer(rs2.getInt(1)));
                rs2.close();
		
            }
            rs.close();
            System.out.println(newIDToSunid.size()+" nodes in new version");

            // history
            history = new HashSet<String>();
            rs = stmt.executeQuery("select old_node_id, new_node_id, change_type_id from scop_history where release_id="+scopReleaseID);
            while (rs.next()) {
                int oldID = rs.getInt(1);
                int newID = rs.getInt(2); // should be 0 for null
                int changeID = rs.getInt(3);
                history.add(oldID+"_"+newID+"_"+changeID);
                history.add(oldID+"_"+changeID);
            }

            // check whether new sunids are consecutive
            for (int sunid = maxOldSunid+1; sunid <= maxNewSunid; sunid++) {
                if (newSunidToID.get(new Integer(sunid)) == null)
                    System.out.println("skipped sunid "+sunid);
            }

            // check whether any nodes have no children
            rs = stmt.executeQuery("select id from scop_node where release_id="+scopReleaseID+" and level_id<8 and id not in (select parent_node_id from scop_node where level_id>1 and release_id="+scopReleaseID+")");
            while (rs.next()) {
                int id = rs.getInt(1);
                System.out.println("no children: "+id);
            }
            rs.close();

            // check whether any descriptions are duplicate
            // check consistency of proteins in families
            rs = stmt.executeQuery("select n1.id, n2.id from scop_node n1, scop_node n2 where n1.release_id=n2.release_id and n1.id<n2.id and n1.release_id="+scopReleaseID+" and n1.level_id=n2.level_id and n1.parent_node_id=n2.parent_node_id and n1.description=n2.description");
            while (rs.next()) {
                int n1 = rs.getInt(1);
                int n2 = rs.getInt(2);
                System.out.println("duplicate description "+n1+" and "+n2);
            }

            // more checks
            checkSid();
            checkSunid();
            checkSCCS();
            checkMerges();
            checkDescriptions();
            checkHistory();
            checkSubsets();
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
