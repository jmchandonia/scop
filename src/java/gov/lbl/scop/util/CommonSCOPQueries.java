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
package gov.lbl.scop.util;

import gov.lbl.scop.app.DumpSeqs;
import gov.lbl.scop.local.LocalSQL;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Vector;

/**
 * This class contains lots of common SCOP queries
 * User: fox
 * Date: 3/11/13
 * Time: 10:46 AM
 * To change this template use File | Settings | File Templates.
 */
public class CommonSCOPQueries {

    /**
     * Convert release ID to name
     * Removed the dependency from the DB to save time.
     * <p/>
     * This method should be updated as new releases are made.
     *
     * @param scopReleaseID
     * @return
     */
    public static String getScopReleaseName(int scopReleaseID) {
        String releaseName = "unknown";
        switch (scopReleaseID) {
            case (1):
                releaseName = "1.55";
                break;
            case (2):
                releaseName = "1.57";
                break;
            case (3):
                releaseName = "1.59";
                break;
            case (4):
                releaseName = "1.61";
                break;
            case (5):
                releaseName = "1.63";
                break;
            case (6):
                releaseName = "1.65";
                break;
            case (7):
                releaseName = "1.67";
                break;
            case (8):
                releaseName = "1.69";
                break;
            case (9):
                releaseName = "1.71";
                break;
            case (10):
                releaseName = "1.73";
                break;
            case (11):
                releaseName = "1.75";
                break;
            case (12):
                releaseName = "2.01";
                break;
            case (13):
                releaseName = "2.02";
                break;
            case (14):
                releaseName = "2.03";
                break;
            case (15):
                releaseName = "2.04";
                break;
        }
        return releaseName;
    }

    /**
     * Get the latest public SCOP release ID
     * IDs are listed in the scop_release table
     */
    final public static int getLatestPublicSCOPReleaseID() throws SQLException {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select id from scop_release where is_public=1 order by id desc limit 1");
        rs.next();
        int scopPublicReleaseID = rs.getInt(1);
        rs.close();
        stmt.close();
        return scopPublicReleaseID;
    }

    /**
     * Get the latest SCOP release ID
     * IDs are listed in the scop_release table
     */
    final public static int getLatestSCOPReleaseID() throws SQLException {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select id from scop_release order by id desc limit 1");
        rs.next();
        int scopReleaseID = rs.getInt(1);
        rs.close();
        stmt.close();
        return scopReleaseID;
    }


    /**
     * Get the PDB chain IDs from the database with the same sequence id (source style seqres)
     *
     * @param seqID
     * @param scopReleaseID get chains that were included in SCOP release <=scopReleaseID
     * @return
     * @throws Exception
     * TODO: should we need scopReleaseID as a parameter?  For evaluation purposes, may need to split into two parameters or have a separate evaluation DB?
     */
    final public static ArrayList<Integer> getPDBChainsWithSequence(int seqID, int scopReleaseID) throws Exception {
        ArrayList<Integer> pdbChainIDs = new ArrayList<Integer>();
        //String query="select distinct(r.pdb_chain_id) from astral_chain ac, raf r where ac.raf_id=r.id and source_id=2 and " +
        //    "r.last_release_id<="+ scopReleaseID + " and seq_id="+seqID;
        String query = "select distinct(r.pdb_chain_id) from astral_chain ac, raf r where ac.raf_id=r.id and source_id=2 and " +
                "r.first_release_id<=" + scopReleaseID + " and r.last_release_id>=" + scopReleaseID + " and ac.seq_id=" + seqID;
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        while (rs.next()) {
            pdbChainIDs.add(rs.getInt(1));
        }
        rs.close();
        stmt.close();
        return pdbChainIDs;
    }


    /**
     * Convert scop_level_id to a string.
     * For example:
     * 1 -> ROOT
     * 2 -> CLASS
     * ..
     * 8 -> DOMAIN
     *
     * @param level
     * @return a string for the level
     * @throws Exception
     */
    public static String getScopNodeLevelString(int level) throws Exception {
        String levelString;
        switch (level) {
            case (1):
                levelString = "ROOT";
                break;
            case (2):
                levelString = "CLASS";
                break;
            case (3):
                levelString = "FOLD";
                break;
            case (4):
                levelString = "SUPERFAMILY";
                break;
            case (5):
                levelString = "FAMILY";
                break;
            case (6):
                levelString = "SPECIES";
                break;
            case (7):
                levelString = "PROTEIN";
                break;
            case (8):
                levelString = "DOMAIN";
                break;
            default:
                throw new Exception("Could not find level " + level);
        }
        return levelString;
    }

