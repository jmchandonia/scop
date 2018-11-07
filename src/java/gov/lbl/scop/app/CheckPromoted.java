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
import java.util.regex.*;
import gov.lbl.scop.local.*;
import gov.lbl.scop.util.*;

/**
   Check for:

   1) missing history

   for domains:
   
   2) missing raf
   3) missing chain seq
   4) missing/null domain seq
   5) missing pdb-style file
   6) missing thumbnail
   7) missing rep for levels above
*/
public class CheckPromoted {
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            Statement stmt2 = LocalSQL.createStatement();

            ResultSet rs, rs2;

            int scopReleaseID = LocalSQL.lookupSCOPRelease(argv[0]);
            if (scopReleaseID==0)
                throw new Exception("Can't determine SCOP version from "+argv[0]);

            boolean fix = false;
            if ((argv.length > 1) &&
                (argv[1].equals("fix")))
                fix = true;

            // get max sunid from stable release
            rs = stmt.executeQuery("select min(n.sunid) from scop_node n, scop_history h where n.id=h.old_node_id and n.release_id="+scopReleaseID+" and n.release_id=h.release_id and h.change_type_id=12");
            rs.next();
            int maxStableSunid = rs.getInt(1)-1;
            rs.close();
            if (maxStableSunid == -1) {
                // all are stable
                rs = stmt.executeQuery("select max(sunid) from scop_node where release_id="+scopReleaseID);
                rs.next();
                maxStableSunid = rs.getInt(1);
                rs.close();
            }

