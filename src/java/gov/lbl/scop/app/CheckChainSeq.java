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
   Check whether calculated chain seqs match FASTA files
*/
public class CheckChainSeq {
    final public static void main(String argv[]) {
	try {
	    LocalSQL.connectRW();
	    Statement stmt = LocalSQL.createStatement();

	    BufferedReader infile = null;

	    int pos = argv[0].lastIndexOf('-');
	    String ver = argv[0].substring(pos+1,pos+5);
	    int scopReleaseID = LocalSQL.lookupSCOPRelease(ver);
	    if (scopReleaseID==0) {
		throw new Exception("Can't determine SCOP version from "+argv[0]);
	    }

	    int seqType = 2;
	    if (argv[0].indexOf("-atom")>-1)
		seqType = 1;

	    if ((seqType==2) && (scopReleaseID < 6))
		seqType = 3;

            BufferedReader proteins = IO.openReader(argv[0]);
            PolymerSet ps = new ProteinSet();
            Enumeration pe = ps.polymersInFile(proteins, null);
            while (pe.hasMoreElements()) {
                Polymer p;
                p = (Polymer)pe.nextElement();

		if (p==null)
		    continue;

		String sid = p.name.substring(0,5);
		String code = sid.substring(0,4);
		char chain = sid.charAt(4);
		if (chain=='_')
		    chain = ' ';
		String seq = p.sequence().toLowerCase();
		String seqND = StringUtil.replace(seq,".","");

		// look up corresponding entry in db
		ResultSet rs = stmt.executeQuery("select c.sid, c.seq from astral_chain c, raf r, pdb_chain ch, pdb_release re, pdb_entry e where source_id="+seqType+" and c.raf_id=r.id and r.first_release_id<="+scopReleaseID+" and r.last_release_id>="+scopReleaseID+" and r.pdb_chain_id=ch.id and ch.chain=\""+chain+"\" and ch.pdb_release_id=re.id and re.pdb_entry_id=e.id and e.code=\""+code+"\"");
		if (rs.next()) {
		    String sid2 = rs.getString(1);
		    String seq2 = rs.getString(2);

		    if (!sid.equals(sid2))
			System.out.println("SID: "+sid+" "+sid2);
		    if (!seq.equals(seq2)) {
			if (seqND.equals(seq2)) {
			    System.out.println("DOTS: "+sid);
			}
			else {
			    System.out.println("SEQ: "+sid);
			    System.out.println(" old: "+seq);
			    System.out.println(" new: "+seq2);
			}
		    }
		    
		}
		else {
		    System.out.println("MISSING: "+sid);
		}
		if (rs.next()) {
		    System.out.println("DUPE: "+sid);
		}
	    }
	}
	catch (Exception e) {
	    System.out.println("Exception: "+e.getMessage());
	    e.printStackTrace();
	}
    }
}
