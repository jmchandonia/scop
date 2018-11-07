/*
 * Software to build and maintain SCOPe, https://scop.berkeley.edu/
 *
 * Copyright (C) 2009-2018 The Regents of the University of California
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
   Dump out master file for a given release.  Throws exception if
   release is mis-sorted.

   <pre>
   Version 1.04, 11/6/12 - requires (and checks) that nodes be in
   same order in table as in master file--this will cause problems
   with weekly added nodes, if we don't SortRelease before dump
   Version 1.03, 2/1/11 - fix for new species format, dumps superfamily
   and uniprot links
   Version 1.02, 5/27/09 - keeps original order exactly, rather than
   compressing all px from same entry into one line
   version 1.01, 5/26/09 - dumps out merges, old ids if asked
   version 1.0, 4/17/09 - original version
   </pre>

   @version 1.04, 11/6/12
*/
public class DumpMasterFile {
    final private static String VERSION = "1.04";

    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            Statement stmt2 = LocalSQL.createStatement();
            Statement stmt3 = LocalSQL.createStatement();
            ResultSet rs,rs2,rs3;

            boolean showMerge = false;
            boolean oldIDs = false;

            int scopReleaseID = LocalSQL.lookupSCOPRelease(argv[0]);

            if (argv.length > 1) {
                if (argv[1].startsWith("m")) {
                    showMerge = true;
                    oldIDs = true;
                }
                else if (argv[1].startsWith("o")) oldIDs = true;
            }

            rs = stmt.executeQuery("select freeze_date, release_date, is_public from scop_release where id="+scopReleaseID);
            rs.next();
            java.util.Date d = rs.getDate(1);
            SimpleDateFormat dfShort = new SimpleDateFormat ("yyyy-MM-dd");
            SimpleDateFormat dfLong = new SimpleDateFormat ("yyyy-MM-dd 'at' HH:mm:ss z");

            System.out.println("# SCOP Masterfile");
            System.out.print("# SCOP release "+argv[0]+" (frozen "+dfShort.format(d)+", released ");
            d = rs.getDate(2);
            boolean isPublic = (rs.getInt(3)==1);
            System.out.println(dfShort.format(d)+", "+(isPublic?"":"not ")+"public)");
            if (oldIDs)
                System.out.println("# not showing sunids assigned since last version");
            if (showMerge)
                System.out.println("# showing merged sunids for domains");
            System.out.println("# produced by gov.lbl.scop.app.DumpMasterFile "+VERSION);
            d = new java.util.Date();
            System.out.println("# on "+dfLong.format(d));
            System.out.println("#");

            int maxOldSunid = 0;
            if (oldIDs) {
                // note:  currently includes non-public releases:
                rs = stmt.executeQuery("select max(sunid) from scop_node where release_id<"+scopReleaseID);
                rs.next();
                maxOldSunid = rs.getInt(1);
            }

            // maps new ids (not sunids) to string with all merged sunids (x+y)
            HashMap<Integer,String> mergedPX = null;
            if (showMerge) {
                mergedPX = new HashMap<Integer,String>();
                rs = stmt.executeQuery("select distinct(new_node_id) from scop_history where release_id="+scopReleaseID+" and change_type_id=4 and new_node_id in (select id from scop_node where release_id="+scopReleaseID+" and level_id=8)");
                while (rs.next()) {
                    int newID = rs.getInt(1);
                    mergedPX.put(new Integer(newID), null);
                }
                HashSet<Integer> keys = new HashSet<Integer>(mergedPX.keySet());
                for (Integer id : keys) {
                    String s = null;
                    rs =stmt.executeQuery("select n.sunid from scop_node n, scop_history h where h.new_node_id="+id+" and h.old_node_id=n.id and h.release_id="+scopReleaseID+" and h.change_type_id=4");
                    while (rs.next()) {
                        if (s==null)
                            s = ""+rs.getInt(1);
                        else
                            s += "+"+rs.getInt(1);
                    }
                    mergedPX.put(id,s);
                }
            }

            // class names on CF field:
            HashMap<Integer,String> className = new HashMap<Integer,String>();
            className.put(new Integer(46456), "a");
            className.put(new Integer(48724), "b");
            className.put(new Integer(51349), "ab");
            className.put(new Integer(53931), "apb");
            className.put(new Integer(56572), "md");
            className.put(new Integer(56835), "mcs");
            className.put(new Integer(56992), "sp");
            className.put(new Integer(57942), "cc");
            className.put(new Integer(58117), "lr");
            className.put(new Integer(58231), "pe");
            className.put(new Integer(58788), "pd");

            // keep track of all the px ids (not sunids) we've printed
            HashSet<Integer> printedPX = new HashSet<Integer>();

