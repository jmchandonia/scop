/*
 * Software to build and maintain SCOPe, https://scop.berkeley.edu/
 */
package gov.lbl.scop.app;

import gov.lbl.scop.local.LocalSQL;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.HashSet;

/**
 * Builds immutable stable bindings in two passes, then cuts an immutable
 * candidate population.  Binding discovery defaults to manifest-only.
 */
public class BuildAllChainPopulation {
    private static final String ELIGIBLE_CHAIN =
        "ac.source_id=2 and c.is_polypeptide=1 and q.is_reject=0 and length(q.seq)>=30";

    private static class Candidate {
        int chainID, rafID, pdbChainID, entryID, pdbReleaseID;
        String sid, orderSource, origin;
        java.sql.Date revisionDate, fileDate, snapshotDate;
        Timestamp processed;
    }

    private static class Binding {
        long id;
        int chainID, rafID, pdbReleaseID;
    }

    private static void writeHeader(BufferedWriter out) throws Exception {
        out.write("priority\tsid\tastral_chain_id\traf_id\tpdb_chain_id\t"+
                  "pdb_entry_id\tpdb_release_id\tprocessing_order_source\t"+
                  "origin\tprocessed_at\trevision_date\tfile_date\tsnapshot_date");
        out.newLine();
    }

    private static void write(BufferedWriter out, String priority, Candidate c)
        throws Exception {
        out.write(priority+"\t"+c.sid+"\t"+c.chainID+"\t"+c.rafID+"\t"+
                  c.pdbChainID+"\t"+c.entryID+"\t"+c.pdbReleaseID+"\t"+
                  c.orderSource+"\t"+c.origin+"\t"+
                  (c.processed==null?"":c.processed)+"\t"+
                  (c.revisionDate==null?"":c.revisionDate)+"\t"+
                  (c.fileDate==null?"":c.fileDate)+"\t"+
                  (c.snapshotDate==null?"":c.snapshotDate));
        out.newLine();
    }

    private static long existingBinding(java.util.HashMap<String,Binding> bindings,
                                        String sid, Candidate expected,
                                        boolean frozen) throws Exception {
        Binding b=bindings.get(sid);
        if (b==null) return 0;
        if (frozen && (b.chainID!=expected.chainID || b.rafID!=expected.rafID ||
                       b.pdbReleaseID!=expected.pdbReleaseID))
            throw new Exception("frozen RAF conflicts with existing binding for "+sid+
                                " (binding "+b.id+")");
        return b.id;
    }

    private static long addEvent(Candidate c) throws Exception {
        PreparedStatement ps=LocalSQL.prepareStatement(
            "insert ignore into astral_chain_processing_event " +
            "(pdb_release_id,raf_id,astral_chain_id,source,job_done_id,time_processed) " +
            "values (?,?,?,?,null,?)");
        ps.setInt(1,c.pdbReleaseID); ps.setInt(2,c.rafID); ps.setInt(3,c.chainID);
        ps.setString(4,c.orderSource); ps.setTimestamp(5,c.processed);
        ps.executeUpdate(); ps.close();
        ps=LocalSQL.prepareStatement("select id from astral_chain_processing_event " +
            "where pdb_release_id=? and raf_id=? and astral_chain_id=? and source=?");
        ps.setInt(1,c.pdbReleaseID); ps.setInt(2,c.rafID); ps.setInt(3,c.chainID);
        ps.setString(4,c.orderSource); ResultSet rs=ps.executeQuery();
        rs.next(); long id=rs.getLong(1); rs.close(); ps.close(); return id;
    }

