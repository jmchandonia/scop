/*
 * Software to build and maintain SCOPe, https://scop.berkeley.edu/
 */
package gov.lbl.scop.app;

import gov.lbl.scop.local.Blast;
import gov.lbl.scop.local.LocalSQL;
import org.strbio.mol.Polymer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Isolated BLAST-2.2.18 compatibility searches for all-chain populations. */
public class AllChainBlast {
    public static final String PARAMETERS =
        "-j 1 -h 1e-2 -v 5000 -b 5000 -a2 -z 100000000 -J T";

    private static String executable(String name) throws Exception {
        String configured = System.getProperty("scop.blast."+name);
        if (configured != null)
            return configured;
        Path lab=Paths.get("/lab/bin",name+"-2.2.18");
        if (Files.isExecutable(lab))
            return lab.toRealPath().toString();
        Process p = new ProcessBuilder("/usr/bin/which",name+"-2.2.18").start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String path = r.readLine();
        r.close();
        if (p.waitFor() != 0 || path == null)
            throw new Exception(name+"-2.2.18 is not on PATH; set -Dscop.blast."+
                                name+"=/absolute/path");
        return path;
    }

    private static String[] configureExecutables() throws Exception {
        String blastpgp=executable("blastpgp");
        String formatdb=executable("formatdb");
        System.setProperty("scop.blast.blastpgp",blastpgp);
        System.setProperty("scop.blast.formatdb",formatdb);
        return new String[]{blastpgp,formatdb};
    }

    private static int methodID(Statement stmt) throws Exception {
        String exe = configureExecutables()[0];
        String checksum = MakeChainPDB.sha256(Paths.get(exe));
        PreparedStatement ps = LocalSQL.prepareStatement(
            "insert ignore into astral_chain_all_blast_method " +
            "(name,version,executable_path,executable_sha256,parameters," +
            "parser_version,created_at) values ('blastpgp','2.2.18',?,?,?,"+
            "'gov.lbl.scop.local.Blast-v3',now())");
        ps.setString(1,exe);
        ps.setString(2,checksum);
        ps.setString(3,PARAMETERS);
        ps.executeUpdate();
        ps.close();
        ResultSet rs = stmt.executeQuery(
            "select id from astral_chain_all_blast_method where name='blastpgp' " +
            "and version='2.2.18' and executable_sha256='"+checksum+"' " +
            "and parser_version='gov.lbl.scop.local.Blast-v3'");
        rs.next();
        int id = rs.getInt(1);
        rs.close();
        return id;
    }

    private static int previousPopulation(Statement stmt, int populationID) throws Exception {
        ResultSet rs=stmt.executeQuery(
            "select max(id) from astral_chain_all_population where id<"+
            populationID+" and status='validated'");
        rs.next(); int previous=rs.getInt(1); rs.close(); return previous;
    }

    private static int baseRelease(Statement stmt, int populationID) throws Exception {
        ResultSet rs=stmt.executeQuery("select base_scop_release_id from " +
            "astral_chain_all_population where id="+populationID);
        rs.next(); int release=rs.getInt(1); rs.close(); return release;
    }

    private static String oldSequenceQuery(Statement stmt, int populationID)
        throws Exception {
        int previous=previousPopulation(stmt,populationID);
        if (previous!=0)
            return "select seq_id from astral_chain_all_candidate where population_id="+previous;
        int release=baseRelease(stmt,populationID);
        return "select distinct c.seq_id from astral_chain_all_candidate c where " +
            "c.population_id="+populationID+" and c.seq_id in (select distinct " +
            "ac.seq_id from astral_chain ac join raf r on r.id=ac.raf_id where " +
            "ac.source_id=2 and r.first_release_id<="+release+
            " and r.last_release_id>="+release+") and c.seq_id in (select " +
            "distinct target_id from job_done force index(job_type_id) where " +
            "job_type_id=20 and status is null and args='2 1 1 "+release+"') " +
            "and exists (select 1 from " +
            "astral_seq_blast b force index(seq1_id) where b.seq1_id=c.seq_id " +
            "and b.release_id="+release+" and b.source_id=2 and b.style1_id=1 " +
            "and b.style2_id=1)";
    }