    /**
     * Search up the tree
     *
     * @param scopNodeID1
     * @param scopNodeID2
     * @param level
     * @return
     */
    public static int getFirstCommonLevel(int scopNodeID1, int scopNodeID2, int level) throws Exception {
        if (level < 0) {
            throw new Exception("Problem");
        }
        if (scopNodeID1 == scopNodeID2) {
            return level;
        }
        int parentNodeID1 = LocalSQL.findParent(scopNodeID1, level - 1);
        int parentNodeID2 = LocalSQL.findParent(scopNodeID2, level - 1);
        return getFirstCommonLevel(parentNodeID1, parentNodeID2, level - 1);
    }

    /**
     * Get the pdb_chain ID for an astral_chain
     *
     * @param astralChainID ID in astral_chain table
     * @return the pdb_chain id
     * @throws Exception
     */
    final public static int getPDBChainIDFromAstralChainID(int astralChainID) throws Exception {
        String query = "select r.pdb_chain_id from astral_chain ac, raf r where ac.raf_id=r.id and ac.id=" + astralChainID;
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        rs.next();
        int pdbChainID = rs.getInt(1);
        rs.close();
        stmt.close();
        return pdbChainID;
    }

    /**
     * Get the scop_node sccs
     *
     * @param scopNodeID
     * @return string
     * @throws Exception
     */
    final public static String getSCCS(int scopNodeID) throws Exception {
        int ScopNodeClass = -1;
        String query = "select sccs from scop_node where id=" + scopNodeID;
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        rs.next();
        String sccs = rs.getString(1);
        rs.close();
        stmt.close();
        return sccs;
    }

    /**
     * Get the scop_node class
     *
     * @param scopNodeID
     * @return character, a-l
     * @throws Exception
     */
    final public static char getScopNodeClass(int scopNodeID) throws Exception {
        int ScopNodeClass = -1;
        String query = "select sccs from scop_node where id=" + scopNodeID;
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        rs.next();
        String sccs = rs.getString(1);
        rs.close();
        stmt.close();
        char char1 = sccs.charAt(0);
        return char1;
    }

    /**
     * Get the comments for a scop node in a long string
     *
     * @param scopNodeID
     * @return String of comments
     * @throws Exception
     */
    final public static String getScopNodeCommentString(int scopNodeID) throws SQLException {
        String query = "select c.description from scop_node n, scop_comment c where c.node_id=n.id and n.id=" + scopNodeID;
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        String commentString = "";
        while (rs.next()) {
            commentString += rs.getString(1) + " ";
        }
        rs.close();
        stmt.close();
        return commentString;
    }


    /**
     * Get the sequence in astral_seq from the seqID
     *
     * @param seqID
     * @return
     * @throws Exception
     */
    final public static String getAstralSeq(int seqID) throws Exception {
        String query = "select seq from astral_seq where id=" + seqID;
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        rs.next();
        String astralSeq = rs.getString(1);
        rs.close();
        stmt.close();
        return astralSeq;
    }


    /**
     * Get the set of ASTEROID IDs for some chainID
     * @param chainID
     * @return list of ASTEROID IDs
     * @throws Exception
     */
    final public static ArrayList<Integer> getAsteroidIDs(int chainID) throws Exception {
        ArrayList<Integer> answerList = new ArrayList<Integer>();
        String query = "select id from asteroid where chain_id=" + chainID;
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        while (rs.next()) {
            answerList.add(new Integer(rs.getInt(1)));
        }
        rs.close();
        stmt.close();
        return answerList;
    }

    /**
     * Get the sid in astral_chain from the chainID
     *
     * @param chainID
     * @return
     * @throws Exception
     */
    final public static String getAstralChainSid(int chainID) throws Exception {
        String query = "select sid from astral_chain where id=" + chainID;
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        rs.next();
        String sid = rs.getString(1);
        rs.close();
        stmt.close();
        return sid;
    }