            // get all chains covered in a release
            rs = stmt.executeQuery("select id, level_id, sunid, sid from scop_node where curation_type_id>2 and release_id="+scopReleaseID+" and (level_id=8 or sunid>"+maxStableSunid+")");
            while (rs.next()) {
                int nodeID = rs.getInt(1);
                int levelID = rs.getInt(2);
                int sunid = rs.getInt(3);
                String sid = rs.getString(4);

                if (sunid > maxStableSunid) {
                    rs2 = stmt2.executeQuery("select id from scop_history where old_node_id="+nodeID+" and release_id="+scopReleaseID+" and change_type_id=12");
                    if (!rs2.next()) {
                        System.out.println("missing history for "+(levelID==8 ? sid : sunid));
                        rs2.close();
                        boolean fixed = false;
                        if (fix) {
                            rs2 = stmt2.executeQuery("select h.time_occurred from scop_history h, scop_node n where n.sunid>="+(sunid-5)+" and n.sunid<="+(sunid+5)+" and n.id=h.old_node_id and h.change_type_id=12 and h.release_id=n.release_id and n.release_id="+scopReleaseID+" limit 1");
                            if (rs2.next()) {
                                String t = rs2.getString(1);
                                rs2.close();
                                stmt2.executeUpdate("insert into scop_history values (null, "+nodeID+", null, "+scopReleaseID+", 12, \""+t+"\")");
                                System.out.println("  fixed");
                                fixed = true;
                            }
                            rs2.close();
                        }
                        if (!fixed)
                            continue;
                    }
                    else
                        rs2.close();
                }

                if (levelID < 8)
                    continue;

                int pdbChainID = 0;
                rs2 = stmt2.executeQuery("select pdb_chain_id from link_pdb where node_id="+nodeID);
                if (rs2.next()) {
                    pdbChainID = rs2.getInt(1);
                }
                else {
                    System.out.println("missing chain link for "+sid);
                    rs2.close();
                    boolean fixed = false;
                    if (fix) {
                        MakeLinks.linkPDB(nodeID);
                        rs2 = stmt2.executeQuery("select pdb_chain_id from link_pdb where node_id="+nodeID);
                        if (rs2.next())
                            pdbChainID = rs2.getInt(1);
                        fixed = true;
                    }
                    if (!fixed)
                        continue;
                }
                rs2.close();
		
                int rafID = 0;
                rs2 = stmt2.executeQuery("select id from raf where first_release_id<="+scopReleaseID+" and last_release_id>="+scopReleaseID+" and pdb_chain_id="+pdbChainID);
                if (rs2.next()) {
                    rafID = rs2.getInt(1);
                }
                else {
                    System.out.println("missing raf for "+sid);
                    // stmt2.executeUpdate("delete from link_pdb where node_id="+nodeID);
                    boolean fixed = false;
                    if (fix) {
                        rafID = FreezeRAF.addRAF(pdbChainID, scopReleaseID);
                        if (rafID > 0)
                            fixed = true;
                    }
                    if (!fixed)
                        continue;
                }
                rs2.close();

                rs2 = stmt2.executeQuery("select id from astral_chain where raf_id="+rafID);
                if (!rs2.next()) {
                    System.out.println("missing chain seq for "+sid);
                    rs2.close();
                    boolean fixed = false;
                    if (fix) {
                        MakeChainSeq.makeChainSeq(rafID);
                        fixed = true;
                    }
                    if (!fixed)
                        continue;
                }
                rs2.close();

                rs2 = stmt2.executeQuery("select id, seq_id from astral_domain where node_id="+nodeID+" and seq_id != 43558");
                if (!rs2.next()) {
                    System.out.println("missing/null domain seq for "+sid);
                    rs2.close();
                    boolean fixed = false;
                    if (fix) {
                        stmt2.executeUpdate("delete from astral_domain where node_id="+nodeID);
                        MakeDomainSeq.makeDomainSeqs(nodeID,false);
                        fixed = true;
                    }
                    if (!fixed)
                        continue;
                }
                else {
                    rs2.close();
                }

                boolean validPDBStyle = false;
                rs2 = stmt2.executeQuery("select file_path from scop_node_pdbstyle where node_id="+nodeID);
                if (!rs2.next()) {
                    System.out.println("missing pdb-style link for "+sid);
                }
                else {
                    String path = rs2.getString(1);
                    File f = new File(path);
                    if (f.exists())
                        validPDBStyle = true;
                    else
                        System.out.println("missing pdb-style file for "+sid);
                }
                rs2.close();
                if (!validPDBStyle) {
                    boolean fixed = false;
                    if (fix) {
                        rs2 = stmt2.executeQuery("select d.id from astral_domain d, scop_node n, astral_seq s where d.seq_id=s.id and d.node_id=n.id and length(s.seq) > 0 and d.source_id=2 and (d.style_id=1 or d.style_id=3) and n.id="+nodeID);
                        while (rs2.next()) {
                            int domainID = rs2.getInt(1);
                            MakePDBStyle.makePDBStyle(domainID);
                        }
                        rs2.close();
                        fixed = true;
                    }
                    if (!fixed)
                        continue;
                }

                rs2 = stmt2.executeQuery("select main from scop_node_thumbnail where node_id="+nodeID+" and main is not null");
                if (!rs2.next()) {
                    System.out.println("missing thumbnail file for "+sid);
                    rs2.close();
                    boolean fixed = false;
                    if (fix) {
                        try {
                            MakeThumbnails.makeThumbnail(nodeID,true);
                            fixed = true;
                        }
                        catch (Exception e2) {
                            e2.printStackTrace();
                        }
                    }
                    if (!fixed)
                        continue;
                }
                rs2.close();

                // fill in missing representative subset data
                // and promote thumbnail to parents, if needed
                HashSet<Integer> parentIDs = new HashSet<Integer>();
                for (levelID=5; levelID<8; levelID++) {
                    int levelNodeID = LocalSQL.findParent(nodeID,levelID);
                    if (levelNodeID == 0) {
                        System.out.println("missing parent at level "+levelID+" for "+sid+"; can't fix!");
                    }
                    else {
                        rs2 = stmt2.executeQuery("select rep_node_id from scop_subset_level where level_node_id="+levelNodeID);
                        if (rs2.next())
                            rs2.close();
                        else {
                            System.out.println("missing rep for level node "+levelNodeID);
                            rs2.close();
                            boolean fixed = false;
                            if (fix) {
                                stmt2.executeUpdate("insert into scop_subset_level values ("+nodeID+", "+levelNodeID+")");
                                MakeThumbnails.copyRepThumbnails(nodeID, levelNodeID);
                                fixed = true;
                            }
                            if (!fixed)
                                continue;
                        }
                    }
                }
            }
            rs.close();
            stmt2.close();
            stmt.close();
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
