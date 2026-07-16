package gov.lbl.scop.app;

import java.sql.*;
import java.io.*;
import java.util.*;
import java.text.*;
import org.strbio.io.*;
import org.strbio.math.*;
import org.strbio.mol.*;
import org.strbio.util.*;
import org.strbio.IO;
import gov.lbl.scop.local.LocalSQL;

/**
   Clusters proteins that 1) are not in SCOPe, and 2) have BLAST hits
   to SCOPe domains, and 3) have at worst 3.2A resolution.
*/
public class PrioritizeManualChecks {
    final public static void main(String argv[]) {
	try {
	    LocalSQL.connectRW();
	    Statement stmt = LocalSQL.createStatement();
	    Statement stmt2 = LocalSQL.createStatement();
	    ResultSet rs, rs2;

	    java.util.Date today = new java.util.Date();
	    
	    Vector<Integer> pdbEntries = DumpSeqs.getPDBEntries(today);
	    Vector<Integer> obsPDBEntries = DumpSeqs.getObsPDBEntries(today);
	    pdbEntries.removeAll(obsPDBEntries);
	    
	    Vector<Integer> pdbEntriesInSCOPe = DumpSeqs.getPDBEntriesInSCOP(LocalSQL.getLatestSCOPRelease(true));

	    // get latest release of each entry
	    Vector<Integer> newReleases = new Vector<Integer>();
	    for (Integer entryID : pdbEntries) {
		rs = stmt.executeQuery("select pr.id from pdb_local l, pdb_release pr, raf r, pdb_chain pc where r.pdb_chain_id=pc.id and pc.is_polypeptide=1 and pc.pdb_release_id=pr.id and l.pdb_release_id=pr.id and pr.pdb_entry_id="+entryID+" and l.is_raf_calculated=1 order by l.snapshot_date desc limit 1");
		if (rs.next()) {
		    Integer releaseID = new Integer(rs.getInt(1));
		    if (!newReleases.contains(releaseID))
			newReleases.add(releaseID);
		}
		rs.close();
	    }

	    // find all Pfam families in SCOPe
	    HashSet<Integer> pfamInSCOPe = new HashSet<Integer>();
	    for (Integer entryID : pdbEntries) {
		if (pdbEntriesInSCOPe.contains(entryID)) {
		    rs = stmt.executeQuery("select p.id from pfam p, astral_seq_hmm_pfam l, astral_seq s, astral_chain ac, raf r, pdb_chain pc, pdb_release pr where p.id=l.pfam_id and l.seq_id=ac.seq_id and l.score>=p.trusted_cutoff and (l.hmm_length >= p.length*0.75) and p.pfam_type_id<3 and p.release_id=56 and ac.source_id=2 and ac.seq_id=s.id and ac.raf_id=r.id and r.pdb_chain_id=pc.id and r.last_release_id=15 and pc.pdb_release_id=pr.id and pr.pdb_entry_id="+entryID);
		    while (rs.next())
			pfamInSCOPe.add(new Integer(rs.getInt(1)));
		    rs.close();
		}
	    }

	    // find all high-res entries not in SCOPe
	    pdbEntries = new Vector<Integer>();
	    for (Integer releaseID : newReleases) {
		rs = stmt.executeQuery("select pr.pdb_entry_id from pdb_release pr where pr.id="+releaseID+" and resolution <= 3.2 and resolution > 0");
		if (rs.next()) {
		    Integer entryID = new Integer(rs.getInt(1));
		    if ((!pdbEntries.contains(entryID)) &&
			(!pdbEntriesInSCOPe.contains(entryID)))
			pdbEntries.add(entryID);
		}
	    }

	    System.out.println(pdbEntries.size()+" unclassified high-res entries");

	    // mapping of "easy" seq ids to clusters
	    HashMap<Integer,Integer> clusterMap = new HashMap<Integer,Integer>();
	    // mapping of "easy" seq ids to representative sid
	    HashMap<Integer,String> seqMap = new HashMap<Integer,String>();
	    Integer nClusters = new Integer(0);
	    Map<Integer,Integer> clusterCounts = new HashMap<Integer,Integer>();

	    // set up BLAST db with unclassified chains
	    // only need to do this once:
	    // BlastSeqs.setupDB("update",true,true);

	    // unique and "easy" seqs
	    HashSet<Integer> uniqueSeqs = new HashSet<Integer>();
	    HashSet<Integer> easySeqs = new HashSet<Integer>();
	    Map<Integer,Integer> seqLengths = new HashMap<Integer,Integer>();

	    // find unique easy seqs, and BLAST them vs other unclassified seqs
	    for (Integer entryID : pdbEntries) {
		int releaseID = 0;

		rs = stmt.executeQuery("select pr.id from pdb_local l, pdb_release pr, raf r, pdb_chain pc where r.pdb_chain_id=pc.id and pc.is_polypeptide=1 and pc.pdb_release_id=pr.id and l.pdb_release_id=pr.id and pr.pdb_entry_id="+entryID+" and l.is_raf_calculated=1 order by l.snapshot_date desc limit 1");
		if (rs.next())
		    releaseID = rs.getInt(1);
		rs.close();

		rs = stmt.executeQuery("select s.id, s.seq, s.is_reject, ac.sid from astral_seq s, astral_chain ac, raf r, pdb_chain pc where ac.seq_id=s.id and ac.raf_id=r.id and ac.source_id=2 and r.pdb_chain_id=pc.id and r.first_release_id is null and r.last_release_id is null and pc.pdb_release_id="+releaseID);
		while (rs.next()) {
		    Integer seqID = new Integer(rs.getInt(1));
		    String seq = rs.getString(2);
		    boolean isReject = (rs.getInt(3)==1);
		    String sid = rs.getString(4);
		    int seqLength = 0;
		    if (seq != null)
			seqLength = seq.length();
		    int seqClass = 0;
		    if (!uniqueSeqs.contains(seqID)) {
			uniqueSeqs.add(seqID);
			boolean isEasy = false;

			rs2 = stmt2.executeQuery("select m.id from astral_seq_blast m, astral_domain d, scop_node n where m.seq1_id="+seqID+" and m.seq2_id=d.seq_id and d.node_id=n.id and d.source_id=2 and m.source_id=2 and m.style1_id=1 and (m.style2_id=d.style_id or d.style_id=1) and m.blast_log10_e <= -4 and n.sccs regexp '^[a-h]' and m.release_id=n.release_id and n.release_id=15 limit 1");
			if (rs2.next())
			    isEasy = true;
			rs2.close();
			    
			// get pfam hits to this sequence
			rs2 = stmt2.executeQuery("select distinct(p.id) from pfam p, astral_seq_hmm_pfam l where p.id=l.pfam_id and l.score>=p.trusted_cutoff and (l.hmm_length >= p.length*0.75) and p.release_id=56 and p.pfam_type_id<3 and l.seq_id="+seqID);
			while (rs2.next()) {
			    Integer pfamID = new Integer(rs2.getInt(1));
			    if (!pfamInSCOPe.contains(pfamID)) {
				isEasy = false;
			    }
			}
			rs2.close();

			if (isEasy) {
			    easySeqs.add(seqID);
			    seqLengths.put(seqID,new Integer(seqLength));
			    seqMap.put(seqID,sid);
			    /*
			      already done for 2.04:
			    
				BlastSeqs.blastSeq(seqID,
						   2,
						   1,
						   1,
						   0);
			    */
			}
		    }
		}
		rs.close();
	    }

	    System.out.println(easySeqs.size()+" easy sequences");

	    // cluster sequences greedily
	    for (Integer seqID  : easySeqs) {
		Integer myCluster = null;
		Integer seqLength = seqLengths.get(seqID);

		rs = stmt.executeQuery("select s.id from astral_seq_blast b, astral_seq s where b.seq1_id="+seqID+" and b.source_id=2 and b.style1_id=1 and b.style2_id=1 and b.release_id is null and b.blast_log10_e <= -4 and b.seq2_id=s.id and b.seq1_id!=b.seq2_id and b.seq1_length>="+(seqLength/2)+" and b.seq2_length>=(length(s.seq)/2)");
		while (rs.next()) {
		    Integer seqID2 = new Integer(rs.getInt(1));
		    if (myCluster == null)
			myCluster = clusterMap.get(seqID2);
		}
		rs.close();
		if (myCluster == null) {
		    rs = stmt.executeQuery("select s.id from astral_seq_blast b, astral_seq s where b.seq2_id="+seqID+" and b.source_id=2 and b.style1_id=1 and b.style2_id=1 and b.release_id is null and b.blast_log10_e <= -4 and b.seq1_id=s.id and b.seq1_id!=b.seq2_id and b.seq2_length>="+(seqLength/2)+" and b.seq1_length>=(length(s.seq)/2)");
		    while (rs.next()) {
			Integer seqID2 = new Integer(rs.getInt(1));
			if (myCluster == null)
			    myCluster = clusterMap.get(seqID2);
		    }
		    rs.close();
		}
		    
		if (myCluster == null)
		    myCluster = new Integer(nClusters++);
		clusterMap.put(seqID,myCluster);
		Integer count = clusterCounts.get(myCluster);
		if (count==null)
		    count = new Integer(0);
		count++;
		clusterCounts.put(myCluster,count);
	    }

	    System.out.println(nClusters+" clusters of easy chains");
	    System.out.println("Biggest easy clusters:");
	    int i = 0;
	    clusterCounts = StatsCoveragePfam6.MapUtil.sortByValue(clusterCounts);
	    Integer biggestCluster = null;
	    for (Map.Entry<Integer,Integer> e : clusterCounts.entrySet()) {
		if (i++<20) {
		    int clusterID = e.getKey();
		    System.out.println(clusterID+" "+e.getValue());

		    int j = 0;
		    for (Integer seqID  : easySeqs) {
			if (clusterMap.get(seqID) == clusterID) {
			    String sid = seqMap.get(seqID);
			    Integer seqLength = seqLengths.get(seqID);
			    if (j++<5)
				System.out.println("  "+sid+" "+seqLength);
			}
		    }
		}
	    }
	}
	catch (Exception e) {
	    System.out.println("Exception: "+e.getMessage());
	    e.printStackTrace();
	}
    }
}