    /**
     * Get the astral_seq id from the astral_chain
     *
     * @param chainID
     * @return the sequence id of the chain
     * @throws Exception
     */
    final public static int getSequenceIDFromAstralChainId(int chainID) throws SQLException {
        String query = "select seq_id from astral_chain where id=" + chainID;
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        rs.next();
        int seqId = rs.getInt(1);
        rs.close();
        stmt.close();
        return seqId;
    }

    /**
     * Get the sequence length of a given chain
     * @param chainID
     * @return
     * @throws SQLException
     */
    public static int getSeqLengthFromAstralChainID(int chainID) throws SQLException {
        String query = "select length(s.seq) from astral_seq s, astral_chain ac where ac.seq_id=s.id and ac.id=" + chainID;
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        rs.next();
        int seqLength = rs.getInt(1);
        rs.close();
        stmt.close();
        return seqLength;
    }

    /**
     * Get the sequence length
     * @param seqID
     * @return
     * @throws SQLException
     */
    public static int getSeqLength(int seqID) throws SQLException {
        String query = "select length(s.seq) from astral_seq s where s.id=" + seqID;
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        rs.next();
        int seqLength = rs.getInt(1);
        rs.close();
        stmt.close();
        return seqLength;
    }

    /**
     * Get the astral_seq id from the node ID
     *
     * @param nodeID
     * @return the sequence id of the chain
     * @throws Exception
     */
    final public static int getSeqLengthFromScopNodeID(int nodeID) throws SQLException {
        String query = "select length(s.seq) from astral_seq s, astral_domain d " +
                "where d.source_id=2 and s.id=d.seq_id and d.node_id=" + nodeID;
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        rs.next();
        int seqLength = rs.getInt(1);
        rs.close();
        stmt.close();
        return seqLength;
    }

    /**
     * Retrieve SCOP nodes for a particular astral chain.
     * If no SCOP nodes were found, will return an empty array
     *
     * @param astralChainID the ID of the astral_chain
     * @return array of SCOP node IDs
     * @throws Exception
     */
    final public static ArrayList<Integer> getScopNodeIDsForAstralChain(int astralChainID, int scopReleaseID) throws Exception {
      int pdbChainID = CommonSCOPQueries.getPDBChainIDFromAstralChainID(astralChainID);
        return getScopNodeIDsForPdbChain(pdbChainID, scopReleaseID);
    }

    /**
     * Get the SCOP node IDs for a pdb chain and a particular SCOP release
     * Returns an empty array list if no SCOP nodes were found
     *
     * @param pdbChainID
     * @param scopReleaseID
     * @return
     * @throws Exception
     */
    final public static ArrayList<Integer> getScopNodeIDsForPdbChain(int pdbChainID, int scopReleaseID) throws SQLException {
        ArrayList<Integer> answerArray = new ArrayList<Integer>();
        String query = "select n.id from scop_node n, link_pdb l where " +
                "n.id=l.node_id and" +
                " n.release_id=" + scopReleaseID +
                " and l.pdb_chain_id=" + pdbChainID;
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        while (rs.next()) {
            answerArray.add(rs.getInt(1));
        }
        rs.close();
        stmt.close();
        return answerArray;
    }


    /**
     * Check if scop node is in the next release and if the sccs is stable
     * @param scopNodeID
     * @return
     * @throws Exception
     */
    public static boolean isScopNodeStableInNextRelease(int scopNodeID) throws Exception {
        String sccs = CommonSCOPQueries.getSCCS(scopNodeID);
        int nextScopNodeID = CommonSCOPQueries.getScopNodeInNextRelease(scopNodeID);
        if (nextScopNodeID < 0) {
            // scop node not found, continue to next annotation
            return false;
        }
        String nextSccs = CommonSCOPQueries.getSCCS(nextScopNodeID);
        if (!sccs.equals(nextSccs)) {
            return false;
        }
        return true;
    }


    /**
     * Get the pdb_release_id from the pdb_chain table
     *
     * @param pdbChainID the ID of the pdb_chain entry
     * @return the ID of the pdb_release
     * @throws Exception
     */
    final public static int getPDBEntryForPDBChain(int pdbChainID) throws SQLException {
        String query = " select r.pdb_entry_id from pdb_release r, pdb_chain c where c.pdb_release_id=r.id and c.id=" + pdbChainID;
        //System.out.println("getPDBEntryForPDBChain query " + query);
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        rs.next();
        int pdbEntryID = rs.getInt(1);
        rs.close();
        stmt.close();
        return pdbEntryID;
    }

