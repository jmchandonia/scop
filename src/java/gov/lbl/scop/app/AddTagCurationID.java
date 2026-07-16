package gov.lbl.scop.app;

import java.sql.*;
import java.lang.*;
import gov.lbl.scop.local.LocalSQL;
import gov.lbl.scop.util.RAF;


/*
These functions will add a new row to scop_curation_type for
the new tag identification method and add a new column to pdb_chain_tag
to keep track of which method was used for identifying a given tag.
This will also remove the foreign key constraint on pdb_chain_diff_id
in pdb_chain_tag.
*/
public class AddTagCurationID {
    public static void addRowToCurationType() throws SQLException {
        try {
            Statement stmt = LocalSQL.createStatement();
            stmt.executeUpdate("insert into scop_curation_type values (8, 'Automated tag identification method introduced in 2.08');");
        } catch (Exception e) {
            System.out.println("Exception: " + e.getMessage());
            e.printStackTrace();
        }
        
    }

    public static void addTypeColumn() throws SQLException {
        try {
            Statement stmt = LocalSQL.createStatement();
            stmt.executeUpdate("alter table pdb_chain_tag add curation_type_id int(20) default null;");

            Statement stmt_set_all = LocalSQL.createStatement();
            stmt_set_all.executeUpdate("update pdb_chain_tag set curation_type_id = 7 where 1 = 1;");
        } catch (Exception e) {
            System.out.println("Exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void removeForeignKey() throws SQLException {
        try {
            Statement stmt = LocalSQL.createStatement();
            stmt.executeUpdate("alter table pdb_chain_tag drop foreign key pdb_chain_tag_ibfk_2;");

            Statement stmt_default_null = LocalSQL.createStatement();
            stmt_default_null.executeUpdate("alter table pdb_chain_tag modify pdb_chain_diff_id int default null;");
            
        } catch (Exception e) {
            System.out.println("Exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            LocalSQL.connectRW();
            addRowToCurationType();
            addTypeColumn();
            removeForeignKey();
        } catch (Exception e) {
            System.out.println("Exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}