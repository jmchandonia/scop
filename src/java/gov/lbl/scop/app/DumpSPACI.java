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
import gov.lbl.scop.util.SPACI;
import org.strbio.io.PrintfWriter;

import java.sql.ResultSet;
import java.sql.Statement;

/**
   Dump out the SPACI/AEROSPACI file used in a given release, or
   for all current PDB files.
*/
public class DumpSPACI {
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            Statement stmt2 = LocalSQL.createStatement();
            ResultSet rs;

            boolean isAero = false;

            PrintfWriter outfile = null;

            if (argv.length >= 2)
                outfile = new PrintfWriter(argv[1]);
            else
                outfile = new PrintfWriter(System.out);

            if (argv.length >= 1) {
                int scopReleaseID = LocalSQL.lookupSCOPRelease(argv[0]);
                isAero = argv[1].startsWith("aero");

                rs = stmt.executeQuery("select id from aerospaci where release_id=" + scopReleaseID + " order by id");
                while (rs.next()) {
                    int id = rs.getInt(1);
                    SPACI.SPACILine s = new SPACI.SPACILine();
                    s.lookupFromID(id, stmt2);
                    s.print(outfile, isAero);
                }
            }
            else {
                rs = stmt.executeQuery("select id, code from pdb_entry where is_literature_reference=0");
                while (rs.next()) {
                    int pdbEntryID = rs.getInt(1);
                    String pdbCode = rs.getString(2);
                    if (pdbCode.startsWith("0"))
                        continue;

                    SPACI.SPACILine s = new SPACI.SPACILine(pdbEntryID, 0, pdbCode);
                    try {
                        s.lookupPDBRelease(stmt2, false);
                        s.lookupFromRelease(stmt2);
                    }
                    catch (Exception e2) {
                        // System.out.println(e2.getMessage());
                        s = null;
                    }
                    if (s != null) {
                        s.print(outfile, true);
                    }
                }
            }
            outfile.flush();
            outfile.close();
        } catch (Exception e) {
            System.out.println("Exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
