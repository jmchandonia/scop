package gov.lbl.scop.app;

import java.sql.*;
import java.io.*;
import java.util.*;
import org.strbio.io.*;
import org.strbio.math.*;
import org.strbio.mol.*;
import org.strbio.util.*;
import org.strbio.IO;
import gov.lbl.scop.util.*;
import gov.lbl.scop.local.LocalSQL;

/**
   Based on org.strgen.lims.MakeDBKnownstr; parses FASTA file of
   on-hold sequences, downloaded from:
   http://www.rcsb.org/pdb/search/searchStatusDoSearch.do?newSearch=yes&full=true&format=SEQ
*/
public class ParsePDBOnHold {
    final public static int lookupOrCreateEntry(String pdbCode) throws Exception {
        int id = LocalSQL.lookupPDBOnHold(pdbCode);
        if (id==0) {
            Statement stmt = LocalSQL.createStatement();
            stmt.executeUpdate("insert into pdb_onhold_entry values (null, \""+pdbCode+"\")",
                               Statement.RETURN_GENERATED_KEYS);
            ResultSet rs = stmt.getGeneratedKeys();
            rs.next();
            id = rs.getInt(1);
            stmt.close();
        }
        return id;
    }
    
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            ResultSet rs;

            // add all proteins in argv[0] that aren't already there.
            BufferedReader proteins = IO.openReader(argv[0]);
            PolymerSet ps = new PolymerSet();
            Enumeration pe = ps.polymersInFile(proteins, null);
            int total = 0;
            int totalNew = 0;

            File f = new File(argv[0]);
            java.util.Date d = new java.util.Date(f.lastModified());
	    
            while (pe.hasMoreElements()) {
                Polymer p;
                p = (Polymer)pe.nextElement();

                total++;

                if (p != null) {
                    String seq = p.sequence();

                    if ((seq != null) && (seq.length()>0) && (p.name != null)) {
                        String origSeq = seq.toLowerCase();
                        seq = RAF.translatePDBSeq(seq);

                        String name = p.name.toLowerCase().trim();
                        String code = name.substring(0,4);

                        int entryID = lookupOrCreateEntry(code);

                        int entity = 0;
                        int pos = name.indexOf(" entity ");
                        if (pos == 4)
                            entity = StringUtil.atoi(name,12);
                        else {
                            pos = name.indexOf("_");
                            if (pos == 4)
                                entity = StringUtil.atoi(name,4);
                            else
                                throw new Exception("error: PDB on-hold format changed; error parsing "+name);
                        }

                        int seqID = MakeDomainSeq.lookupOrCreateSeq(seq);

                        // see if it's there already.
                        rs = stmt.executeQuery("select id from pdb_onhold_chain where pdb_onhold_entry_id="+
                                               entryID+" and entity="+
                                               entity+" and seq_id="+
                                               seqID+"");
			
                        if (!rs.next()) {
                            System.out.println("Adding "+name);

                            totalNew++;

                            boolean isPeptide = !(RAF.isNucleotide(origSeq));

                            stmt.executeUpdate("insert into pdb_onhold_chain values (NULL, "+
                                               entryID+", "+
                                               entity+", "+
                                               seqID+", \""+
                                               ParsePDB.sqlDateFormat.format(d)+"\", "+
                                               (isPeptide ? 1 : 0)+ ")");
                        }
                        else {
                            System.out.println("already have "+name);
                        }
                    }
                    else {
                        System.out.println("null 2 "+p.name);
                    }
                }
                else {
                    System.out.println("null 1");
                }
            }
            System.out.println("\n"+total+" proteins processed.\n");
            System.out.println(totalNew+" new proteins added to on-hold list.\n");
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