    private static long insertBinding(int releaseID, Candidate c,
                                      boolean coordinates) throws Exception {
        long eventID=0;
        if (!c.orderSource.equals("frozen_raf")) eventID=addEvent(c);
        PreparedStatement ps=LocalSQL.prepareStatement(
            "insert into astral_chain_stable_binding " +
            "(base_scop_release_id,sid,astral_chain_id,raf_id,pdb_chain_id,"+
            "pdb_entry_id,pdb_release_id,processing_event_id,processing_order_source,"+
            "origin,revision_date,file_date,snapshot_date,status,created_at) " +
            "values (?,?,?,?,?,?,?,?,?,?,?,?,?,'registered',now())",
            Statement.RETURN_GENERATED_KEYS);
        ps.setInt(1,releaseID); ps.setString(2,c.sid); ps.setInt(3,c.chainID);
        ps.setInt(4,c.rafID); ps.setInt(5,c.pdbChainID); ps.setInt(6,c.entryID);
        ps.setInt(7,c.pdbReleaseID);
        if (eventID==0) ps.setNull(8,Types.BIGINT); else ps.setLong(8,eventID);
        ps.setString(9,c.orderSource); ps.setString(10,c.origin);
        ps.setDate(11,c.revisionDate); ps.setDate(12,c.fileDate); ps.setDate(13,c.snapshotDate);
        ps.executeUpdate(); ResultSet rs=ps.getGeneratedKeys(); rs.next();
        long id=rs.getLong(1); rs.close(); ps.close();

        if (coordinates) MakeChainPDB.makeChainPDB(id);
        return id;
    }

    /**
     * Fill only missing AEROSPACI rows, once per entry, from a deterministic
     * pinned binding.  Frozen bindings rank first; otherwise processing time
     * ranks before the documented legacy pdb_release.id fallback.
     */
    public static int backfillMissingScores(int releaseID, int limit) throws Exception {
        Statement stmt=LocalSQL.createStatement();
        ResultSet rs=stmt.executeQuery(
            "select distinct b.pdb_entry_id from astral_chain_stable_binding b " +
            "left join aerospaci a on a.pdb_entry_id=b.pdb_entry_id and " +
            "a.release_id=b.base_scop_release_id where b.base_scop_release_id="+
            releaseID+" and a.id is null order by b.pdb_entry_id"+
            (limit>0?" limit "+limit:""));
        java.util.ArrayList<Integer> entries=new java.util.ArrayList<Integer>();
        while (rs.next()) entries.add(rs.getInt(1)); rs.close();
        PreparedStatement choose=LocalSQL.prepareStatement(
            "select b.id,b.pdb_release_id from astral_chain_stable_binding b " +
            "left join astral_chain_processing_event pe on pe.id=b.processing_event_id " +
            "where b.base_scop_release_id=? and b.pdb_entry_id=? order by " +
            "(b.processing_order_source='frozen_raf') desc,"+
            "(pe.time_processed is null),pe.time_processed,b.pdb_release_id,b.sid collate latin1_bin limit 1");
        PreparedStatement provenance=LocalSQL.prepareStatement(
            "insert into astral_chain_aerospaci_source " +
            "(base_scop_release_id,pdb_entry_id,aerospaci_id,stable_binding_id,"+
            "pdb_release_id,source,created_at) select ?,?,a.id,?,?,'generated_from_pinned_release',now() " +
            "from aerospaci a where a.pdb_entry_id=? and a.release_id=? " +
            "on duplicate key update aerospaci_id=values(aerospaci_id)");
        int added=0;
        for (Integer entryID:entries) {
            choose.setInt(1,releaseID); choose.setInt(2,entryID);
            rs=choose.executeQuery();
            if (!rs.next()) { rs.close(); throw new Exception("no pinned binding for entry "+entryID); }
            long bindingID=rs.getLong(1); int pdbReleaseID=rs.getInt(2); rs.close();
            if (FreezeSPACI.addSPACIFromPDBRelease(entryID,pdbReleaseID,releaseID)!=null)
                added++;
            provenance.setInt(1,releaseID); provenance.setInt(2,entryID);
            provenance.setLong(3,bindingID); provenance.setInt(4,pdbReleaseID);
            provenance.setInt(5,entryID); provenance.setInt(6,releaseID);
            provenance.executeUpdate();
        }
        choose.close(); provenance.close(); stmt.close(); return added;
    }

    private static void nullableTimestamp(PreparedStatement ps,int index,String value)
        throws Exception {
        if (value==null || value.length()==0) ps.setNull(index,Types.TIMESTAMP);
        else ps.setTimestamp(index,Timestamp.valueOf(value));
    }

    private static void nullableDate(PreparedStatement ps,int index,String value)
        throws Exception {
        if (value==null || value.length()==0) ps.setNull(index,Types.DATE);
        else ps.setDate(index,java.sql.Date.valueOf(value));
    }

