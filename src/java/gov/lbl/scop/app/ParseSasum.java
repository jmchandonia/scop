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
   Parses files in sum, sum-gd directories to extract comparison
   info.

   Can either import or compare (i.e., show differences with
   database version)
*/
public class ParseSasum {
    private static int scopReleaseID = 0;
    private static int sourceID = 0;
    private static boolean isChain = false;
    private static int styleID = 0;
    private static boolean DEBUG = false;

    // lookup sequence ids in the cache, or from the db
    private static HashMap<String,Integer> cacheIDs = null;
    final public static int findDomain(String sid) throws Exception {
	Integer id = cacheIDs.get(sid);
	int rv = 0;
	if (id != null)
	    rv = id.intValue();
	else {
	    Statement stmt = LocalSQL.createStatement();
	    ResultSet rs;
	    if (isChain) {
		rv = LocalSQL.findASTRALChain(sid,scopReleaseID,sourceID);
		rs = stmt.executeQuery("select seq_id from astral_chain where id="+rv);
	    }
	    else {
		rv = LocalSQL.findASTRALDomain(sid,scopReleaseID,sourceID);
		rs = stmt.executeQuery("select seq_id from astral_domain where id="+rv);
	    }
	    rs.next();
	    rv = rs.getInt(1);
	    rs.close();
	    stmt.close();
	    cacheIDs.put(sid, new Integer(rv));
	}
	return rv;
    }

