package gov.lbl.scop.app;

import java.sql.*;
import java.io.*;
import java.util.*;
import java.text.*;
import org.strbio.io.*;
import org.strbio.local.*;
import org.strbio.math.*;
import org.strbio.mol.*;
import org.strbio.util.*;
import org.strbio.IO;
import gov.lbl.scop.util.*;
import gov.lbl.scop.local.LocalSQL;

/**
   Back up old SPACI scores, then recalculate spaci for proteins that
   don't have one.
*/
public class RecalcSPACI {
    final public static void main(String argv[]) {
	try {
	    LocalSQL.connectRW();
	    Statement stmt = LocalSQL.createStatement();
	    Statement stmt2 = LocalSQL.createStatement();

	    BufferedReader infile = null;

	    /*
	    ResultSet rs = stmt.executeQuery("select id from pdb_release where has_valid_procheck=0 or has_valid_whatcheck=0");
	    while (rs.next()) {
		int id = rs.getInt(1);
		// backup old values
		stmt2.executeUpdate("insert into tmp_pdb_release(select * from pdb_release where id="+id+")");
		LocalSQL.newJob(2,id,null,stmt2);
	    }
	    */

	    ResultSet rs = stmt.executeQuery("select id from pdb_release");
	    while (rs.next()) {
		int id = rs.getInt(1);
		LocalSQL.newJob(5,id,null,stmt2);
	    }
	}
	catch (Exception e) {
	    System.out.println("Exception: "+e.getMessage());
	    e.printStackTrace();
	}
    }
}