    private static String sqlValue(String value) {
        if (value==null || value.length()==0) return "null";
        return "'"+value.replace("\\","\\\\").replace("'","\\'")+"'";
    }

    /** Apply a reviewed manifest with batched staging and set-based inserts. */
    public static int applyManifest(int releaseID, Path manifest) throws Exception {
        String buildKey=MakeChainPDB.sha256(manifest);
        Statement stmt=LocalSQL.createStatement();
        BufferedReader in=Files.newBufferedReader(manifest,StandardCharsets.UTF_8);
        String line=in.readLine();
        if (line==null || !line.startsWith("priority\tsid\t"))
            throw new Exception("not an all-chain binding manifest: "+manifest);
        int expected=0, pending=0;
        String insertPrefix="insert ignore into astral_chain_binding_stage " +
            "(build_key,priority_name,sid,astral_chain_id,raf_id,pdb_chain_id,"+
            "pdb_entry_id,pdb_release_id,processing_order_source,origin,processed_at,"+
            "revision_date,file_date,snapshot_date) values ";
        StringBuilder values=new StringBuilder(insertPrefix);
        while ((line=in.readLine())!=null) {
            String[] f=line.split("\\t",-1);
            if (f.length!=13) throw new Exception("malformed manifest line "+(expected+2));
            if (pending>0) values.append(',');
            values.append('(').append(sqlValue(buildKey)).append(',')
                .append(sqlValue(f[0])).append(',').append(sqlValue(f[1])).append(',')
                .append(Integer.parseInt(f[2])).append(',').append(Integer.parseInt(f[3])).append(',')
                .append(Integer.parseInt(f[4])).append(',').append(Integer.parseInt(f[5])).append(',')
                .append(Integer.parseInt(f[6])).append(',').append(sqlValue(f[7])).append(',')
                .append(sqlValue(f[8])).append(',').append(sqlValue(f[9])).append(',')
                .append(sqlValue(f[10])).append(',').append(sqlValue(f[11])).append(',')
                .append(sqlValue(f[12])).append(')');
            expected++; pending++;
            if (pending==1000) {
                stmt.executeUpdate(values.toString());
                values=new StringBuilder(insertPrefix); pending=0;
            }
        }
        if (pending>0) stmt.executeUpdate(values.toString());
        in.close();
        ResultSet rs=stmt.executeQuery("select count(*) from astral_chain_binding_stage " +
                                      "where build_key='"+buildKey+"'");
        rs.next(); int staged=rs.getInt(1); rs.close();
        if (staged!=expected) throw new Exception("staging count mismatch: "+staged+
                                                  " vs "+expected);

        rs=stmt.executeQuery("select count(*) from astral_chain_binding_stage s " +
            "join astral_chain_stable_binding b on b.base_scop_release_id="+releaseID+
            " and b.sid=s.sid where s.build_key='"+buildKey+"' and " +
            "s.priority_name='frozen' and (b.astral_chain_id<>s.astral_chain_id " +
            "or b.raf_id<>s.raf_id or b.pdb_release_id<>s.pdb_release_id)");
        rs.next(); int frozenConflicts=rs.getInt(1); rs.close();
        if (frozenConflicts!=0) throw new Exception(frozenConflicts+
            " frozen manifest rows conflict with immutable bindings");

        stmt.executeUpdate("insert ignore into astral_chain_processing_event " +
            "(pdb_release_id,raf_id,astral_chain_id,source,job_done_id,time_processed) " +
            "select pdb_release_id,raf_id,astral_chain_id,processing_order_source,null,"+
            "processed_at from astral_chain_binding_stage where build_key='"+buildKey+
            "' and priority_name<>'frozen'");
        int inserted=stmt.executeUpdate("insert ignore into astral_chain_stable_binding " +
            "(base_scop_release_id,sid,astral_chain_id,raf_id,pdb_chain_id,pdb_entry_id,"+
            "pdb_release_id,processing_event_id,processing_order_source,origin,"+
            "revision_date,file_date,snapshot_date,status,created_at) select "+releaseID+
            ",s.sid,s.astral_chain_id,s.raf_id,s.pdb_chain_id,s.pdb_entry_id,"+
            "s.pdb_release_id,e.id,s.processing_order_source,s.origin,s.revision_date,"+
            "s.file_date,s.snapshot_date,'registered',now() from " +
            "astral_chain_binding_stage s left join astral_chain_processing_event e on " +
            "e.pdb_release_id=s.pdb_release_id and e.raf_id=s.raf_id and " +
            "e.astral_chain_id=s.astral_chain_id and e.source=s.processing_order_source " +
            "where s.build_key='"+buildKey+"'");
        rs=stmt.executeQuery("select count(*) from astral_chain_binding_stage s join " +
            "astral_chain_stable_binding b on b.base_scop_release_id="+releaseID+
            " and b.sid=s.sid and b.astral_chain_id=s.astral_chain_id and " +
            "b.raf_id=s.raf_id and b.pdb_release_id=s.pdb_release_id where " +
            "s.build_key='"+buildKey+"'");
        rs.next(); int matched=rs.getInt(1); rs.close(); stmt.close();
        if (matched!=expected) throw new Exception("only "+matched+" of "+expected+
                                                   " staged bindings match registry");
        System.out.println("manifest_sha256="+buildKey+" staged="+staged+
                           " inserted="+inserted+" matched="+matched);
        return inserted;
    }

