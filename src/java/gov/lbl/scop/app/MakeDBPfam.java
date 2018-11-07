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
import org.strbio.IO;
import org.strbio.io.*;
import org.strbio.math.*;
import org.strbio.mol.*;
import org.strbio.util.*;
import gov.lbl.scop.local.LocalSQL;

/*
  run in downloaded directory.  Reads:  Pfam-A.hmm or Pfam_ls, Pfam-a.seed.
*/
public class MakeDBPfam {
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            ResultSet rs;

            // get version from cmd line
            int pfamReleaseID = LocalSQL.lookupPfamRelease(argv[0]);
            if (pfamReleaseID==0)
                throw new Exception("no such version "+argv[0]);

            // delete old info from this version
            stmt.executeUpdate("delete from pfam where release_id="+pfamReleaseID);

            // add all HMMs from Pfam-A.hmm, or Pfam_ls for older versions
            BufferedReader infile = null;
            try {
                infile = IO.openReader("Pfam-A.hmm");
            }
            catch (Exception e1) {
                infile = IO.openReader("Pfam_ls");
            }
            String name= null, acc=null, desc=null;
            double tc=0.0;
            int l=0;
		
            while (infile.ready()) {
                String buffer = infile.readLine();

                if (buffer.startsWith("NAME ")) {
                    name = buffer.substring(6).trim();
                }
                else if (buffer.startsWith("DESC ")) {
                    desc = buffer.substring(6).trim();
                }
                else if (buffer.startsWith("TC ")) {
                    tc = StringUtil.atod(buffer.substring(6));
                }
                else if (buffer.startsWith("ACC ")) {
                    acc = buffer.substring(6).trim();
                }
                else if (buffer.startsWith("LENG ")) {
                    l = StringUtil.atoi(buffer.substring(6));
                }
                else if (buffer.startsWith("HMM ")) {
                    stmt.executeUpdate("insert into pfam values (NULL, "+
                                       pfamReleaseID+", \""+
                                       StringUtil.replace(name,"\"","\\\"")+"\", \""+
                                       acc+"\", \""+
                                       StringUtil.replace(desc,"\"","\\\"")+"\", "+
                                       l+", "+
                                       tc+", null)");
                }
            }

            // fill in pfam types from Pfam-A.seed
            infile = IO.openReader("Pfam-A.seed");
            int id = 0;
            while (infile.ready()) {
                String buffer = infile.readLine();

                if (buffer.startsWith("# STOCK")) {
                    id = 0;
                }
                else if (buffer.startsWith("#=GF AC ")) {
                    acc = buffer.substring(10);
                    rs = stmt.executeQuery("select id from pfam where accession = \""+acc+"\" and release_id="+pfamReleaseID);
                    if (rs.next()) {
                        id = rs.getInt(1);
                        // stmt.executeUpdate("update pfam set accession=\""+acc+"\" where id="+id);
                    }
                    else {
                        System.err.println("acc not found "+acc);
                        System.exit(1);
                    }
                }
                else if (buffer.startsWith("#=GF TP ")) {
                    String tpS = buffer.substring(10);
                    int tp = 0;
                    if (tpS.equals("Family"))
                        tp = 1;
                    else if (tpS.equals("Domain"))
                        tp = 2;
                    else if (tpS.equals("Repeat"))
                        tp = 3;
                    else if (tpS.equals("Motif"))
                        tp = 4;
                    else if (tpS.equals("Disordered"))
                        tp = 5;
                    else if (tpS.equals("Coiled-coil"))
                        tp = 6;
                    else
                        System.err.println("Unknown type "+tpS);
                    stmt.executeUpdate("update pfam set pfam_type_id = "+tp+" where id="+id);
                }
            }
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
