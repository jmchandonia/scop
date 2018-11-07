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
import java.text.*;
import org.strbio.io.*;
import org.strbio.local.*;
import org.strbio.math.*;
import org.strbio.mol.*;
import org.strbio.util.*;
import org.strbio.IO;
import gov.lbl.scop.local.*;

/**
   Create a new release, given a freeze date or the master file.
   Assumes release is neither comprehensive nor public, and that the
   release date is today.  Will overwrite a non-public version, if
   it's the most recent version. New version must be greater than old
   version.

   Produces list of new, updated, and now-obsolete PDB files since
   the last release.  Obsolete list is cumulative with previous
   releases.
*/
public class MakeNewRelease {
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            Statement stmt2 = LocalSQL.createStatement();
            ResultSet rs,rs2;

            if (argv.length != 2) {
                System.out.println("syntax:  MakeNewRelease (version) (file)");
                System.out.println("     or  MakeNewRelease (version) YYYY-MM-DD");
            }

            String newVersion = argv[0];
            String newPDBFile = null;
            String newFreeze = null;
            if (Pattern.matches("\\d\\d\\d\\d-\\d\\d-\\d\\d",argv[1]))
                newFreeze = argv[1];
            else
                newPDBFile = argv[1];

            // read in list of new pdbs, calculate freeze date:
            HashSet<String> newPDBs = new HashSet<String>();
            if (newPDBFile != null) {
                java.util.Date latestRelease = null;
                int latestEntry = 0;
                BufferedReader infile = IO.openReader(newPDBFile);
                String buffer;
                while ((buffer = infile.readLine()) != null) {
                    if (!buffer.startsWith("ID"))
                        continue;
                    String code = buffer.substring(3,7).toLowerCase();
                    newPDBs.add(code);
                    // date of 0000-00-00 will cause exception
                    rs = stmt.executeQuery("select id, release_date from pdb_entry where code=\""+code+"\" and release_date != \"0000-00-00\"");
                    if (rs.next()) {
                        int entryID = rs.getInt(1);
                        java.util.Date d = rs.getDate(2);
                        if ((latestRelease==null) || (d.after(latestRelease))) {
                            latestEntry = entryID;
                            latestRelease = d;
                        }
                    }
                    else {
                        rs = stmt.executeQuery("select id from pdb_entry where code=\""+code+"\"");
                        if (!rs.next())
                            throw new Exception("error in input pdb file - code "+code+" not found in database");
                    }
                }

                // find freeze date
                rs = stmt.executeQuery("select pdb_path, xml_path from pdb_local where pdb_release_id in (select id from pdb_release where pdb_entry_id = "+latestEntry+" order by file_date) limit 1");
                rs.next();
                String path = rs.getString(2);
                if (path == null)
                    path = rs.getString(1);
                int pos = path.indexOf("data/");
                newFreeze = ParseRAF.convertDate(path.substring(pos+5,pos+11));
            }

            if (newFreeze==null)
                throw new Exception("couldn't determine new freeze date");
            else
                System.out.println("Freeze date calculated as "+newFreeze);

            rs = stmt.executeQuery("select max(id) from scop_release");
            rs.next();
            int oldID = rs.getInt(1);

            rs = stmt.executeQuery("select version, is_public from scop_release where id="+oldID);
            rs.next();
            String oldVersion = rs.getString(1);
            boolean isPublic = (rs.getInt(2)==1);

            int newID;

            // are we replacing current version?
            if (newVersion.equals(oldVersion) &&
                (!isPublic)) {
                System.out.println("updating version "+newVersion);
                newID = oldID;
                oldID--;

                stmt.executeUpdate("update scop_release set release_date=now() where id="+newID);
                stmt.executeUpdate("update scop_release set freeze_date=\""+newFreeze+"\" where id="+newID);
            }
            else {
                newID = oldID+1;
                stmt.executeUpdate("insert into scop_release values ("+
                                   newID+", \""+
                                   newVersion+"\", 0, 0, \""+
                                   newFreeze+"\", now(), 2, null)");
            }
	    
            // show all new/updated PDB files between freeze dates.

            // show all obsolete files:
            rs = stmt.executeQuery("select e.code, e.obsolete_date, e.obsoleted_by from pdb_entry e, scop_release s where s.id="+newID+" and e.obsolete_date <= s.freeze_date");
            while (rs.next()) {
                String code1 = rs.getString(1);
                String date = rs.getString(2);
                String code2 = LocalSQL.getPDBCode(rs.getInt(3));
                if (code2==null)
                    code2 = "NONE";
                System.out.println("obsolete:\t"+code1+"\t"+date+"\t"+code2);
            }

