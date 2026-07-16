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
import gov.lbl.scop.util.*;
import gov.lbl.scop.local.LocalSQL;

/**
   Parses list of PDB on-hold entries, to update the deposition
   dates and to report on how many are on hold without sequences
   in each month.

   See ~/data/pdb/log for details
*/
public class ParsePDBOnHold2 {
    final public static SimpleDateFormat ohDateFormat =
        new SimpleDateFormat ("MM/dd/yy");
    final public static SimpleDateFormat ohDateFormat2 =
        new SimpleDateFormat ("yyyy-MM-dd");

    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            ResultSet rs;

            // calc # of months to compile stats for, starting with Jan 2009
            java.util.Date d = new java.util.Date();
            int month = d.getMonth();
            int year = d.getYear();
            int nMonths = (year-109)*12+month+1;
    
            BufferedReader infile = IO.openReader(argv[0]);
            String buffer;
            int[] count = new int[nMonths]; // # of str per month w/ no seq, starting with 1/09
            while ((buffer = infile.readLine()) != null) {
                String[] field = buffer.split("\t");
                if (field.length < 2)
                    continue;
                String code = field[0].toLowerCase();
                d = ohDateFormat2.parse(field[1]);
                month = d.getMonth();
                year = d.getYear();
                boolean hasSeq = ((field.length > 2) &&
                                  (field[2].equals("RELEASE NOW")));
                if (hasSeq) {
                    // update date in pdb onhold table
                    int id = LocalSQL.lookupPDBOnHold(code);
                    if (id != 0) {
                        rs = stmt.executeQuery("select reported_date from pdb_onhold_chain where pdb_onhold_entry_id="+id+" limit 1");
                        if (rs.next()) {
                            String oldDate = rs.getString(1);
                            System.out.println("updating "+code+" from "+oldDate+" to "+ParsePDB.sqlDateFormat.format(d));
                        }
                        stmt.executeUpdate("update pdb_onhold_chain set reported_date=\""+ParsePDB.sqlDateFormat.format(d)+"\" where pdb_onhold_entry_id="+id);
                    }
                }
                else {
                    // just keep the count
                    int i = (year-109)*12+month;
                    count[i]++;
                }
            }

            System.out.println("PDB no-seq counts, from 1/09:");
            for (int i=0; i<nMonths; i++) {
                System.out.println(count[i]);
            }
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