            // don't put blank line before first CF in each CL:
            boolean firstCF = false;

            // track last parent; make sure tree is consistent
            int[] parentIDs = new int[7];

            // nodes must be in order:
            rs = stmt.executeQuery("select n.id, n.sunid, n.description, l.id, l.master_abbreviation, n.parent_node_id from scop_node n, scop_level l where n.level_id=l.id and n.release_id="+scopReleaseID+" order by n.id");
            while (rs.next()) {
                int id = rs.getInt(1);
                int sunid = rs.getInt(2);
                String description = rs.getString(3);
                int levelID = rs.getInt(4);
                String level = rs.getString(5);
                int parentID = rs.getInt(6);

                // check consistency
                if (levelID > 1)
                    if (parentID != parentIDs[levelID-2])
                        throw new Exception ("Parent node error for node "+id+"; may be unsorted");
                if (levelID < 8)
                    parentIDs[levelID-1] = id;

                String sunidString = null;
                if ((showMerge) && (levelID==8) &&
                    (mergedPX.containsKey(new Integer(id)))) {
                    sunidString = mergedPX.get(new Integer(id));
                }
                else if ((oldIDs) && (sunid > maxOldSunid))
                    sunidString = null;
                else if (sunid != 0)
                    sunidString = ""+sunid;

                if (levelID==1)
                    continue;

                if ((levelID==8) && (printedPX.contains(new Integer(id))))
                    continue;

                // if level has only one child, and same description,
                // make this description blank
                if (levelID < 6) {
                    String childDescription = null;
                    rs2 = stmt2.executeQuery("select description from scop_node where parent_node_id="+id+" limit 2");
                    if (rs2.next())
                        childDescription = rs2.getString(1);
                    if (rs2.next())
                        childDescription = null;
                    if ((childDescription != null) &&
                        (description.equals(childDescription)))
                        description = "";
                }

                // hacks to mangle level appropriately
                if (levelID==2) {
                    System.out.println();
                    if (sunidString != null)
                        description += " ["+sunidString+"]";
                    firstCF = true;
                }
                if (levelID==3) {
                    if (firstCF)
                        firstCF = false;
                    else
                        System.out.println();
                    int clSunid = LocalSQL.getSunid(parentID);
                    String clName = className.get(new Integer(clSunid));
                    if (clName != null) {
                        if (description.length()>0)
                            description += " ";
                        description += "cl "+clName;
                    }
                }
                if (levelID==7) {
                    rs2 = stmt2.executeQuery("select s.ncbi_taxid from link_species l, species s where l.node_id="+id+" and l.species_id=s.id and s.ncbi_taxid is not null");
                    if (rs2.next()) {
                        int taxid = rs2.getInt(1);
                        int pos1 = description.indexOf("[TaxId:");
                        if (pos1 > -1) {
                            int pos2 = description.indexOf("]",pos1+1);
                            description = description.substring(0,pos1)+
                                "<a href=\"http://www.ncbi.nih.gov/Taxonomy/Browser/wwwtax.cgi?id="+taxid+"&lvl=0\" class=\"taxid\">"+
                                description.substring(pos1,pos2+1)+
                                "</a>"+
                                description.substring(pos2+1);
                        }
                    }
                }

                if (levelID<8) {
                    System.out.print(level);
                    if (description.length()>0)
                        System.out.print(" "+description);
                    if (sunidString != null)
                        System.out.println(" #$ "+sunidString);
                    else
                        System.out.println();

                    // print link to superfam
                    if (levelID==4) {
                        System.out.println("! <a href=\"http://supfam.mrc-lmb.cam.ac.uk/SUPERFAMILY/cgi-bin/scop.cgi?sunid="+sunid+"\"><img src=\"/img/superfamily-s.gif\" alt=\"link to SUPERFAMILY database\"><em>uperfamily</em></a>");
                    }
                }
                else {
                    // combine multiple nodes into one line
                    String code = description.substring(0,4);
                    System.out.print("ID "+code.toUpperCase());

                    // only merge nodes	with the exact same set of comments
                    String allComments = "";
                    rs2 = stmt2.executeQuery("select description from scop_comment where node_id="+id+" and is_autogenerated=0 order by id");
                    while (rs2.next())
                        allComments += rs2.getString(1)+" ! ";

                    // figure out whether RE, CH, or neither
                    int pos = description.indexOf('-');
                    int idType = 0;
                    if (pos == -1)
                        idType = 1; // CH
                    else if (pos != 5)
                        idType = 2; // RE

                    if (idType == 0) {
                        if (sunidString != null) {
                            System.out.println(" #$ "+sunidString);
                        }
                        printedPX.add(new Integer(id));
                    }
                    else {
                        rs2 = stmt2.executeQuery("select c.pdb_release_id from pdb_chain c, link_pdb l where l.pdb_chain_id=c.id and l.node_id="+id);
                        rs2.next();
                        int releaseID = rs2.getInt(1);
                        if (idType==1)
                            System.out.print(" CH ");
                        else
                            System.out.print(" RE ");
                        String sunids = " #$";
                        int lastID = id-1;
                        int nSunids = 0; // # of sunids printed on this line
                        int nDesc = 0; // # of descriptions on this line
                        rs2 = stmt2.executeQuery("select n.id, n.sunid, n.description from scop_node n, link_pdb l, pdb_chain c where n.parent_node_id="+parentID+" and n.description "+(idType==1 ? "NOT" : "")+" like \"%-%\" and l.node_id=n.id and l.pdb_chain_id=c.id and c.pdb_release_id="+releaseID+" group by n.id order by n.id");
                        while (rs2.next()) {
                            int id2 = rs2.getInt(1);
                            sunid = rs2.getInt(2);
                            description = rs2.getString(3).substring(5);

                            // skip if not same set of comments
                            String allComments2 = "";
                            rs3 = stmt3.executeQuery("select description from scop_comment where node_id="+id2+" and is_autogenerated=0 order by id");
                            while (rs3.next())
                                allComments2 += rs3.getString(1)+" ! ";
                            /*
                              if (code.equals("1c7c")) {
                              System.err.println("DEBUG: "+description+" "+sunid+" "+allComments+" "+allComments2);
                              }
                            */
                            if (!allComments.equals(allComments2))
                                continue;

                            if (printedPX.contains(new Integer(id2)))
                                continue;

                            if (id2 != lastID+1)
                                break;
                            lastID = id2;

                            sunidString = null;
                            if ((showMerge) && 
                                (mergedPX.containsKey(new Integer(id2)))) {
                                sunidString = mergedPX.get(new Integer(id2));
                            }
                            else if ((oldIDs) && (sunid > maxOldSunid))
                                sunidString = null;
                            else if (sunid != 0)
                                sunidString = ""+sunid;

                            /*
                              if (code.equals("1c7c"))
                              System.err.println("DEBUG: "+sunidString);
                            */
			    
                            if (nDesc > 0)
                                System.out.print(";");
                            if (idType==1)
                                description = description.replaceAll(":","");
                            if ((sunidString != null) && (nSunids == nDesc)) {
                                sunids += " "+sunidString;
                                nSunids++;
                            }
                            System.out.print(description);
                            nDesc++;
                            printedPX.add(new Integer(id2));
                        }
                        /*
                          if (code.equals("1wlp"))
                          System.err.println("DEBUG: "+nSunids+" "+sunids);
                        */
                        if (nSunids > 0)
                            System.out.println(sunids);
                        else
                            System.out.println();
                    }
                }

                // print comments related to this node
                rs2 = stmt2.executeQuery("select description from scop_comment where node_id="+id+" and is_autogenerated=0 order by id");
                while (rs2.next()) {
                    String comment = rs2.getString(1);
                    if (comment.indexOf("SQ ") > -1) {
                        // add in uniprot links
                        rs3 = stmt3.executeQuery("select uniprot_accession from link_uniprot where node_id="+id);
                        while (rs3.next()) {
                            String acc = rs3.getString(1);
                            int pos = comment.lastIndexOf(acc);
                            while (pos > -1) {
                                comment = comment.substring(0,pos)+"<a href=\"http://www.uniprot.org/uniprot/"+acc+"\">"+acc+"</a>"+comment.substring(pos+6);
                                pos = comment.lastIndexOf(acc,pos);
                            }
                        }
                    }

                    if ((comment.indexOf("Pfam ") > -1) ||
                        (comment.indexOf("PfamB ") > -1)) {
                        // add in pfam links
                        rs3 = stmt3.executeQuery("select pfam_accession from link_pfam where node_id="+id);
                        while (rs3.next()) {
                            String acc = rs3.getString(1);
                            String acc2;
                            boolean pfamA = true;
                            String url = "<a href=\"http://pfam.sanger.ac.uk/";
                            if (acc.charAt(1)=='F') {
                                acc2 = "Pfam "+acc.substring(2);
                                url += "family?acc=";
                            }
                            else {
                                acc2 = "PfamB "+acc.substring(2);
                                url += "pfamb?entry=";
                            }
                            int pos = comment.lastIndexOf(acc2);
                            while (pos > -1) {
                                comment = comment.substring(0,pos)+url+acc+"\">"+acc2+"</a>"+comment.substring(pos+acc2.length());
                                pos = comment.lastIndexOf(acc2,pos);
                            }
                        }
                    }
		    
                    System.out.println("! "+comment);
                }
            }
            System.out.println("END");
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
