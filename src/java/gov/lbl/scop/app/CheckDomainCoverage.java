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
import java.util.regex.*;
import gov.lbl.scop.local.*;
import gov.lbl.scop.util.*;

/**
   Check for:

   1) overlapping domains
   2) domains that refer to nonexistent chains
   3) domains that refer to nonexistent ATOM number
   4) chains that are only partially classified (lots of these since 1.73)
   5) chains with a significantly different length since last release
*/
public class CheckDomainCoverage {
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            Statement stmt2 = LocalSQL.createStatement();
            Statement stmt3 = LocalSQL.createStatement();

            ResultSet rs, rs2, rs3;

            int scopReleaseID = LocalSQL.lookupSCOPRelease(argv[0]);
            if (scopReleaseID==0)
                throw new Exception("Can't determine SCOP version from "+argv[0]);

            Pattern regionPattern = Pattern.compile("\\s*(\\S+?)-(\\S+)\\s*$");
	    
            // get all chains covered in a release
            rs = stmt.executeQuery("select distinct(l.pdb_chain_id) from link_pdb l, scop_node n where l.node_id=n.id and n.release_id="+scopReleaseID);
            while (rs.next()) {
                int pdbChainID = rs.getInt(1);
                // System.out.println("pdb chain "+pdbChainID);

                String rafLine = null;
                rs2 = stmt2.executeQuery("select line from raf where first_release_id<="+scopReleaseID+" and last_release_id>="+scopReleaseID+" and pdb_chain_id="+pdbChainID);
                if (rs2.next())
                    rafLine = rs2.getString(1);
                rs2.close();

                // find info for last release, if applicable
                String lastRafLine = null;
                rs2 = stmt2.executeQuery("select r.line from raf r, pdb_chain c1, pdb_chain c2, pdb_release r1, pdb_release r2 where r.first_release_id<="+(scopReleaseID-1)+" and r.last_release_id>="+(scopReleaseID-1)+" and r.pdb_chain_id=c1.id and c1.pdb_release_id=r1.id and r1.pdb_entry_id=r2.pdb_entry_id and c2.pdb_release_id=r2.id and c2.chain=c1.chain and c2.id="+pdbChainID);
                if (rs2.next())
                    lastRafLine = rs2.getString(1);
                rs2.close();
                
                if (rafLine==null) {
                    rs2 = stmt2.executeQuery("select e.code, c.chain from pdb_entry e, pdb_release r, pdb_chain c where c.id="+pdbChainID+" and c.pdb_release_id=r.id and r.pdb_entry_id=e.id");
                    rs2.next();
                    String fullCode = rs2.getString(1)+rs2.getString(2);
                    rs2.close();
                    if ((fullCode.charAt(0) != '0') &&
                        (fullCode.charAt(0) != 's'))
                        System.out.println("Warning: no RAF found for PDB chain "+fullCode);
                    continue;
                }

                String rafCode = rafLine.substring(0,4);
                char rafChain = rafLine.charAt(4);
                String rafBody = RAF.getRAFBody(rafLine);
                int l = RAF.getSeqLength(rafBody);

                int l2 = l;
                if (lastRafLine != null) {
                    l2 = RAF.getSeqLength(RAF.getRAFBody(lastRafLine));
                    if (Math.abs(l2-l) > 10) {
                        System.out.println("Warning: chain "+rafCode+rafChain+" changed by "+(l-l2)+" residues");
                    }
                }

                // track all ATOM residues, to make sure they are covered
                boolean[] covered = new boolean[l];

                // cover annotations for all domains mapping to this chain;
                // check that not already covered
                boolean gapsOK = false;
                rs2 = stmt2.executeQuery("select n.sid, n.description, n.curation_type_id from scop_node n, link_pdb l where n.id=l.node_id and n.release_id="+scopReleaseID+" and l.pdb_chain_id="+pdbChainID);
                while (rs2.next()) {
                    String sid = rs2.getString(1);
                    String description = rs2.getString(2);
                    int curationType = rs2.getInt(3);

                    // System.out.println("  applying domain "+sid);

                    if (curationType==2)
                        gapsOK = true;

                    String code = description.substring(0,4);
                    String[] regions = description.substring(5).split(",");
	
                    for (String region : regions) {
                        char chain = '_';
                        if (region.indexOf(':')==1) {
                            chain = region.charAt(0);
                            region = region.substring(2);
                        }
                        // if this chain, cover it
                        if (chain==rafChain) {
                            Matcher m = regionPattern.matcher(region);
                            String resIDStart = null;
                            String resIDEnd = null;
                            if (m.matches()) {
                                resIDStart = m.group(1);
                                resIDEnd = m.group(2);
                            }
                            else if ((region.length() == 0) ||
                                     (region.equals("-"))) {
                                // use whole region
                                resIDStart = rafLine.substring(28, 33).trim();
                                resIDEnd = rafLine.substring(33, 38).trim();
                            }

                            int indexStart = RAF.indexOf(rafBody, resIDStart, true);
                            int indexEnd = RAF.indexOf(rafBody, resIDEnd, false);
                            if ((indexStart==-1) ||
                                (indexEnd==-1)) {
                                System.out.println("Unknown residue for "+rafCode+rafChain+": "+sid+" "+description);
                                indexEnd = indexStart - 1; // avoid crash in next step
                            }
                            for (int i=indexStart; i<=indexEnd; i++) {
                                if (covered[i]==true) {
                                    System.out.println("Overlapping domains for "+rafCode+rafChain+": "+sid+" "+description);
                                    i = indexEnd;
                                }
                            }
                            for (int i=indexStart; i<=indexEnd; i++)
                                covered[i] = true;
                        }
                        else {
                            // otherwise, just verify that the chain exists
                            rs3 = stmt3.executeQuery("select c2.id from pdb_chain c1, pdb_chain c2 where c1.pdb_release_id=c2.pdb_release_id and c1.id="+pdbChainID+" and c2.chain=\""+chain+"\"");
                            if (!rs3.next()) {
                                System.out.println("Unknown chain in domain "+rafCode+rafChain+": "+sid+" "+description);
                            }
                            rs3.close();
                        }
                    }
                }
                rs2.close();

                // finally, cover all un-observed residues
                for (int i=0; i<l; i++) {
                    String resID = RAF.getResID(rafBody,i);
                    if (resID.equals("B") ||
                        resID.equals("M") ||
                        resID.equals("E"))
                        covered[i] = true;
                }

                // are there leftover residues, for a non-1.73 chain?
                if (!gapsOK) {
                    int nMissing=0;
                    for (int i=0; i<l; i++)
                        if (covered[i]==false)
                            nMissing++;
                    if (nMissing>0)
                        System.out.println("Chain "+rafCode+rafChain+" not fully covered by domains; missing "+nMissing+" residues");
                }
            }
            rs.close();
            stmt3.close();
            stmt2.close();
            stmt.close();
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
