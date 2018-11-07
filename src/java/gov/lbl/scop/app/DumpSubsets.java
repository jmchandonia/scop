/*
 * Software to build and maintain SCOPe, https://scop.berkeley.edu/
 *
 * Copyright (C) 2009-2018 The Regents of the University of California
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
import gov.lbl.scop.util.*;
import gov.lbl.scop.local.LocalSQL;

/**
   Dump ASTRAL subsets and logs
   <pre>
   Version 1.1, 3/22/11 - can dump all types of subsets
   Version 1.0, 8/28/09 - original version
   </pre>
*/
public class DumpSubsets {
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            ResultSet rs;

            int verbosity = -1;  // 0=sids, 1=logs, 2=seqs
            int setType = -1; // 0=level, 1=id, 2=e-value
            PrintfStream outfile;
            int scopLevel = -1;  // for set type 0
            int pctID = 0; // for set type 1
            double log10E = 0.0; // for set type 2
            boolean isChain = false;  // for set types 1-2
            boolean isGD = true;  // for set types 1-2
            int sourceID = 2; // seqres, by default
            // valid pctID values, for sets 1-2
            int[] validPctID = null;
            // valid log10E values, for set type 2
            double[] validLog10E = null;

            // get scop version
            int pos = argv[0].lastIndexOf('-');
            int pos2 = argv[0].indexOf('.',pos+4);
            String ver = argv[0].substring(pos+1,pos2);
            int scopReleaseID = LocalSQL.lookupSCOPRelease(ver);
            if (scopReleaseID==0) {
                throw new Exception("Can't determine SCOP version from "+argv[0]);
            }

            // chain or domain?
            if (argv[0].indexOf("-chain-")>-1) {
                isChain = true;
                validPctID = MakeSubsets.makePctIDChain;
                validLog10E = null;
                if (argv[0].indexOf("-tg-")>-1)
                    sourceID = 4;
            }
            else {
                isChain = false;
                validPctID = MakeSubsets.makePctIDDomain;
                validLog10E = MakeSubsets.makeLog10E;
            }

            // gd or non-gd?
            if (argv[0].indexOf("-gd-")>-1)
                isGD = true;
            else
                isGD = false;

            // determine set type
            if (argv[0].indexOf("-sel-gs-sc-")>-1) {
                setType = 0;
                String abbrev = argv[0].substring(pos-2,pos);
                scopLevel = LocalSQL.lookupLevelAbbrev(abbrev);
                if (scopLevel==-1)
                    throw new Exception("Can't determine SCOP level from "+argv[0]);
            }
            else if ((pos2=argv[0].indexOf("-sel-gs-bib-"))>-1) {
                if (argv[0].indexOf("-verbose-")>-1)
                    pos2+=8;
                setType = 1;
                pctID = StringUtil.atoi(argv[0],pos2+12);
                if (Arrays.binarySearch(validPctID,pctID) < 0)
                    throw new Exception("Valid pct id not specified in "+argv[0]+": "+pctID);
            }
            else if ((pos2=argv[0].indexOf("-sel-gs-e100m-"))>-1) {
                if (argv[0].indexOf("-verbose-")>-1)
                    pos2+=8;
                setType = 2;
                String e = StringUtil.replace(argv[0].substring(pos2+15,pos),
                                              ",",".");
                log10E = StringUtil.atod(e);
                log10E = (double)Math.round(log10E*10.0) / 10.0;
                if ((validLog10E != null) &&
                    (Arrays.binarySearch(validLog10E,log10E) < 0))
                    throw new Exception("Valid log10E not specified in "+argv[0]+": "+log10E);
            }

            if (argv[0].endsWith(".id"))
                verbosity = 0;
            else if (argv[0].endsWith(".txt"))
                verbosity = 1;
            else if (argv[0].endsWith(".fa"))
                verbosity = 2;

            if ((setType == -1) || (verbosity == -1))
                throw new IllegalArgumentException("Don't know how to make file "+argv[0]);
	    
            outfile = new PrintfStream(argv[0]);

            Vector<Integer> repNodeID = new Vector<Integer>();
            Vector<String> repSid = new Vector<String>();
            Vector<Vector<SPACI.SPACINode>> allSets = new Vector<Vector<SPACI.SPACINode>>();

