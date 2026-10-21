/*
 * Software to build and maintain SCOPe, https://scop.berkeley.edu/
 */
package gov.lbl.scop.app;

import gov.lbl.scop.local.LocalSQL;

import java.sql.*;

/**
 * Staged shadow-table rebuild for retained chain/domain FATCAT hits.  Nothing
 * is renamed unless the explicit swap command and confirmation token are used.
 */
public class PruneChainFatcat {
    public static final String RETAIN =
        "p_val is not null and p_val<=0.05 and ini_rmsd is not null and ini_rmsd<4";

    private static long scalar(Statement stmt, String sql) throws Exception {
        ResultSet rs=stmt.executeQuery(sql); rs.next(); long n=rs.getLong(1);
        rs.close(); return n;
    }

    public static void report() throws Exception {
        Statement stmt=LocalSQL.createStatement();
        long all=scalar(stmt,"select count(*) from scop_node_fatcat_chain");
        long keep=scalar(stmt,"select count(*) from scop_node_fatcat_chain where "+RETAIN);
        long dup=scalar(stmt,"select count(*) from (select chain_id,domain_id from " +
            "scop_node_fatcat_chain group by chain_id,domain_id having count(*)>1) d");
        System.out.println("parent_rows\t"+all);
        System.out.println("retained_rows\t"+keep);
        System.out.println("discarded_rows\t"+(all-keep));
        System.out.println("duplicate_pair_groups\t"+dup);
        stmt.close();
    }

    public static void auditDuplicates() throws Exception {
        Statement stmt=LocalSQL.createStatement();
        stmt.executeUpdate("create table if not exists scop_node_fatcat_chain_duplicate_audit " +
            "(chain_id int unsigned,domain_id int unsigned,row_count int unsigned,"+
            "parent_variant_count int unsigned,min_id int unsigned,max_id int unsigned,"+
            "audited_at datetime not null,primary key(chain_id,domain_id)) engine=InnoDB");
        stmt.executeUpdate("insert into scop_node_fatcat_chain_duplicate_audit " +
            "select chain_id,domain_id,count(*),count(distinct md5(concat_ws('|',"+
            "coalesce(chain_len,'N'),coalesce(domain_len,'N'),coalesce(ini_len,'N'),"+
            "coalesce(ini_rmsd,'N'),coalesce(opt_equ,'N'),coalesce(opt_rmsd,'N'),"+
            "coalesce(chain_rmsd,'N'),coalesce(score,'N'),coalesce(align_len,'N'),"+
            "coalesce(gaps,'N'),coalesce(p_val,'N'),coalesce(afp_num,'N'),"+
            "coalesce(pct_identical,'N'),coalesce(similarity,'N'),coalesce(chain_start,'N'),"+
            "coalesce(chain_length,'N'),coalesce(domain_start,'N'),coalesce(domain_length,'N')))),"+
            "min(id),max(id),now() from scop_node_fatcat_chain group by chain_id,domain_id " +
            "having count(*)>1 on duplicate key update row_count=values(row_count),"+
            "parent_variant_count=values(parent_variant_count),min_id=values(min_id),"+
            "max_id=values(max_id),audited_at=values(audited_at)");
        stmt.close();
    }

    private static boolean tableExists(Statement stmt,String name) throws Exception {
        ResultSet rs=stmt.executeQuery("show tables like '"+name+"'");
        boolean yes=rs.next(); rs.close(); return yes;
    }