            // show new entries:
            rs = stmt.executeQuery("select e.code, e.release_date from pdb_entry e, scop_release r1, scop_release r2 where r1.id="+oldID+" and r2.id="+newID+" and e.release_date >= r1.freeze_date and e.release_date <= r2.freeze_date and e.obsolete_date is null");
            while (rs.next()) {
                String code = rs.getString(1);
                String date = rs.getString(2);
                String included = "no";
                if (newPDBs.contains(code))
                    included= "yes";
                System.out.println("new:\t"+code+"\t"+date+"\t"+included);
            }

            // show newly updated entries:
            rs = stmt.executeQuery("select e.code, r.file_date, r.id from pdb_entry e, pdb_release r, scop_release r1, scop_release r2 where r1.id="+oldID+" and r2.id="+newID+" and r.pdb_entry_id=e.id and ((r.revision_date >= r1.freeze_date and r.revision_date <= r2.freeze_date) or (r.file_date >= r1.freeze_date and r.file_date <= r2.freeze_date)) and e.obsolete_date is null and e.release_date < r1.freeze_date and r.file_date is not null");
            while (rs.next()) {
                String code = rs.getString(1);
                String date = rs.getString(2);
                int releaseID = rs.getInt(3);
                String changed = null;
                // System.out.println("debug: "+code+" "+date+" "+releaseID);
                int oldReleaseID;
                rs2 = stmt2.executeQuery("select id from pdb_release where replaced_by="+releaseID);
                if (rs2.next())
                    oldReleaseID = rs2.getInt(1);
                else {
                    System.out.println("updated:\t"+code+"\t"+date+"\tReplacedBy:"+releaseID);
                    continue;
                }
                // same # of chains?
                rs2 = stmt2.executeQuery("select count(*) from pdb_chain where pdb_release_id = "+releaseID+" and is_polypeptide=1");
                rs2.next();
                int newChains = rs2.getInt(1);
                rs2 = stmt2.executeQuery("select count(*) from pdb_chain where pdb_release_id = "+oldReleaseID+" and is_polypeptide=1");
                rs2.next();
                int oldChains = rs2.getInt(1);
                if (oldChains > newChains)
                    changed = "chains-"+(oldChains-newChains);
                else if (oldChains < newChains)
                    changed = "chains+"+(newChains-oldChains);
                else {
                    changed = "RAF:same";
                    // first, see if raf changed from that used in last release
                    rs2 = stmt2.executeQuery("select raf_get_body(r1.id), raf_get_body(r2.id) from raf r1, raf r2, pdb_chain c1, pdb_chain c2 where r1.pdb_chain_id=c1.id and r2.pdb_chain_id=c2.id and c1.chain=c2.chain and c1.pdb_release_id="+oldReleaseID+" and c2.pdb_release_id="+releaseID+" and r1.last_release_id="+oldID);
                    int n = 0;
                    while (rs2.next()) {
                        n++;
                        String raf1 = rs2.getString(1);
                        String raf2 = rs2.getString(2);
                        if (!raf1.equals(raf2))
                            changed = "RAF:different";
                    }
                    if ((n>0) && (n != oldChains))
                        changed += ":newch";
                    if (n==0) {
                        // if it wasn't used, then see if generated versions changed
                        rs2 = stmt2.executeQuery("select raf_get_body(r1.id), raf_get_body(r2.id) from raf r1, raf r2, pdb_chain c1, pdb_chain c2 where r1.pdb_chain_id=c1.id and r2.pdb_chain_id=c2.id and c1.chain=c2.chain and c1.pdb_release_id="+oldReleaseID+" and c2.pdb_release_id="+releaseID+" and r1.last_release_id is null");
                        while (rs2.next()) {
                            n++;
                            String raf1 = rs2.getString(1);
                            String raf2 = rs2.getString(2);
                            if (!raf1.equals(raf2))
                                changed = "RAF:different";
                        }
                        if (n != oldChains)
                            changed = "chains "+oldReleaseID+" "+releaseID;
                    }
                }
                System.out.println("updated:\t"+code+"\t"+date+"\t"+changed);
            }
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