    /**
     * Get the pdb_release_id from the pdb_chain table
     *
     * @param pdbChainID the ID of the pdb_chain entry
     * @return the ID of the pdb_release
     * @throws Exception
     */
    final public static int getPdbReleaseForPDBChain(int pdbChainID) throws SQLException {
        String query = "select pdb_release_id from pdb_chain where id=" + pdbChainID;
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        rs.next();
        int pdbReleaseID = rs.getInt(1);
        rs.close();
        stmt.close();
        return pdbReleaseID;
    }

    /**
     * get the scop node description
     *
     * @param scopNodeID
     * @return
     * @throws Exception
     */
    final public static String getScopNodeDescription(int scopNodeID) throws SQLException {
        String query = "select description from scop_node where id=" + scopNodeID;
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        rs.next();
        String description = rs.getString(1);
        rs.close();
        stmt.close();
        return description;
    }

    /**
     * get the scop node description
     *
     * @param scopNodeID
     * @return
     * @throws Exception
     */
    final public static String getScopNodeSid(int scopNodeID) throws SQLException {
        String query = "select sid from scop_node where id=" + scopNodeID;
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        rs.next();
        String sid = rs.getString(1);
        rs.close();
        stmt.close();
        return sid;
    }


    /**
     * Check if a scop node is a genetic domain.
     * <p/>
     * By genetic domain, we mean that the domain contains regions from more than one chain.
     *
     * @param scopNodeID
     * @return true if scop node is a genetic domain, false otherwise
     * @throws Exception
     */
    final public static boolean isGeneticDomain(int scopNodeID) throws SQLException {
        // get the number of pdb chains associated with a scop node
        String query = "select count(pdb_chain_id) from link_pdb where node_id=" + scopNodeID;
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        rs.next();
        int numPdbChains = rs.getInt(1);
        rs.close();
        stmt.close();
        return (numPdbChains > 1 ? true : false);
    }

    /**
     * Check if the domain-level scop_node is in a ribosomal protein or family
     *
     * @param scopNodeID
     * @return
     * @throws Exception
     */
    public static boolean isRibosomal(int scopNodeID) throws Exception {
        boolean ribosomal = false;
        int protNode = LocalSQL.findParent(scopNodeID, 6);
        int famNode = LocalSQL.findParent(protNode, 5);
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select id from scop_node where description like '%ibosomal%' and id in (" + protNode + "," + famNode + ")");
        if (rs.next()) {
            ribosomal = true;
        }
        rs.close();
        stmt.close();
        return ribosomal;
    }

    /**
     * returns the node id from the scop node with specified sid and release ID
     * returns -1 if no node was found
     *
     * @param sid
     * @param releaseID
     * @return
     */
    public static int getScopNodeBySidAndReleaseID(String sid, int releaseID) throws SQLException {
        String query = "select id from scop_node where sid=\"" + sid + "\" and release_id=" + releaseID;
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        int scopNodeID = -1;
        if (rs.next()) {
            scopNodeID = rs.getInt(1);
        }
        rs.close();
        stmt.close();
        return scopNodeID;
    }


    public static int getScopNodeBySunIDAndReleaseID(int sunID, int releaseID) throws SQLException {
        String query = "select id from scop_node where sunid=" + sunID + " and release_id=" + releaseID;
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        int scopNodeID = -1;
        if (rs.next()) {
            scopNodeID = rs.getInt(1);
        }
        rs.close();
        stmt.close();
        return scopNodeID;
    }

    public static int getSunID(int nodeID) throws SQLException {
        String query = "select sunid from scop_node where id=" + nodeID;
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        int sunID = -1;
        if (rs.next()) {
            sunID = rs.getInt(1);
        }
        rs.close();
        stmt.close();
        return sunID;
    }


    public static java.sql.Date getReleaseDate(int scopReleaseID) throws SQLException {
        String query = "select release_date from scop_release where id=" + scopReleaseID;
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        java.sql.Date releaseDate = null;
        if (rs.next()) {
            releaseDate = rs.getDate(1);
        }
        rs.close();
        stmt.close();
        return releaseDate;
    }



