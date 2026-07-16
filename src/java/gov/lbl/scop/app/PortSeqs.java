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
import gov.lbl.scop.local.*;

/**
   Port sequence data to new schema
*/
public class PortSeqs {
    final public static void main(String argv[]) {
	try {
	    LocalSQL.connectRW();
	    Statement stmt = LocalSQL.createStatement();
	    Statement stmt2 = LocalSQL.createStatement();
	    ResultSet rs, rs2;
	    boolean found = true;

	    while (found) {
		found = false;
		rs = stmt.executeQuery("select id, seq, is_reject from astral_domain where seq_id=0 limit 100");
		while (rs.next()) {
		    found = true;
		    int id = rs.getInt(1);
		    String seq = rs.getString(2);
		    int oldIR = rs.getInt(3);

		    int seqID = MakeDomainSeq.lookupOrCreateSeq(seq);

		    stmt2.executeUpdate("update astral_domain set seq_id="+seqID+" where id="+id);

		    rs2 = stmt2.executeQuery("select is_reject from astral_seq where id="+seqID);
		    rs2.next();
		    int newIR = rs2.getInt(1);

		    if (oldIR != newIR)
			throw new Exception("Error - check IR for domain id "+id+" seq "+seqID);
		}
	    }

	    found = true;
	    while (found) {
		found = false;
		rs = stmt.executeQuery("select id, seq, is_reject from astral_chain where seq_id=0 limit 100");
		while (rs.next()) {
		    found = true;
		    int id = rs.getInt(1);
		    String seq = rs.getString(2);
		    int oldIR = rs.getInt(3);

		    int seqID = MakeDomainSeq.lookupOrCreateSeq(seq);

		    stmt2.executeUpdate("update astral_chain set seq_id="+seqID+" where id="+id);

		    rs2 = stmt2.executeQuery("select is_reject from astral_seq where id="+seqID);
		    rs2.next();
		    int newIR = rs2.getInt(1);

		    if (oldIR != newIR)
			throw new Exception("Error - check IR for chain id "+id+" seq "+seqID);
		}
	    }

	    found = true;
	    while (found) {
		found = false;
		rs = stmt.executeQuery("select id, seq, is_reject from asteroid where seq_id=0 limit 100");
		while (rs.next()) {
		    found = true;
		    int id = rs.getInt(1);
		    String seq = rs.getString(2);
		    int oldIR = rs.getInt(3);

		    int seqID = MakeDomainSeq.lookupOrCreateSeq(seq);

		    stmt2.executeUpdate("update asteroid set seq_id="+seqID+" where id="+id);

		    rs2 = stmt2.executeQuery("select is_reject from astral_seq where id="+seqID);
		    rs2.next();
		    int newIR = rs2.getInt(1);

		    if (oldIR != newIR)
			throw new Exception("Error - check IR for asteroid id "+id+" seq "+seqID);
		}
	    }
	}
	catch (Exception e) {
	    System.out.println("Exception: "+e.getMessage());
	    e.printStackTrace();
	}
    }
}
