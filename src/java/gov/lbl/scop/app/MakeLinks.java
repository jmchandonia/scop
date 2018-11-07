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
   Link the SCOP nodes to PDB files, after importing
   both SCOP (ParseDirDes) and PDB, and running ParseRAF.
*/
public class MakeLinks {
    /**
       link a node to the right PDB entry, based on description
    */
    final public static void linkPDB(int nodeID) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs;
	
        rs = stmt.executeQuery("select description, level_id, release_id from scop_node where id="+nodeID);
        if (!rs.next())
            throw new Exception("unknown node "+nodeID);
        String description = rs.getString(1);
        int levelID = rs.getInt(2);
        int scopReleaseID = rs.getInt(3);
        if (levelID != 8)
            return;

        stmt.executeUpdate("delete from link_pdb where node_id="+nodeID);

        HashSet<Character> chains = new HashSet<Character>();
        String code = description.substring(0,4);
        int pos = description.indexOf(':');
        if (pos==-1)
            chains.add(new Character(' '));
        while (pos > -1) {
            char chain = description.charAt(pos-1);
            chains.add(new Character(chain));
            pos = description.indexOf(':',pos+1);
        }

        int pdbEntryID = LocalSQL.lookupPDB(code);
        if (pdbEntryID==0) {
            if (code.startsWith("s")) {
                stmt.executeUpdate("insert into pdb_entry values (null, \""+
                                   code+"\", \"\", \"0000-00-00\", \"0000-00-00\", null, null, 1)");
                pdbEntryID = LocalSQL.lookupPDB(code);
            }
            else
                throw new Exception("unknown pdb "+code);
        }

        int pdbReleaseID = 0;

        // look up releases for periodic updates
        rs = stmt.executeQuery("select r.id from pdb_release r, scop_history h where r.pdb_entry_id="+pdbEntryID+" and r.revision_date<=h.time_occurred and h.old_node_id="+nodeID+" and h.change_type_id=12 and h.release_id="+scopReleaseID+" and r.file_date is not null order by r.revision_date desc limit 1");
        if (rs.next())
            pdbReleaseID = rs.getInt(1);
        rs.close();

        if (pdbReleaseID==0)
            pdbReleaseID = FreezeRAF.findPDBRelease(pdbEntryID,
                                                    scopReleaseID);
        if (pdbReleaseID==0) {
            if (code.startsWith("s")) {
                rs = stmt.executeQuery("select r.id from pdb_release r, scop_release s where r.pdb_entry_id="+pdbEntryID+" and r.revision_date<=s.freeze_date and s.id="+scopReleaseID+" order by r.revision_date desc limit 1");
                if (rs.next())
                    pdbReleaseID = rs.getInt(1);
                rs.close();
            }
        }

        if (pdbReleaseID==0) {
            // try to fix missing history
            rs = stmt.executeQuery("select h.time_occurred from scop_history h, scop_node n1, scop_node n2 where n1.id="+nodeID+" and n2.id=h.old_node_id and h.change_type_id=12 and h.release_id=n1.release_id and n2.release_id=n1.release_id and substring(n1.description,1,4)=substring(n2.description,1,4) limit 1");
            if (rs.next()) {
                String t = rs.getString(1);
                rs.close();
                stmt.executeUpdate("insert into scop_history values (null, "+nodeID+", null, 14, 12, \""+t+"\")");
            }
            else
                rs.close();

            // now look again for PDB release
            rs = stmt.executeQuery("select r.id from pdb_release r, scop_history h where r.pdb_entry_id="+pdbEntryID+" and r.revision_date<=h.time_occurred and h.old_node_id="+nodeID+" and h.change_type_id=12 and h.release_id="+scopReleaseID+" and r.file_date is not null order by r.revision_date desc limit 1");
            if (rs.next())
                pdbReleaseID = rs.getInt(1);
            rs.close();
        }

        if (pdbReleaseID==0)
            throw new Exception("no PDB release before freeze date for "+description);

        for (Character c : chains) {
            char chain = c.charValue();
            int pdbChainID = 0;
            rs = stmt.executeQuery("select c.id from pdb_chain c where c.pdb_release_id="+pdbReleaseID+" and c.is_polypeptide=1 and c.chain=\""+chain+"\"");
            if (rs.next())
                pdbChainID = rs.getInt(1);
            if (pdbChainID==0)
                throw new Exception("can't parse "+description+" - undefined chain");
            stmt.executeUpdate("insert into link_pdb values ("+
                               nodeID+", "+
                               pdbChainID+")");
        }
        stmt.close();
    }
      
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            ResultSet rs;

            // link unlinked domains for all non-public releases
            rs = stmt.executeQuery("select id from scop_node where level_id=8 and id not in (select node_id from link_pdb) and release_id in (select id from scop_release where is_public=0)");
            while (rs.next())
                linkPDB(rs.getInt(1));
            rs.close();
            stmt.close();
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