    /**
     * Get the RAF Line for a SCOP node
     * This will get the RAF record where the scop release id falls within
     * the RAF record's first_release_id and last_release_id
     *
     * @param scopNodeID
     * @return
     * @throws Exception
     */
    final public static String getRAFLineForScopNode(int scopNodeID) throws SQLException {
        String query = "select r.line from scop_node n, link_pdb l, raf r where " +
                "l.node_id=n.id and l.pdb_chain_id=r.pdb_chain_id and " +
                "r.first_release_id<=n.release_id and " +
                "r.last_release_id>=n.release_id and n.id=" + scopNodeID +
                " order by first_release_id desc";
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        rs.next();
        String rafLine = rs.getString(1);
        rs.close();
        stmt.close();
        return rafLine;
    }


    /**
     * Get the RAF Line for a SCOP node
     * This will get the RAF record where the release id falls within
     * the RAF record's first_release_id and last_release_id
     *
     * @param scopNodeID
     * @return
     * @throws Exception
     */
    final public static int getAstralChainIDFromScopNode(int scopNodeID) throws SQLException {
        String query = "select ac.id from astral_chain ac, raf r, scop_node n, link_pdb l ";
        query += "where n.id=l.node_id ";  // join scop_node and link_pdb
        query += "and l.pdb_chain_id=r.pdb_chain_id ";  // join link_pdb and raf
        query += "and ac.raf_id=r.id ";  // join astral_chain and raf
        query += "and ac.source_id=2 "; // restrict to astral_chains of source 2
        query += "and r.first_release_id<=n.release_id and r.last_release_id>=n.release_id ";  // restrict date range
        query += "and n.id=" + scopNodeID;
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        rs.next();
        int astralChainID = rs.getInt(1);
        rs.close();
        stmt.close();
        return astralChainID;
    }

    /**
     * Get the RAF Line for a astral chain
     *
     * @param astralChainID
     * @return
     * @throws Exception
     */
    final public static String getRAFLineForAstralChain(int astralChainID) throws SQLException {
        String query = "select r.line from astral_chain ac, raf r where r.id=ac.raf_id and ac.id=" + astralChainID;
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        rs.next();
        String rafLine = rs.getString(1);
        rs.close();
        stmt.close();
        return rafLine;
    }


    /**
     * Get all of the distinct astral chains for chains that were newly added in this release of SCOP.
     *
     * @param scopReleaseID
     * @return
     * @throws Exception
     */
    final public static ArrayList<Integer> getNewAstralChainsInScopRelease(int scopReleaseID) throws SQLException {
        ArrayList<Integer> chainIDs = new ArrayList<Integer>();
        //String query = "select distinct(ac.id) from raf r, astral_chain ac where r.id=ac.raf_id and ac.source_id=2 and r.raf_version_id=2 and r.first_release_id="+ scopReleaseID;
        String query = "select distinct(ac.id) from astral_chain ac, raf r where r.id=ac.raf_id and ac.source_id=2 and (select count(r2.first_release_id) from raf r2, astral_chain ac2 where ac2.sid=ac.sid and ac2.raf_id=r2.id and r2.first_release_id<r.first_release_id)=0 and r.first_release_id=" + scopReleaseID;
        query += " order by ac.id";
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        while (rs.next()) {
            chainIDs.add(new Integer(rs.getInt(1)));
        }
        rs.close();
        stmt.close();
        return chainIDs;
    }


