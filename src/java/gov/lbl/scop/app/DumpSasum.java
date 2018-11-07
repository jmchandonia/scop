/*
 * Software to build and maintain SCOPe, https://scop.berkeley.edu/
 *
 * Copyright (C) 2010-2018 The Regents of the University of California
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
   Dump BLAST results in sasum format, for comparison to previous results

   <pre>
   Version 1.21, 5/27/11 - more fixes for compatibility with old files
   Version 1.2, 5/9/11 - more fixes for compatibility with old files
   Version 1.1, 6/16/10 - update to fix bg dump
   Version 1.0, 5/14/10 - original version
   </pre>
*/
public class DumpSasum {
    final public static void main(String argv[]) {
        try {
            LocalSQL.connect();
            Statement stmt = LocalSQL.createStatementOneRow();
            ResultSet rs;

            PrintfStream outfile;

            int pos = argv[0].lastIndexOf('-');
            int scopReleaseID = LocalSQL.lookupSCOPRelease(argv[0].substring(pos+1));
            boolean isChain = (argv[0].indexOf("-chain-")>-1);
            int sourceID = 1;  // ATOM
            if (argv[0].indexOf("-seqres-")>-1) {
                if ((scopReleaseID < 6) && (isChain))
                    sourceID = 3;
                else 
                    sourceID = 2;
            }
            boolean isBiB = (argv[0].indexOf(".bib.")>-1);
            boolean isGD = (argv[0].indexOf("-gd-")>-1);

            outfile = new PrintfStream(argv[0]);
            outfile.printf("$Id: DumpSasum $\n");
            outfile.printf("Expect 0 fa, 0 sf, 0 cf, 0 cl\n");

            if (isChain) {
                rs = stmt.executeQuery("select c1.sid, c2.sid, m.blast_log10_e, m.pct_identical, m.seq1_start, m.seq1_length, m.seq2_start, m.seq2_length from astral_seq_blast m, astral_chain c1, astral_chain c2, raf r1, raf r2, astral_seq s1, astral_seq s2 where c1.id!=c2.id and c1.seq_id=s1.id and c2.seq_id=s2.id and m.seq1_id=s1.id and m.seq2_id=s2.id and m.release_id="+scopReleaseID+" and m.style1_id=1 and m.style2_id=1 and s1.is_reject=0 and s2.is_reject=0 and m.source_id=c1.source_id and c1.source_id="+sourceID+" and c2.source_id=c1.source_id and c1.raf_id=r1.id and c2.raf_id=r2.id and r1.first_release_id <= "+scopReleaseID+" and r1.last_release_id >= "+scopReleaseID+" and r2.first_release_id <= "+scopReleaseID+" and r2.last_release_id >= "+scopReleaseID+" order by c1.sid,c2.sid,m.blast_log10_e");
            }
            else {
                int styleID = 2;  // original style
                if (isGD)
                    styleID = 3;

                rs = stmt.executeQuery("select d1.sid, d2.sid, m.blast_log10_e, m.pct_identical, m.seq1_start, m.seq1_length, m.seq2_start, m.seq2_length, n1.sccs, n2.sccs from astral_seq_blast m, astral_domain d1, astral_domain d2, scop_node n1, scop_node n2, astral_seq s1, astral_seq s2 where d1.id!=d2.id and d1.seq_id=s1.id and d2.seq_id=s2.id and m.seq1_id=s1.id and m.seq2_id=s2.id and s1.is_reject=0 and s2.is_reject=0 and d1.source_id="+sourceID+" and d2.source_id=d1.source_id and d1.source_id=m.source_id and (d1.style_id=1 or d1.style_id="+styleID+") and (d2.style_id=1 or d2.style_id="+styleID+") and m.style1_id=m.style2_id and m.style1_id="+styleID+" and d1.node_id=n1.id and d2.node_id=n2.id and n1.release_id = "+scopReleaseID+" and n2.release_id = n1.release_id and m.release_id=n1.release_id order by d1.sid,d2.sid,m.blast_log10_e");
            }

            String lastSid1 = null;
            String lastSid2 = null;
            while (rs.next()) {
                String sid1 = rs.getString(1);
                String sid2 = rs.getString(2);
		
                // calc SCOP similarity first, skip if NTSC
                String similarity;
                if (isChain) {
                    similarity = "x";
                }
                else {
                    String[] sccs1 = rs.getString(9).split("\\.");
                    String[] sccs2 = rs.getString(10).split("\\.");
                    if ((sccs1[0].charAt(0) > 'g') ||
                        (sccs2[0].charAt(0) > 'g')) {
                        continue;  // sasum doesn't contain ntsc domains
                    }
                    int i=0;
                    for (i=0; i<4; i++) {
                        if (!sccs1[i].equals(sccs2[i]))
                            break;
                    }
                    similarity = new Integer(i).toString();
                }

                // bib only contains 1 line per pair
                if (isBiB &&
                    sid1.equals(lastSid1) &&
                    sid2.equals(lastSid2))
                    continue;

                lastSid1 = sid1;
                lastSid2 = sid2;

                // don't print bg lines where length is 0.
                // these are actually due to bugs in old SCOP
                // versions (known: 1.59, d117ea_ d117eb_)
                // but kept for backward compatibility
                int l1 = rs.getInt(6);
                int l2 = rs.getInt(8);
                if ((!isBiB) &&
                    ((l1==0) || (l2==0)))
                    continue;
		
                // print line starting with sids
                outfile.printf("%s ",sid1);
                outfile.printf("%s ",sid2);
		
                if (isBiB) {
                    outfile.printf("%.2f ",rs.getDouble(4));
                    outfile.printf("%s\n",similarity);
                }
                else {
                    double log10E = rs.getDouble(3);
                    if (log10E==-9999.0) {
                        outfile.printf("0 ");
                    }
                    else {
                        int coef = 0;
                        if (log10E <= -4) {
                            coef = (int)Math.floor(log10E);
                            log10E -= (double)coef;
                        }
                        double E = Math.pow(10,log10E);
                        if (coef==0) {
                            // print 3 significant digits
                            char[] b = new char[10];
                            // System.err.println(E+" "+log10E+" "+coef);
                            StringUtil.sprintf(b,"%0.6f",E);
                            int i;
                            // find first non-zero digit from right
                            for (i=7; i>0; i--) {
                                if (b[i] == '.') {
                                    i--;
                                    break;
                                }
                                if (b[i] != '0')
                                    break;
                            }
                            int firstSig = -1;
                            // System.err.println("i="+i);
                            for (int j=0; j<=i; j++) {
                                char c = b[j];
                                if ((c != '.') && (c != '0') && (firstSig==-1)) {
                                    firstSig = j;
                                    int newStop = j+2;
                                    if (j==0)
                                        newStop++;
                                    if (i>newStop)
                                        i = newStop;
                                    // System.err.println("newStop="+newStop);
                                }
                                if ((i==j) && (j<7) &&
                                    (b[j+1]>='5') &&
                                    (b[j+1]<='9')) {
                                    c++;
                                }
                                outfile.printf("%c",c);

                            }
                            outfile.printf(" ");
                        }
                        else {
                            char[] b = new char[6];
                            StringUtil.sprintf(b,"%0.2f",E);
                            if (b[2]=='.') {  // special handling for 10.0, etc
                                outfile.printf("%c",b[0]);
                                coef++;
                            }
                            else {
                                int i;
                                for (i=3; i>0; i--) {
                                    if ((b[i]!='0') && (b[i]!='.'))
                                        break;
                                }
                                for (int j=0; j<=i; j++)
                                    outfile.printf("%c",b[j]);
                            }
                            outfile.printf("e%2.2d ",coef);
                        }
                    }

                    outfile.printf("%s ",similarity);
                    int s = rs.getInt(5);
                    outfile.printf("%d ",s+1);
                    outfile.printf("%d ",l1+s);
                    s = rs.getInt(7);
                    outfile.printf("%d ",s+1);
                    outfile.printf("%d\n",l2+s);
                }
                outfile.flush();
            }
            outfile.close();
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
