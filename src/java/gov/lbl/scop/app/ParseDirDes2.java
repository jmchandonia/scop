package gov.lbl.scop.app;

import java.sql.*;
import java.io.*;
import java.util.*;
import org.strbio.io.*;
import org.strbio.math.*;
import org.strbio.mol.*;
import org.strbio.util.*;
import org.strbio.IO;
import gov.lbl.scop.local.LocalSQL;

/**
   Fill in SCOP node table from SCOP dir.des file;
   this just sets sunid and sccs if not already set.
*/
public class ParseDirDes2 {
    final public static void main(String argv[]) {
	try {
	    LocalSQL.connectRW();
	    Statement stmt = LocalSQL.createStatement();
	    ResultSet rs;

	    String ver = argv[0].substring(17);
	    int scopReleaseID = LocalSQL.lookupSCOPRelease(ver);
	    if (scopReleaseID==0) {
		throw new Exception("Can't determine SCOP version from "+argv[0]);
	    }

	    // create root node for this release, if not present
	    rs = stmt.executeQuery("select id from scop_node where level_id=1 and release_id="+scopReleaseID);
	    if (rs.next()) {
		int id = rs.getInt(1);
		stmt.executeUpdate("update scop_node set sunid=0 where id="+id);
	    }

	    // read dir.des from argv[0];
            BufferedReader infile = IO.openReader(argv[0]);
	    String buffer = infile.readLine();
	    while (buffer != null) {
		if (!buffer.startsWith("#")) {
		    StringTokenizer st = new StringTokenizer(buffer,"\t");
		    int sunid = StringUtil.atoi(st.nextToken());
		    String level = st.nextToken();
		    int levelID = LocalSQL.lookupLevelAbbrev(level);
		    String sccs = st.nextToken();
		    String sid = st.nextToken();
		    String quotedSid = "null";
		    String sidTest = " is null";
		    if (sid.length() > 1) {
			quotedSid = "\""+sid+"\"";
			sidTest = "="+quotedSid;
		    }
		    String description = st.nextToken();
		    description = StringUtil.replace(description,"\"","\\\"");
		    String description2 = StringUtil.replace(description," ","_");

		    boolean found = false;
		    // see if appropriate node already exists
		    rs = stmt.executeQuery("select id from scop_node where sccs=\"\" and sunid=-1 and sid"+sidTest+" and description=\""+description+"\" and level_id="+levelID+" and release_id="+scopReleaseID+" order by id limit 1");
		    if (rs.next())
			found = true;
		    else {
			rs = stmt.executeQuery("select id from scop_node where sccs=\"\" and sunid=-1 and sid"+sidTest+" and description like \""+description2+"\" and level_id="+levelID+" and release_id="+scopReleaseID+" order by id limit 1");

			if (rs.next())
			    found = true;
			else
			    System.out.println("error finding "+buffer);
		    }
		    if (found) {
			int id = rs.getInt(1);

			stmt.executeUpdate("update scop_node set sunid="+sunid+" where id="+id);
			stmt.executeUpdate("update scop_node set sccs=\""+sccs+"\" where id="+id);
		    }
		}
		buffer = infile.readLine();
	    }
	}
	catch (Exception e) {
	    System.out.println("Exception: "+e.getMessage());
	    e.printStackTrace();
	}
    }
}