    public static void buildShadow() throws Exception {
        Statement stmt=LocalSQL.createStatement();
        long dup=scalar(stmt,"select count(*) from (select chain_id,domain_id from " +
            "scop_node_fatcat_chain group by chain_id,domain_id having count(*)>1) d");
        if (dup!=0) throw new Exception(dup+" duplicate pair groups remain; audit and resolve before rebuilding");
        String[] tables={"scop_node_fatcat_chain_new","scop_node_fatcat_chain_gap_new",
                         "scop_node_fatcat_chain_matrix_new"};
        for (String table:tables)
            if (tableExists(stmt,table)) throw new Exception(table+" already exists");
        stmt.executeUpdate("create table scop_node_fatcat_chain_new like scop_node_fatcat_chain");
        stmt.executeUpdate("create table scop_node_fatcat_chain_gap_new like scop_node_fatcat_chain_gap");
        stmt.executeUpdate("create table scop_node_fatcat_chain_matrix_new like scop_node_fatcat_chain_matrix");
        stmt.executeUpdate("insert into scop_node_fatcat_chain_new select * from " +
                           "scop_node_fatcat_chain where "+RETAIN);
        stmt.executeUpdate("insert into scop_node_fatcat_chain_gap_new select g.* from " +
            "scop_node_fatcat_chain_gap g join scop_node_fatcat_chain_new h on h.id=g.hit_id");
        stmt.executeUpdate("insert into scop_node_fatcat_chain_matrix_new select m.* from " +
            "scop_node_fatcat_chain_matrix m join scop_node_fatcat_chain_new h on h.id=m.hit_id");
        stmt.executeUpdate("alter table scop_node_fatcat_chain_new add unique key pair_once(chain_id,domain_id)");
        stmt.executeUpdate("alter table scop_node_fatcat_chain_gap_new add constraint " +
            "fk_snfccg_new_hit foreign key(hit_id) references scop_node_fatcat_chain_new(id) " +
            "on delete cascade on update cascade");
        stmt.executeUpdate("alter table scop_node_fatcat_chain_matrix_new add constraint " +
            "fk_snfccm_new_hit foreign key(hit_id) references scop_node_fatcat_chain_new(id) " +
            "on delete cascade on update cascade");
        stmt.close();
    }

    public static void validateShadow() throws Exception {
        Statement stmt=LocalSQL.createStatement();
        long expected=scalar(stmt,"select count(*) from scop_node_fatcat_chain where "+RETAIN);
        long found=scalar(stmt,"select count(*) from scop_node_fatcat_chain_new");
        long bad=scalar(stmt,"select count(*) from scop_node_fatcat_chain_new where not("+RETAIN+")");
        long gapOrphans=scalar(stmt,"select count(*) from scop_node_fatcat_chain_gap_new g " +
            "left join scop_node_fatcat_chain_new h on h.id=g.hit_id where h.id is null");
        long matrixOrphans=scalar(stmt,"select count(*) from scop_node_fatcat_chain_matrix_new m " +
            "left join scop_node_fatcat_chain_new h on h.id=m.hit_id where h.id is null");
        stmt.close();
        if (expected!=found || bad!=0 || gapOrphans!=0 || matrixOrphans!=0)
            throw new Exception("shadow validation failed expected="+expected+" found="+found+
                " bad="+bad+" gap_orphans="+gapOrphans+" matrix_orphans="+matrixOrphans);
        System.out.println("validated retained parent rows: "+found);
    }

    public static void swap(String token) throws Exception {
        throw new Exception("Java swap is disabled; use the audited " +
            "scripts/swap_chain_fatcat_tables.sh preflight/swap workflow");
    }

    public static void main(String[] argv) {
        try {
            if (argv.length<1) throw new Exception("Usage: PruneChainFatcat report|audit-duplicates|build-shadow|validate-shadow|swap [token]");
            LocalSQL.connectRW();
            if (argv[0].equals("report")) report();
            else if (argv[0].equals("audit-duplicates")) auditDuplicates();
            else if (argv[0].equals("build-shadow")) buildShadow();
            else if (argv[0].equals("validate-shadow")) validateShadow();
            else if (argv[0].equals("swap")) swap(argv.length>1?argv[1]:"");
            else throw new Exception("unknown command: "+argv[0]);
        }
        catch (Exception e) {
            System.err.println("Exception: "+e.getMessage()); e.printStackTrace(); System.exit(1);
        }
    }
}