    final public static void main(String argv[]) {
	try {
	    LocalSQL.connectRW();
	    Statement stmt = LocalSQL.createStatement();
	    ResultSet rs;

	    cacheIDs = new HashMap<String,Integer>();

            if (argv.length != 2) {
		System.out.println("syntax:  ParseSasum (file) (import|compare)");
		System.exit(1);
	    }
	    
	    // figure out what release we're parsing
	    int pos = argv[0].lastIndexOf('-');
	    String ver = argv[0].substring(pos+1,pos+5);
	    scopReleaseID = LocalSQL.lookupSCOPRelease(ver);
	    if (scopReleaseID==0) {
		throw new Exception("Can't determine SCOP version from "+argv[0]);
	    }

	    // is it bg or bib type summary?
	    boolean isBG = false;
	    if (argv[0].indexOf(".bg.")>-1)
		isBG = true;

	    // domains or chains?	    
	    isChain = true;
	    if ((argv[0].indexOf("-scopdom-")>-1) ||
		(argv[0].indexOf("-scopedom-")>-1))
		isChain = false;

	    // comparing or importing?
	    boolean compare = false;
	    boolean init = false;
	    if (argv[1].equals("compare"))
		compare = true;
	    else if (argv[1].equals("init"))
		init = true;
	    else if (!argv[1].equals("import")) {
		System.out.println("syntax:  ParseSasum (file) (import|compare|init)");
		System.exit(1);
	    }

	    // sasum always done on seqres, not atom
	    sourceID = 2;
	    if ((scopReleaseID < 6) && (isChain))
		sourceID = 3;

	    // style can matter for non-chain sasum
	    if (isChain)
		styleID = 1;
	    else {
		styleID = 2;  // original style
		if (argv[0].indexOf("-gd-")>-1)
		    styleID = 3;
	    }

	    // initialize?  Needs to be done once per release
	    // for both chains and domains	 
	    if (init)
		stmt.executeUpdate("delete from astral_seq_blast where release_id="+scopReleaseID+" and source_id="+sourceID+" and style1_id="+styleID+" and style2_id=style1_id");
	    
	    // get summary file from argv[0]
            BufferedReader infile = IO.openReader(argv[0]);

	    int lastID1 = 0;

	    loop:
            while (infile.ready()) {
		String buffer = infile.readLine();

		StringTokenizer st = new StringTokenizer(buffer);
		try {
		    String sid = st.nextToken();
		    int id1 = findDomain(sid);
		    sid = st.nextToken();
		    int id2 = findDomain(sid);

		    // searches are grouped together in files,
		    // and we don't need to remember searches
		    // for older queries
		    lastID1 = id1;

		    double pctID = 0.0;
		    double log10E = 0.0;
		    int start1 = 0;
		    int start2 = 0;
		    int length1 = 0;
		    int length2 = 0;

		    // get other info depending on file type
		    if (isBG) {
			String evalue = st.nextToken();
			int e = evalue.indexOf("e-");
			if (e==-1) {
			    // for example, Expect = 0.001
			    log10E = StringUtil.atod(evalue);
			    if (log10E==0.0)
				log10E = -9999.0;
			    else
				log10E = Math.log(log10E) / Math.log(10.0);
			}
			else {
			    // for example, '9e-20' or 'e-100'
			    log10E = (double)StringUtil.atoi(evalue,e+1);
			    double coef = StringUtil.atod(evalue,0,e);
			    if (coef > 0)
				log10E += 
				    Math.log(coef) / Math.log(10.0);
			}

			st.nextToken();

			start1 = StringUtil.atoi(st.nextToken())-1;
			length1 = StringUtil.atoi(st.nextToken()) - start1;
			start2 = StringUtil.atoi(st.nextToken())-1;
			length2 = StringUtil.atoi(st.nextToken()) - start2;
		    }
		    else {
			pctID = StringUtil.atod(st.nextToken());
			st.nextToken();
		    }

		    if ((id1 != 0) && (id2 != 0)) {
			Vector<Integer> ids = new Vector<Integer>();
			int sameRegionID = 0;
			double log10EDB = 0.0;
			double pctIDDB = 0.0;
			rs = stmt.executeQuery("select id, blast_log10_e, pct_identical, seq1_start, seq1_length, seq2_start, seq2_length from astral_seq_blast where seq1_id="+id1+" and seq2_id="+id2+" and release_id="+scopReleaseID+" and source_id="+sourceID+" and style1_id="+styleID+" and style2_id=style1_id");
			while (rs.next()) {
			    int id = rs.getInt(1);
			    int start1DB = rs.getInt(4);
			    int length1DB = rs.getInt(5);
			    int start2DB = rs.getInt(6);
			    int length2DB = rs.getInt(7);

			    ids.add(new Integer(id));
			    if (isBG) {
				if ((start1 == start1DB) &&
				    (length1 == length1DB) &&
				    (start2 == start2DB) &&
				    (length2 == length2DB)) {
				    sameRegionID = id;
				    log10EDB = rs.getDouble(2);
				}
			    }
			    else {
				sameRegionID = id;
				pctIDDB = rs.getDouble(3);
			    }
			}
			rs.close();

			if (DEBUG) {
			    System.err.println("parsing "+buffer);
			    System.err.println("found ids: "+ids);
			    System.err.println("found same region: "+sameRegionID);
			}

			boolean makeNewRecord = false;
			// if no matching records, create new one:
			if (sameRegionID == 0) {
			    // new record if not just comparing
			    makeNewRecord = true;
			    if (compare) {
				if (isBG)
				    System.out.println("MISSINGE "+buffer);
				else
				    System.out.println("MISSINGID "+buffer);
			    }
			}
			else {
			    // compare to old data
			    if (isBG) {
				if (log10E != 0.0) {
				    long l1 = Math.round(log10E * 1000000.0);
				    long l2 = Math.round(log10EDB * 1000000.0);
				    if (l1 != l2) {
					if (log10EDB != 0.0)
					    makeNewRecord = true;
					if (compare) {
					    if (l2==0)
						System.out.println("MISSINGE "+buffer);
					    else
						System.out.println("WRONGE "+l1+" "+l2+" "+buffer);
					}
				    }
				}
			    }
			    else {
				if (pctID != 0.0) {
				    long l1 = Math.round(pctID * 100.0);
				    long l2 = Math.round(pctIDDB * 100.0);
				    if (l1 != l2) {
					if (pctIDDB != 0.0)
					    makeNewRecord = true;
					if (compare) {
					    if (l2==0)
						System.out.println("MISSINGID "+buffer);
					    else {
						System.out.println("WRONGID "+l1+" "+l2+" "+buffer);
					    }
					}
				    }
				}
			    }
			}
			if (compare) // all comparison is done above
			    continue loop;

			// don't need new record if we have
			// one that we can overwrite
			if (sameRegionID != 0)
			    makeNewRecord = false;

			if (makeNewRecord) {
			    stmt.executeUpdate("insert into astral_seq_blast values (NULL, "+
					       id1+", "+
					       id2+", "+
					       sourceID+", "+
					       styleID+", "+
					       styleID+", "+
					       scopReleaseID+", "+
					       log10E+", "+
					       pctID+", "+
					       start1+", "+
					       length1+", "+
					       start2+", "+
					       length2+")");
			    continue loop;
			}
			
			if (isBG)
			    stmt.executeUpdate("update astral_seq_blast set blast_log10_e="+log10E+" where id="+sameRegionID);
			else {
			    for (Integer myID : ids)
				stmt.executeUpdate("update astral_seq_blast set pct_identical="+pctID+" where id="+myID);
			}
		    }
		    else {
			System.out.println("skipping(2) line: '"+buffer+"'");
		    }
		}
		catch (NoSuchElementException nse) {
		    System.out.println("skipping(1) line: '"+buffer+"'");
		}
	    }
	}
	catch (Exception e) {
	    System.out.println("Exception: "+e.getMessage());
	    e.printStackTrace();
	}
    }
}
