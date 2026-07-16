package gov.lbl.scop.app;

import java.sql.*;
import java.io.*;
import java.util.*;
import org.strbio.io.*;
import org.strbio.local.*;
import org.strbio.math.*;
import org.strbio.mol.*;
import org.strbio.util.*;
import org.strbio.IO;
import gov.lbl.scop.util.*;
import gov.lbl.scop.local.LocalSQL;

/**
   Uses PyMol to display all ASTEROIDS for a given ASTRAL chain
*/
public class ViewASTEROIDS {
    /**
       View thumbnail files for an ASTRAL chain.
    */
    final public static void viewASTEROIDS(int astralChainID) throws Exception {
	int scopReleaseID = LocalSQL.getLatestSCOPRelease(true);
	String scopRelease = LocalSQL.lookupSCOPRelease(scopReleaseID);
	
	System.out.println("Viewing ASTEROIDS for ASTRAL chain "+astralChainID+"\n");
	
	Statement stmt = LocalSQL.createStatement();
	Statement stmt2 = LocalSQL.createStatement();
	ResultSet rs, rs2;

	rs = stmt.executeQuery("select l.pdb_path, r.line from astral_chain ac, raf r, pdb_chain pc, pdb_local l where l.pdb_release_id=pc.pdb_release_id and r.pdb_chain_id=pc.id and r.id=ac.raf_id and ac.id="+astralChainID);
	rs.next();
	String pdbPath = rs.getString(1);
	String rafLine = rs.getString(2);
	System.out.println(rafLine+"\n");
	rs.close();
	
	Vector<String> descriptions = new Vector<String>();
	rs = stmt.executeQuery("select sid, header, description, blast_hit_id from asteroid where chain_id="+astralChainID+" and scop_release_id="+scopReleaseID+" and sid like 'u%'");
	while (rs.next()) {
	    String sid = rs.getString(1);
	    String header = rs.getString(2);
	    String description = rs.getString(3);
	    int hitID = rs.getInt(4);
	    String chainSid = sid.substring(1,5);

	    descriptions.add(description);

	    System.out.println(sid+"\n "+header+"\n "+description);

	    int pos = header.indexOf("BLAST-");
	    if ((pos > -1) && (hitID > 0)) {
		String hitSid = header.substring(pos+6, pos+13);
		System.out.println("http://strgen.org/~jmc/scop-newui/?sid="+hitSid);
		rs2 = stmt2.executeQuery("select description from scop_node where sid=\""+hitSid+"\" and release_id="+scopReleaseID);
		rs2.next();
		System.out.println(hitSid+": "+rs2.getString(1));
		rs2.close();

		// show BLAST results
		rs2 = stmt2.executeQuery("select seq1_id, seq2_id from astral_seq_blast where id="+hitID);
		rs2.next();
		String chainSeqID = ""+rs2.getInt(1);
		String domainSeqID = ""+rs2.getInt(2);
		rs2.close();

		int l = chainSeqID.length();
		String hash = chainSeqID.substring(l-2,l);
		String blastFileName = "/lab/proj/astral/blast/"+scopRelease+"/chain_domain/seqres/seqs/"+hash+"/"+chainSeqID+".bla.gz";
		System.out.println("Relevant BLAST results from "+blastFileName);
		System.out.println("(Query is "+chainSid+", Hit is "+hitSid+", aka "+domainSeqID+")");
		BufferedReader infile = IO.openReader(blastFileName);
		if (infile==null)
		    throw new Exception("failed to open BLAST output");

		boolean found = false;
		boolean printMe = false;
		String buffer;
		while ((buffer=infile.readLine()) != null) {
		    if (buffer.startsWith(">"+domainSeqID)) {
			printMe = true;
			found = true;
		    }
		    if (printMe) {
			if (buffer.startsWith(">") &&
			    (!buffer.startsWith(">"+domainSeqID)))
			    printMe = false;
			else
			    System.out.println(buffer);
		    }
		}
		if (!found)
		    System.out.println("Domain "+domainSeqID+" not found in file!");
	    }
	    else {
		System.out.println("No BLAST hit in this domain.\n");
	    }
	}
	rs.close();
	stmt.close();

	if (descriptions.size()==0) {
	    System.out.println("No ASTEROIDS found for chain "+astralChainID);
	    return;
	}

	File f = File.createTempFile("structure",".ent");
	f.delete();

	// deal with bundles
	File unBundled = null;
	if (pdbPath.endsWith(".pdb-bundle.tar.gz")) {
	    unBundled = ParsePDBXML.unBundle(pdbPath);
	    pdbPath = unBundled.getAbsolutePath();
	}

	HashMap<String,String> mm = Thumbnail.mungePDB(pdbPath,
						       f.getPath(),
						       null);

	if (unBundled != null)
	    unBundled.delete();
	
	if (mm==null) {
	    f.delete();
	    throw new Exception("Couldn't find any atoms for "+pdbPath);
	}

	File scriptFile = File.createTempFile("pml",".pml");
	scriptFile.delete();

	Thumbnail.writePymolScript(scriptFile,
				   f.getPath(),
				   0.0,
				   descriptions.toArray(new String[descriptions.size()]),
				   mm,
				   false,
				   false);

	PrintfWriter outfile = new PrintfWriter(scriptFile.getPath(),
						true);
	outfile.printf("set seq_view,1\n");
	outfile.printf("cmd.bg_color('grey80')\n");
	outfile.close();

	Program pymol = new Program("/usr/bin/pymol");
	String[] args = new String[1];
	args[0] = scriptFile.getPath();
	pymol.run(args);

	f.delete();
	scriptFile.delete();
    }
    
    final public static void main(String argv[]) {
	try {
	    LocalSQL.connectRW();
	    Statement stmt = LocalSQL.createStatement();
	    ResultSet rs;

	    int astralChainID = 0;

	    if (argv[0].length()==5) {
		int scopReleaseID = LocalSQL.getLatestSCOPRelease(true);
		rs = stmt.executeQuery("select c.id from astral_chain c, asteroid a where c.sid=\""+argv[0]+"\" and a.chain_id=c.id and a.scop_release_id="+scopReleaseID+" order by c.id desc limit 1");
		if (rs.next())
		    astralChainID = rs.getInt(1);
		rs.close();
	    }
	    if (astralChainID==0)
		astralChainID = StringUtil.atoi(argv[0]);
	    
	    viewASTEROIDS(astralChainID);
	    stmt.close();
	}
	catch (Exception e) {
	    System.out.println("Exception: "+e.getMessage());
	    e.printStackTrace();
	}
    }
}
