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
import java.util.regex.*;
import org.strbio.io.*;
import org.strbio.math.*;
import org.strbio.mol.*;
import org.strbio.util.*;
import org.strbio.IO;
import gov.lbl.scop.local.LocalSQL;

/**
   Fill in SCOP comment table from SCOP dir.com file, after running ParseDirDes
*/
public class ParseDirCom {
    final public static void linkPfam(int nodeID,
                                      String comment) throws Exception{
        int i=comment.indexOf("Pfam ");
        if (i==-1)
            return;
	
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs;
	
        while (i>-1) {
            boolean ok = true;
            for (int j=i+5; j<i+10; j++)
                if (!Character.isDigit(comment.charAt(j)))
                    ok = false;

            if (ok) {
                String acc = "PF"+comment.substring(i+5,i+10);
                rs = stmt.executeQuery("select node_id from link_pfam where node_id="+nodeID+" and pfam_accession=\""+acc+"\"");
                if (!rs.next()) {
                    stmt.executeUpdate("insert into link_pfam values ("+
                                       nodeID+", \""+
                                       acc+"\")");
                }
                rs.close();
            }
            i=comment.indexOf("Pfam ",i+1);
        }
        stmt.close();
    }

    final public static void linkPfamB(int nodeID,
                                       String comment) throws Exception{
        int i=comment.indexOf("PfamB ");
        if (i==-1)
            return;
	
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs;
	
        while (i>-1) {
            boolean ok = true;
            for (int j=i+6; j<i+12; j++)
                if (!Character.isDigit(comment.charAt(j)))
                    ok = false;

            if (ok) {
                String acc = "PB"+comment.substring(i+6,i+12);
                rs = stmt.executeQuery("select node_id from link_pfam where node_id="+nodeID+" and pfam_accession=\""+acc+"\"");
                if (!rs.next()) {
                    stmt.executeUpdate("insert into link_pfam values ("+
                                       nodeID+", \""+
                                       acc+"\")");
                }
                rs.close();
            }
            i=comment.indexOf("PfamB ",i+1);
        }
        stmt.close();
    }
    
    final public static void linkUniprot(int nodeID,
                                         String comment) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs;

        int i = comment.indexOf("http://www.uniprot.org/uniprot/");
        if (i > -1) {
            while (i>-1) {
                int j = comment.indexOf(">",i);
                int k = comment.indexOf("<",j);
                String acc = comment.substring(j+1,k);
                // check for valid uniprot regex
                if (Pattern.matches("[OPQ][0-9][A-Z0-9]{3}[0-9]|[A-NR-Z][0-9]([A-Z][A-Z0-9]{2}[0-9]){1,2}",acc)) {
                    rs = stmt.executeQuery("select node_id from link_uniprot where node_id="+nodeID+" and uniprot_accession=\""+acc+"\"");
                    if (!rs.next()) {
                        stmt.executeUpdate("insert into link_uniprot values ("+
                                           nodeID+", \""+
                                           acc+"\")");
                    }
                    rs.close();
                }
                i = comment.indexOf("http://www.uniprot.org/uniprot/",i+1);
            }
        }
        else {
            i = comment.indexOf("SQ ");
            while (i>-1) {
                if (i+9 <= comment.length()) {
                    String acc = comment.substring(i+3,i+9);
                    // check for valid 6-character regex
                    if (Pattern.matches("[OPQ][0-9][A-Z0-9]{3}[0-9]|[A-NR-Z][0-9][A-Z][A-Z0-9]{2}[0-9]",acc)) {
                        rs = stmt.executeQuery("select node_id from link_uniprot where node_id="+nodeID+" and uniprot_accession=\""+acc+"\"");
                        if (!rs.next()) {
                            stmt.executeUpdate("insert into link_uniprot values ("+
                                               nodeID+", \""+
                                               acc+"\")");
                        }
                        rs.close();
                    }
                }
                i = comment.indexOf("SQ ",i+1);
            }
        }
        stmt.close();
    }
    
    final public static void linkPubMed(int nodeID,
                                        String comment) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs;

        int i = comment.indexOf("PubMed ");
        while (i>-1) {
            int pubmedID = StringUtil.atoi(comment,i+7);
            rs = stmt.executeQuery("select node_id from link_pubmed where node_id="+nodeID+" and pubmed_id="+pubmedID);
            if (!rs.next()) {
                stmt.executeUpdate("insert into link_pubmed values ("+
                                   nodeID+", "+
                                   pubmedID+")");
            }
            rs.close();
            i = comment.indexOf("PubMed ",i+1);
        }
        stmt.close();
    }
    
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            ResultSet rs;

            String ver = argv[0].substring(17);
            int scopReleaseID = LocalSQL.lookupSCOPRelease(ver);
            if (scopReleaseID==0) {
                throw new Exception("Can't determine SCOP version from "+argv[0]);
            }

            // delete old comments
            stmt.executeUpdate("delete from scop_comment where node_id in (select id from scop_node where release_id="+scopReleaseID+")");

            // read dir.com from argv[0];
            BufferedReader infile = IO.openReader(argv[0]);
            String buffer = infile.readLine();
            while (buffer != null) {
                if (!buffer.startsWith("#")) {
                    // System.err.println(buffer);
                    int sunid = StringUtil.atoi(buffer);
                    int pos1 = buffer.indexOf(" ! ");
                    while ((pos1 != -1) && (pos1 < buffer.length())) {
                        int pos2 = buffer.indexOf(" ! ",pos1+1);
                        if (pos2==-1)
                            pos2 = buffer.length();
                        if (pos2 > pos1+3) {
                            String comment = buffer.substring(pos1+3,pos2).trim();

                            int isAuto = 0;

                            // these are guesses:
                            if (comment.startsWith("complexed with "))
                                isAuto = 1;
                            if (comment.startsWith("automatically matched to "))
                                isAuto = 1;

                            int nodeID = LocalSQL.lookupNodeBySunid(sunid,scopReleaseID);
                            if (nodeID==0) {
                                throw new Exception("error looking up sunid "+sunid);
                            }

                            // special handling of Pfam links
                            int i=comment.indexOf("http://pfam.sanger.ac.uk/family?acc=PF");
                            if (i>-1)
                                linkPfam(nodeID, comment);
                            i=comment.indexOf("http://pfam.sanger.ac.uk/pfamb?entry=PB");
                            if (i>-1)
                                linkPfamB(nodeID, comment);
			    
                            // special handling of Uniprot links
                            if (comment.indexOf("SQ ")>-1)
                                linkUniprot(nodeID, comment);

                            // special handling of PubMed links
                            if (comment.indexOf("PubMed ")>-1)
                                linkPubMed(nodeID, comment);
			    
                            // filter out links
                            if (comment.indexOf("http://")>-1)
                                comment = HTML.stripHTML(comment);

                            // other comments
                            stmt.executeUpdate("insert into scop_comment values (null,"+
                                               nodeID+", \""+
                                               StringUtil.replace(comment,"\"","\\\"")+"\", "+
                                               isAuto+")");
                        }
                        pos1 = pos2;
                    }
                }
                buffer = infile.readLine();
            }
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
