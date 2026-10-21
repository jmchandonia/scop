/*
 * Software to build and maintain SCOPe, https://scop.berkeley.edu/
 */
package gov.lbl.scop.app;

import gov.lbl.scop.local.LocalSQL;

import java.sql.*;

/** Build the persistent closest-representative mapping for an all-chain subset. */
public class MakeAllChainClusters {
    public static final String ASSIGNMENT_VERSION = "closest-representative-v1";

    private static long scalar(Statement stmt, String sql) throws Exception {
        ResultSet rs=stmt.executeQuery(sql);
        if (!rs.next()) {
            rs.close();
            throw new Exception("query returned no rows: "+sql);
        }
        long answer=rs.getLong(1);
        rs.close();
        return answer;
    }

    private static void phase(Statement stmt, String name, String sql)
        throws Exception {
        long started=System.currentTimeMillis();
        System.out.println(new java.util.Date()+" "+name+" started");
        stmt.executeUpdate(sql);
        System.out.println(new java.util.Date()+" "+name+" finished in "+
                           ((System.currentTimeMillis()-started)/1000.0)+" seconds");
    }

    private static String workName(String kind, int populationID, int pctID) {
        return "astral_chain_all_cluster_"+kind+"_work_"+
            populationID+"_"+pctID;
    }