    private static void writeFasta(Statement stmt, int populationID,
                                   boolean newOnly, Path fasta) throws Exception {
        Files.createDirectories(fasta.getParent());
        String filter = "";
        if (newOnly)
            filter = " and c.seq_id not in ("+oldSequenceQuery(stmt,populationID)+")";
        ResultSet rs = stmt.executeQuery(
            "select distinct c.seq_id,s.seq from astral_chain_all_candidate c " +
            "join astral_seq s on s.id=c.seq_id where c.population_id="+
            populationID+filter+" order by c.seq_id");
        BufferedWriter out = Files.newBufferedWriter(fasta,StandardCharsets.US_ASCII,
            StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
        while (rs.next()) {
            out.write(">"+rs.getInt(1));
            out.newLine();
            String seq=rs.getString(2);
            for (int i=0;i<seq.length();i+=60) {
                out.write(seq.substring(i,Math.min(i+60,seq.length())));
                out.newLine();
            }
        }
        out.close();
        rs.close();
    }

    private static int addDatabase(Statement stmt, int populationID,
                                   String kind, Path fasta,
                                   String formatdb) throws Exception {
        String checksum=MakeChainPDB.sha256(fasta);
        String formatChecksum=MakeChainPDB.sha256(Paths.get(formatdb));
        ResultSet rs=stmt.executeQuery("select count(distinct seq_id) from " +
            "astral_chain_all_candidate where population_id="+populationID+
            (kind.equals("new") ? " and seq_id not in ("+
             oldSequenceQuery(stmt,populationID)+")" : ""));
        rs.next(); int count=rs.getInt(1); rs.close();
        PreparedStatement ps=LocalSQL.prepareStatement(
            "insert into astral_chain_all_blast_database " +
            "(population_id,kind,fasta_path,fasta_sha256,sequence_count,"+
            "formatdb_executable_path,formatdb_executable_sha256,"+
            "formatdb_parameters,created_at) values (?,?,?,?,?,?,?,'-o T',now()) "+
            "on duplicate key update " +
            "fasta_path=values(fasta_path),fasta_sha256=values(fasta_sha256),"+
            "sequence_count=values(sequence_count),"+
            "formatdb_executable_path=values(formatdb_executable_path),"+
            "formatdb_executable_sha256=values(formatdb_executable_sha256),"+
            "formatdb_parameters=values(formatdb_parameters)");
        ps.setInt(1,populationID); ps.setString(2,kind);
        ps.setString(3,fasta.toString()); ps.setString(4,checksum); ps.setInt(5,count);
        ps.setString(6,formatdb); ps.setString(7,formatChecksum);
        ps.executeUpdate(); ps.close();
        rs=stmt.executeQuery("select id from astral_chain_all_blast_database where " +
                            "population_id="+populationID+" and kind='"+kind+"'");
        rs.next(); int id=rs.getInt(1); rs.close(); return id;
    }

    /** Build isolated all/new databases and explicit query coverage rows. */
    public static void setup(int populationID) throws Exception {
        Statement stmt=LocalSQL.createStatement();
        ResultSet rs=stmt.executeQuery("select status from astral_chain_all_population where id="+populationID);
        if (!rs.next()) throw new Exception("population not found: "+populationID);
        rs.close();
        String[] executables=configureExecutables();
        int methodID=methodID(stmt);
        String root=System.getProperty("scop.allchain.blast.dir",
                                       "/lab/proj/astral/blast/all-chain");
        Path base=Paths.get(root,Integer.toString(populationID));
        Path all=base.resolve("all/seqs.fa");
        Path newer=base.resolve("new/seqs.fa");
        writeFasta(stmt,populationID,false,all);
        writeFasta(stmt,populationID,true,newer);
        Blast formatter=new Blast("2.2.18");
        formatter.baseDir=all.getParent().toFile(); formatter.formatDB(all.toString());
        verifyFormattedDatabase(all);
        int allID=addDatabase(stmt,populationID,"all",all,executables[1]);

        int previous=previousPopulation(stmt,populationID);
        formatter.baseDir=newer.getParent().toFile(); formatter.formatDB(newer.toString());
        verifyFormattedDatabase(newer);
        int newID=addDatabase(stmt,populationID,"new",newer,executables[1]);

        String oldQuery=oldSequenceQuery(stmt,populationID);
        String newFilter=" and seq_id not in ("+oldQuery+")";
        stmt.executeUpdate("insert ignore into astral_chain_all_blast_coverage " +
            "(population_id,method_id,database_id,query_seq_id,status) " +
            "select distinct "+populationID+","+methodID+","+allID+",seq_id,'queued' " +
            "from astral_chain_all_candidate where population_id="+populationID+newFilter);
        stmt.executeUpdate("insert ignore into astral_chain_all_blast_coverage " +
            "(population_id,method_id,database_id,query_seq_id,status) " +
            "select distinct "+populationID+","+methodID+","+newID+",seq_id,'queued' " +
            "from astral_chain_all_candidate where population_id="+populationID+
            " and seq_id in ("+oldQuery+")");

        // For the initial population, retain accepted legacy old/old edges.
        // New/old, old/new and new/new are still searched explicitly above.
        if (previous==0) {
            int release=baseRelease(stmt,populationID);
            stmt.executeUpdate("insert ignore into astral_chain_all_blast_edge " +
                "(population_id,method_id,database_id,seq1_id,seq2_id,blast_log10_e,"+
                "pct_identical,seq1_start,seq1_length,seq2_start,seq2_length,"+
                "provenance,legacy_release_id,legacy_blast_id) " +
                "select "+populationID+","+methodID+","+allID+",b.seq1_id,b.seq2_id,"+
                "b.blast_log10_e,b.pct_identical,b.seq1_start,b.seq1_length,"+
                "b.seq2_start,b.seq2_length,'legacy_reused',"+release+",b.id "+
                "from astral_chain_all_blast_coverage q1 join " +
                "astral_seq_blast b force index(seq1_id) on b.seq1_id=q1.query_seq_id " +
                "join astral_chain_all_blast_coverage q2 on q2.population_id="+
                populationID+" and q2.method_id="+methodID+" and q2.database_id="+
                newID+" and q2.query_seq_id=b.seq2_id where q1.population_id="+
                populationID+" and q1.method_id="+methodID+" and q1.database_id="+
                newID+" and " +
                "b.release_id="+release+" and b.source_id=2 and b.style1_id=1 " +
                "and b.style2_id=1 and b.pct_identical>=90");
        }
        else {
            stmt.executeUpdate("insert ignore into astral_chain_all_blast_edge " +
                "(population_id,method_id,database_id,seq1_id,seq2_id,blast_log10_e,"+
                "pct_identical,seq1_start,seq1_length,seq2_start,seq2_length,"+
                "provenance,legacy_release_id,legacy_blast_id) " +
                "select "+populationID+","+methodID+","+allID+",seq1_id,seq2_id,"+
                "blast_log10_e,pct_identical,seq1_start,seq1_length,seq2_start,"+
                "seq2_length,provenance,legacy_release_id,legacy_blast_id from "+
                "astral_chain_all_blast_edge where population_id="+
                previous+" and seq1_id in ("+oldQuery+") and seq2_id in ("+
                oldQuery+")");
        }
        stmt.close();
    }

    private static void verifyFormattedDatabase(Path fasta) throws Exception {
        for (String suffix:new String[]{".phr",".pin",".psq"}) {
            Path file=Paths.get(fasta.toString()+suffix);
            if (!Files.isRegularFile(file) || Files.size(file)==0)
                throw new Exception("formatdb did not create a nonempty "+file);
        }
    }

    private static Blast configuredBlast(File base, String fasta) throws Exception {
        configureExecutables();
        Blast b=new Blast("2.2.18"); b.baseDir=base; b.inputs=new String[19];
        b.inputs[0]="-d"; b.inputs[1]=fasta; b.inputs[2]="-i"; b.inputs[3]=null;
        b.inputs[4]="-j"; b.inputs[5]="1"; b.inputs[6]="-h"; b.inputs[7]="1e-2";
        b.inputs[8]="-v"; b.inputs[9]="5000"; b.inputs[10]="-b"; b.inputs[11]="5000";
        b.inputs[12]="-a2"; b.inputs[13]="-z"; b.inputs[14]="100000000";
        b.inputs[15]="-J"; b.inputs[16]="T"; b.inputs[17]="-O"; b.inputs[18]=null;
        return b;
    }

    /** Run one queued search; safe for repeated worker invocations. */
    public static boolean runNext(int populationID) throws Exception {
        String worker=java.net.InetAddress.getLocalHost().getHostName()+"-"+
            java.lang.management.ManagementFactory.getRuntimeMXBean().getName()+"-"+
            UUID.randomUUID().toString().substring(0,8);
        Statement stmt=LocalSQL.createStatement();
        LocalSQL.setAutoCommit(false);
        ResultSet rs=stmt.executeQuery(
            "select method_id,database_id,query_seq_id from "+
            "astral_chain_all_blast_coverage where population_id="+populationID+
            " and status='queued' order by query_seq_id limit 1 for update skip locked");
        if (!rs.next()) {
            rs.close(); LocalSQL.commit(); LocalSQL.setAutoCommit(true);
            stmt.close(); return false;
        }
        int methodID=rs.getInt(1), databaseID=rs.getInt(2), seqID=rs.getInt(3);
        rs.close();
        PreparedStatement claim=LocalSQL.prepareStatement(
            "update astral_chain_all_blast_coverage set status='running',"+
            "attempt_count=attempt_count+1,worker_id=?,started_at=now(),"+
            "completed_at=null,error_message=null where population_id=? and "+
            "method_id=? and database_id=? and query_seq_id=? and status='queued'");
        claim.setString(1,worker); claim.setInt(2,populationID);
        claim.setInt(3,methodID); claim.setInt(4,databaseID); claim.setInt(5,seqID);
        if (claim.executeUpdate()!=1) throw new Exception("lost BLAST coverage claim");
        claim.close(); LocalSQL.commit(); LocalSQL.setAutoCommit(true);
        rs=stmt.executeQuery("select d.fasta_path,s.seq from "+
            "astral_chain_all_blast_database d join astral_seq s on s.id="+seqID+
            " where d.id="+databaseID);
        if (!rs.next()) throw new Exception("claimed BLAST input disappeared");
        String fasta=rs.getString(1), seq=rs.getString(2); rs.close();
        try {
            stmt.executeUpdate("delete from astral_chain_all_blast_edge where population_id="+
                populationID+" and method_id="+methodID+" and database_id="+databaseID+
                " and seq1_id="+seqID);
            Polymer p=new Polymer(seq); p.name=Integer.toString(seqID);
            File base=Paths.get(fasta).getParent().toFile();
            Blast b=configuredBlast(base,fasta);
            File[] files=b.process(p,Paths.get(fasta).getFileName().toString(),3,18);
            int outputHits=b.processOutputAllChains(stmt,files[0],files[1],seqID,
                                                    populationID,methodID,databaseID);
            rs=stmt.executeQuery("select count(*) from astral_chain_all_blast_edge where " +
                "population_id="+populationID+" and method_id="+methodID+
                " and database_id="+databaseID+" and seq1_id="+seqID);
            rs.next(); int hits=rs.getInt(1); rs.close();
            stmt.executeUpdate("update astral_chain_all_blast_coverage set status='complete',"+
                "hit_count="+outputHits+",stored_edge_count="+hits+
                ",hit_ceiling_reached="+(outputHits>=5000?1:0)+
                ",completed_at=now() where population_id="+populationID+
                " and method_id="+methodID+" and database_id="+databaseID+
                " and query_seq_id="+seqID);
        }
        catch (Exception e) {
            PreparedStatement fail=LocalSQL.prepareStatement(
                "update astral_chain_all_blast_coverage set status='failed',"+
                "error_message=? where population_id=? and method_id=? and database_id=? " +
                "and query_seq_id=?");
            fail.setString(1,e.toString()); fail.setInt(2,populationID);
            fail.setInt(3,methodID); fail.setInt(4,databaseID); fail.setInt(5,seqID);
            fail.executeUpdate(); fail.close(); stmt.close(); throw e;
        }
        stmt.close(); return true;
    }

    public static int runWorker(int populationID) throws Exception {
        int completed=0;
        while (runNext(populationID)) {
            completed++;
            if (completed%100==0)
                System.out.println("BLAST worker completed "+completed+" queries");
        }
        return completed;
    }

    public static int requeue(int populationID, String state, int staleMinutes)
        throws Exception {
        if (!state.equals("failed") && !state.equals("running"))
            throw new Exception("only failed or stale running searches may be requeued");
        Statement stmt=LocalSQL.createStatement();
        String age=state.equals("running") ?
            " and started_at<date_sub(now(),interval "+staleMinutes+" minute)" : "";
        int n=stmt.executeUpdate("update astral_chain_all_blast_coverage set "+
            "status='queued',worker_id=null where population_id="+populationID+
            " and status='"+state+"'"+age);
        stmt.close(); return n;
    }

    public static void main(String[] argv) {
        try {
            if (argv.length<2 || argv.length>3)
                throw new Exception("Usage: AllChainBlast setup|run-next|worker|retry-failed population_id | requeue-stale population_id minutes");
            LocalSQL.connectRW(); int populationID=Integer.parseInt(argv[1]);
            if (argv[0].equals("setup")) setup(populationID);
            else if (argv[0].equals("run-next")) runNext(populationID);
            else if (argv[0].equals("worker"))
                System.out.println("worker completed "+runWorker(populationID)+" queries");
            else if (argv[0].equals("retry-failed"))
                System.out.println("requeued "+requeue(populationID,"failed",0)+
                                   " failed searches");
            else if (argv[0].equals("requeue-stale") && argv.length==3)
                System.out.println("requeued "+requeue(populationID,"running",
                    Integer.parseInt(argv[2]))+" stale searches");
            else throw new Exception("unknown command: "+argv[0]);
        }
        catch (Exception e) {
            System.err.println("Exception: "+e.getMessage()); e.printStackTrace(); System.exit(1);
        }
    }
}
