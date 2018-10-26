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
import java.text.*;
import org.strbio.io.*;
import org.strbio.local.*;
import org.strbio.math.*;
import org.strbio.mol.*;
import org.strbio.util.*;
import org.strbio.IO;
import gov.lbl.scop.local.*;

/**
   Add chains and make new RAF lines for all files that don't have one.
*/
public class MakeNewRAF {
    final public static int lookupChain(int releaseID,
                                        char chainCode) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select id from pdb_chain where pdb_release_id="+releaseID+" and chain=\""+chainCode+"\"");
        if (rs.next()) {
            int rv = rs.getInt(1);
            stmt.close();
            return rv;
        }
        else {
            stmt.close();
            return 0;
        }
    }

    final public static int lookupOrCreateChain(int releaseID,
                                                char chainCode,
                                                boolean isPeptide) throws Exception {
        int id = lookupChain(releaseID, chainCode);
        if (id>0) {
            return id;
        }
        else {
            Statement stmt = LocalSQL.createStatement();
            stmt.executeUpdate("insert into pdb_chain values (null, "+
                               releaseID+", \""+
                               chainCode+"\", "+
                               (isPeptide ? 1 : 0)+")",
                               Statement.RETURN_GENERATED_KEYS);
            ResultSet rs = stmt.getGeneratedKeys();
            rs.next();
            int rv = rs.getInt(1);
            stmt.close();
            return rv;
        }
    }

    /**
       make a raf for an individual PDB release
    */
    final public static void makeRAF(int pdbReleaseID) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select xml_path from pdb_local where pdb_release_id="+pdbReleaseID);
        if (!rs.next()) {
            stmt.close();
            return;
        }
	
        String xml = rs.getString(1);

        stmt.executeUpdate("update pdb_local set is_raf_calculated=2 where pdb_release_id="+pdbReleaseID);
        // delete old versions of this raf not used before
        // stmt.executeUpdate("delete a, r from astral_chain a join raf r on (a.raf_id = r.id and r.first_release_id is null and r.last_release_id is null) join pdb_chain c on (c.id=r.pdb_chain_id and c.pdb_release_id="+pdbReleaseID+")");
        stmt.executeUpdate("delete from astral_chain where raf_id in (select id from raf where first_release_id is null and last_release_id is null and pdb_chain_id in (select id from pdb_chain where pdb_release_id="+pdbReleaseID+"))");
        stmt.executeUpdate("delete from raf where first_release_id is null and last_release_id is null and pdb_chain_id in (select id from pdb_chain where pdb_release_id="+pdbReleaseID+")");


        System.out.println("making RAF for "+xml);
        System.out.flush();

        // get all chains
        HashMap<Character,String> chains = GetChains.getChains(xml);
        if (chains != null) {
            for (Character c : chains.keySet()) {
                char chain = c.charValue();
                String chainType = chains.get(c);
                if (chain=='_')
                    chain = ' ';
		    
                int pos = chainType.indexOf("polypeptide(L)");
                boolean isPeptide = (pos > -1);
		    
                int pdbChainID = lookupOrCreateChain(pdbReleaseID,chain,isPeptide);
            }
        }

        // get all RAF lines
        Vector<String> raf = XML2RAF.getRAF(xml);
        if (raf != null) {
            for (String line : raf) {
                char chain = line.charAt(4);
                if (chain=='_')
                    chain = ' ';
                int pdbChainID = lookupChain(pdbReleaseID,chain);
                if (pdbChainID == 0) {
                    System.out.println("Error looking up chain "+line.substring(0,5));
                }
                else {
                    // check that it's listed as polypeptide
                    rs = stmt.executeQuery("select is_polypeptide from pdb_chain where id="+pdbChainID);
                    rs.next();
                    int poly = rs.getInt(1);
                    if (poly==1) {
                        stmt.executeUpdate("insert into raf values (null, 2, "+
                                           pdbChainID+", null, null, \""+
                                           line+"\")",
                                           Statement.RETURN_GENERATED_KEYS);
                        rs = stmt.getGeneratedKeys();
                        rs.next();
                        int rafID = rs.getInt(1);

                        // make job to calculate chain sequence
                        LocalSQL.newJob(6,rafID,null,stmt);
                    }
                }
            }
        }
        stmt.executeUpdate("update pdb_local set is_raf_calculated=1 where pdb_release_id="+pdbReleaseID);
        stmt.close();
    }
    
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();

            BufferedReader infile = null;

            boolean done = false;
            while (!done) {
                // pick one entry at a time to work on
                ResultSet rs = stmt.executeQuery("select pdb_release_id from pdb_local where xml_path is not null and is_raf_calculated=0 limit 1");
                int id = 0;
                if (rs.next()) {
                    id = rs.getInt(1);
                }
                else {
                    done = true;
                    break;
                }

                makeRAF(id);
            }
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
