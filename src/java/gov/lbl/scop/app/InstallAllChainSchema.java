/*
 * Software to build and maintain SCOPe, https://scop.berkeley.edu/
 */
package gov.lbl.scop.app;

import gov.lbl.scop.local.LocalSQL;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;

/** Install and verify the additive all-chain schema on the configured DB. */
public class InstallAllChainSchema {
    public static void install(Path sqlFile) throws Exception {
        String raw=new String(Files.readAllBytes(sqlFile),StandardCharsets.UTF_8);
        StringBuilder cleaned=new StringBuilder();
        for (String line:raw.split("\\r?\\n"))
            if (!line.trim().startsWith("--")) cleaned.append(line).append('\n');
        Statement stmt=LocalSQL.createStatement();
        for (String sql:cleaned.toString().split(";")) {
            sql=sql.trim();
            if (sql.length()>0) stmt.executeUpdate(sql);
        }

        ResultSet rs=stmt.executeQuery("select count(*) from (select pdb_entry_id,"+
            "release_id,count(distinct concat_ws('|',coalesce(aerospaci,'N'),spaci,"+
            "method_summary,effective_resolution,effective_r_factor,summary_whatcheck,"+
            "summary_procheck,coalesce(resolution,'N'),coalesce(r_factor,'N'),"+
            "coalesce(whatcheck1,'N'),coalesce(whatcheck2,'N'),coalesce(whatcheck3,'N'),"+
            "coalesce(whatcheck4,'N'),coalesce(procheck1,'N'),coalesce(procheck2,'N'),"+
            "coalesce(procheck3,'N'))) variants from aerospaci group by pdb_entry_id,"+
            "release_id having count(*)>1 and variants>1) d");
        rs.next(); int conflicts=rs.getInt(1); rs.close();
        if (conflicts!=0) {
            stmt.close();
            throw new Exception("not adding AEROSPACI uniqueness: "+conflicts+
                                " conflicting entry/release groups require review");
        }
        // Exact duplicate rows are redundant, have no inbound foreign keys,
        // and do not alter any public value.  Retain the lowest historical ID.
        int removed=stmt.executeUpdate("delete newer from aerospaci older join " +
            "aerospaci newer on newer.pdb_entry_id=older.pdb_entry_id and " +
            "newer.release_id=older.release_id and newer.id>older.id and " +
            "newer.aerospaci<=>older.aerospaci and newer.spaci<=>older.spaci and " +
            "newer.method_summary<=>older.method_summary and " +
            "newer.effective_resolution<=>older.effective_resolution and " +
            "newer.effective_r_factor<=>older.effective_r_factor and " +
            "newer.summary_whatcheck<=>older.summary_whatcheck and " +
            "newer.summary_procheck<=>older.summary_procheck and " +
            "newer.resolution<=>older.resolution and newer.r_factor<=>older.r_factor and " +
            "newer.whatcheck1<=>older.whatcheck1 and newer.whatcheck2<=>older.whatcheck2 and " +
            "newer.whatcheck3<=>older.whatcheck3 and newer.whatcheck4<=>older.whatcheck4 and " +
            "newer.procheck1<=>older.procheck1 and newer.procheck2<=>older.procheck2 and " +
            "newer.procheck3<=>older.procheck3");
        if (removed>0)
            System.out.println("removed "+removed+" exact duplicate AEROSPACI rows");
        rs=stmt.executeQuery("select count(*) from information_schema.statistics " +
            "where table_schema=database() and table_name='aerospaci' " +
            "and index_name='aerospaci_entry_release_unique'");
        rs.next(); boolean hasUnique=rs.getInt(1)>0; rs.close();
        if (!hasUnique)
            stmt.executeUpdate("alter table aerospaci add unique key " +
                               "aerospaci_entry_release_unique(pdb_entry_id,release_id)");
        stmt.close();
    }

    public static void main(String[] argv) {
        try {
            if (argv.length!=1) throw new Exception("Usage: InstallAllChainSchema sql-file");
            LocalSQL.connectRW();
            install(Paths.get(argv[0]));
            Statement stmt=LocalSQL.createStatement();
            ResultSet rs=stmt.executeQuery("select database(),@@hostname"); rs.next();
            System.out.println("installed all-chain schema in "+rs.getString(1)+
                               " on "+rs.getString(2));
            rs.close(); stmt.close();
        }
        catch (Exception e) {
            System.err.println("Exception: "+e.getMessage()); e.printStackTrace(); System.exit(1);
        }
    }
}