            // gather data:
            if (setType==0) {
                // scop clade-based sets:
                Vector<Integer> levelNodeID = new Vector<Integer>();

                // only dump classes 1-7
                rs = stmt.executeQuery("select s.rep_node_id, s.level_node_id from scop_subset_level s, scop_node n1, scop_node n2 where s.rep_node_id=n1.id and s.level_node_id=n2.id and n2.release_id="+scopReleaseID+" and n2.level_id="+scopLevel+" and substring(n1.sccs,1,1)<'h'");
                while (rs.next()) {
                    Vector<Integer> scopNodeID = new Vector<Integer>();
                    scopNodeID.add(new Integer(rs.getInt(1)));
                    Vector<Integer> newDomains = ASTRAL.nodeIDToDomainID(scopNodeID,
                                                                         false,
                                                                         isGD);
                    repNodeID.addAll(newDomains);
                    HashMap <Integer,String> newSidMap = ASTRAL.domainIDToSid(newDomains);
                    for (Integer repNode : newDomains)
                        repSid.add(newSidMap.get(repNode));
                    levelNodeID.add(new Integer(rs.getInt(2)));
                }

                if (verbosity==1) {
                    for (int i=0; i<levelNodeID.size(); i++) {
                        int parentNode = levelNodeID.get(i).intValue();
                        Vector<Integer> nodeIDs = MakeSubsets.descendentsOf(parentNode,8);
                        Vector<Integer> domainIDs = ASTRAL.nodeIDToDomainID(nodeIDs,
                                                                            false,
                                                                            isGD);
                        Vector<SPACI.SPACINode> nodes = MakeSubsets.getSortedDomains(domainIDs);
                        allSets.add(nodes);
                    }
                }
            }
            else if (setType==1) {
                // % id-based sets

                String query = "";
                if (isChain)
                    query = "select c.id from astral_chain_subset_id s, astral_chain c where c.id=s.astral_chain_id and s.pct_identical="+pctID+" and s.release_id="+scopReleaseID+" and c.source_id="+sourceID+" order by c.sid";
                else {
                    if (isGD)
                        query = "select d.id from astral_domain_subset_id s, astral_domain d where d.id=s.astral_domain_id and s.pct_identical="+pctID+" and s.release_id="+scopReleaseID+" and s.style_id=3 order by d.node_id";
                    else
                        query = "select d.id from astral_domain_subset_id s, astral_domain d where d.id=s.astral_domain_id and s.pct_identical="+pctID+" and s.release_id="+scopReleaseID+" and s.style_id=2 order by d.node_id";
                }
                rs = stmt.executeQuery(query);
                while (rs.next()) {
                    repNodeID.add(new Integer(rs.getInt(1)));
                }

                if (verbosity==0) {
                    HashMap<Integer,String> sidMap;
                    if (isChain)
                        sidMap = ASTRAL.astralChainIDToSid(repNodeID);
                    else
                        sidMap = ASTRAL.domainIDToSid(repNodeID);
                    for (Integer nodeID : repNodeID)
                        repSid.add(sidMap.get(nodeID));
                }
                else if (verbosity==1) {
                    Vector<Integer> allNodes = 
                        DumpSeqs.getSeqs(scopReleaseID,
                                         isChain,
                                         0, // no rejects
                                         0, // no ntc
                                         false, // no sort
                                         sourceID,
                                         (isGD ? 1 : 0),
                                         false); // unique
                    if (isChain)
                        allSets = MakeSubsets.getSortedChainSets(allNodes, pctID,scopReleaseID);
                    else
                        allSets = MakeSubsets.getSortedDomainSets(allNodes, pctID,(isGD?3:2));
                }
            }
            else if (setType==2) {
                // E value-based sets

                String query = "";
                if (isChain)
                    throw new Exception("no current E-value based chain subsets");
                else {
                    if (isGD)
                        query = "select d.id from astral_domain_subset_blast_e s, astral_domain d where d.id=s.astral_domain_id and s.blast_log10_e="+log10E+" and release_id="+scopReleaseID+" and s.style_id=3 order by d.node_id";
                    else
                        query = "select d.id from astral_domain_subset_blast_e s, astral_domain d where d.id=s.astral_domain_id and s.blast_log10_e="+log10E+" and release_id="+scopReleaseID+" and s.style_id=2 order by d.node_id";
                }
                rs = stmt.executeQuery(query);
                while (rs.next()) {
                    repNodeID.add(new Integer(rs.getInt(1)));
                }

                if (verbosity==0) {
                    HashMap<Integer,String> sidMap = ASTRAL.domainIDToSid(repNodeID);
                    for (Integer nodeID : repNodeID) {
                        repSid.add(sidMap.get(nodeID));
                    }
                }
                else if (verbosity==1) {
                    Vector<Integer> allNodes = 
                        DumpSeqs.getSeqs(scopReleaseID,
                                         isChain,
                                         0, // no rejects
                                         0, // no ntc
                                         false, // no sort
                                         sourceID,
                                         (isGD ? 1 : 0),
                                         false); // unique
                    allSets = MakeSubsets.getSortedDomainSets(allNodes, log10E,(isGD?3:2));
                }
            }

            // show data:
            if (verbosity==0) {
                for (String s : repSid) {
                    if (scopReleaseID==1)
                        s = s.toLowerCase();
                    outfile.printf("%s\n",s);
                }
            }
            else if (verbosity==1) {
                for (Vector<SPACI.SPACINode> nodes : allSets) {
                    MakeSubsets.printVerboseLog(nodes,outfile);
                    outfile.printf("\n");
                }
            }
            else if (verbosity==2) {
                DumpSeqs.writeFasta(outfile,
                                    repNodeID,
                                    (isChain ? 2 : 1),
                                    true);
            }
	    
            outfile.flush();
            outfile.close();
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