    /**
     * Find whether the pdb_source associated with the astral_chain is synthetic
     * or whether the pdb_entry description mentions designed or synthesized
     *
     * @param astralChainID
     * @return
     * @throws Exception
     */
    final public static boolean isSyntheticConstruct(int astralChainID) throws SQLException {
        boolean synthetic = false;
        Statement stmt = LocalSQL.createStatement();

        // this query checks if ANY chain within the pdb release is synthetic
        String query = "select s.id from pdb_source s, pdb_chain_source pcs, pdb_release pr1, pdb_release pr2, pdb_chain pc1, pdb_chain pc2, astral_chain ac, raf r where s.is_synthetic=1 and pr1.pdb_entry_id=pr2.pdb_entry_id and pc2.pdb_release_id=pr2.id and pc2.id=pcs.pdb_chain_id and pcs.pdb_source_id=s.id and pc1.pdb_release_id=pr1.id and r.pdb_chain_id=pc1.id and ac.raf_id=r.id and ac.id=" + astralChainID;

        // this query checks if the specified chain is synthetic.  Since older releases might not have had that
        // meta data, it will look for other releases that might have had that, hence the need to look at
        // multiple pdb_chain entries
        // String query = "select s.id from pdb_source s, pdb_chain_source pcs, pdb_release pr1, pdb_release pr2, pdb_chain pc1, pdb_chain pc2, astral_chain ac, raf r where s.is_synthetic=1 and pr1.pdb_entry_id=pr2.pdb_entry_id and pc2.pdb_release_id=pr2.id and pc2.id=pcs.pdb_chain_id and pc1.chain=pc2.chain and pcs.pdb_source_id=s.id and pc1.pdb_release_id=pr1.id and r.pdb_chain_id=pc1.id and ac.raf_id=r.id and ac.id=" + astralChainID;
        ResultSet rs = stmt.executeQuery(query);
        if (rs.next()) {
            synthetic = true;
        }
        rs.close();
        query = "select e.id from pdb_entry e, pdb_release pr, pdb_chain pc, astral_chain ac, raf r where (e.description like '%design%' or e.description like '%synthesized%') and pc.pdb_release_id=pr.id and pr.pdb_entry_id=e.id and r.pdb_chain_id=pc.id and ac.raf_id=r.id and ac.id=" + astralChainID;
        rs = stmt.executeQuery(query);
        if (rs.next()) {
            synthetic = true;
        }
        rs.close();
        stmt.close();
        return synthetic;
    }

