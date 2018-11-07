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
import org.strbio.math.*;
import org.strbio.mol.*;
import org.strbio.util.*;
import org.strbio.IO;
import gov.lbl.scop.local.LocalSQL;

/**
   Import old RAF files, after running ParsePDB
*/
public class ParseRAF {
    final public static SimpleDateFormat rafDateFormat =
        new SimpleDateFormat ("yyMMdd");
    final public static SimpleDateFormat sqlDateFormat =
        new SimpleDateFormat ("yyyy-MM-dd");
	
    /**
       convert date format:  931120 to 1993-11-20
       null if fmt problem
    */
    final public static String convertDate(String d) throws Exception {
        if ((d == null) || (d.length() != 6)) return null;
        java.util.Date tmpD = rafDateFormat.parse(d);
        return sqlDateFormat.format(tmpD);
    }

    final public static int lookupOrCreateChain(int releaseID, char chainCode) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select id from pdb_chain where pdb_release_id="+releaseID+" and chain=\""+chainCode+"\"");
        if (rs.next())
            return rs.getInt(1);
        else {
            stmt.executeUpdate("insert into pdb_chain values (null, "+
                               releaseID+", \""+
                               chainCode+"\", 1)",
                               Statement.RETURN_GENERATED_KEYS);
            rs = stmt.getGeneratedKeys();
            rs.next();
            return rs.getInt(1);
        }
    }
    
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            ResultSet rs;

            // figure out what release we're parsing
            String ver = argv[0].substring(20,24);
            int scopReleaseID = LocalSQL.lookupSCOPRelease(ver);
            if (scopReleaseID==0) {
                throw new Exception("Can't determine SCOP version from "+argv[0]);
            }

            // read raf from argv[0];
            BufferedReader infile = IO.openReader(argv[0]);
            String buffer = infile.readLine();
            while (buffer != null) {
                if (!buffer.startsWith("#")) {
                    String pdbCode = buffer.substring(0,4);
                    char chainCode = buffer.charAt(4);
                    if (chainCode=='_')
                        chainCode = ' ';
                    int rafVersionID = (int)(buffer.charAt(9))-(int)'0';
                    String date = convertDate(buffer.substring(14,20));

                    // is there already a version of this chain in RAF?
                    int oldChain = LocalSQL.findRAF(pdbCode,chainCode,scopReleaseID);
                    if (oldChain > 0) {
                        // System.out.println("skipping "+pdbCode+chainCode);
                        buffer = infile.readLine();
                        continue;
                    }
						    
                    int pdbID = LocalSQL.lookupPDB(pdbCode);
                    if (pdbID==0)
                        throw new Exception("unknown PDB in RAF: "+pdbCode);

                    // get newest version of this entry prior to date in RAF.
                    int releaseID = 0;

                    if (rafVersionID==1) {
                        rs = stmt.executeQuery("select id from pdb_release where pdb_entry_id="+pdbID+" and revision_date<=\""+date+"\" order by revision_date desc limit 1");
                    }
                    else {
                        rs = stmt.executeQuery("select id from pdb_release where pdb_entry_id="+pdbID+" and file_date=\""+date+"\" order by revision_date desc limit 1");
                    }
                    if (rs.next())
                        releaseID = rs.getInt(1);
		    
                    if (releaseID==0) {
                        // check up to 1 week after
                        rs = stmt.executeQuery("select id from pdb_release where pdb_entry_id="+pdbID+" and revision_date<=adddate(\""+date+"\",7) order by revision_date desc limit 1");
                        if (rs.next()) {
                            releaseID = rs.getInt(1);
                        }
                    }
		    
                    if (releaseID==0) {
                        System.out.println("error finding release for "+pdbCode);
                        System.out.println("date is "+date);
                        System.out.println("available releases are:");
                        rs = stmt.executeQuery("select revision_date, file_date from pdb_release where pdb_entry_id="+pdbID+" order by revision_date desc");
                        while (rs.next())
                            System.out.println(" "+rs.getString(1)+" "+rs.getString(2));
                        System.exit(1);
                    }

                    int pdbChainID = lookupOrCreateChain(releaseID,chainCode);

                    String line = StringUtil.replace(buffer,"\"","\\\"");

                    // find old raf, if it exists
                    boolean oldFound = false;
                    rs = stmt.executeQuery("select id, first_release_id, last_release_id from raf where pdb_chain_id="+pdbChainID+" and line=\""+line+"\"");
                    while (rs.next()) {
                        int rafID = rs.getInt(1);
                        int firstRelease = rs.getInt(2);
                        int lastRelease = rs.getInt(3);
                        if (firstRelease == scopReleaseID+1) {
                            stmt.executeUpdate("update raf set first_release_id="+scopReleaseID+" where id="+rafID);
                            oldFound = true;
                            // System.out.println("using prior RAF for "+pdbCode+chainCode);
                        }
                        if (lastRelease == scopReleaseID-1) {
                            stmt.executeUpdate("update raf set last_release_id="+scopReleaseID+" where id="+rafID);
                            oldFound = true;
                            // System.out.println("using prior RAF for "+pdbCode+chainCode);
                        }
                    }
                    if (!oldFound) {
                        // System.out.println("new RAF for "+pdbCode+chainCode);
                        stmt.executeUpdate("insert into raf values (null,"+
                                           rafVersionID+", "+
                                           pdbChainID+", "+
                                           scopReleaseID+", "+
                                           scopReleaseID+", \""+
                                           line+"\")");
                    }
                }
                buffer = infile.readLine();
            }
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
