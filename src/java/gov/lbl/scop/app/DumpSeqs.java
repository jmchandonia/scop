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

import gov.lbl.scop.local.LocalSQL;
import gov.lbl.scop.util.CommonSCOPQueries;
import org.strbio.io.Printf;
import org.strbio.io.PrintfStream;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.Vector;

/**
   Dump proteins in fasta format for sequence analysis.

   Version 2.2, 1/30/16 - options to dump tagless seqs, ignore
   sequences that will not be replaced by automated protocols
   Version 2.1, 1/7/14 - options to ignore obsolete sequences,
   to improve efficiency; deleted getUpdatedSeqs
   Version 2.0, 4/17/12 - many API changes
   Version 1.1, 6/30/09 - can dump new chain sequences within date range
   Version 1.0, 11/26/08 - original version
*/
public class DumpSeqs {
    /**
       gets all the sequences of PDB chains that are on hold
    */
    final public static Vector<Integer> getOnHoldSeqs() throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select distinct(seq_id) from pdb_onhold_chain");
        Vector<Integer> rv = new Vector<Integer>();
        while (rs.next()) {
            rv.add(new Integer(rs.getInt(1)));
        }
        rs.close();
        stmt.close();
        return rv;
    }

    /**
       gets all the sequences of structures with no other sequence
    */
    final public static Vector<Integer> getStructureSeqs() throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select distinct(seq_id) from structure_seq");
        Vector<Integer> rv = new Vector<Integer>();
        while (rs.next()) {
            rv.add(new Integer(rs.getInt(1)));
        }
        rs.close();
        stmt.close();
        return rv;
    }

    /**
       gets all the sequences of uniprot entries from human, non-obsolete,
       swissprot entries
    */
    final public static Vector<Integer> getUniprotSeqs() throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select distinct(seq_id) from uniprot_seq where uniprot_id in (select id from uniprot where long_id like '%_HUMAN' and is_swissprot=1 and is_obsolete=0)");
        Vector<Integer> rv = new Vector<Integer>();
        while (rs.next()) {
            rv.add(new Integer(rs.getInt(1)));
        }
        rs.close();
        stmt.close();
        return rv;
    }
    
    /**
       gets all PDB entries released before a certain date, for which
       we have a RAF; orders them by code
    */
    final public static Vector<Integer> getPDBEntries(java.util.Date d) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        Vector<Integer> rv = new Vector<Integer>();
        ResultSet rs = stmt.executeQuery("select distinct(pe.id) from pdb_local l, pdb_release pr, pdb_entry pe where l.pdb_release_id=pr.id and pe.id=pr.pdb_entry_id and l.is_raf_calculated=1 and l.snapshot_date <= \""+ParsePDB.sqlDateFormat.format(d)+"\" order by pe.code");
        while (rs.next())
            rv.add(new Integer(rs.getInt(1)));
        rs.close();
        stmt.close();
        return rv;
    }

    /**
       gets all PDB entries that are classified in a given SCOP release
    */
    final public static Vector<Integer> getPDBEntriesInSCOP(int scopReleaseID) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        Vector<Integer> rv = new Vector<Integer>();
        ResultSet rs = stmt.executeQuery("select pr.pdb_entry_id from raf r, pdb_chain pc, pdb_release pr where r.pdb_chain_id=pc.id and pc.pdb_release_id=pr.id and r.first_release_id<="+scopReleaseID+" and r.last_release_id>="+scopReleaseID);
        while (rs.next())
            rv.add(new Integer(rs.getInt(1)));
        rs.close();
        stmt.close();
        return rv;
    }

    /**
       gets all PDB chains that are classified in a given SCOP
       release.  if best is set, only gets chains with scop_curation
       types that are reliable enough to not replace with current
       automated methods.
    */
    final public static Vector<Integer> getPDBChainsInSCOP(int scopReleaseID,
                                                           boolean best) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        Vector<Integer> rv = new Vector<Integer>();
        String query = "select distinct(r.pdb_chain_id) from raf r, scop_node n, link_pdb l where r.pdb_chain_id=l.pdb_chain_id and r.first_release_id<="+scopReleaseID+" and r.last_release_id>="+scopReleaseID+" and n.level_id=8 and n.id=l.node_id and n.release_id="+scopReleaseID;
        if (best)
            query += " and n.curation_type_id in (1, 3, 4, 5, 6)";
        ResultSet rs = stmt.executeQuery(query);
        while (rs.next())
            rv.add(new Integer(rs.getInt(1)));
        rs.close();
        stmt.close();
        return rv;
    }
    
    /**
       gets all PDB entries that are obsolete as of a given date
    */
    final public static Vector<Integer> getObsPDBEntries(java.util.Date d) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        Vector<Integer> rv = new Vector<Integer>();
        ResultSet rs = stmt.executeQuery("select distinct(pe.id) from pdb_entry pe where pe.obsolete_date <= \""+ParsePDB.sqlDateFormat.format(d)+"\"");
        while (rs.next())
            rv.add(new Integer(rs.getInt(1)));
        rs.close();
        stmt.close();
        return rv;
    }

    /**
     * Retrieves chains or unique seqs in a PDB release
     *
     */
    public static Vector<Integer> getSeqsInPDBRelease(int releaseID,
                                                      boolean uniqueSeqs,
                                                      boolean ignoreReject,
                                                      int sourceType) throws SQLException {
        Vector<Integer> seqs = new Vector<Integer>();
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs;

        String queryType = "ac.id";
        if (uniqueSeqs)
            queryType = "distinct(ac.seq_id)";

        String query = "SELECT " + queryType +
            " FROM astral_chain ac, raf r, pdb_chain pc, astral_seq s" +
            " WHERE ac.seq_id=s.id and ac.raf_id=r.id and r.pdb_chain_id=pc.id and pc.pdb_release_id=" + releaseID +
            " and pc.is_polypeptide=1 and r.first_release_id is null and r.last_release_id is null";
        if (ignoreReject)
            query += " and s.is_reject=0";

        query += " and ac.source_id="+sourceType;
        query += " order by pc.chain";
        rs = stmt.executeQuery(query);
        while (rs.next()) {
            seqs.add(new java.lang.Integer(rs.getInt(1)));
            // don't filter by is_polypeptide, since is_reject should work
        }
        rs.close();
        stmt.close();
        return seqs;
    }

    /**
     * Get the latest PDB release, released on or before an end date
     * @param entryID pdb_entry ID
     * @param end end date
     * @return pdb_release ID
     */
    public static int getLatestPDBRelease(int entryID, java.util.Date end) throws SQLException {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs;
        rs = stmt.executeQuery("select pr.id from pdb_local l, pdb_release pr where l.pdb_release_id=pr.id and pr.pdb_entry_id="+entryID+" and l.is_raf_calculated=1 and l.snapshot_date <= \""+ParsePDB.sqlDateFormat.format(end)+"\" order by l.snapshot_date desc limit 1");
        rs.next();  // must be just 1
        int releaseID = rs.getInt(1);
        rs.close();
        stmt.close();
        return releaseID;
    }

    /**
     * Implements getNewSeqs, with option to include rejects
     *
     * @param start
     * @param end
     * @param uniqueSeqs
     * @param ignoreObs
     * @param ignoreReject
     * @param ignoreClassifiedPDB
     * @param ignoreReliableChains
     * @return
     * @throws Exception
     */
    final public static Vector<Integer> getNewSeqs(java.util.Date start,
                                                   java.util.Date end,
                                                   boolean uniqueSeqs,
                                                   boolean ignoreObs,
                                                   boolean ignoreReject,
                                                   int sourceType,
                                                   boolean ignoreClassified,
                                                   boolean ignoreReliableChains) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs;

        // get all entries available on end date, ordered by code
        Vector<Integer> entriesEnd = getPDBEntries(end);

        if (start != null) {
            Vector<Integer> entriesStart = getPDBEntries(start);
            entriesEnd.removeAll(entriesStart);
        }

        if (ignoreObs) {
            Vector<Integer> obsEntries = getObsPDBEntries(end);
            entriesEnd.removeAll(obsEntries);
        }

        if (ignoreClassified) {
            int scopReleaseID = LocalSQL.getLatestSCOPRelease(true);
            Vector<Integer> classifiedEntries = getPDBEntriesInSCOP(scopReleaseID);
            entriesEnd.removeAll(classifiedEntries);
        }

        // get latest release of each entry, before end date
        Vector<Integer> newPDBReleases = new Vector<Integer>();
        for (int entryID : entriesEnd) {
            newPDBReleases.add(getLatestPDBRelease(entryID, end));
        }

        // get all chains from those releases
        Vector<Integer> newPDBChains = new Vector<Integer>();
        for (Integer pdbReleaseID : newPDBReleases) {
            newPDBChains.addAll(getSeqsInPDBRelease(pdbReleaseID,
                                                    uniqueSeqs,
                                                    ignoreReject,
                                                    sourceType));
        }

        // reject reliably classified chains
        if ((!uniqueSeqs) && ignoreReliableChains) {
            int scopReleaseID = LocalSQL.getLatestSCOPRelease(false);
            Vector<Integer> reliableChains = getPDBChainsInSCOP(scopReleaseID,
                                                                true);
            newPDBChains.removeAll(reliableChains);
        }

        stmt.close();
        return newPDBChains;
    }


    /**
       get a set of ASTRAL chain sequences added between SCOP
       versions, for ASTEROIDS.

       Note:  SEQRES sequences, excluding rejects

       If uniqueSeqs is true, returns seq ids of unique sequences; otherwise, returns chain ids

       if ignoreObsolete is true ignores versions/entries that are obsolete
       as of the end date

       the curationType allows you to restrict the set of chains to those with nodes classified
       with some particular method, as listed in the scop_curation_type table
    */
    final public static Vector<Integer> getNewSeqs(int scopReleaseID1,
                                                   int scopReleaseID2,
                                                   boolean uniqueSeqs,
                                                   boolean ignoreObs,
                                                   int curationType) throws Exception {
        return getNewSeqs(scopReleaseID1, scopReleaseID2, uniqueSeqs, ignoreObs, true, curationType);
    }


    /**
     * @param scopReleaseID1   first SCOP release (e.g. 12)
     * @param scopReleaseID2   first SCOP release (e.g. 14)
     * @param uniqueSeqs flag for whether to retrieve unique seqs
     * @param ignoreObs flag for whether to exclude chains from obsolete PDB entries
     * @param ignoreReject flag for whether to exclude 'reject' chains
     * @param curationType  curation type: manual, auto, etc. from scop_curation_type table
     * @return
     * @throws Exception
     */
    final public static Vector<Integer> getNewSeqs(int scopReleaseID1,
                                                   int scopReleaseID2,
                                                   boolean uniqueSeqs,
                                                   boolean ignoreObs,
                                                   boolean ignoreReject,
                                                   int curationType) throws Exception {
        Statement stmt = LocalSQL.createStatement();

        // get all entries included in new SCOP
        Vector<Integer> SCOPEntries = getPDBEntriesInSCOP(scopReleaseID2);

        // remove all entries included in old SCOP
        Vector<Integer> oldSCOPEntries = getPDBEntriesInSCOP(scopReleaseID1);
        SCOPEntries.removeAll(oldSCOPEntries);

        // remove all entries that are obsolete
        if (ignoreObs) {
            Date end = CommonSCOPQueries.getReleaseDate(scopReleaseID2);
            Vector<Integer> obsEntries = getObsPDBEntries(end);
            SCOPEntries.removeAll(obsEntries);
        }

        // get all PDB chains corresponding to new SCOP entries
        Vector<Integer> newChains = new Vector<Integer>();
        String queryType = "ac.id";
        if (uniqueSeqs)
            queryType = "distinct(ac.seq_id)";
        for (Integer entryID : SCOPEntries) {
            String query = "SELECT " + queryType +
                " FROM astral_chain ac, raf r, link_pdb lp, scop_node n, pdb_chain pc, pdb_release pr, astral_seq s " +
                " WHERE ac.source_id=2 and ac.seq_id=s.id and ac.raf_id=r.id and r.pdb_chain_id=lp.pdb_chain_id " +
                " and pc.id=r.pdb_chain_id and pc.pdb_release_id=pr.id and pc.is_polypeptide=1 and pr.pdb_entry_id=" + entryID +
                " and lp.node_id=n.id and n.release_id=" + scopReleaseID2 + " and r.first_release_id<=" + scopReleaseID2 +
                " and r.last_release_id>=" + scopReleaseID2;
            if (ignoreReject) {
                query += " and s.is_reject=0 ";
            }
            if(curationType > 0) {  // restrict the curationType, if necessary
                query += " and n.curation_type_id="+curationType;
            }

            ResultSet rs = stmt.executeQuery(query);
            while (rs.next()) {
                Integer i = new Integer(rs.getInt(1));
                if (!newChains.contains(i))
                    newChains.add(i);
                // don't filter by is_polypeptide, since is_reject should work
            }
            rs.close();
        }

        stmt.close();
        return newChains;
    }

    /**
       get a set of ASTRAL sequences.
       
       rejects:  0 = exclude, 1 = include, 2 = only
       
       ntc: 0 = exclude, 1 = include, 2 = only (for domain seqs only)

       sourceType = 1 = atom, 2 = seqres, 3 = pre-1.65 seqres,
       4 = tagless seqres
       
       gdStyle: 0 = just os/single, 1 = just gd/single, 2 = both gd
       and os (for domain seqs only).
       
       If uniqueSeqs is true, returns seq ids of unique sequences;
       otherwise, returns chain/domain ids
    */
    final public static Vector<Integer> getSeqs(int scopReleaseID,
                                                boolean isChain,
                                                int rejects,
                                                int ntc,
                                                boolean sort,
                                                int sourceType,
                                                int gdStyle,
                                                boolean uniqueSeqs) {
        Vector<Integer> rv = new Vector<Integer>();
        try {
            Statement stmt = LocalSQL.createStatement();
            String query = "select ";
            if (uniqueSeqs)
                query += "distinct(s.id)";
            else
                query += "a.id";
            query += " from ";
            if (isChain) {
                query += "astral_chain a, raf r, astral_seq s where a.seq_id=s.id and a.raf_id=r.id and ";
                if (scopReleaseID > 0)
                    query += "r.first_release_id <= "+scopReleaseID+" and r.last_release_id >= "+scopReleaseID;
                else
                    query += "r.first_release_id is null and r.last_release_id is null";
            }
            else {
                query += "astral_domain a, scop_node n, astral_seq s where a.node_id=n.id and a.seq_id=s.id and n.release_id="+scopReleaseID;
            }
            query += " and a.source_id = "+sourceType;
            if (rejects==0)
                query += " and s.is_reject=0";
            else if (rejects==2)
                query += " and s.is_reject=1";
            if (!isChain) {
                if (gdStyle==0)
                    query += " and (a.style_id=1 or a.style_id=2)";
                else if (gdStyle==1)
                    query += " and (a.style_id=1 or a.style_id=3)";

                if (ntc==0)
                    query += " and n.sccs regexp '^[a-g]'";
                else if (ntc==2)
                    query += " and n.sccs not regexp '^[a-g]'";
            }
            if (sort) {
                if (isChain)
                    query+=" order by a.sid";
                else
                    query+=" order by a.header, a.sid";
            }

            // System.out.println(query);
	    
            ResultSet rs = stmt.executeQuery(query);
            while (rs.next())
                rv.add(new Integer(rs.getInt(1)));
            rs.close();
            stmt.close();
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
            rv = null;
        }
        return rv;
    }

    /**
       Write an ASTRAL sequence to a file.  Sequence length must be
       at least 1, or nothing will be written.

       seqType = 0 for seqID, 1 for domainID, 2 for astralChainID

       If fullHeader, writes ASTRAL-style header; otherwise, writes only
       the numeric id as a header.
    */
    final public static void writeFasta(Printf outfile,
                                        int astralID,
                                        int seqType,
                                        boolean fullHeader) throws Exception {
        Statement stmt = LocalSQL.createStatement();

        ResultSet rs;

        String query = "select s.seq";

        if (fullHeader) {
            query += ", a.sid";
            if (seqType==1)
                query += ", a.header";
        }
        query += " from astral_seq s";
        if (seqType==1)
            query += ", astral_domain a";
        else if (seqType==2)
            query += ", astral_chain a";

        if (seqType==0)
            query += " where s.id="+astralID;
        else 
            query += " where s.id=a.seq_id and a.id="+astralID;

        rs = stmt.executeQuery(query);

        if (!rs.next()) {
            rs.close();
            stmt.close();
            return;
        }

        String seq = rs.getString(1);
        int l = seq.length();
        if (l==0) {
            rs.close();
            stmt.close();
            return;
        }

        String header = ">";
        if (!fullHeader)
            header += astralID;
        else {
            String sid = rs.getString(2);
            header += sid;
            if (seqType==2) {
                char chain = sid.charAt(4);
                if (chain=='_')
                    header += " (-)";
                else
                    header += " ("+chain+":)";
            }
            else {
                header += " "+rs.getString(3);
            }
        }
        rs.close();
        stmt.close();

        outfile.printf("%s\n",header);
        int i=0;
        for (i=0; i<(l/60); i++)
            outfile.printf("%s\n",seq.substring(i*60,(i+1)*60));
        if (i*60 < l)
            outfile.printf("%s\n",seq.substring(i*60));
    }

    /**
       write FASTA file for set of sequences.
       
       seqType = 0 for seqID, 1 for domainID, 2 for astralChainID
    */
    final public static void writeFasta(Printf outfile,
                                        Collection<Integer> ids,
                                        int seqType,
                                        boolean fullHeader) throws Exception {
        for (Integer i : ids)
            writeFasta(outfile,
                       i.intValue(),
                       seqType,
                       fullHeader);
    }

    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();

            Vector<Integer> ids;
            boolean isChain = false;
            boolean fullHeader = true;
            int sourceType = 0;
            PrintfStream outfile;

            int pos = argv[0].lastIndexOf('-');
            if (pos==-1) {
                // must be one or 2 dates, in 6-digit fmt
                java.util.Date start, end;
                if (argv.length > 1) {
                    start = ParsePDB.snapshotDateFormat.parse(argv[0]);
                    end = ParsePDB.snapshotDateFormat.parse(argv[1]);
                }
                else {
                    start = null;
                    end = ParsePDB.snapshotDateFormat.parse(argv[0]);
                }

                ids = getNewSeqs(start,
                                 end,
                                 false, //uniqueSeqs
                                 false, // ignoreObs
                                 true, // ignoreReject
                                 2, // seqRes
                                 true, // ignoreClassified
                                 false); // ignoreRelableChains
                isChain = true;
                outfile = new PrintfStream(System.out);
            }
            else {
                // dump to ASTRAL fmt file
                int pos2 = argv[0].indexOf('.',pos+4);
                String ver = argv[0].substring(pos+1,pos2);
                int scopReleaseID = LocalSQL.lookupSCOPRelease(ver);
                if (scopReleaseID==0) {
                    throw new Exception("Can't determine SCOP version from "+argv[0]);
                }

                if (argv[0].indexOf("-chain")>-1)
                    isChain = true;

                if (argv[0].indexOf("-atom")>-1) {
                    sourceType = 1;
                }
                else if ((scopReleaseID < 6) && isChain) {
                    sourceType = 3;
                }
                else {
                    if (argv[0].indexOf("-tg")>-1)
                        sourceType = 4;
                    else
                        sourceType = 2;
                }

                boolean isGD = false;
                if (argv[0].indexOf("-gd")>-1)
                    isGD = true;

                if (argv[0].indexOf("-id")>-1)
                    fullHeader = false;

                int reject = 0;
                if (argv[0].indexOf("-reject")>-1)
                    reject = 2;

                int ntc = 0;
                if (argv[0].indexOf("-ntc")>-1)
                    ntc = 2;

                ids = getSeqs(scopReleaseID,
                              isChain,
                              reject,
                              ntc,
                              fullHeader,
                              sourceType,
                              (isGD ? 1 : 0),
                              false);

                outfile = new PrintfStream(argv[0]);
            }
	    
            writeFasta(outfile,
                       ids,
                       (isChain ? 2 : 1),
                       fullHeader);
	    
            outfile.flush();
            outfile.close();
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