    public static int generateCoordinates(int releaseID, int limit, int shard,
                                          int shardCount, boolean retryFailed)
        throws Exception {
        if (shardCount<1 || shard<0 || shard>=shardCount)
            throw new Exception("invalid coordinate shard "+shard+"/"+shardCount);
        Statement stmt=LocalSQL.createStatement();
        ResultSet rs=stmt.executeQuery("select id from astral_chain_stable_binding where " +
            "base_scop_release_id="+releaseID+" and status="+
            (retryFailed?"'failed'":"'registered'")+" and mod(id,"+shardCount+")="+
            shard+" and exists (select 1 from astral_chain ac join astral_seq q on "+
            "q.id=ac.seq_id join pdb_chain c on c.id=astral_chain_stable_binding.pdb_chain_id "+
            "where ac.id=astral_chain_stable_binding.astral_chain_id and "+
            ELIGIBLE_CHAIN+") order by id"+
            (limit>0?" limit "+limit:""));
        java.util.ArrayList<Long> ids=new java.util.ArrayList<Long>();
        while (rs.next()) ids.add(rs.getLong(1)); rs.close(); stmt.close();
        int made=0, failed=0, unobservedCount=0, attempted=0;
        PreparedStatement markFailed=LocalSQL.prepareStatement(
            "update astral_chain_stable_binding set status='failed',error_message=? " +
            "where id=? and status in ('registered','failed')");
        for (Long id:ids) {
            attempted++;
            try {
                MakeChainPDB.makeChainPDB(id); made++;
            }
            catch (Exception e) {
                String message=e.getMessage();
                if (message==null) message=e.getClass().getName();
                if (message.length()>4000) message=message.substring(0,4000);
                if (e instanceof MakeChainPDB.NoCoordinatesException) {
                    unobservedCount++;
                    PreparedStatement unobserved=LocalSQL.prepareStatement(
                        "update astral_chain_stable_binding set status='unobserved',"+
                        "error_message=? where id=? and status in ('registered','failed')");
                    unobserved.setString(1,message); unobserved.setLong(2,id);
                    unobserved.executeUpdate(); unobserved.close();
                    System.err.println("coordinate binding "+id+" unobserved: "+message);
                }
                else {
                    failed++;
                    markFailed.setString(1,message); markFailed.setLong(2,id);
                    markFailed.executeUpdate();
                    System.err.println("coordinate binding "+id+" failed: "+message);
                }
            }
            if (attempted%1000==0)
                System.out.println("coordinate progress shard="+shard+"/"+shardCount+
                    " attempted="+attempted+" ready="+made+" unobserved="+
                    unobservedCount+" failed="+failed);
        }
        markFailed.close();
        System.out.println("coordinate summary shard="+shard+"/"+shardCount+
            " attempted="+attempted+" ready="+made+" unobserved="+
            unobservedCount+" failed="+failed);
        return made;
    }

