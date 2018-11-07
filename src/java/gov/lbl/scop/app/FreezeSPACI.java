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

import gov.lbl.scop.local.LocalSQL;
import gov.lbl.scop.util.SPACI;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;
import java.util.Vector;

/**
   Run this to freeze SPACI scores for a release.
*/
public class FreezeSPACI {
    /**
       adds pdb code to frozen SPACI for a SCOP release, if not
       already present.  Returns SPACI line.
    */
    final public static SPACI.SPACILine addSPACI(int pdbEntryID,
                                                 int scopReleaseID,
                                                 boolean isWeeklyUpdate)
        throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select code from pdb_entry where id=" + pdbEntryID);
        if (!rs.next()) {
            rs.close();
            stmt.close();
            return null;
        }
        String pdbCode = rs.getString(1);
        rs.close();

        if (pdbCode.startsWith("0")) {
            stmt.close();
            return null;
        }

        // check that not already added
        rs = stmt.executeQuery("select id from aerospaci where pdb_entry_id=" + pdbEntryID + " and release_id=" + scopReleaseID);
        if (rs.next()) {
            rs.close();
            stmt.close();
            return null;
        }
        rs.close();

        SPACI.SPACILine s = new SPACI.SPACILine(pdbEntryID,
                                                scopReleaseID,
                                                pdbCode);
        try {
            s.lookupPDBRelease(stmt, isWeeklyUpdate);
            s.lookupFromRelease(stmt);
        } catch (Exception e2) {
            System.out.println(e2.getMessage());
            s = null;
        }
        if ((s != null) && isWeeklyUpdate)
            s.store(stmt);
        stmt.close();
        return s;
    }

    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            Statement stmt2 = LocalSQL.createStatement();
            ResultSet rs;

            int scopID = LocalSQL.lookupSCOPRelease(argv[0]);
            int scopReleaseID = LocalSQL.getLatestSCOPRelease(false);
            if (scopID != scopReleaseID)
                throw new Exception("Can't freeze old release");

            int lastPublicRelease = LocalSQL.getLatestSCOPRelease(true);
            if (scopReleaseID==lastPublicRelease)
                throw new Exception("can't freeze a public release");

            // clear out old SPACI entries
            stmt.executeUpdate("delete from aerospaci where release_id=" + scopReleaseID);

            Vector<SPACI.SPACILine> scores = new Vector<SPACI.SPACILine>();

            // get all PDB codes that were valid on freeze date
            rs = stmt.executeQuery("select e.id from pdb_entry e, scop_release s where e.release_date <= s.freeze_date and e.is_literature_reference=0 and s.id=" + scopReleaseID);
            while (rs.next()) {
                int pdbEntryID = rs.getInt(1);

                SPACI.SPACILine s = addSPACI(pdbEntryID,
                                             scopReleaseID,
                                             false);
                if (s != null)
                    scores.add(s);
            }
            Collections.sort(scores);
            for (SPACI.SPACILine s : scores)
                s.store(stmt2);
        } catch (Exception e) {
            System.out.println("Exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
