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
import gov.lbl.scop.util.ASTEROIDS.*;
import gov.lbl.scop.local.LocalSQL;

/**
   Dump chain-vs-domain hits in ASTEROIDS parsed format
*/
public class DumpASTEROIDS {
    final public static void main(String argv[]) {
        try {
            LocalSQL.connect();
            Statement stmt = LocalSQL.createStatement();
            Statement stmt2 = LocalSQL.createStatement();
            ResultSet rs, rs2;
            int fileType = -1;
            Annotation.Source source = Annotation.Source.UNKNOWN;
	    
            java.util.Date start=null;
            if (!argv[1].equals("000000"))
                start = ParsePDB.snapshotDateFormat.parse(argv[1]);
            java.util.Date end=ParsePDB.snapshotDateFormat.parse(argv[2]);
	    
            rs = stmt.executeQuery("select max(id) from scop_release where is_public=1");
            rs.next();
            int scopReleaseID = rs.getInt(1);
            rs.close();
	    
            int pfamReleaseID = 0;
	    
            // what kind of file
            if (argv[0].startsWith("blast"))   {
                fileType = 0;
                source = Annotation.Source.BLAST;
            }
            else if (argv[0].startsWith("pfam-tc")) {
                fileType = 1;
                source = Annotation.Source.PFAM;
            }
            else if (argv[0].startsWith("muscle")) {
                if (argv[0].endsWith("_fam.out")) {
                    fileType = 2;
                    source = Annotation.Source.FAM;
                }
                else if (argv[0].endsWith("_sf.out"))
                    fileType = 3;
                source = Annotation.Source.SF;
            }
            else if (argv[0].startsWith("merged")) {
                fileType=4;
                source = Annotation.Source.UNKNOWN;
                pfamReleaseID = LocalSQL.lookupPfamRelease(argv[3]);
                if (pfamReleaseID==0)
                    throw new Exception("Can't determine Pfam version from "+argv[3]);
            }
            else if (argv[0].startsWith("astral-newseqs")) {
                fileType=5;
                source = Annotation.Source.UNKNOWN;
            }
            if (fileType==-1)
                throw new Exception("Unknown file format "+argv[0]);
	    
            PrintfStream outfile = new PrintfStream(argv[0]);
	    
            Vector<Integer> ids = DumpSeqs.getNewSeqs(start,
                                                      end,
                                                      false, // unique
                                                      true, // ignore obs
                                                      true, // ignoreReject
                                                      2, // seqRes   
                                                      true, // ignore classified
                                                      false); // ignore reliable
            for (Integer i : ids) {
                if (fileType==5) {
                    rs = stmt.executeQuery("select seq, concat(sid,' ',header) from astral_domain where style_id=4 and id in (select domain_id from astral_chain_link_domain where chain_id="+i+") order by id");
                    while (rs.next()) {
                        Polymer p = new Polymer(rs.getString(1));
                        p.name = rs.getString(2);
                        p.writeFasta(outfile);
                    }
                    continue;
                }
		
                rs = stmt.executeQuery("select sid from astral_chain where id="+i);
                rs.next();
                outfile.printf(">%s\n",rs.getString(1));
		
                String query = null;
                if (fileType==0) {
                    query = "select m.id from astral_seq_blast m, astral_domain d, scop_node n, astral_chain ac where ac.id="+i+" and m.seq1_id=ac.seq_id and m.seq2_id=d.seq_id and d.node_id=n.id and d.source_id=2 and m.style1_id=1 and (m.style2_id = d.style_id or (m.style2_id=2 and d.style_id=1)) and m.blast_log10_e <= -4 and m.release2_id=n.release_id and n.release_id="+scopReleaseID;
                }
                else if (fileType==1) {
                    query = "select m.id from astral_seq_hmm_pfam m, pfam p, astral_chain ac where ac.seq_id=m.seq_id and ac.id="+i+" and m.pfam_id=p.id and m.log10_e <= -2 and p.release_id="+pfamReleaseID;
                }
                else if (fileType < 4) {
                    query = "select m.id from astral_seq_hmm_asteroids m, scop_node n, astral_chain ac where ac.seq_id=m.seq_id and ac.id="+i+" and m.node_id=n.id and m.log10_e <= -4 and n.level_id=";
                    if (fileType==2)
                        query += "5";
                    else
                        query += "4";
                    query += " and n.release_id="+scopReleaseID;
                }

                AnnotationSet as =
                    new AnnotationSet(i.intValue());
		
                if (query != null) {
                    rs = stmt.executeQuery(query);
                    while (rs.next()) {
                        Annotation a =
                            new Annotation(source,
                                           rs.getInt(1));
                        a.load(stmt2);
                        as.annotations.add(a);
                    }
                    Collections.sort(as.annotations);
                }
                else {
                    AnnotationSet as2 =
                        new AnnotationSet(i.intValue());
                    as2.loadAnnotations(stmt,
                                        scopReleaseID,
                                        pfamReleaseID);
                    for (Annotation a : as2.annotations)
                        as.annotate(a,10);
                    as.fillGaps(50);
                }
		
                for (Annotation a : as.annotations) {
                    outfile.printf("%s ",a.getHeaderName(fileType==4));
                    outfile.printf("%d ",a.getStart()+1);
                    outfile.printf("%d ",a.getEnd()+1);
                    outfile.printf("%s 0.0 ",a.getHeaderRegions());
                    if (a.log10E==-9999.0)
                        outfile.printf("-9999\n");
                    else if (a.log10E > -10.0)
                        outfile.printf("%0.14f\n",a.log10E);
                    else if (a.log10E > -100.0)
                        outfile.printf("%0.13f\n",a.log10E);
                    else
                        outfile.printf("%0.12f\n",a.log10E);
                }
            }
            outfile.flush();
            outfile.close();
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