    public static int generateCoordinates(int releaseID, int limit) throws Exception {
        return generateCoordinates(releaseID,limit,0,1,false);
    }

    private static Candidate candidate(ResultSet rs, boolean frozen) throws Exception {
        Candidate c=new Candidate(); c.chainID=rs.getInt(1); c.sid=rs.getString(2);
        c.rafID=rs.getInt(3); c.pdbChainID=rs.getInt(4); c.entryID=rs.getInt(5);
        c.pdbReleaseID=rs.getInt(6); c.revisionDate=rs.getDate(7);
        c.fileDate=rs.getDate(8); c.snapshotDate=rs.getDate(9);
        if (frozen) {
            c.orderSource="frozen_raf"; c.origin="frozen_raf";
        }
        else {
            c.processed=rs.getTimestamp(10);
            boolean hasJob=!rs.wasNull();
            c.orderSource=hasJob ? "job_history" : "legacy_order_inferred";
            boolean newEntry=rs.getInt(11)==1;
            c.origin=(hasJob ? "first_processed_" : "legacy_inferred_")+
                (newEntry ? "new_entry" : "existing_entry");
            if (!hasJob) c.processed=rs.getTimestamp(12); // snapshot evidence
        }
        return c;
    }

    /** Discover or insert stable bindings; always writes the full manifest. */
    public static void buildBindings(int releaseID, Timestamp cutoff,
                                     Path manifest, boolean apply,
                                     boolean coordinates) throws Exception {
        // A normal statement is required because applying a candidate issues
        // additional statements on the same connection while iterating.
        Statement stmt=LocalSQL.createStatement();
        java.util.HashMap<String,Binding> bindings=new java.util.HashMap<String,Binding>();
        ResultSet existing=stmt.executeQuery("select sid,id,astral_chain_id,raf_id,"+
            "pdb_release_id from astral_chain_stable_binding where base_scop_release_id="+
            releaseID);
        while (existing.next()) {
            Binding b=new Binding(); b.id=existing.getLong(2); b.chainID=existing.getInt(3);
            b.rafID=existing.getInt(4); b.pdbReleaseID=existing.getInt(5);
            bindings.put(existing.getString(1),b);
        }
        existing.close();
        HashSet<String> selected=new HashSet<String>();
        BufferedWriter out=Files.newBufferedWriter(manifest,StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
        writeHeader(out);

        String frozenSQL=
            "select distinct ac.id,ac.sid,r.id,c.id,re.pdb_entry_id,re.id,"+
            "re.revision_date,re.file_date,l.snapshot_date " +
            "from raf r join astral_chain ac on ac.raf_id=r.id " +
            "join astral_seq q on q.id=ac.seq_id join pdb_chain c on c.id=r.pdb_chain_id " +
            "join pdb_release re on re.id=c.pdb_release_id " +
            "join pdb_local l on l.pdb_release_id=re.id " +
            "where r.first_release_id<="+releaseID+" and r.last_release_id>="+releaseID+
            " and r.raf_version_id=3 and "+ELIGIBLE_CHAIN+
            " order by ac.sid collate latin1_bin,ac.id";
        ResultSet rs=stmt.executeQuery(frozenSQL);
        while (rs.next()) {
            Candidate c=candidate(rs,true);
            if (!selected.add(c.sid))
                throw new Exception("ambiguous frozen RAF binding for exact SID "+c.sid);
            write(out,"frozen",c);
            long id=existingBinding(bindings,c.sid,c,true);
            if (apply && id==0) {
                id=insertBinding(releaseID,c,coordinates);
                Binding b=new Binding(); b.id=id; b.chainID=c.chainID; b.rafID=c.rafID;
                b.pdbReleaseID=c.pdbReleaseID; bindings.put(c.sid,b);
            }
            else if (apply && coordinates) MakeChainPDB.makeChainPDB(id);
        }
        rs.close();

        // Carry every previously pinned SID forward before considering new
        // working RAFs.  The registry, not a later database reordering, is the
        // source of truth for subsequent population generations.
        rs=stmt.executeQuery("select b.astral_chain_id,b.sid,b.raf_id,b.pdb_chain_id,"+
            "b.pdb_entry_id,b.pdb_release_id,b.revision_date,b.file_date,"+
            "b.snapshot_date,pe.time_processed,b.processing_order_source,b.origin " +
            "from astral_chain_stable_binding b join astral_chain ac on "+
            "ac.id=b.astral_chain_id join astral_seq q on q.id=ac.seq_id join "+
            "pdb_chain c on c.id=b.pdb_chain_id left join " +
            "astral_chain_processing_event pe on pe.id=b.processing_event_id " +
            "where b.base_scop_release_id="+releaseID+" and "+ELIGIBLE_CHAIN+
            " order by b.sid collate latin1_bin");
        while (rs.next()) {
            String sid=rs.getString(2);
            if (selected.contains(sid)) continue;
            Candidate c=new Candidate(); c.chainID=rs.getInt(1); c.sid=sid;
            c.rafID=rs.getInt(3); c.pdbChainID=rs.getInt(4); c.entryID=rs.getInt(5);
            c.pdbReleaseID=rs.getInt(6); c.revisionDate=rs.getDate(7);
            c.fileDate=rs.getDate(8); c.snapshotDate=rs.getDate(9);
            c.processed=rs.getTimestamp(10); c.orderSource=rs.getString(11);
            c.origin=rs.getString(12); selected.add(sid);
            write(out,"existing_binding",c);
            if (apply && coordinates)
                MakeChainPDB.makeChainPDB(bindings.get(sid).id);
        }
        rs.close();

        PreparedStatement history=LocalSQL.prepareStatement(
            "select ac.id,ac.sid,r.id,c.id,re.pdb_entry_id,re.id,re.revision_date,"+
            "re.file_date,l.snapshot_date,case when j.first_processed>=timestamp(l.snapshot_date) " +
            "then j.first_processed else null end valid_first_processed,"+
            "if(e.release_date>s.freeze_date,1,0),timestamp(l.snapshot_date) " +
            "from raf r join astral_chain ac on ac.raf_id=r.id " +
            "join astral_seq q on q.id=ac.seq_id join pdb_chain c on c.id=r.pdb_chain_id " +
            "join pdb_release re on re.id=c.pdb_release_id " +
            "join pdb_entry e on e.id=re.pdb_entry_id " +
            "join pdb_local l on l.pdb_release_id=re.id " +
            "join scop_release s on s.id=? left join " +
            "(select target_id,min(time_finished) first_processed from job_done " +
            " where job_type_id=6 and status is null group by target_id) j on j.target_id=r.id " +
            "where r.raf_version_id=3 and r.first_release_id is null " +
            "and r.last_release_id is null and ac.source_id=2 and c.is_polypeptide=1 " +
            "and q.is_reject=0 and length(q.seq)>=30 and l.pdb_path is not null " +
            "and l.is_raf_calculated=1 " +
            "and coalesce(case when j.first_processed>=timestamp(l.snapshot_date) " +
            "then j.first_processed else null end,timestamp(l.snapshot_date))<=? " +
            "order by ac.sid collate latin1_bin,"+
            "(case when j.first_processed>=timestamp(l.snapshot_date) then j.first_processed " +
            "else null end is null),case when j.first_processed>=timestamp(l.snapshot_date) " +
            "then j.first_processed else null end,re.id,ac.id");
        history.setInt(1,releaseID); history.setTimestamp(2,cutoff);
        rs=history.executeQuery();
        String priorSID=null;
        while (rs.next()) {
            Candidate c=candidate(rs,false);
            if (c.sid.equals(priorSID)) continue;
            priorSID=c.sid;
            if (selected.contains(c.sid)) continue;
            long id=existingBinding(bindings,c.sid,c,false);
            selected.add(c.sid);
            write(out,"first_processed",c);
            if (apply && id==0) {
                id=insertBinding(releaseID,c,coordinates);
                Binding b=new Binding(); b.id=id; b.chainID=c.chainID; b.rafID=c.rafID;
                b.pdbReleaseID=c.pdbReleaseID; bindings.put(c.sid,b);
            }
            else if (apply && coordinates) MakeChainPDB.makeChainPDB(id);
        }
        rs.close(); history.close(); stmt.close(); out.close();
        System.out.println((apply?"applied":"discovered")+" "+selected.size()+
                           " exact-case stable bindings; manifest "+manifest);
    }

    /** Cut candidates from already-ready bindings and frozen AEROSPACI. */
    public static int createPopulation(int releaseID, Timestamp cutoff,
                                       java.sql.Date effectiveDate,
                                       String buildVersion) throws Exception {
        Statement stmt=LocalSQL.createStatement();
        ResultSet rs=stmt.executeQuery("select freeze_date from scop_release where id="+releaseID);
        if (!rs.next()) throw new Exception("SCOP release not found: "+releaseID);
        java.sql.Date freeze=rs.getDate(1); rs.close();

        PreparedStatement readiness=LocalSQL.prepareStatement(
            "select count(*) from astral_chain_stable_binding b " +
            "join astral_chain ac on ac.id=b.astral_chain_id " +
            "join astral_seq q on q.id=ac.seq_id " +
            "join pdb_chain c on c.id=b.pdb_chain_id " +
            "join pdb_entry e on e.id=b.pdb_entry_id " +
            "left join astral_chain_processing_event pe on pe.id=b.processing_event_id " +
            "where b.base_scop_release_id=? and "+ELIGIBLE_CHAIN+
            " and (b.processing_order_source='frozen_raf' " +
            "or pe.time_processed<=?) and (e.obsolete_date is null or e.obsolete_date>?) " +
            "and b.status<>'unobserved' and (b.status<>'ready' or b.coordinate_file_path is null " +
            "or b.coordinate_sha256 is null)");
        readiness.setInt(1,releaseID); readiness.setTimestamp(2,cutoff);
        readiness.setDate(3,effectiveDate); rs=readiness.executeQuery();
        rs.next(); int notReady=rs.getInt(1); rs.close(); readiness.close();
        if (notReady!=0) throw new Exception(notReady+
            " eligible stable bindings lack validated versioned coordinates");

        PreparedStatement scoreCheck=LocalSQL.prepareStatement(
            "select count(*) from (select b.id from astral_chain_stable_binding b " +
            "join astral_chain ac on ac.id=b.astral_chain_id " +
            "join astral_seq q on q.id=ac.seq_id " +
            "join pdb_chain c on c.id=b.pdb_chain_id " +
            "join pdb_entry e on e.id=b.pdb_entry_id " +
            "left join astral_chain_processing_event pe on pe.id=b.processing_event_id " +
            "left join aerospaci a on a.pdb_entry_id=b.pdb_entry_id " +
            "and a.release_id=b.base_scop_release_id where b.base_scop_release_id=? and "+
            ELIGIBLE_CHAIN+" " +
            "and (b.processing_order_source='frozen_raf' or pe.time_processed<=?) " +
            "and (e.obsolete_date is null or e.obsolete_date>?) group by b.id " +
            "having count(a.id)<>1) missing");
        scoreCheck.setInt(1,releaseID); scoreCheck.setTimestamp(2,cutoff);
        scoreCheck.setDate(3,effectiveDate); rs=scoreCheck.executeQuery();
        rs.next(); int badScores=rs.getInt(1); rs.close();
        scoreCheck.close();
        if (badScores!=0) throw new Exception(badScores+
            " ready bindings lack exactly one immutable AEROSPACI row");

        PreparedStatement add=LocalSQL.prepareStatement(
            "insert into astral_chain_all_population " +
            "(base_scop_release_id,freeze_date,processing_cutoff_event_id,"+
            "processing_cutoff_time,effective_date,build_version,status,created_at) " +
            "values (?,?,(select max(id) from astral_chain_processing_event " +
            "where time_processed<=?),?,?,?,'building',now())",
            Statement.RETURN_GENERATED_KEYS);
        add.setInt(1,releaseID); add.setDate(2,freeze); add.setTimestamp(3,cutoff);
        add.setTimestamp(4,cutoff); add.setDate(5,effectiveDate);
        add.setString(6,buildVersion); add.executeUpdate();
        rs=add.getGeneratedKeys(); rs.next(); int populationID=rs.getInt(1);
        rs.close(); add.close();

        PreparedStatement insert=LocalSQL.prepareStatement(
            "insert into astral_chain_all_candidate " +
            "(population_id,stable_binding_id,astral_chain_id,sid,seq_id,"+
            "pdb_entry_id,pdb_release_id,origin,aerospaci) " +
            "select ?,b.id,b.astral_chain_id,b.sid,ac.seq_id,b.pdb_entry_id,"+
            "b.pdb_release_id,b.origin,a.aerospaci from astral_chain_stable_binding b " +
            "join astral_chain ac on ac.id=b.astral_chain_id " +
            "join astral_seq q on q.id=ac.seq_id " +
            "join pdb_chain c on c.id=b.pdb_chain_id " +
            "join pdb_entry e on e.id=b.pdb_entry_id " +
            "join aerospaci a on a.pdb_entry_id=b.pdb_entry_id " +
            "and a.release_id=b.base_scop_release_id " +
            "left join astral_chain_processing_event pe on pe.id=b.processing_event_id " +
            "where b.base_scop_release_id=? and b.status='ready' and "+ELIGIBLE_CHAIN+" " +
            "and (b.processing_order_source='frozen_raf' or pe.time_processed<=?) " +
            "and (e.obsolete_date is null or e.obsolete_date>?)");
        insert.setInt(1,populationID); insert.setInt(2,releaseID);
        insert.setTimestamp(3,cutoff); insert.setDate(4,effectiveDate);
        int count=insert.executeUpdate(); insert.close();
        stmt.executeUpdate("update astral_chain_all_population set candidate_count="+
            count+",sequence_count=(select count(distinct seq_id) from " +
            "astral_chain_all_candidate where population_id="+populationID+
            ") where id="+populationID);
        stmt.close(); return populationID;
    }

    public static void main(String[] argv) {
        try {
            LocalSQL.connectRW();
            if (argv.length>=1 && (argv[0].equals("bindings") ||
                                   argv[0].equals("apply-bindings"))) {
                if (argv.length<4 || argv.length>5)
                    throw new Exception("Usage: BuildAllChainPopulation bindings|apply-bindings release_id 'YYYY-MM-DD HH:MM:SS' manifest.tsv [--coordinates]");
                buildBindings(Integer.parseInt(argv[1]),Timestamp.valueOf(argv[2]),
                    Paths.get(argv[3]),argv[0].equals("apply-bindings"),
                    argv.length==5 && argv[4].equals("--coordinates"));
            }
            else if (argv.length==5 && argv[0].equals("create-population")) {
                int id=createPopulation(Integer.parseInt(argv[1]),Timestamp.valueOf(argv[2]),
                    java.sql.Date.valueOf(argv[3]),argv[4]);
                System.out.println("created building population "+id);
            }
            else if ((argv.length==2 || argv.length==3) &&
                     argv[0].equals("backfill-scores"))
                System.out.println("inserted "+backfillMissingScores(Integer.parseInt(argv[1]),
                    argv.length==3?Integer.parseInt(argv[2]):0)+
                                   " missing immutable AEROSPACI rows");
            else if (argv.length==3 && argv[0].equals("apply-manifest"))
                applyManifest(Integer.parseInt(argv[1]),Paths.get(argv[2]));
            else if ((argv.length==2 || argv.length==3 || argv.length==5) &&
                     argv[0].equals("generate-coordinates")) {
                int limit=argv.length>=3?Integer.parseInt(argv[2]):0;
                int shard=argv.length==5?Integer.parseInt(argv[3]):0;
                int shardCount=argv.length==5?Integer.parseInt(argv[4]):1;
                System.out.println("generated "+generateCoordinates(Integer.parseInt(argv[1]),
                    limit,shard,shardCount,false)+" coordinate files");
            }
            else if ((argv.length==2 || argv.length==3) &&
                     argv[0].equals("retry-failed-coordinates"))
                System.out.println("recovered "+generateCoordinates(Integer.parseInt(argv[1]),
                    argv.length==3?Integer.parseInt(argv[2]):0,0,1,true)+
                    " coordinate files");
            else throw new Exception("Commands: bindings, apply-bindings, create-population");
        }
        catch (Exception e) {
            System.err.println("Exception: "+e.getMessage()); e.printStackTrace(); System.exit(1);
        }
    }
}
