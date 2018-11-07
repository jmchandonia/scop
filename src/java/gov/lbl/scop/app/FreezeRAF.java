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
import org.strbio.io.*;
import org.strbio.math.*;
import org.strbio.mol.*;
import org.strbio.util.*;
import org.strbio.IO;
import gov.lbl.scop.local.LocalSQL;

/**
   Run this on a master file for a release, or just on the
   nodes already in the database, to freeze RAF
   for that release.  Obsolete entries are included if freezing
   using the master file, but not included if freezing on the
   nodes.  Obsolete entries are not deleted unless there is
   a classified replacement.
*/
public class FreezeRAF {
    /**
       find correct PDB release for a PDB entry and SCOP release
       (i.e., the last one before the freeze date).  0 if none.
    */
    final public static int findPDBRelease(int pdbEntryID,
                                           int scopReleaseID) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs;
        int rv = 0;
        rs = stmt.executeQuery("select r.id from pdb_release r, scop_release s, pdb_local l where s.id="+scopReleaseID+" and r.file_date <= s.freeze_date and r.revision_date <= s.freeze_date and l.snapshot_date <= s.freeze_date and r.pdb_entry_id="+pdbEntryID+" and l.pdb_release_id=r.id and l.xml_path is not null order by l.snapshot_date desc limit 1");
        if (rs.next())
            rv = rs.getInt(1);
        rs.close();
        stmt.close();
        return rv;
    }

    /**
       finds and adds appropriate line to RAF table; returns id of line
       or 0 if error.
    */
    final public static int addRAF(int pdbChainID,
                                   int scopReleaseID) throws Exception {

        Statement stmt = LocalSQL.createStatement();
        ResultSet rs;
        int oldScopID = scopReleaseID-1;  // must be consecutive
	    
        rs = stmt.executeQuery("select line from raf where pdb_chain_id="+pdbChainID+" and last_release_id is null");
        if (rs.next()) {
            String newLine = rs.getString(1);

            // is there an old RAF line that's the same as this one?
            rs = stmt.executeQuery("select id,line from raf where pdb_chain_id="+pdbChainID+" and last_release_id="+oldScopID);
            if (rs.next()) {
                int oldRAFID = rs.getInt(1);
                String oldLine = rs.getString(2);
                if (oldLine.equals(newLine)) {
                    rs.close();
                    stmt.executeUpdate("update raf set last_release_id="+scopReleaseID+" where id="+oldRAFID);
                    stmt.close();
                    return oldRAFID;
                }
            }
            rs.close();

            // old line different or missing; we need to insert it.
            stmt.executeUpdate("insert into raf values (null, 2, "+pdbChainID+", "+scopReleaseID+", "+scopReleaseID+", \""+newLine+"\")",
                               Statement.RETURN_GENERATED_KEYS);
            rs = stmt.getGeneratedKeys();
            rs.next();
            int rv = rs.getInt(1);
            rs.close();
            stmt.close();
            return rv;
        }
        else {
            System.out.println("Warning: chain "+pdbChainID+" not in new RAF");
        }
        stmt.close();
        return 0;
    }
	
    /**
       finds and adds appropriate line to RAF table; returns id of line
       or 0 if error.
    */
    final public static int addRAF(String code,
                                   char chain,
                                   int scopReleaseID,
                                   boolean includeObs) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs;

        int pdbID = LocalSQL.lookupPDB(code);
        if (pdbID == 0)
            throw new Exception("Error - pdb code "+code+" not found");

        // check for obsolete entries
        rs = stmt.executeQuery("select e.obsolete_date, e.obsoleted_by from pdb_entry e, scop_release s where s.id="+scopReleaseID+" and e.obsolete_date <= s.freeze_date and e.id="+pdbID);
        if (rs.next()) {
            String date = rs.getString(1);
            int newPDBID = rs.getInt(2);
            String code2 = LocalSQL.getPDBCode(newPDBID);
            if (code2==null)
                code2 = "NONE";
            System.out.println("Warning: obsolete:\t"+code+"\t"+date+"\t"+code2);

            // check whether newPDBID is classified; if not, don't delete old
            rs.close();
            rs = stmt.executeQuery("select n.id from scop_node n, link_pdb l, pdb_chain c, pdb_release r where n.release_id="+scopReleaseID+" and n.level_id=8 and n.id=l.node_id and l.pdb_chain_id=c.id and c.pdb_release_id=r.id and r.pdb_entry_id="+newPDBID+" limit 1");
            if (!rs.next())
                includeObs = true;
            rs.close();
	    
            if (!includeObs) {
                stmt.close();
                return 0;
            }
        }
        else
            rs.close();

        // find latest release of this PDB entry before freeze date,
        // for which we have an xml file
        int releaseID = findPDBRelease(pdbID,
                                       scopReleaseID);
        if (releaseID==0)
            throw new Exception("Error - pdb release for "+code+" not found");

        // lookup chain
        int pdbChainID = 0;
        boolean isPeptide = true;
        rs = stmt.executeQuery("select id,is_polypeptide from pdb_chain where pdb_release_id="+releaseID+" and chain=\""+chain+"\"");
        if (rs.next()) {
            pdbChainID = rs.getInt(1);
            isPeptide = (rs.getInt(2)==1);
        }
        else
            System.out.println("Warning: chain "+code+chain+" not found - release id is "+releaseID);

        if (!isPeptide)
            System.out.println("Warning: chain "+code+chain+" not polypeptide - release id is "+releaseID);
	    
        if (pdbChainID > 0) {
            stmt.close();
            return (addRAF(pdbChainID, scopReleaseID));
        }

        stmt.close();
        return 0;
    }
    
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            ResultSet rs;

            int scopID = LocalSQL.lookupSCOPRelease(argv[0]);
            int scopReleaseID = LocalSQL.getLatestSCOPRelease(false);
            if (scopID != scopReleaseID)
                throw new Exception("Can't freeze old release");

            int lastPublicRelease = LocalSQL.getLatestSCOPRelease(true);
            if (scopReleaseID==lastPublicRelease)
                throw new Exception("can't freeze a public release");

            int oldScopID = scopReleaseID-1;  // must be consecutive

            BufferedReader infile = null;
            if (argv.length > 1)
                infile = IO.openReader(argv[1]);

            // clear out old RAF entries
            stmt.executeUpdate("delete a from astral_chain a join raf r on (a.raf_id = r.id and r.first_release_id="+scopReleaseID+" and r.last_release_id="+scopReleaseID+")");
            stmt.executeUpdate("delete from raf where first_release_id="+scopReleaseID+" and last_release_id="+scopReleaseID);
            stmt.executeUpdate("delete from astral_chain where raf_id in (select id from raf where first_release_id="+scopReleaseID+" and last_release_id="+scopReleaseID+")");
            stmt.executeUpdate("delete from raf where first_release_id="+scopReleaseID+" and last_release_id="+scopReleaseID);
            stmt.executeUpdate("update raf set last_release_id = "+oldScopID+" where last_release_id="+scopReleaseID);

            // keep track of chains we're using
            HashSet<String> included = new HashSet<String>();

            if (infile == null) {
                // use domains defined in table
                rs = stmt.executeQuery("select e.code, c.chain, n.id from pdb_entry e, pdb_release r, pdb_chain c, link_pdb l, scop_node n where e.id=r.pdb_entry_id and r.id=c.pdb_release_id and c.id=l.pdb_chain_id and n.id=l.node_id and n.release_id="+scopReleaseID+" and n.level_id=8");
                while (rs.next()) {
                    String code = rs.getString(1);
                    if (code.startsWith("s"))
                        continue;
                    char chain = rs.getString(2).charAt(0);
                    int nodeID = rs.getInt(3);
                    if (!included.contains(code+chain)) {
                        int rafID = addRAF(code,chain,scopReleaseID,false);
                        if (rafID==0) {
                            // delete the node
                            System.out.println("deleting node for "+code+chain);
                            ManualEdit.deleteNode(nodeID,true);
                        }
                        else
                            included.add(code+chain);
                    }
                }
            }
            else {
                // use Master file
                String buffer;
                while ((buffer = infile.readLine()) != null) {
                    if (!buffer.startsWith("ID"))
                        continue;
                    int pos = 0;
                    String code = buffer.substring(3,7).toLowerCase();
                    while (pos != -1) {
                        int nextPos = buffer.length()+1;
                        int pos2 = buffer.indexOf(" CH ",pos+1);
                        if (pos2 > -1)
                            nextPos = Math.min(nextPos,pos2+4);
                        pos2 = buffer.indexOf(" RE ",pos+1);
                        if (pos2 > -1)
                            nextPos = Math.min(nextPos,pos2+4);
                        pos2 = buffer.indexOf(",",pos+1);
                        if (pos2 > -1)
                            nextPos = Math.min(nextPos,pos2+1);
                        pos2 = buffer.indexOf(";",pos+1);
                        if (pos2 > -1)
                            nextPos = Math.min(nextPos,pos2+1);

                        if (nextPos == buffer.length()+1)
                            nextPos = -1;
                        else {
                            char chain = buffer.charAt(nextPos);
                            if (!included.contains(code+chain)) {
                                addRAF(code,chain,scopReleaseID,true);
                                included.add(code+chain);
                            }
                        }
			
                        pos = nextPos;
                    }
                }
            }
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
