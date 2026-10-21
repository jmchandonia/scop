/*
 * Software to build and maintain SCOPe, https://scop.berkeley.edu/
 */
package gov.lbl.scop.app;

import gov.lbl.scop.local.LocalSQL;

import java.sql.*;
import java.util.*;

/** Deterministic, indexed 90%-identity greedy selector for one population. */
public class MakeAllChainSubsets {
    private static class Chain {
        int chainID, seqID;
        String sid;
    }

    public static int makeSubset(int populationID, int pctID) throws Exception {
        if (pctID!=90) throw new Exception("only the reviewed 90% subset is supported");
        Statement stmt=LocalSQL.createStatement();
        ResultSet rs=stmt.executeQuery("select status from astral_chain_all_population where id="+populationID);
        if (!rs.next()) throw new Exception("population not found: "+populationID);
        if (!"building".equals(rs.getString(1)))
            throw new Exception("population is not in building state");
        rs.close();
        rs=stmt.executeQuery("select count(*) from astral_chain_all_blast_coverage " +
            "where population_id="+populationID+" and status<>'complete'");
        rs.next(); int incomplete=rs.getInt(1); rs.close();
        if (incomplete!=0) throw new Exception(incomplete+" BLAST searches are incomplete");
        rs=stmt.executeQuery("select (select count(distinct seq_id) from " +
            "astral_chain_all_candidate where population_id="+populationID+
            "),(select count(distinct query_seq_id) from astral_chain_all_blast_coverage " +
            "where population_id="+populationID+" and status='complete')");
        rs.next(); int expectedQueries=rs.getInt(1), completedQueries=rs.getInt(2);
        rs.close();
        if (expectedQueries!=completedQueries)
            throw new Exception("BLAST coverage is absent or incomplete for "+
                (expectedQueries-completedQueries)+" population sequences");

        ArrayList<Chain> ordered=new ArrayList<Chain>();
        HashMap<Integer,ArrayList<Integer>> chainsBySeq=new HashMap<Integer,ArrayList<Integer>>();
        rs=stmt.executeQuery("select astral_chain_id,seq_id,sid from " +
            "astral_chain_all_candidate where population_id="+populationID+
            " order by round(aerospaci,2) desc,sid collate latin1_bin asc");
        while (rs.next()) {
            Chain c=new Chain(); c.chainID=rs.getInt(1); c.seqID=rs.getInt(2);
            c.sid=rs.getString(3); ordered.add(c);
            ArrayList<Integer> ids=chainsBySeq.get(c.seqID);
            if (ids==null) { ids=new ArrayList<Integer>(); chainsBySeq.put(c.seqID,ids); }
            ids.add(c.chainID);
        }
        rs.close();

        HashMap<Integer,HashSet<Integer>> peers=new HashMap<Integer,HashSet<Integer>>();
        rs=stmt.executeQuery("select seq1_id,seq2_id from astral_chain_all_blast_edge " +
            "where population_id="+populationID+" and pct_identical>="+pctID);
        while (rs.next()) {
            int a=rs.getInt(1), b=rs.getInt(2);
            if (!chainsBySeq.containsKey(a) || !chainsBySeq.containsKey(b)) continue;
            HashSet<Integer> p=peers.get(a);
            if (p==null) { p=new HashSet<Integer>(); peers.put(a,p); } p.add(b);
            p=peers.get(b); if (p==null) { p=new HashSet<Integer>(); peers.put(b,p); } p.add(a);
        }
        rs.close();

        HashSet<Integer> covered=new HashSet<Integer>();
        ArrayList<Chain> reps=new ArrayList<Chain>();
        HashSet<Integer> legacyReps=new HashSet<Integer>();
        HashSet<Integer> legacySeqs=new HashSet<Integer>();
        rs=stmt.executeQuery("select distinct old.astral_chain_id from " +
            "astral_chain_subset_id old join astral_chain_all_candidate c on " +
            "c.astral_chain_id=old.astral_chain_id and c.population_id="+populationID+
            " join astral_chain_all_population p on p.id=c.population_id where " +
            "old.release_id=p.base_scop_release_id and old.pct_identical="+pctID);
        while (rs.next()) legacyReps.add(rs.getInt(1));
        rs.close();

        // Preserve existing release representatives first.  Their stable chain
        // IDs key the expensive legacy FATCAT results.  A few historical rows
        // represent different chains with the exact same seq_id; preserve the
        // first under the normal AEROSPACI/SID order.  Still abort rather than
        // silently keep two different legacy sequences that the complete new
        // graph shows are >=90% identical.
        int collapsedExactLegacy=0;
        for (Chain c:ordered) {
            if (!legacyReps.contains(c.chainID)) continue;
            if (legacySeqs.contains(c.seqID)) {
                collapsedExactLegacy++;
                continue;
            }
            if (covered.contains(c.chainID))
                throw new Exception("legacy 90% representatives are redundant in the " +
                                    "new graph at chain "+c.chainID+" ("+c.sid+")");
            reps.add(c);
            legacySeqs.add(c.seqID);
            covered.addAll(chainsBySeq.get(c.seqID));
            HashSet<Integer> adjacent=peers.get(c.seqID);
            if (adjacent!=null)
                for (Integer seq:adjacent) covered.addAll(chainsBySeq.get(seq));
        }
        int preservedLegacy=reps.size();

        for (Chain c:ordered) {
            if (covered.contains(c.chainID)) continue;
            reps.add(c);
            ArrayList<Integer> same=chainsBySeq.get(c.seqID);
            covered.addAll(same);
            HashSet<Integer> adjacent=peers.get(c.seqID);
            if (adjacent!=null)
                for (Integer seq:adjacent) covered.addAll(chainsBySeq.get(seq));
        }
        if (covered.size()!=ordered.size())
            throw new Exception("coverage invariant failed: "+covered.size()+" of "+ordered.size());

        LocalSQL.setAutoCommit(false);
        try {
            PreparedStatement del=LocalSQL.prepareStatement(
                "delete from astral_chain_all_subset_id where population_id=? and pct_identical=?");
            del.setInt(1,populationID); del.setInt(2,pctID); del.executeUpdate(); del.close();
            PreparedStatement ins=LocalSQL.prepareStatement(
                "insert into astral_chain_all_subset_id " +
                "(population_id,pct_identical,astral_chain_id,representative_rank) values (?,?,?,?)");
            int rank=0;
            for (Chain c:reps) {
                ins.setInt(1,populationID); ins.setInt(2,pctID);
                ins.setInt(3,c.chainID); ins.setInt(4,++rank); ins.addBatch();
            }
            ins.executeBatch(); ins.close();
            stmt.executeUpdate("update astral_chain_all_population set status='validated',"+
                "representative_count="+reps.size()+",validated_at=now() where id="+populationID);
            LocalSQL.commit();
        }
        catch (Exception e) { LocalSQL.rollback(); throw e; }
        finally { LocalSQL.setAutoCommit(true); }
        System.out.println("preserved "+preservedLegacy+
                           " eligible legacy release representatives");
        System.out.println("collapsed "+collapsedExactLegacy+
                           " exact-sequence legacy representative rows");
        stmt.close(); return reps.size();
    }

    public static void main(String[] argv) {
        try {
            if (argv.length!=1) throw new Exception("Usage: MakeAllChainSubsets population_id");
            LocalSQL.connectRW(); int id=Integer.parseInt(argv[0]);
            System.out.println("selected "+makeSubset(id,90)+" representatives");
        }
        catch (Exception e) {
            System.err.println("Exception: "+e.getMessage()); e.printStackTrace(); System.exit(1);
        }
    }
}
