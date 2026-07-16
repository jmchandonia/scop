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
import gov.lbl.scop.util.*;

/**
   Makes chain sequences of type 3 for all RAF entries that don't already have
   them, and were used prior to 1.65
*/
public class MakeChainSeq2 {
    final public static void main(String argv[]) {
	try {
	    LocalSQL.connectRW();
	    Statement stmt = LocalSQL.createStatement();
	    Statement stmt2 = LocalSQL.createStatement();

	    BufferedReader infile = null;

	    boolean done = false;
	    while (!done) {
		// pick one entry at a time to work on
		ResultSet rs = stmt.executeQuery("select id from raf where (first_release_id<6 or last_release_id<6) and id not in (select raf_id from astral_chain where source_id=3) limit 1");
		int rafID = 0;
		int astralChainID = 0;
		if (rs.next()) {
		    rafID = rs.getInt(1);
		    try {
			stmt.executeUpdate("insert into astral_chain values (null,"+rafID+",3,\"\",\"\",1)",
					   Statement.RETURN_GENERATED_KEYS);
			rs = stmt.getGeneratedKeys();
			if (rs.next())
			    astralChainID = rs.getInt(1);
			else
			    rafID = 0; // parallel process already claimed it
		    }
		    catch (Exception e2) {
			rafID = 0;
		    }
		}
		else {
		    done = true;
		    break;
		}

		if (rafID==0)
		    continue;

		rs = stmt.executeQuery("select e.code, c.chain, raf_get_body(r.id) from raf r, pdb_chain c, pdb_entry e, pdb_release re where r.id="+rafID+" and r.pdb_chain_id=c.id and c.pdb_release_id=re.id and re.pdb_entry_id=e.id");
		rs.next();
		String code = rs.getString(1);
		char chain = rs.getString(2).charAt(0);
		String body = rs.getString(3);

		String sid = code;
		if (chain==' ')
		    sid+='_';
		else
		    sid+=chain;

		System.out.println("working on "+sid);
		System.out.flush();

		RAF.SequenceFragment sf = RAF.wholeChainSeq(body,3);
		String seq = sf.getSequence();
		int isReject = 0;
		if (sf.isReject()) isReject = 1;
		
		stmt.executeUpdate("update astral_chain set sid=\""+
				   StringUtil.replace(sid,"\"","\\\"")+
				   "\",seq=\""+
				   seq+
				   "\",is_reject="+
				   isReject+
				   " where id="+astralChainID);
	    }
	}
	catch (Exception e) {
	    System.out.println("Exception: "+e.getMessage());
	    e.printStackTrace();
	}
    }
}