    public static long build(int populationID, int pctID) throws Exception {
        if (populationID<=0) throw new Exception("population_id must be positive");
        if (pctID!=90) throw new Exception("only the reviewed 90% subset is supported");

        Statement stmt=LocalSQL.createStatement();
        ResultSet rs=stmt.executeQuery("select status from astral_chain_all_population "+
                                       "where id="+populationID);
        if (!rs.next()) throw new Exception("population not found: "+populationID);
        String populationStatus=rs.getString(1);
        rs.close();
        if (!"validated".equals(populationStatus))
            throw new Exception("population is not validated: "+populationStatus);

        long candidateCount=scalar(stmt,"select count(*) from "+
            "astral_chain_all_candidate where population_id="+populationID);
        long sequenceCount=scalar(stmt,"select count(distinct seq_id) from "+
            "astral_chain_all_candidate where population_id="+populationID);
        long representativeCount=scalar(stmt,"select count(*) from "+
            "astral_chain_all_subset_id where population_id="+populationID+
            " and pct_identical="+pctID);
        if (candidateCount==0 || sequenceCount==0 || representativeCount==0)
            throw new Exception("population candidates or representatives are absent");

        String match=workName("match",populationID,pctID);
        String best=workName("best",populationID,pctID);
        String stage=workName("member",populationID,pctID);
        stmt.executeUpdate("drop table if exists "+stage);
        stmt.executeUpdate("drop table if exists "+best);
        stmt.executeUpdate("drop table if exists "+match);

        phase(stmt,"create match work table",
            "create table "+match+" ("+
            "member_seq_id int unsigned not null,"+
            "representative_astral_chain_id int unsigned not null,"+
            "representative_seq_id int unsigned not null,"+
            "representative_pct_identity double not null,"+
            "identity_provenance varchar(64) not null,"+
            "primary key(member_seq_id,representative_astral_chain_id),"+
            "key match_rank(member_seq_id,representative_pct_identity)"+
            ") engine=InnoDB");

        phase(stmt,"add exact-sequence matches",
            "insert into "+match+" "+
            "select distinct m.seq_id,r.astral_chain_id,r.seq_id,100.0,'same_seq' "+
            "from astral_chain_all_candidate m join astral_chain_all_candidate r "+
            "on r.population_id=m.population_id and r.seq_id=m.seq_id join "+
            "astral_chain_all_subset_id s on s.population_id=r.population_id and "+
            "s.pct_identical="+pctID+" and s.astral_chain_id=r.astral_chain_id "+
            "where m.population_id="+populationID);

        String updateBest=
            " on duplicate key update "+
            "identity_provenance=if(values(representative_pct_identity)>"+
            "representative_pct_identity,values(identity_provenance),"+
            "identity_provenance),representative_pct_identity="+
            "greatest(representative_pct_identity,"+
            "values(representative_pct_identity))";

        phase(stmt,"add forward BLAST matches",
            "insert into "+match+" "+
            "select e.seq2_id,r.astral_chain_id,r.seq_id,max(e.pct_identical),"+
            "'blastpgp-2.2.18' from astral_chain_all_blast_edge e join "+
            "astral_chain_all_candidate r on r.population_id=e.population_id and "+
            "r.seq_id=e.seq1_id join astral_chain_all_subset_id s on "+
            "s.population_id=r.population_id and s.pct_identical="+pctID+" and "+
            "s.astral_chain_id=r.astral_chain_id join (select distinct seq_id "+
            "from astral_chain_all_candidate where population_id="+populationID+
            ") m on m.seq_id=e.seq2_id where e.population_id="+populationID+
            " and e.pct_identical>="+pctID+" group by e.seq2_id,"+
            "r.astral_chain_id,r.seq_id"+updateBest);

        phase(stmt,"add reverse BLAST matches",
            "insert into "+match+" "+
            "select e.seq1_id,r.astral_chain_id,r.seq_id,max(e.pct_identical),"+
            "'blastpgp-2.2.18' from astral_chain_all_blast_edge e join "+
            "astral_chain_all_candidate r on r.population_id=e.population_id and "+
            "r.seq_id=e.seq2_id join astral_chain_all_subset_id s on "+
            "s.population_id=r.population_id and s.pct_identical="+pctID+" and "+
            "s.astral_chain_id=r.astral_chain_id join (select distinct seq_id "+
            "from astral_chain_all_candidate where population_id="+populationID+
            ") m on m.seq_id=e.seq1_id where e.population_id="+populationID+
            " and e.pct_identical>="+pctID+" group by e.seq1_id,"+
            "r.astral_chain_id,r.seq_id"+updateBest);

        long matchedSequences=scalar(stmt,"select count(distinct member_seq_id) from "+match);
        if (matchedSequences!=sequenceCount)
            throw new Exception("match coverage is "+matchedSequences+" of "+sequenceCount+
                                " population sequences");

        phase(stmt,"create best work table",
            "create table "+best+" ("+
            "member_seq_id int unsigned not null primary key,"+
            "representative_astral_chain_id int unsigned not null,"+
            "representative_seq_id int unsigned not null,"+
            "representative_pct_identity double not null,"+
            "identity_provenance varchar(64) not null"+
            ") engine=InnoDB");

        phase(stmt,"rank closest representatives",
            "insert into "+best+" select member_seq_id,"+
            "representative_astral_chain_id,representative_seq_id,"+
            "representative_pct_identity,identity_provenance from (select "+
            "w.*,row_number() over (partition by w.member_seq_id order by "+
            "w.representative_pct_identity desc,round(r.aerospaci,2) desc,"+
            "r.sid collate latin1_bin asc,w.representative_astral_chain_id asc) rn "+
            "from "+match+" w join astral_chain_all_candidate r on "+
            "r.population_id="+populationID+" and "+
            "r.astral_chain_id=w.representative_astral_chain_id) ranked where rn=1");

        long bestSequences=scalar(stmt,"select count(*) from "+best);
        if (bestSequences!=sequenceCount)
            throw new Exception("best mapping is "+bestSequences+" of "+sequenceCount+
                                " population sequences");

        phase(stmt,"create member staging table",
            "create table "+stage+" like astral_chain_all_cluster_member");
        phase(stmt,"populate member staging table",
            "insert into "+stage+" (population_id,pct_identical,"+
            "member_stable_binding_id,member_astral_chain_id,"+
            "representative_stable_binding_id,representative_astral_chain_id,"+
            "member_seq_id,representative_seq_id,representative_pct_identity,"+
            "identity_provenance,assignment_version,created_at) select "+
            populationID+","+pctID+",m.stable_binding_id,m.astral_chain_id,"+
            "r.stable_binding_id,r.astral_chain_id,m.seq_id,r.seq_id,"+
            "b.representative_pct_identity,b.identity_provenance,'"+
            ASSIGNMENT_VERSION+"',now() from astral_chain_all_candidate m join "+
            best+" b on b.member_seq_id=m.seq_id join "+
            "astral_chain_all_candidate r on r.population_id=m.population_id and "+
            "r.astral_chain_id=b.representative_astral_chain_id where "+
            "m.population_id="+populationID);

        long stageCount=scalar(stmt,"select count(*) from "+stage);
        if (stageCount!=candidateCount)
            throw new Exception("staging mapping is "+stageCount+" of "+candidateCount+
                                " population candidates");
        long badSelf=scalar(stmt,"select count(*) from "+
            "astral_chain_all_subset_id s straight_join "+
            "astral_chain_all_candidate r force index(population_chain) on "+
            "r.population_id=s.population_id and "+
            "r.astral_chain_id=s.astral_chain_id straight_join "+best+
            " b on b.member_seq_id=r.seq_id where s.population_id="+
            populationID+" and s.pct_identical="+pctID+" and "+
            "(b.representative_astral_chain_id<>s.astral_chain_id or "+
            "b.representative_pct_identity<>100)");
        if (badSelf!=0)
            throw new Exception(badSelf+" representatives do not map to themselves at 100%");
        long lowIdentity=scalar(stmt,"select count(*) from "+stage+
            " where representative_pct_identity<"+pctID+
            " or representative_pct_identity>100");
        if (lowIdentity!=0)
            throw new Exception(lowIdentity+" assignments have invalid identity");
        long badExact=scalar(stmt,"select count(*) from "+stage+
            " where identity_provenance='same_seq' and "+
            "member_seq_id<>representative_seq_id");
        if (badExact!=0)
            throw new Exception(badExact+" exact-sequence assignments disagree on seq_id");

        System.out.println("validated candidates="+candidateCount+
                           " sequences="+sequenceCount+
                           " representatives="+representativeCount+
                           " assignments="+stageCount);

        LocalSQL.setAutoCommit(false);
        try {
            PreparedStatement del=LocalSQL.prepareStatement(
                "delete from astral_chain_all_cluster_member where "+
                "population_id=? and pct_identical=?");
            del.setInt(1,populationID); del.setInt(2,pctID); del.executeUpdate(); del.close();
            phase(stmt,"publish assignments",
                "insert into astral_chain_all_cluster_member select * from "+stage);
            LocalSQL.commit();
        }
        catch (Exception e) {
            LocalSQL.rollback();
            throw e;
        }
        finally {
            LocalSQL.setAutoCommit(true);
        }

        long published=scalar(stmt,"select count(*) from "+
            "astral_chain_all_cluster_member where population_id="+populationID+
            " and pct_identical="+pctID);
        if (published!=candidateCount)
            throw new Exception("published mapping is "+published+" of "+candidateCount);

        stmt.executeUpdate("drop table "+stage);
        stmt.executeUpdate("drop table "+best);
        stmt.executeUpdate("drop table "+match);
        stmt.close();
        return published;
    }

    public static void main(String[] argv) {
        try {
            if (argv.length<1 || argv.length>2)
                throw new Exception("Usage: MakeAllChainClusters population_id [pct_identity]");
            int populationID=Integer.parseInt(argv[0]);
            int pctID=(argv.length==2) ? Integer.parseInt(argv[1]) : 90;
            LocalSQL.connectRW();
            System.out.println("published "+build(populationID,pctID)+
                               " cluster-member assignments");
        }
        catch (Exception e) {
            System.err.println("Exception: "+e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
