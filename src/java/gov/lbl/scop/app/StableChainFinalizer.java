/*
 * Software to build and maintain SCOPe, https://scop.berkeley.edu/
 */
package gov.lbl.scop.app;

import gov.lbl.scop.local.LocalSQL;

import java.sql.*;

/**
 * Converges the independent RAF/sequence and SPACI import branches.  All
 * operations are idempotent; an existing stable-release SID binding wins and
 * is never rebound to a newer PDB release.
 */
public class StableChainFinalizer {
    private static boolean tableExists(String table) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("show tables like '"+table+"'");
        boolean rv = rs.next();
        rs.close();
        stmt.close();
        return rv;
    }

    /** Queue the finalizer after either prerequisite branch finishes. */
    public static void queueIfInstalled(int pdbReleaseID) throws Exception {
        if (!tableExists("astral_chain_stable_binding"))
            return;
        int scopReleaseID = LocalSQL.getLatestSCOPRelease(true);
        if (scopReleaseID == 0)
            return;
        Statement stmt = LocalSQL.createStatement();
        LocalSQL.newJob(26,pdbReleaseID,Integer.toString(scopReleaseID),stmt);
        stmt.close();
    }

    private static long lookupBinding(PreparedStatement ps, int releaseID,
                                      String sid) throws Exception {
        ps.setInt(1,releaseID);
        ps.setString(2,sid);
        ResultSet rs = ps.executeQuery();
        long rv = rs.next() ? rs.getLong(1) : 0;
        rs.close();
        return rv;
    }

    private static void prepareBinding(long bindingID) throws Exception {
        Statement stmt=LocalSQL.createStatement();
        ResultSet rs=stmt.executeQuery("select base_scop_release_id,pdb_entry_id,"+
            "pdb_release_id from astral_chain_stable_binding where id="+bindingID);
        if (!rs.next()) throw new Exception("stable binding not found: "+bindingID);
        int releaseID=rs.getInt(1), entryID=rs.getInt(2), pdbReleaseID=rs.getInt(3);
        rs.close();
        boolean generated=FreezeSPACI.addSPACIFromPDBRelease(entryID,pdbReleaseID,
                                                             releaseID)!=null;
        if (generated)
            stmt.executeUpdate("insert ignore into astral_chain_aerospaci_source " +
                "(base_scop_release_id,pdb_entry_id,aerospaci_id,stable_binding_id,"+
                "pdb_release_id,source,created_at) select "+releaseID+","+entryID+
                ",id,"+bindingID+","+pdbReleaseID+
                ",'generated_from_pinned_release',now() from aerospaci where " +
                "pdb_entry_id="+entryID+" and release_id="+releaseID);
        stmt.close();
        MakeChainPDB.makeChainPDB(bindingID);
    }

    /**
     * Register all ready standard-SEQRES chains from one exact PDB release.
     * Returns the number of newly registered bindings.  Missing prerequisites
     * are a clean no-op so this can be called from both import branches.
     */
    public static synchronized int finalizePDBRelease(int pdbReleaseID,
                                                       int scopReleaseID)
        throws Exception {
        if (!tableExists("astral_chain_stable_binding"))
            throw new Exception("all-chain schema is not installed");

        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select r.spaci from pdb_release r " +
                                         "where r.id="+pdbReleaseID);
        if (!rs.next()) {
            rs.close();
            stmt.close();
            throw new Exception("PDB release not found: "+pdbReleaseID);
        }
        rs.getDouble(1);
        boolean hasSPACI = !rs.wasNull();
        rs.close();
        if (!hasSPACI) {
            stmt.close();
            return 0;
        }

        PreparedStatement findBinding = LocalSQL.prepareStatement(
            "select id from astral_chain_stable_binding where " +
            "base_scop_release_id=? and sid=?");
        PreparedStatement addEvent = LocalSQL.prepareStatement(
            "insert ignore into astral_chain_processing_event " +
            "(pdb_release_id,raf_id,astral_chain_id,source,job_done_id,time_processed) " +
            "values (?,?,?,'registration_event',null,now())");
        PreparedStatement findEvent = LocalSQL.prepareStatement(
            "select id from astral_chain_processing_event where pdb_release_id=? " +
            "and raf_id=? and astral_chain_id=? and source='registration_event'");
        PreparedStatement addBinding = LocalSQL.prepareStatement(
            "insert ignore into astral_chain_stable_binding " +
            "(base_scop_release_id,sid,astral_chain_id,raf_id,pdb_chain_id," +
            "pdb_entry_id,pdb_release_id,processing_event_id," +
            "processing_order_source,origin,revision_date,file_date,snapshot_date," +
            "status,created_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?,'registered',now())",
            Statement.RETURN_GENERATED_KEYS);

        rs = stmt.executeQuery(
            "select ac.id, ac.sid, ac.raf_id, r.pdb_chain_id, re.pdb_entry_id, " +
            "re.revision_date, re.file_date, l.snapshot_date, e.release_date, " +
            "s.freeze_date " +
            "from astral_chain ac join astral_seq q on q.id=ac.seq_id " +
            "join raf r on r.id=ac.raf_id join pdb_chain c on c.id=r.pdb_chain_id " +
            "join pdb_release re on re.id=c.pdb_release_id " +
            "join pdb_entry e on e.id=re.pdb_entry_id " +
            "join pdb_local l on l.pdb_release_id=re.id " +
            "join scop_release s on s.id="+scopReleaseID+" " +
            "where re.id="+pdbReleaseID+" and ac.source_id=2 " +
            "and c.is_polypeptide=1 and q.is_reject=0 and length(q.seq)>=30 " +
            "and r.first_release_id is null and r.last_release_id is null " +
            "order by ac.sid collate latin1_bin, ac.id");

        int added = 0;
        while (rs.next()) {
            int astralChainID = rs.getInt(1);
            String sid = rs.getString(2);
            int rafID = rs.getInt(3);
            int pdbChainID = rs.getInt(4);
            int pdbEntryID = rs.getInt(5);
            Date revisionDate = rs.getDate(6);
            Date fileDate = rs.getDate(7);
            Date snapshotDate = rs.getDate(8);
            Date entryReleaseDate = rs.getDate(9);
            Date freezeDate = rs.getDate(10);

            long existing=lookupBinding(findBinding,scopReleaseID,sid);
            if (existing != 0) {
                // Retry preparation for the already-pinned source, never the
                // newer occurrence that happened to trigger this finalizer.
                prepareBinding(existing);
                continue;
            }

            // Public release scores are insert-only.  This exact pinned PDB
            // release is used only when the stable-release row is missing.
            addEvent.setInt(1,pdbReleaseID);
            addEvent.setInt(2,rafID);
            addEvent.setInt(3,astralChainID);
            addEvent.executeUpdate();
            findEvent.setInt(1,pdbReleaseID);
            findEvent.setInt(2,rafID);
            findEvent.setInt(3,astralChainID);
            ResultSet ers = findEvent.executeQuery();
            if (!ers.next()) {
                ers.close();
                throw new Exception("failed to register processing event for "+sid);
            }
            long eventID = ers.getLong(1);
            ers.close();

            boolean newEntry = entryReleaseDate != null && freezeDate != null &&
                entryReleaseDate.after(freezeDate);
            String origin = newEntry ? "first_processed_new_entry" :
                "first_processed_existing_entry";
            addBinding.setInt(1,scopReleaseID);
            addBinding.setString(2,sid);
            addBinding.setInt(3,astralChainID);
            addBinding.setInt(4,rafID);
            addBinding.setInt(5,pdbChainID);
            addBinding.setInt(6,pdbEntryID);
            addBinding.setInt(7,pdbReleaseID);
            addBinding.setLong(8,eventID);
            addBinding.setString(9,"registration_event");
            addBinding.setString(10,origin);
            addBinding.setDate(11,revisionDate);
            addBinding.setDate(12,fileDate);
            addBinding.setDate(13,snapshotDate);
            if (addBinding.executeUpdate() > 0)
                added++;

            long bindingID = lookupBinding(findBinding,scopReleaseID,sid);
            if (bindingID != 0) {
                prepareBinding(bindingID);
            }
        }
        rs.close();
        findBinding.close();
        addEvent.close();
        findEvent.close();
        addBinding.close();
        stmt.close();
        return added;
    }

    public static void main(String[] argv) {
        try {
            if (argv.length < 1 || argv.length > 2)
                throw new Exception("Usage: StableChainFinalizer pdb_release_id [scop_release_id]");
            LocalSQL.connectRW();
            int pdbReleaseID = Integer.parseInt(argv[0]);
            int scopReleaseID = argv.length == 2 ? Integer.parseInt(argv[1]) :
                LocalSQL.getLatestSCOPRelease(true);
            System.out.println("registered "+finalizePDBRelease(pdbReleaseID,
                                                                scopReleaseID)+
                               " stable chain bindings");
        }
        catch (Exception e) {
            System.err.println("Exception: "+e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
