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
   Calculate stable sid for a release, based on a previous release.
*/
public class StableSid {
    /**
       keep map of ids to ids, from old to new version
       format is oldID_newID -> total overlap, times -1 if
       pair is "near-exact" match
    */
    private static HashMap<String,Integer> idMap = null;

    /**
       Keep track of keys in vector rather than using keySet(),
       so the keys always come back in original order
    */
    private static Vector<String> idMapKeys = null;

    /**
       all indexed sid assigned, including obsolete ones,
       ones in the old release, and ones assigned at any point
       in the new release
    */
    public static HashSet<String> usedSid = null;

    /**
       code+chain for full chains that were originally assigned
       sids as if they were regions, later corrected in the RE->CH
       assignments
    */
    public static HashSet<String> oldReCh = null;

    /**
       scop id in new release
    */
    public static int scopReleaseID = 0;

    /**
       get all the mappings for a particular id in the id map.
    */
    final public static Vector<Integer> getMappings(int id, boolean forward) {
        Vector<Integer> rv = new Vector<Integer>();
        for (String map : idMapKeys) {
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
       initialize list of used sids
    */
    final public static void initUsedSid() throws Exception {
        Statement stmt = LocalSQL.createStatement();
        int oldScopID = LocalSQL.getLatestSCOPRelease(false);
        ResultSet rs = stmt.executeQuery("select o.sid from scop_node o, scop_history h where h.old_node_id=o.id and o.level_id=8 and o.release_id<="+oldScopID);
        usedSid = new HashSet<String>();
        while (rs.next()) {
            String sid = rs.getString(1);
            if ((sid != null) && (!sid.endsWith("_")))
                usedSid.add(sid);
        }
        rs.close();
        rs = stmt.executeQuery("select sid from scop_node where release_id="+oldScopID+" and level_id=8");
        while (rs.next()) {
            String sid = rs.getString(1);
            if ((sid != null) && (!sid.endsWith("_")))
                usedSid.add(sid);
        }
        rs.close();
        stmt.close();
    }
    
    /**
       assign the next available sid, and add it to the collection
       of used sid.
    */
    final public static String assignNewSid(String code,
                                            char chain) throws Exception {
        if (chain== ' ')
            chain = '_';
        for (int i=1; i<=36; i++) {
            String sid = 'd'+code+chain;
            sid = sid.toLowerCase();
            if (i<=9)
                sid += i;
            else
                sid += (char)((int)'a'-10+i);
            if (usedSid==null)
                initUsedSid();
            if (!usedSid.contains(sid.toLowerCase())) {
                usedSid.add(sid.toLowerCase());
                return sid;
            }
        }
        throw new Exception ("error - too many sid used for "+code+chain);
    }

    /**
       assign a new sid to a description
    */
    final public static String assignNewSid(String description)
        throws Exception {
        String code = description.substring(0,4);
        String[] regions = description.substring(5).split(",");
	
        boolean needsIndex = false; // sid ends in 1, 2, etc
        if (description.lastIndexOf('-') > 5)
            needsIndex = true;

        char chain = ' ';
        for (String region : regions) {
            if (region.indexOf(':')==1) {
                char c = region.charAt(0);
                if (chain==' ')
                    chain = c;
                else if (chain != c) {
                    chain = '.'; // multichain
                    needsIndex = true;
                }
            }
        }

        // bug compatibility:  some chains got
        // "region-like" sid in 1.57
        if ((scopReleaseID < 5) &&
            (!needsIndex) &&
            (oldReCh != null) &&
            (oldReCh.contains(code+chain))) {
            needsIndex = true;
        }
	
        String sid = null;
        if (!needsIndex) {
            if (chain== ' ')
                chain = '_';
            sid = ('d'+code+chain+'_').toLowerCase();
        }
        else
            sid = assignNewSid(code,chain);

        return sid;
    }
    
    /**
       return sid in correct case
    */
    final public static String fixCase(String sid,
                                       int nodeID)
        throws Exception {
        char oldChain = sid.charAt(5);
        if (oldChain != '.') {
            Statement stmt = LocalSQL.createStatement();
            ResultSet rs = stmt.executeQuery("select c.chain from pdb_chain c, link_pdb m where m.node_id="+nodeID+" and m.pdb_chain_id=c.id limit 1");
            rs.next();
            char newChain = rs.getString(1).charAt(0);
            stmt.close();

            if (newChain==' ')
                newChain = '_';
            sid = sid.substring(0,5)+newChain+sid.substring(6);
        }
        return sid;
    }
    
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

            // clear out old SID
            if (!checkOnly)  {
                stmt.executeUpdate("update scop_node set sid=null where release_id="+scopReleaseID);
                // stmt.executeUpdate("delete from scop_history where release_id="+scopReleaseID);
            }

            // get obsolete SID
            System.out.println("getting obsolete sid");
            usedSid = new HashSet<String>();
            rs = stmt.executeQuery("select o.sid from scop_node o, scop_history h where h.old_node_id=o.id and o.level_id=8 and o.release_id<="+oldScopID);
            while (rs.next()) {
                String sid = rs.getString(1);
                if (!sid.endsWith("_"))
                    usedSid.add(sid);
            }

            // get current (old) history
            // keep list of silent changes
            HashMap<Integer,Integer> silentMap = new HashMap<Integer,Integer>();
            HashSet<String> oldHistory = new HashSet<String>();
            rs = stmt.executeQuery("select old_node_id, new_node_id, change_type_id from scop_history where release_id="+scopReleaseID+" and old_node_id in (select id from scop_node where release_id="+oldScopID+" and level_id=8)");
            while (rs.next()) {
                int oldID = rs.getInt(1);
                int newID = rs.getInt(2); // should be 0 for null
                int changeID = rs.getInt(3);
                oldHistory.add(oldID+"_"+newID+"_"+changeID);
                if (changeID==11)
                    silentMap.put(new Integer(newID), new Integer(oldID));
            }

            // map old bugs:  description changed, but same sid/px
            // even though these should have been treated as modification
            HashMap<String,String> oldBugs = new HashMap<String,String>();
            if (oldScopID==1) {
                oldBugs.put("1dgr N:,M:","1dgr M:,N:"); // accidently reverted, 1.51 -> 1.53
                oldBugs.put("1qrj B:16-130", "1qrj A:,B:16-130"); // reverted to avoid confusion, 1.51 -> 1.53
            }
            else if (oldScopID==9) {
                // this would be difficult to automate at this level:
                oldBugs.put("1gmc A:", "1gmc E:,F:,G:");
                oldBugs.put("1gmd A:", "1gmd E:,F:,G:");
                oldBugs.put("1gmh -", "1gmh E:,F:,G:");
                oldBugs.put("2gch -", "2gch E:,F:,G:");
                oldBugs.put("3gct A:", "3gct E:,F:,G:");
                oldBugs.put("4cha A:", "4cha A:,B:,C:");
                oldBugs.put("4cha B:", "4cha E:,F:,G:");
                oldBugs.put("4gch -", "4gch E:,F:,G:");
                oldBugs.put("5cha A:", "5cha A:,B:,C:");
                oldBugs.put("5cha B:", "5cha E:,F:,G:");
                oldBugs.put("5gch -", "5gch E:,F:,G:");
                oldBugs.put("6cha A:", "6cha A:,B:,C:");
                oldBugs.put("6cha B:", "6cha E:,F:,G:");
                oldBugs.put("6gch -", "6gch E:,F:,G:");
                oldBugs.put("7gch -", "7gch E:,F:,G:");
                oldBugs.put("8gch -", "8gch E:,F:,G:");
                oldBugs.put("1pca 4A-99A", "1pca A:4A-99A");
                oldBugs.put("1pca 1-308", "1pca B:1-308");
                oldBugs.put("1mfa 1L-111L", "1mfa L:1-111");
                oldBugs.put("1mfa 251H-367H", "1mfa H:251-367");
            }
            else if (oldScopID==11) {
                // pdb remediation chains that changed:
                oldBugs.put("1tk2 Y:", "1tk2 B:");
                oldBugs.put("1pca B:1-308", "1pca A:1-308");
                oldBugs.put("1z6e B:87-138", "1z6e L:87-138");
            }

            // code+chain for full chains that were assigned as regions,
            // for no apparent reason
            oldReCh = new HashSet<String>();
            oldReCh.add("1ezvC");
            oldReCh.add("1ezvF");
            oldReCh.add("1ezvG");
            oldReCh.add("1ezvH");
            oldReCh.add("1ezvI");
            oldReCh.add("1f6nL");
            oldReCh.add("1f6nM");
            oldReCh.add("1fnpL");
            oldReCh.add("1fnpM");
            oldReCh.add("1fnqL");
            oldReCh.add("1fnqM");
            oldReCh.add("1fzjB");
            oldReCh.add("1fzkB");
            oldReCh.add("1fzmB");
            oldReCh.add("1fzoB");
            oldReCh.add("1hpzB");
            oldReCh.add("1hqeB");
            oldReCh.add("1hquB");
            oldReCh.add("1hysB");
            oldReCh.add("1i4fB");
            oldReCh.add("1ikvB");
            oldReCh.add("1ikwB");
            oldReCh.add("1ikxB");
            oldReCh.add("1ikyB");
            oldReCh.add("1im3B");
            oldReCh.add("1im3F");
            oldReCh.add("1im3J");
            oldReCh.add("1im3N");
            oldReCh.add("1im9B");
            oldReCh.add("1im9F");
            oldReCh.add("1jf1B");
            oldReCh.add("1jgwL");
            oldReCh.add("1jgwM");
            oldReCh.add("1jgxL");
            oldReCh.add("1jgxM");
            oldReCh.add("1jgyL");
            oldReCh.add("1jgyM");
            oldReCh.add("1jgzL");
            oldReCh.add("1jgzM");
            oldReCh.add("1jh0L");
            oldReCh.add("1jh0M");
            oldReCh.add("1jhfB");
            oldReCh.add("1jhhB");
            oldReCh.add("1jhtB");
            oldReCh.add("1jlqB");

            // from 1.59:
            oldReCh.add("1i7rB");
            oldReCh.add("1i7rE");
            oldReCh.add("1i7tB");
            oldReCh.add("1i7tE");
            oldReCh.add("1i7uB");
            oldReCh.add("1i7uE");
            oldReCh.add("1jfeA");
            oldReCh.add("1jfeD");
            oldReCh.add("1jkhB");
            oldReCh.add("1jlaB");
            oldReCh.add("1jlbB");
            oldReCh.add("1jlcB");
            oldReCh.add("1jleB");
            oldReCh.add("1jlfB");
            oldReCh.add("1jlgB");
            oldReCh.add("1jpfB");
            oldReCh.add("1jpgB");
            oldReCh.add("1jwlC");
            oldReCh.add("1k8dB");
            oldReCh.add("1kohB");
            oldReCh.add("1kohD");
            oldReCh.add("1kooB");
            oldReCh.add("1kooD");

            // from 1.61:
            oldReCh.add("1kyoC");
            oldReCh.add("1kyoH");
            oldReCh.add("1kyoG");
            oldReCh.add("1kyoF");
            oldReCh.add("1kyoI");
            oldReCh.add("1kyoN");
            oldReCh.add("1kyoQ");
            oldReCh.add("1kyoR");
            oldReCh.add("1kyoS");
            oldReCh.add("1kyoT");
            oldReCh.add("1l9jS");
            oldReCh.add("1l9bL");
            oldReCh.add("1l9bM");
            oldReCh.add("1l9jL");
            oldReCh.add("1l9jM");
            oldReCh.add("1l9jR");
            oldReCh.add("1legB");
            oldReCh.add("1lekB");
            oldReCh.add("1k6lM");
            oldReCh.add("1k6lL");
            oldReCh.add("1k6nM");
            oldReCh.add("1k6nL");
            oldReCh.add("1kbiB");
            oldReCh.add("1kj2M");
            oldReCh.add("1kj2L");
            oldReCh.add("1kj3M");
            oldReCh.add("1kj3L");
            oldReCh.add("1lwuJ");
            oldReCh.add("1lwuG");
            oldReCh.add("1lwuD");
            oldReCh.add("1lwuA");
            oldReCh.add("1lshB");
            oldReCh.add("1m57I");
            oldReCh.add("1m57J");
            oldReCh.add("1m57C");
            oldReCh.add("1m57A");
            oldReCh.add("1m57D");
            oldReCh.add("1m57G");
            oldReCh.add("1m56A");
            oldReCh.add("1m56I");
            oldReCh.add("1m56J");
            oldReCh.add("1m56C");
            oldReCh.add("1m56D");
            oldReCh.add("1m56G");
            oldReCh.add("1m3xL");
            oldReCh.add("1m3xM");
            oldReCh.add("1m1jA");
            oldReCh.add("1m1jD");
            oldReCh.add("1jufB");
            oldReCh.add("1jtrM");
            oldReCh.add("1jtrL");
            oldReCh.add("1j5oB");
            oldReCh.add("1inqB");
            oldReCh.add("1gzqB");
            oldReCh.add("1gzpB");
            oldReCh.add("1g7pB");
            oldReCh.add("1g7qB");

            // map old descriptions to sids
            System.out.println("getting old mappings");
            HashMap<String,String> oldDescToSid = new HashMap<String,String>();
            HashMap<String,Integer> oldDescToID = new HashMap<String,Integer>();
            HashMap<Integer,String> oldIDToSid = new HashMap<Integer,String>();
            HashMap<String,String> oldSidToDesc = new HashMap<String,String>();
            HashMap<String,Integer> oldSidToID = new HashMap<String,Integer>();
            rs = stmt.executeQuery("select id, sid, description from scop_node where release_id="+oldScopID+" and level_id=8");
            while (rs.next()) {
                int nodeID = rs.getInt(1);
                String sid = rs.getString(2);
                String description = rs.getString(3);
                if (oldBugs.containsKey(description))
                    description = oldBugs.get(description);
                if (!oldDescToSid.containsKey(description))
                    oldDescToSid.put(description, sid);
                if (!oldDescToID.containsKey(description))
                    oldDescToID.put(description, new Integer(nodeID));
                oldSidToID.put(sid, new Integer(nodeID));
                oldIDToSid.put(new Integer(nodeID), sid);
                oldSidToDesc.put(sid, description);
                if (!sid.endsWith("_"))
                    usedSid.add(sid);
            }
            System.out.println(oldDescToSid.size()+" domains in old version");

            // map new descriptions to ids, and keep history
            System.out.println("getting new descriptions");
            HashSet<String> history = new HashSet<String>();
            HashMap<Integer,String> newIDToDesc = new HashMap<Integer,String>();
            HashMap<String,Integer> newDescToID = new HashMap<String,Integer>();
            HashMap<Integer,String> newIDToSid = new HashMap<Integer,String>();
            Vector<String> newDesc = new Vector<String>(); // ordered by id
            rs = stmt.executeQuery("select id, description from scop_node where release_id="+scopReleaseID+" and level_id=8 order by id");
            while (rs.next()) {
                int nodeID = rs.getInt(1);
                String description = rs.getString(2);

                if (newDesc.contains(description))
                    System.out.println("warning - duplicate description: "+description);
                newDesc.add(description);
                newDescToID.put(description, new Integer(nodeID));
                newIDToDesc.put(new Integer(nodeID), description);
            }
            System.out.println(newDesc.size()+" domains in new version");

            // map all identical nodes first, to save time
            Vector<String> tmpD = new Vector<String>(newDesc);
            for (String description : tmpD) {
                if (oldDescToSid.containsKey(description)) {
                    // System.out.println("mapped(1) "+description);
                    String sid = oldDescToSid.get(description);
                    Integer oldID = oldDescToID.get(description);
                    Integer id = newDescToID.get(description);

                    boolean oldCh = (sid.endsWith("_"));
                    boolean newCh = ((description.lastIndexOf('-') <= 5) &&
                                     (description.indexOf(',') == -1));
                    // if CH->RE or RE->CH assign new id
                    if ((!oldCh) && (newCh) && (scopReleaseID==5)) {
                        String oldSid = sid;
                        sid = assignNewSid(description);
                        System.out.println("re->ch: "+oldSid+" to "+sid);
                        history.add(oldID+"_"+id+"_10");
                    }
                    else if (oldBugs.containsValue(description)) {
                        String oldSid = sid;
                        sid = assignNewSid(description);

                        // if chain didn't change, use old sid
                        boolean chainChanged = (Character.toUpperCase(sid.charAt(5)) !=
                                                Character.toUpperCase(oldSid.charAt(5)));

                        if (!chainChanged)
                            sid = fixCase(oldSid,id.intValue());

                        if (scopReleaseID != 7) {
                            // in 1.67, we assigned new px
                            if (!oldSid.toLowerCase().equals(sid.toLowerCase())) {
                                System.out.println("same px1: "+oldSid+" to "+sid);
                                history.add(oldID+"_"+id+"_11");
                            }
                        }
                    }
                    else 
                        sid = fixCase(sid,id.intValue());

                    newIDToSid.put(id, sid);

                    // remove these from old map
                    // and to-do list as they're done
                    oldDescToSid.remove(description);
                    newDesc.remove(description);
                }
            }

            // map silent changes
            for (Integer id : silentMap.keySet()) {
                Integer oldID = silentMap.get(id);
                String sid = oldIDToSid.get(oldID);
                String oldDesc = oldSidToDesc.get(sid);
                String description = newIDToDesc.get(id);

                System.out.println("same px4: "+sid+" "+oldDesc+" -> "+description);
                history.add(oldID+"_"+id+"_11");

                newIDToSid.put(id, sid);

                // remove these from old map
                // and to-do list as they're done
                oldDescToSid.remove(oldDesc);
                newDesc.remove(description);
            }
	    
            System.out.println(newIDToSid.size()+" domains matched exactly");

            // map all remaining nodes next, using idMap
            tmpD = new Vector<String>(newDesc);
            idMap = new HashMap<String,Integer>();
            idMapKeys = new Vector<String>();
            for (String description : tmpD) {
                int newID = newDescToID.get(description).intValue();

                String code = description.substring(0,4);
                String[] regions = description.substring(5).split(",");

                boolean foundMatch = false;

                for (String oldDesc : oldDescToSid.keySet()) {
                    if (!oldDesc.startsWith(code))
                        continue;

                    int oldID = oldDescToID.get(oldDesc).intValue();
                    String[] regions2 = oldDesc.substring(5).split(",");

                    boolean allRegionsMatch = (regions.length == regions2.length);
                    int totalOverlap = 0;
		    
                    for (int j=0; j<regions2.length; j++) {
                        String region2 = regions2[j];
                        char chain2 = ' ';
                        int start2 = 0;  // ignore insert codes!
                        int end2 = 0;
                        boolean fakeBoundaries = false;
                        boolean fullChain1 = false;
                        boolean fullChain2 = false;
                        if (region2.indexOf(':')==1) {
                            chain2 = region2.charAt(0);
                            region2 = region2.substring(2);
                        }
                        if (region2.length() > 1) {  // to ignore "-"
                            start2 = StringUtil.atoi(region2);
                            int pos = region2.indexOf('-');
                            if (pos==0)
                                pos = region2.indexOf('-',1);
                            if (pos > 0)
                                end2 = StringUtil.atoi(region2,pos+1);
                        }
                        else {
                            fullChain2 = true;
			    
                            // full boundaries of chain, from RAF
                            int rafID = LocalSQL.findRAF(code,chain2,oldScopID);
                            if (rafID!=0) {
                                fakeBoundaries = true;
                                start2 = StringUtil.atoi(LocalSQL.getFirstRAF(rafID));
                                end2 = StringUtil.atoi(LocalSQL.getLastRAF(rafID));
                            }
                        }
			
                        // see whether any regions in 1 match
                        for (int i=0; i<regions.length; i++) {
                            String region1 = regions[i];
                            char chain1 = ' ';
                            int start1 = 0;  // ignore insert codes!
                            int end1 = 0;
                            if (region1.indexOf(':')==1) {
                                chain1 = region1.charAt(0);
                                region1 = region1.substring(2);
                            }
                            if (region1.length() > 1) {  // to ignore "-"
                                start1 = StringUtil.atoi(region1);
                                int pos = region1.indexOf('-');
                                if (pos==0)
                                    pos = region1.indexOf('-',1);
                                if (pos > 0)
                                    end1 = StringUtil.atoi(region1,pos+1);
                            }
                            else {
                                fullChain1 = true;
				
                                // full boundaries of chain, from RAF
                                int rafID = LocalSQL.findRAF(code,chain1,scopReleaseID);
                                if (rafID!=0) {
                                    fakeBoundaries = true;
                                    start1 = StringUtil.atoi(LocalSQL.getFirstRAF(rafID));
                                    end1 = StringUtil.atoi(LocalSQL.getLastRAF(rafID));
                                }
                            }

                            // # of residues to tolerate before changing sid
                            int minorChangeBothEnds = 50;
                            int minorChangeOneEnd = 75;
                            if (scopReleaseID < 5) {
                                minorChangeBothEnds = 70;
                                minorChangeOneEnd = 100;
                            }
                            if (scopReleaseID == 11) {
                                minorChangeOneEnd = 101;
                            }

                            boolean goodMatch = false;
                            if ((Character.toUpperCase(chain1)==
                                 Character.toUpperCase(chain2)) ||
                                ((chain1=='A') && (chain2==' '))) {
                                if ((fakeBoundaries) ||
                                    ((Math.abs(start1-start2) < minorChangeBothEnds) &&
                                     (Math.abs(end1-end2) < minorChangeBothEnds)) ||
                                    ((Math.abs(start1-start2) < minorChangeOneEnd) &&
                                     (end1==end2)) ||
                                    ((Math.abs(end1-end2) < minorChangeOneEnd) &&
                                     (start1==start2))) {
                                    goodMatch = true;
                                }
                                if ((fullChain1) || (fullChain2) ||
                                    ((start1 <= end2) &&
                                     (start2 <= end1))) {
                                    if ((fullChain1) && (fullChain2))
                                        totalOverlap += 1;
                                    else if (fullChain1)
                                        totalOverlap += end2-start2+1;
                                    else if (fullChain2)
                                        totalOverlap += end1-start1+1;
                                    else
                                        totalOverlap += Math.min(end1, end2) - Math.max(start1, start2) + 1;
                                }
                            }

                            if ((allRegionsMatch) &&
                                (i==j) &&
                                (!goodMatch))
                                allRegionsMatch = false;

                            if (description.startsWith("1imt")) {
                                System.out.println("debug: "+
                                                   description+" "+
                                                   oldDesc+" "+
                                                   start1+" "+
                                                   start2+" "+
                                                   end1+" "+
                                                   end2+" "+
                                                   chain1+" "+
                                                   chain2+" "+
                                                   totalOverlap+" "+
                                                   (allRegionsMatch ? "y" : "n"));
                            }
                        }
                    }

                    if (allRegionsMatch) {
                        if (description.startsWith("1imt"))
                            System.out.println("debug: "+description+" mapping "+oldID+" perfect match for "+newID);
                        idMap.put(oldID+"_"+newID,
                                  new Integer(0-totalOverlap));
                        idMapKeys.add(oldID+"_"+newID);
                    }
                    else if (totalOverlap > 0) {
                        if (description.startsWith("1imt"))
                            System.out.println("debug: "+description+" mapping "+oldID+" partial match for "+newID);
                        idMap.put(oldID+"_"+newID,
                                  new Integer(totalOverlap));
                        idMapKeys.add(oldID+"_"+newID);
                    }

                    if ((allRegionsMatch) ||
                        (totalOverlap > 0)) {
                        foundMatch = true;
                    }
                }

                // any unmatched ones get ids now
                if (!foundMatch) {
                    String sid = assignNewSid(description);
                    // wait to assign multichain sids until end
                    if (sid.charAt(5)=='.') {
                        // reject:
                        usedSid.remove(sid);
                    }
                    else {
                        newIDToSid.put(new Integer(newID), sid);
                        newDesc.remove(description);
                    }
                }
            }
            System.out.println(newIDToSid.size()+" domains assigned after new pdbs");

            // next, do any 1->many splits, and many->1 merges
            Vector<String> mapped = new Vector<String>(idMapKeys);
            for (String map : mapped) {
                int pos = map.indexOf('_');
                int oldID = StringUtil.atoi(map);
                int newID = StringUtil.atoi(map,pos+1);
                if (newIDToSid.containsKey(new Integer(newID)))
                    continue;

                // is old id split?
                Vector<Integer> fwdMappings = getMappings(oldID,true);
                boolean isSplit = (fwdMappings.size() > 1);
                if (isSplit) {
                    // make sure all map back only to original
                    for (Integer newID2 : fwdMappings) {
                        Vector<Integer> rvsMappings = getMappings(newID2,false);
                        if (rvsMappings.size() != 1)
                            isSplit = false;
                    }
                }
                if (isSplit) {
                    for (Integer newID2 : fwdMappings) {
                        String description = newIDToDesc.get(newID2);
                        String sid = assignNewSid(description);
                        newIDToSid.put(newID2, sid);
                        newDesc.remove(description);
                        String oldSid = oldIDToSid.get(oldID);
                        String oldDesc = oldSidToDesc.get(oldSid);
                        System.out.println("split: "+oldSid+" to "+sid+" "+oldDesc+" -> "+description);
                        history.add(oldID+"_"+newID2+"_5");
                        oldDescToSid.remove(oldSidToDesc.get(oldSid));
                        idMap.remove(oldID+"_"+newID2);
                        idMapKeys.remove(oldID+"_"+newID2);
                    }
                }

                // is new id merged?
                Vector<Integer> rvsMappings = getMappings(newID,false);
                boolean isMerge = (rvsMappings.size() > 1);
                if (isMerge) {
                    // make sure all map back only to original
                    for (Integer oldID2 : rvsMappings) {
                        fwdMappings = getMappings(oldID2,true);
                        if (fwdMappings.size() != 1)
                            isMerge = false;
                    }
                }
                if (isMerge) {
                    String description = newIDToDesc.get(new Integer(newID));
                    String sid = assignNewSid(description);
                    newIDToSid.put(newID, sid);
                    newDesc.remove(description);
                    for (Integer oldID2 : rvsMappings) {
                        String oldSid = oldIDToSid.get(oldID2);
                        String oldDesc = oldSidToDesc.get(oldSid);
                        System.out.println("merge: "+oldSid+" to "+sid+" "+oldDesc+" -> "+description);
                        history.add(oldID2+"_"+newID+"_4");
                        oldDescToSid.remove(oldSidToDesc.get(oldSid));
                        idMap.remove(oldID+"_"+newID);
                        idMapKeys.remove(oldID+"_"+newID);
                    }
                }
            }

            // next, assign any single hits with conserved sid (some re->ch)
            // these will have 0 or negative olap scores
            mapped = new Vector<String>(idMapKeys);
            for (String map : mapped) {
                Integer olap = idMap.get(map);
                if ((olap==null) ||
                    (olap.intValue()>0))
                    continue;
		
                int pos = map.indexOf('_');
                int oldID = StringUtil.atoi(map);
                int newID = StringUtil.atoi(map,pos+1);
                if (newIDToSid.containsKey(new Integer(newID)))
                    continue;

                Vector<Integer> fwdMappings = getMappings(oldID,true);
                Vector<Integer> rvsMappings = getMappings(newID,false);

                // there may be more than 1 "perfect" match, for short
                // domains.  In that case, pick the one with the
                // best overlap score
                if (rvsMappings.size() > 1) {
                    for (Integer oldID2 : rvsMappings) {
                        Integer olap2 = idMap.get(oldID2+"_"+newID);
                        if ((olap2 != null) &&
                            (olap2.intValue() < olap.intValue())) {
                            olap = olap2;
                            oldID = oldID2.intValue();
                        }
                    }
                }
		
                String sid = oldIDToSid.get(new Integer(oldID));
                String oldSid = sid;
                String oldDesc = oldSidToDesc.get(oldSid);
                String description = newIDToDesc.get(new Integer(newID));
                boolean oldCh = (sid.endsWith("_"));
                boolean newCh = !(description.lastIndexOf('-') > 5);
                // if CH->RE or RE->CH assign new id
                if (oldCh != newCh) {
                    sid = assignNewSid(description);
                    if (!oldCh) {
                        System.out.println("re->ch: "+oldSid+" to "+sid);
                        history.add(oldID+"_"+newID+"_10");
                    }
                    else {
                        System.out.println("mod1: "+oldSid+" to "+sid+" "+oldDesc+" -> "+description);
                        history.add(oldID+"_"+newID+"_7");
                    }
                }
                else {
                    sid = fixCase(sid,newID);

                    boolean chainChanged = (Character.toUpperCase(sid.charAt(5)) !=
                                            Character.toUpperCase(oldSid.charAt(5)));

                    // preserve bug in 1.71 -> 1.73
                    if ((chainChanged) &&
                        (!sid.endsWith("_")) &&
                        (scopReleaseID==10)) {
                        String sid2 = sid;
                        sid = assignNewSid(description);
                        if (!sid.equals(sid2))
                            System.out.println("1.73 fix: assigned "+sid+" to "+description);
                    }
		    
                    if (scopReleaseID != 7) {
                        if (!oldSid.toLowerCase().equals(sid.toLowerCase())) {
                            System.out.println("same px2: "+oldSid+" to "+sid);
                            history.add(oldID+"_"+newID+"_11");
                        }
                    }
                }
		    
                newIDToSid.put(newID, sid);
                newDesc.remove(description);

                oldDescToSid.remove(oldSidToDesc.get(oldSid));
                idMap.remove(oldID+"_"+newID);
                idMapKeys.remove(oldID+"_"+newID);
            }

            // finally, assign any remaining hits to new sid (mod),
            // in order by most overlap
            mapped = new Vector<String>(idMapKeys);
            for (String map : mapped) {
                Integer olap = idMap.get(map);
                if (olap==null)
                    continue;
                int bestOlap = olap.intValue();
                int pos = map.indexOf('_');
                int oldID = StringUtil.atoi(map);
                int newID = StringUtil.atoi(map,pos+1);
                if (newIDToSid.containsKey(new Integer(newID)))
                    continue;

                Vector<Integer> fwdMappings = getMappings(oldID,true);
                for (Integer newID2 : fwdMappings) {
                    // all these just get history
                    history.add(oldID+"_"+newID2+"_7");
                }
		
                Vector<Integer> rvsMappings = getMappings(newID,false);
                for (Integer oldID2 : rvsMappings) {
                    int olap2 = idMap.get(oldID2+"_"+newID).intValue();
                    if (olap2 > bestOlap) {
                        oldID = oldID2.intValue();
                        bestOlap = olap2;
                    }
                    idMap.remove(oldID2+"_"+newID);
                    idMapKeys.remove(oldID2+"_"+newID);

                    // all these get history
                    history.add(oldID2+"_"+newID+"_7");
                }

                String oldSid = oldIDToSid.get(new Integer(oldID));
                String oldDesc = oldSidToDesc.get(oldSid);
                String description = newIDToDesc.get(new Integer(newID));
                String sid = assignNewSid(description);
                // look for modifications that result in chains merging:
                if ((oldSid.endsWith("_")) &&
                    (sid.charAt(5)=='.')) {
                    if (scopReleaseID <= 5) {
                        // used to be counted as merge
                        System.out.println("merge: "+oldSid+" to "+sid+" "+oldDesc+" -> "+description);
                        history.add(oldID+"_"+newID+"_4");
                        // don't also count as mod:
                        history.remove(oldID+"_"+newID+"_7");
                    }
                    else if (scopReleaseID >= 9) {
                        // pdb chain trickery (when no real merge) gets same px:
                        System.out.println("same px3: "+oldSid+" to "+sid+" "+oldDesc+" -> "+description);
                        history.remove(oldID+"_"+newID+"_7");
                        history.add(oldID+"_"+newID+"_11");
                    }
                    else {
                        System.out.println("mod2: "+oldSid+" to "+sid+" "+oldDesc+" -> "+description);
                    }
                }
                else
                    System.out.println("mod2: "+oldSid+" to "+sid+" "+oldDesc+" -> "+description);
		
                newIDToSid.put(newID, sid);
                oldDescToSid.remove(oldSidToDesc.get(oldSid));
                newDesc.remove(description);
            }

            // last, assign new sids to anything left:
            tmpD = new Vector<String>(newDesc);
            for (String description : tmpD) {
                int newID = newDescToID.get(description).intValue();
                String sid = assignNewSid(description);
                newIDToSid.put(new Integer(newID), sid);
                newDesc.remove(description);
            }
		
            // remove all obsolete files from old list
            rs = stmt.executeQuery("select o.description from scop_node o, pdb_entry e, pdb_release r, pdb_chain c, link_pdb m, scop_release s where o.release_id="+oldScopID+" and o.level_id=8 and o.id=m.node_id and m.pdb_chain_id=c.id and c.pdb_release_id=r.id and r.pdb_entry_id=e.id and s.id="+scopReleaseID+" and e.obsolete_date<=s.freeze_date and e.obsolete_date is not null");
            while (rs.next()) {
                String description = rs.getString(1);
                if (oldDescToSid.containsKey(description)) {
                    System.out.println("obsoleted "+description);
                    oldDescToSid.remove(description);
                    Integer oldID = oldDescToID.get(description);
                    if (description.startsWith("0"))
                        history.add(oldID+"_0_3");
                    else if (description.startsWith("s"))
                        history.add(oldID+"_0_1");
                    else
                        history.add(oldID+"_0_2");
                }
            }

            // show all unmapped old files:
            // these need to be all chains, and marked as obsolete
            for (String description : oldDescToSid.keySet()) {
                System.out.println("?old "+description);
                Integer oldID = oldDescToID.get(description);
                if (description.startsWith("0"))
                    history.add(oldID+"_0_3");
                else if (description.startsWith("s"))
                    history.add(oldID+"_0_1");
                else
                    history.add(oldID+"_0_2");
            }

            // update all nodes with new sid, or just check
            for (Integer newID : newIDToSid.keySet()) {
                int id = newID.intValue();
                String sid = newIDToSid.get(newID).toLowerCase();

                if (checkOnly) {
                    rs = stmt.executeQuery("select sid from scop_node where id="+id);
                    if (rs.next()) {
                        String oldSid = rs.getString(1);

                        if (!oldSid.equals(sid))
                            System.out.println("sid mapping error: "+sid+" should be "+oldSid);
                    }
                }
                else {
                    stmt.executeUpdate("update scop_node set sid=\""+sid+"\" where id="+id);
                }
            }

            System.out.println("updating history");

            // add new nodes to history, or check:
            // see if we need to add type 11 nodes
            rs = stmt.executeQuery("select id from scop_history where release_id="+scopReleaseID+" and change_type_id=11 limit 1");
            boolean needs11Nodes = false;
            if (!rs.next()) {
                needs11Nodes = true;
            }
            for (String histLine : history) {
                String[] histVal = histLine.split("_");
                int oldID = StringUtil.atoi(histVal[0]);
                int newID = StringUtil.atoi(histVal[1]);
                int changeID = StringUtil.atoi(histVal[2]);

                if (checkOnly) {
                    if (oldHistory.contains(histLine)) {
                        oldHistory.remove(histLine);
                    }
                    else {
                        if (!((changeID==11) && (needs11Nodes)))
                            System.out.println("old history missing "+histLine);
                    }
                }
                if ((!checkOnly) &&
                    (!oldHistory.contains(histLine))) {
                    System.out.println("insert into scop_history values (null, "+
                                       oldID+", "+
                                       (newID==0 ? "null" : newID)+", "+
                                       scopReleaseID+", "+
                                       changeID+", now());");

                    /*
                      stmt.executeUpdate("insert into scop_history values (null, "+
                      oldID+", "+
                      (newID==0 ? "null" : newID)+", "+
                      scopReleaseID+", "+
                      changeID+", now())");
                    */
                }
            }
            if ((oldHistory != null) && (checkOnly)) {
                for (String histLine : oldHistory) {
                    System.out.println("missing history "+histLine);
                }
            }
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
