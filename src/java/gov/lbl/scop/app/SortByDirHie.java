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
import gov.lbl.scop.local.LocalSQL;

/**
   Sort scop nodes within a release so their order by id is the same
   as their order as children in the dir.hie file.
*/
public class SortByDirHie {
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            ResultSet rs;

            String ver = argv[0].substring(argv[0].length()-4);
            int scopReleaseID = LocalSQL.lookupSCOPRelease(ver);
            if (scopReleaseID==0) {
                throw new Exception("Can't determine SCOP version from "+argv[0]);
            }

            // check that all nodes are consecutive, and get current order
            rs = stmt.executeQuery("select min(id), max(id) from scop_node where release_id="+scopReleaseID);
            rs.next();
            int minID = rs.getInt(1);
            int maxID = rs.getInt(2);
            int[] sunids = new int[maxID-minID+1];
            rs = stmt.executeQuery("select count(*) from scop_node where release_id="+scopReleaseID+" order by id");
            rs.next();
            if (rs.getInt(1) != maxID-minID+1)
                throw new IOException("gap in release");

            // find last node
            rs = stmt.executeQuery("select max(id) from scop_node");
            rs.next();
            int newMinID = rs.getInt(1)+1;

            // work around mysql bug #5103 by temporarily disabling keys
            stmt.executeUpdate("alter table scop_node drop foreign key scop_node_ibfk_3");
		

            // read dir.hie from argv[0];
            BufferedReader infile = IO.openReader(argv[0]);
            String buffer = infile.readLine();
            int i = 0;
            while (buffer != null) {
                if (!buffer.startsWith("#")) {
                    int sunid = StringUtil.atoi(buffer);
                    rs = stmt.executeQuery("select id from scop_node where sunid="+sunid+" and release_id="+scopReleaseID);
                    rs.next();
                    int id = rs.getInt(1);
                    stmt.executeUpdate("update scop_node set id="+(newMinID+i)+" where id="+id);
                    // because of mysql bug (above), need to cascade parent
                    stmt.executeUpdate("update scop_node set parent_node_id="+(newMinID+i)+" where parent_node_id="+id);
			
                    i++;
                }
                buffer = infile.readLine();
            }
            if (i != maxID-minID+1)
                System.out.println("error in dir.hie");
	    
            stmt.executeUpdate("update scop_node set id=id-"+(newMinID-minID)+" where release_id="+scopReleaseID);

            // because of mysql bug (above), need to cascade parent
            stmt.executeUpdate("update scop_node set parent_node_id=parent_node_id-"+(newMinID-minID)+" where release_id="+scopReleaseID);

            // re-enable the constraint, dropped above
            stmt.executeUpdate("alter table scop_node add CONSTRAINT `scop_node_ibfk_3` FOREIGN KEY (`parent_node_id`) REFERENCES `scop_node` (`id`) ON UPDATE CASCADE");
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
