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
import gov.lbl.scop.util.*;

/**
   Makes domain sequences for all SCOP nodes that don't already have
   them.
*/
public class MakeDomainSeq {
    private static boolean debug = false;
    
    final public static int nChains(int nodeID) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select count(*) from link_pdb where node_id="+nodeID);
        rs.next();
        int rv = rs.getInt(1);
        rs.close();
        stmt.close();
        return rv;
    }
				    
    final public static String domainHeader(int nodeID, int sourceType, int styleType, int order) {
        try {
            Statement stmt = LocalSQL.createStatement();
            ResultSet rs = stmt.executeQuery("select sid, sccs, description from scop_node where id="+nodeID);
            rs.next();
            String sid = rs.getString(1);
            String sccs = rs.getString(2);
            String description = rs.getString(3).substring(5);
            rs.close();
	    
            char chain = ' ';
            char lastChain = ' ';
            int currentChain = 0;
            boolean firstRegion = true;
            boolean addedFragment = false;
            String regionString = null;
            String rv;
		
            // first, get correct string describing region in this sequence
            if ((styleType==1) || (styleType==3)) {
                regionString = description;
            }
            else {
                sid = "e"+sid.substring(1);
                StringTokenizer st = new StringTokenizer(description,",");
                while (st.hasMoreTokens()) {
                    String region = st.nextToken();
                    int pos = region.indexOf(':');
                    if (pos==-1)
                        chain = ' ';
                    else
                        chain = region.charAt(pos-1);
		
                    if (firstRegion) {
                        lastChain = chain;
                        firstRegion = false;
                    }

                    if (lastChain != chain)
                        currentChain++;

                    if ((styleType != 2) || (currentChain == order)) {
                        // include this region
                        if (addedFragment) {
                            regionString += ",";
                            regionString += region;
                        }
                        else {
                            sid += chain;
                            regionString = region;
                            addedFragment = true;
                        }
                    }
		    
                    lastChain = chain;
                }
            }

            // get sid right for GD
            if (styleType==3) {
                sid = "g"+sid.substring(1);
            }

            // build up header
            rv = sid;
            rv += " "+sccs;
            rv += " ("+regionString+")";
            int protein = LocalSQL.findParent(nodeID,6);
            String name = LocalSQL.getDescription(protein);
            if (name != null)
                rv += " "+name;
            int species = LocalSQL.findParent(nodeID,7);
            name = LocalSQL.getDescription(species);
            if (name != null)
                rv += " {"+name+"}";

            stmt.close();
            return rv;
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
       note:  seq is case sensitive
    */
    final public static int lookupOrCreateSeq(String seq) throws Exception {
        int rv = 0;
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select id from astral_seq where seq=\""+
                                         seq+
                                         "\"");
        if (rs.next())
            rv = rs.getInt(1);
        else {
            rs.close();
            RAF.SequenceFragment sf = new RAF.SequenceFragment(seq);
            int isReject = 0;
            if (sf.isReject())
                isReject = 1;
            stmt.executeUpdate("insert into astral_seq values (null, \""+
                               seq+"\", "+
                               isReject+")",
                               Statement.RETURN_GENERATED_KEYS);
            rs = stmt.getGeneratedKeys();
            rs.next();
            rv = rs.getInt(1);
        }
        rs.close();
        stmt.close();
        return rv;
    }

    /**
       Make ASTRAL domains corresponding to a given scop node
    */
    final public static void makeDomainSeqs(int nodeID,
                                            boolean headersOnly) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select sid from scop_node where id="+nodeID);

        rs.next();
        String scopSID = rs.getString(1);
        rs.close();

        if (debug)
            System.out.println("working on "+scopSID+" "+nodeID);

        int nChains = nChains(nodeID);
        for (int sourceID=1; sourceID<=2; sourceID++) {
            if ((nChains == 1) && (scopSID.indexOf('.')==-1)) {
                // just make regular sequences
                String header = domainHeader(nodeID,sourceID,1,0);
                // System.out.println(header);

                int pos = header.indexOf(' ');
                String sid = header.substring(0,pos);
                header = header.substring(pos+1);

                if (headersOnly) {
                    stmt.executeUpdate("update astral_domain set header = \""+
                                       StringUtil.replace(header,"\"","\\\"")+"\" "+
                                       "where node_id="+nodeID+" and source_id="+sourceID+" and style_id=1");
                }
                else {
                    RAF.SequenceFragment sf = LocalSQL.domainSeq(nodeID,sourceID,1,0);
                    String seq = null;
                    int isReject = 0;
                    if (sf==null) {
                        System.out.println("error getting sequence for "+sid);
                        seq = "";
                        isReject = 1;
                    }
                    else {
                        seq = sf.getSequence();
                        if (sf.isReject()) isReject = 1;
                        if (debug)
                            System.out.println("got sequence for "+sid+" reject="+isReject);
                    }

                    int seqID = lookupOrCreateSeq(seq);

                    stmt.executeUpdate("insert into astral_domain values (null,"+
                                       nodeID+", "+
                                       sourceID+", 1, \""+
                                       StringUtil.replace(sid,"\"","\\\"")+"\", \""+
                                       StringUtil.replace(header,"\"","\\\"")+"\", "+
                                       seqID+")");
                }
            }
            else {
                // make genetic domain sequence
                String header = domainHeader(nodeID,sourceID,3,0);
                int pos = header.indexOf(' ');
                String sid = header.substring(0,pos);
                header = header.substring(pos+1);

                if (headersOnly) {
                    stmt.executeUpdate("update astral_domain set header = \""+
                                       StringUtil.replace(header,"\"","\\\"")+"\" "+
                                       "where node_id="+nodeID+" and source_id="+sourceID+" and style_id=3");
                }
                else {
                    RAF.SequenceFragment sf = LocalSQL.domainSeq(nodeID,sourceID,3,0);
                    String seq = null;
                    int isReject = 0;
                    if (sf==null) {
                        System.out.println("error getting sequence for "+sid);
                        seq = "";
                        isReject = 1;
                    }
                    else {
                        seq = sf.getSequence();
                        if (sf.isReject()) isReject = 1;
                        if (debug)
                            System.out.println("got sequence for "+sid+" reject="+isReject);
                    }

                    if (debug)
                        System.out.println("got sequence for "+sid+" reject="+isReject);

                    int seqID = lookupOrCreateSeq(seq);

                    stmt.executeUpdate("insert into astral_domain values (null,"+
                                       nodeID+", "+
                                       sourceID+", 3, \""+
                                       StringUtil.replace(sid,"\"","\\\"")+"\", \""+
                                       StringUtil.replace(header,"\"","\\\"")+"\", "+
                                       seqID+")");
                }

                // and one original-style sequence per chain
                for (int i=0; i<nChains; i++) {
                    header = domainHeader(nodeID,sourceID,2,i);
                    // System.out.println(header);
                    // System.out.println("chain "+i+" of "+nChains);
                    pos = header.indexOf(' ');
                    sid = header.substring(0,pos);
                    header = header.substring(pos+1);

                    if (headersOnly) {
                        stmt.executeUpdate("update astral_domain set header = \""+
                                           StringUtil.replace(header,"\"","\\\"")+"\" "+
                                           "where node_id="+nodeID+" and source_id="+sourceID+" and style_id=2 and sid=\""+
                                           StringUtil.replace(sid,"\"","\\\"")+"\"");
                    }
                    else {
                        RAF.SequenceFragment sf = LocalSQL.domainSeq(nodeID,sourceID,2,i);
                        String seq = null;
                        int isReject = 0;
                        if (sf==null) {
                            System.out.println("error getting sequence for "+sid);
                            seq = "";
                            isReject = 1;
                        }
                        else {
                            seq = sf.getSequence();
                            if (sf.isReject()) isReject = 1;
                            if (debug)
                                System.out.println("got sequence for "+sid+" reject="+isReject);
                        }

                        if (debug)
                            System.out.println("got sequence for "+sid+" reject="+isReject);
                        int seqID = lookupOrCreateSeq(seq);
                        stmt.executeUpdate("insert into astral_domain values (null,"+
                                           nodeID+", "+
                                           sourceID+", 2, \""+
                                           StringUtil.replace(sid,"\"","\\\"")+"\", \""+
                                           StringUtil.replace(header,"\"","\\\"")+"\", "+
                                           seqID+")");
                    }
                }
            }
        }
        stmt.close();
        return;
    }
    
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            ResultSet rs;

            boolean headersOnly = false;

            if ((argv.length>0) && (argv[0].equals("-debug")))
                debug = true;

            if ((argv.length>0) && (argv[0].equals("-headers")))
                headersOnly = true;

            if (headersOnly) {
                System.out.println("redoing headers for latest SCOP");
                debug = true;
            }

            if ((argv.length>0) && (argv[0].startsWith("H"))) {
                // redo headers for a specific node
                int nodeID = StringUtil.atoi(argv[0],1);
                makeDomainSeqs(nodeID, true);
                System.exit(0);
            }
	    
            while (true) {
                boolean found = false;
                if (debug)
                    System.out.println("fetching more domains.");
                if (headersOnly) {
                    int scopReleaseID = LocalSQL.getLatestSCOPRelease(false);
		    
                    rs = stmt.executeQuery("select id from scop_node where level_id=8 and release_id="+scopReleaseID);
                }
                else
                    rs = stmt.executeQuery("select id from scop_node where level_id=8 and id not in (select node_id from astral_domain) limit 1000");
                while (rs.next()) {
                    if (!headersOnly)
                        found = true;

                    int nodeID = rs.getInt(1);
		    
                    makeDomainSeqs(nodeID,
                                   headersOnly);
                }
                rs.close();
                if (!found)
                    System.exit(0);
            }
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