    /**
     * Determine whether the sequence for the astral chain was marked as a "reject"
     *
     * @param astralChainID
     * @return
     * @throws Exception
     */
    final public static boolean isSeqReject(int astralChainID) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        String query = "select seq.is_reject from astral_chain ac, astral_seq seq where ac.seq_id=seq.id and ac.id=" + astralChainID;
        ResultSet rs = stmt.executeQuery(query);
        rs.next();
        int rejectCode = rs.getInt(1);
        rs.close();
        stmt.close();
        if (rejectCode == 0) {
            return false;
        } else if (rejectCode == 1) {
            return true;
        }
        throw new Exception("Unknown code in is_reject column for sequence for chain " + astralChainID);
    }

    /**
     * Get the curation type
     * @param scopNodeID
     * @return
     * @throws SQLException
     */
    public static int getCurationType(int scopNodeID) throws SQLException {
        Statement stmt = LocalSQL.createStatement();
        String query = "SELECT curation_type_id FROM scop_node WHERE id="+scopNodeID;
        ResultSet rs = stmt.executeQuery(query);
        rs.next();
        int curationType = rs.getInt(1);
        rs.close();
        stmt.close();
        return curationType;
    }

    /**
     * Get the resolution of an astral_chain
     *
     * @param astralChainID
     * @return
     * @throws Exception
     */
    final public static double getResolution(int astralChainID) throws SQLException {
        Statement stmt = LocalSQL.createStatement();
        String query = "select pr.resolution from pdb_release pr, pdb_chain pc, astral_chain ac, raf r where pc.pdb_release_id=pr.id and r.pdb_chain_id=pc.id and ac.raf_id=r.id and ac.id=" + astralChainID;
        ResultSet rs = stmt.executeQuery(query);
        rs.next();
        double res = rs.getDouble(1);
        rs.close();
        stmt.close();
        return res;
    }

    /**
     * Get the id of the root node for a particular SCOP release
     *
     * @param scopReleaseID
     * @return
     * @throws Exception
     */
    final public static int getRootNodeID(int scopReleaseID) throws SQLException {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select id from scop_node where level_id=1 and release_id=" + scopReleaseID);
        rs.next();
        int rootNodeID = rs.getInt(1);
        rs.close();
        stmt.close();
        return rootNodeID;
    }

    /**
     * returns the hit seq ID if the blast hit is found, otherwise returns -1
     *
     * @param blastHitID
     * @return
     */
    public static int getHitSeqID(int blastHitID) throws Exception {
        String query = "select seq2_id from astral_seq_blast where id=" + blastHitID;
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        int hitSeqID = 0;
        if (rs.next()) {
            hitSeqID = rs.getInt(1);
        } else {
            hitSeqID = -1;
        }
        rs.close();
        stmt.close();
        return hitSeqID;
    }

    /**
     * Get the corresponding scop node from the next release
     *
     * @param scopNodeID
     * @return
     */
    public static int getScopNodeInNextRelease(int scopNodeID) throws SQLException {
        String query = "select n2.id from scop_node n1, scop_node n2 where n1.sunid=n2.sunid and n2.release_id=n1.release_id+1 and n1.id=" + scopNodeID;
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        int nextScopNodeID = -1;
        if (rs.next()) {
            nextScopNodeID = rs.getInt(1);
        }
        rs.close();
        return nextScopNodeID;
    }

    public static ArrayList<Integer> getPdbChainsInSamePDBRelease(int pdbReleaseID) throws Exception {
        String query = "select id from pdb_chain where pdb_release_id="+pdbReleaseID;
        ArrayList<Integer> pdbChains = new ArrayList<Integer>();
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        while (rs.next()) {
            pdbChains.add(new Integer(rs.getInt(1)));
        }
        rs.close();
        return pdbChains;
    }

    /**
     * Get seq ID from a particular astral chain
     * @param astralChainID
     * @return
     */
    public static int getSeqID(int astralChainID) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        String query = "SELECT seq_id FROM astral_chain where id=" + astralChainID;
        ResultSet rs = stmt.executeQuery(query);
        if (!rs.next()) {
            rs.close();
            stmt.close();
            throw new Exception("Problem with executing query: " + query);
        }
        int seqID = rs.getInt(1);
        rs.close();
        stmt.close();
        return seqID;
    }

    /**
     * Class for storing BlastInfo
     */
    public static class BlastInfo {
        public int hitNode;
        public int hitSunid;
        public String hitSCCS;
        public String hitSid;
        public String hitDescription;
        public int hitSeqLength;
        public double hitLog10E;
        public double hitPctID ;
        public int hitStart ;
        public int hitLength;

        public BlastInfo(int blastHitID, Statement stmt) throws Exception {
            //String query = "select n.id, n.sunid, n.sccs, n.sid, n.description, length(s.seq), b.blast_log10_e, b.pct_identical, b.seq2_start, b.seq2_length from astral_seq_blast b, astral_domain d, scop_node n, astral_seq s where n.id=d.node_id and d.seq_id=s.id and s.id=b.seq2_id and b.source_id=d.source_id and b.style1_id=1 and (b.style2_id=d.style_id or d.style_id=1) and n.sccs regexp '^[a-g]' and b.release_id=n.release_id and b.id=" + blastHitID;
            String query = "select n.id, n.sunid, n.sccs, n.sid, n.description, length(s.seq), b.blast_log10_e, b.pct_identical, b.seq2_start, b.seq2_length from astral_seq_blast b, astral_domain d, scop_node n, astral_seq s where n.id=d.node_id and d.seq_id=s.id and s.id=b.seq2_id and b.source_id=d.source_id and b.style1_id=1 and (b.style2_id=d.style_id or d.style_id=1) and n.sccs regexp '^[a-h]' and b.release_id=n.release_id and b.id=" + blastHitID;

            ResultSet rs = stmt.executeQuery(query);
            if (!rs.next()) {
                rs.close();
                throw new Exception("Blast hit " + blastHitID + " not found ");
            }
            hitNode = rs.getInt(1);
            hitSunid = rs.getInt(2);
            hitSCCS = rs.getString(3);
            hitSid = rs.getString(4);
            hitDescription = rs.getString(5);
            hitSeqLength = rs.getInt(6);
            hitLog10E = rs.getDouble(7);
            hitPctID = rs.getDouble(8);
            hitStart = rs.getInt(9);
            hitLength = rs.getInt(10);
            rs.close();
        }
    }

    public static ArrayList<Integer> getAutomatchedNodes(int nodeID) throws Exception {
        ArrayList<Integer> automatchedNodes = new ArrayList<Integer>();
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs;

        // 1. get SCOP release
        int scopReleaseID = LocalSQL.getLatestSCOPRelease(false);
        int lastPublicRelease = LocalSQL.getLatestSCOPRelease(true);
        if (scopReleaseID==lastPublicRelease)
            throw new Exception("can't edit a public release");

        // 2. Get the sid for the node
        String sid = CommonSCOPQueries.getScopNodeSid(nodeID);

        // 3. Retrieve all nodes that have comments of the form:
        // "automated match to dXXXXX" or
        // "automatically match to dXXXXX"
        String query = "select distinct n.id from scop_node n, scop_comment c ";
        query += " where n.id=c.node_id";  // join scop_node and scop_comment tables
        query += " and n.release_id=" + scopReleaseID;  // restrict date range
        query += " and (c.description='automated match to " + sid + "' or c.description='automatically matched to " + sid + "')";
        System.out.println(query);
        rs = stmt.executeQuery(query);
        while (rs.next()) {
            automatchedNodes.add(rs.getInt(1));
        }
        return automatchedNodes;
    }

    public static ArrayList<Integer> recursivelyGetAutomatchedNodes(int nodeID) throws Exception {
        ArrayList<Integer> automatchedNodes = getAutomatchedNodes(nodeID);
        ArrayList<Integer> childAutomatchedNodes = new ArrayList<Integer>();
        for (int childNodeID : automatchedNodes) {
            childAutomatchedNodes.addAll(recursivelyGetAutomatchedNodes(childNodeID));
        }
        automatchedNodes.addAll(childAutomatchedNodes);
        return automatchedNodes;
    }

    /**
     * Print out a list of astral chains whose classification depended on the parameter node
     *
     * For example: If the node is d2bhra1
     * The following chains would be printed:
     *  1981558 2bmfA
     *  1981560 2bmfB
     *  1981564 2bhrB
     *
     * @param nodeID
     * @throws Exception
     */
    public static void propagateChange(int nodeID) throws Exception {
        ArrayList<Integer> automatchedNodes =  recursivelyGetAutomatchedNodes(nodeID);
        HashSet<Integer> astralChainIDs = new HashSet<Integer>();
        for (int automatchedNodeID : automatchedNodes) {
            astralChainIDs.add(CommonSCOPQueries.getAstralChainIDFromScopNode(automatchedNodeID));
        }

        System.out.println("Chain IDS to reclassify: ");
        for (int astralChainID : astralChainIDs) {
            System.out.println(" " + astralChainID + " " + CommonSCOPQueries.getAstralChainSid(astralChainID));
        }
    }

    /**
     *  Get the pfam accession from astral_seq_hmm_pfam id
     */
    public static String getPfamAccessionFromPfamHit(int pfamHitID) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        String query = "select accession from pfam f, astral_seq_hmm_pfam h where f.id=h.pfam_id and h.id=" + pfamHitID;
        ResultSet rs = stmt.executeQuery(query);
        if (!rs.next()) {
            throw new Exception("No pfam for hit ID " + pfamHitID + " found");
        }
        String pfamAccessionString = rs.getString(1);
        rs.close();
        stmt.close();
        return pfamAccessionString;
    }

    /**
     * Get the number of residues in a PDB file
     *
     * @param pdbReleaseID
     * @param uniqueSeqs get only unique seqs
     * @param ignoreReject
     * @param seqRes
     * @return
     * @throws Exception
     */
    public static int getNumResiduesInPDB(int pdbReleaseID, boolean uniqueSeqs, boolean ignoreReject, boolean seqRes) throws Exception {
        int numRes = 0;
        if (seqRes) {
            throw new Exception("seqRes flags not yet supported");
        }
        Vector<Integer> seqOrChainIDs = DumpSeqs.getSeqsInPDBRelease(pdbReleaseID,
                                                                     uniqueSeqs,
                                                                     ignoreReject,
                                                                     (seqRes ? 2 : 1));

        if (!uniqueSeqs) {
            for (int chainID : seqOrChainIDs) {
                numRes += CommonSCOPQueries.getSeqLengthFromAstralChainID(chainID);
            }
        }
        else {
            for(int seqID : seqOrChainIDs) {
                numRes += CommonSCOPQueries.getSeqLength(seqID);
            }
        }
        return numRes;
    }
}
