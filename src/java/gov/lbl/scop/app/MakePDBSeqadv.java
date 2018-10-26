/*
 * Software to build and maintain SCOPe, https://scop.berkeley.edu/
 *
 * Copyright (C) 2012-2018 The Regents of the University of California
 *
 * For feedback, mailto:scope@compbio.berkeley.edu
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * Version 2.1 of the License, or (at your option) any later version.
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301
 * USA
 */
package gov.lbl.scop.app;

import java.sql.*;
import java.io.*;
import java.util.*;
import org.strbio.io.*;
import org.strbio.math.*;
import org.strbio.mol.*;
import org.strbio.util.*;
import org.strbio.IO;
import org.xml.sax.*;
import org.xml.sax.helpers.*;
import javax.xml.parsers.*;
import gov.lbl.scop.local.LocalSQL;
import gov.lbl.scop.util.RAF;

/**
   Import dbrefs and seqadv records from XML files
*/
public class MakePDBSeqadv {
    /* class to hold a single DBREF record */
    static class DBRef {
        public String id;
        public String entity;
	
        public String dbName;
        public String dbCode;
        public String dbAccession;
        public int dbStart = -1;
        public int dbEnd = -1;
        public String pdbStart;
        public String pdbEnd;

        public boolean ok() {
            if ((dbName != null) &&
                (dbCode != null))
                return true;
            return false;
        }

        // copy all non-default attributes from another dbref:
        public void merge(DBRef b) {
            if (b.id != null)
                id = b.id;
            if (b.entity != null)
                entity = b.entity;
            if (b.dbName != null)
                dbName = b.dbName;
            if (b.dbCode != null)
                dbCode = b.dbCode;
            if (b.dbAccession != null)
                dbAccession = b.dbAccession;
            if (b.pdbStart != null)
                pdbStart = b.pdbStart;
            if (b.pdbEnd != null)
                pdbEnd = b.pdbEnd;
            if (b.dbStart != -1)
                dbStart = b.dbStart;
            if (b.dbEnd != -1)
                dbEnd = b.dbEnd;
        }

        public String toString() {
            return ("DBRef: "+dbName+" "+dbCode+" "+id+" "+entity+" "+pdbStart+" "+pdbEnd);
        }
    }

    /* class to hold a consecutive set of SEQADV records */
    static class SEQADV {
        DBRef dbRef;
	
        public String category;
        public String pdbResID;
        public String seq;

        public int pdbStart; // used in post-processing
        public int pdbEnd;

        public boolean ok() {
            if ((dbRef != null) &&
                (category != null) &&
                (pdbResID != null) &&
                (seq != null))
                return true;
            return false;
        }

        public boolean equals(Object o) {
            if ((o == null) ||
                !getClass().equals(o.getClass()))
                return false;
            if (o == this)
                return true;
	    
            SEQADV b = (SEQADV)o;
            if (!b.dbRef.equals(dbRef))
                return false;
            if (!b.category.equals(category))
                return false;
            if (!b.pdbResID.equals(pdbResID))
                return false;
            if (!b.seq.equals(seq))
                return false;
            return true;
        }
    }
    
    static class PDBMLHandler extends DefaultHandler {
        private int inElement = 0;

        public DBRef curDBRef = null;
        public SEQADV curSeqadv = null;
        public String tmpStr = null;
        public String curEntity = null;
        public String curChain = "";
        public String curAlignID = null;

        // map of entities to chain letters
        public HashMap<String,String> chainMap = new HashMap<String,String>();
	
        // maps between entities and data
        public HashMap<String,Vector<DBRef>> dbRefs = new HashMap<String,Vector<DBRef>>();
        public HashMap<String,Vector<SEQADV>> seqadvs = new HashMap<String,Vector<SEQADV>>();

        // mapping of ref ids to dbrefs
        public HashMap<String,DBRef> refIDMap = new HashMap<String,DBRef>();
        // mapping of align ids to dbrefs
        public HashMap<String,DBRef> alignIDMap = new HashMap<String,DBRef>();

        public void startElement(String uri,
                                 String localName,
                                 String qName,
                                 Attributes attributes) {
            if ((qName.equals("PDBx:pdbx_poly_seq_scheme"))) {
                inElement = 0;
                if (attributes != null)
                    curEntity = attributes.getValue("entity_id");
                else
                    curEntity = null;
                curChain = "";
            }
            else if (qName.equals("PDBx:struct_ref")) {
                inElement = 1;
                curDBRef = new DBRef();
                if (attributes != null) {
                    curDBRef.id = attributes.getValue("id");
                    refIDMap.put(curDBRef.id, curDBRef);
                    // System.out.println("debug2: ref id="+curDBRef.id);
                }
            }	    
            else if (qName.equals("PDBx:struct_ref_seq")) {
                inElement = 2;
                curDBRef = new DBRef();
                // System.out.println("debug3: new dbref");
                curAlignID = null;
                if (attributes != null)
                    curAlignID = attributes.getValue("align_id");
            }
            else if (qName.equals("PDBx:struct_ref_seq_dif")) {
                inElement = 3;
                curSeqadv = new SEQADV();
            }
            else if ((qName.equals("PDBx:db_code")) &&
                     (inElement==1)) {
                inElement = 4;
                curDBRef.dbCode = "";
            }
            else if ((qName.equals("PDBx:db_name")) &&
                     (inElement==1)) {
                inElement = 5;
                curDBRef.dbName = "";
            }
            else if ((qName.equals("PDBx:entity_id")) &&
                     (inElement==1)) {
                inElement = 6;
                curDBRef.entity = "";
            }
            else if ((qName.equals("PDBx:pdbx_db_accession")) &&
                     (inElement==1)) {
                inElement = 7;
                curDBRef.dbAccession = "";
            }
            else if ((qName.equals("PDBx:db_align_beg")) &&
                     (inElement==2)) {
                inElement = 8;
                tmpStr = "";
            }
            else if ((qName.equals("PDBx:db_align_end")) &&
                     (inElement==2)) {
                inElement = 9;
                tmpStr = "";
            }
            else if ((qName.equals("PDBx:ref_id")) &&
                     (inElement==2)) {
                inElement = 10;
                tmpStr = "";
            }
            else if ((qName.equals("PDBx:pdbx_auth_seq_align_beg")) &&
                     (inElement==2)) {
                inElement = 11;
                curDBRef.pdbStart = "";
            }
            else if ((qName.equals("PDBx:pdbx_auth_seq_align_end")) &&
                     (inElement==2)) {
                inElement = 12;
                curDBRef.pdbEnd = "";
            }
            else if ((qName.equals("PDBx:align_id")) &&
                     (inElement==3)) {
                inElement = 13;
                tmpStr = "";
            }
            else if ((qName.equals("PDBx:details")) &&
                     (inElement==3)) {
                inElement = 14;
                curSeqadv.category = "";
            }
            else if ((qName.equals("PDBx:mon_id")) &&
                     (inElement==3)) {
                inElement = 15;
                tmpStr = "";
            }
            else if ((qName.equals("PDBx:pdbx_auth_seq_num")) &&
                     (inElement==3)) {
                inElement = 16;
                curSeqadv.pdbResID = "";
            }
            else if (qName.equals("PDBx:pdb_strand_id")) {
                if (curChain.length()==0)
                    inElement = 17;
            }
            else if ((qName.equals("PDBx:pdbx_db_accession")) &&
                     (inElement==2)) {
                inElement = 18;
                curDBRef.dbAccession = "";
            }
        }

        public void endElement(String uri,
                               String localName,
                               String qName) {
            if (qName.equals("PDBx:struct_ref")) {
                inElement = 0;
                // System.out.println("debug2: ref="+curDBRef.toString());
            }	    
            else if (qName.equals("PDBx:struct_ref_seq")) {
                inElement = 0;
                // System.out.println("debug3: ref="+curDBRef.toString());
                if (curDBRef.entity == null)
                    System.out.println("DBRef found without entity");
                else {
                    if (curDBRef.ok()) {
                        if (dbRefs.get(curDBRef.entity)==null)
                            dbRefs.put(curDBRef.entity, new Vector<DBRef>());
                        if (!dbRefs.get(curDBRef.entity).contains(curDBRef))
                            dbRefs.get(curDBRef.entity).add(curDBRef);
                        alignIDMap.put(curAlignID, curDBRef);
                    }
                }
            }
            else if (qName.equals("PDBx:struct_ref_seq_dif")) {
                inElement = 0;
                if ((curSeqadv==null) ||
                    (curSeqadv.dbRef==null) ||
                    (curSeqadv.dbRef.entity==null))
                    System.out.println("DBRef found without entity - 2");
                else {
                    String entity = curSeqadv.dbRef.entity;
                    if (curSeqadv.ok()) {
                        if (seqadvs.get(entity)==null)
                            seqadvs.put(entity, new Vector<SEQADV>());
                        if (!seqadvs.get(entity).contains(curSeqadv))
                            seqadvs.get(entity).add(curSeqadv);
                    }
                }
            }
            else if (((qName.equals("PDBx:db_code")) &&
                      (inElement==4)) ||
                     ((qName.equals("PDBx:db_name")) &&
                      (inElement==5)) ||
                     ((qName.equals("PDBx:entity_id")) &&
                      (inElement==6)) ||
                     ((qName.equals("PDBx:pdbx_db_accession")) &&
                      ((inElement==7)))) {
                inElement = 1;
            }
            else if ((qName.equals("PDBx:db_align_beg")) &&
                     (inElement==8)) {
                inElement = 2;
                curDBRef.dbStart = StringUtil.atoi(tmpStr);
                tmpStr = null;
            }
            else if ((qName.equals("PDBx:db_align_end")) &&
                     (inElement==9)) {
                inElement = 2;
                curDBRef.dbEnd = StringUtil.atoi(tmpStr);
                tmpStr = null;
            }
            else if ((qName.equals("PDBx:ref_id")) &&
                     (inElement==10)) {
                inElement = 2;
                DBRef savedDBRef = refIDMap.get(tmpStr);
                if (savedDBRef == null)
                    System.out.println("unknown ref_id "+tmpStr);
                else {
                    savedDBRef.merge(curDBRef);
                    curDBRef = savedDBRef;
                }
                tmpStr = null;
            }
            else if (((qName.equals("PDBx:pdbx_auth_seq_align_beg")) &&
                      (inElement==11)) ||
                     ((qName.equals("PDBx:pdbx_auth_seq_align_end")) &&
                      (inElement==12))) {
                inElement = 2;
            }
            else if ((qName.equals("PDBx:align_id")) &&
                     (inElement==13)) {
                inElement = 3;
                curSeqadv.dbRef = alignIDMap.get(tmpStr);
                if (curSeqadv.dbRef==null)
                    System.out.println("unknown align_id "+tmpStr);
                tmpStr = null;
            }
            else if (((qName.equals("PDBx:details")) &&
                      (inElement==14)) ||
                     ((qName.equals("PDBx:pdbx_auth_seq_num")) &&
                      (inElement==16))) {
                inElement = 3;
            }
            else if ((qName.equals("PDBx:mon_id")) &&
                     (inElement==15)) {
                inElement = 3;
                try {
                    curSeqadv.seq = RAF.translatePDBRes(tmpStr);
                }
                catch (Exception e) {
                    System.out.println("Exception: "+e.getMessage());
                    e.printStackTrace();
                }
                tmpStr = null;
            }
            else if (qName.equals("PDBx:pdbx_poly_seq_scheme")) {
                inElement = 0;
                if (curEntity != null) {
                    if (curChain.length() == 1) {
                        String x = chainMap.get(curEntity);
                        if (x==null)
                            x = "";
                        if (x.indexOf(curChain) == -1)
                            x += curChain;
                        chainMap.put(curEntity, x);
                    }
                }
            }
            else if (qName.equals("PDBx:pdb_strand_id")) {
                inElement = 0;
                curChain = curChain.trim();
            }
            else if ((qName.equals("PDBx:pdbx_db_accession")) &&
                     (inElement==18)) {
                inElement = 2;
            }
        }

        public void characters(char[] ch,
                               int start,
                               int length) {
            if (inElement==4)
                curDBRef.dbCode += new String(ch, start, length);
            else if (inElement==5)
                curDBRef.dbName += new String(ch, start, length);
            else if (inElement==6)
                curDBRef.entity += new String(ch, start, length);
            else if ((inElement==7) ||
                     (inElement==18))
                curDBRef.dbAccession += new String(ch, start, length);
            else if ((inElement==8) ||
                     (inElement==9) ||
                     (inElement==10) ||
                     (inElement==13) ||
                     (inElement==15))
                tmpStr += new String(ch, start, length);
            else if (inElement==11)
                curDBRef.pdbStart += new String(ch, start, length);
            else if (inElement==12)
                curDBRef.pdbEnd += new String(ch, start, length);
            else if (inElement==14)
                curSeqadv.category += new String(ch, start, length);
            else if (inElement==16)
                curSeqadv.pdbResID += new String(ch, start, length);
            else if (inElement==17)
                curChain += new String(ch, start, length);
        }
    }

    final public static int lookupOrCreateCategory(String description) throws Exception {
        int rv = 0;
        Statement stmt = LocalSQL.createStatement();
        description = StringUtil.replace(description,"\"","\\\"");
        ResultSet rs = stmt.executeQuery("select id from pdb_chain_diff_category where description=\""+description+"\"");
        if (rs.next()) {
            rv = rs.getInt(1);
        }
        else {
            stmt.executeUpdate("insert into pdb_chain_diff_category values (null, \""+
                               description+"\")",
                               Statement.RETURN_GENERATED_KEYS);
            rs = stmt.getGeneratedKeys();
            rs.next();
            rv = rs.getInt(1);
        }
        stmt.close();
        return rv;
    }

    /**
       find dbrefs and seqadv records for all chains from an
       individual PDB release
    */
    final public static void findSeqadv(int pdbReleaseID) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select xml_path from pdb_local where pdb_release_id="+pdbReleaseID);
        if (!rs.next()) {
            stmt.close();
            return;
        }
        String xml = rs.getString(1);
        rs.close();

        PreparedStatement stmt2 = LocalSQL.prepareStatement("insert into pdb_chain_dbref values (null, ?, ?, ?, ?, ?, ?, ?, ?)",
                                                            Statement.RETURN_GENERATED_KEYS);
        PreparedStatement stmt3 = LocalSQL.prepareStatement("insert into pdb_chain_diff values (null, ?, ?, ?, ?, ?, ?)");

        System.out.println("finding dbref/seqadv for "+xml);
        System.out.flush();

        BufferedReader infile = IO.openReader(xml);
        PDBMLHandler h = new PDBMLHandler();

        SAXParserFactory factory
            = SAXParserFactory.newInstance();
        factory.setValidating(false);
        SAXParser parser = factory.newSAXParser();
	
        parser.parse(new InputSource(infile), h);

        for (String entity : h.chainMap.keySet()) {
            String tmp = h.chainMap.get(entity);
            for (int i=0; i<tmp.length(); i++) {
                char chain = tmp.charAt(i);
                // System.out.println("debug: chain "+chain+" entity "+entity);

                rs = stmt.executeQuery("select id from pdb_chain where pdb_release_id="+pdbReleaseID+" and chain=\""+chain+"\" limit 1");
                if (rs.next()) {
                    int pdbChainID = rs.getInt(1);
                    rs.close();

                    stmt.executeUpdate("delete from pdb_chain_tag where pdb_chain_diff_id in (select id from pdb_chain_diff where pdb_chain_id="+pdbChainID+")");
                    stmt.executeUpdate("delete from pdb_chain_diff where pdb_chain_id="+pdbChainID);
                    stmt.executeUpdate("delete from pdb_chain_dbref where pdb_chain_id="+pdbChainID);

                    rs = stmt.executeQuery("select raf_get_body(id) from raf where pdb_chain_id="+pdbChainID+" and first_release_id is null and last_release_id is null");
                    if (!rs.next()) {
                        rs.close();
                        continue;
                    }
                    String rafBody = rs.getString(1);
                    rs.close();

                    stmt2.setInt(1,pdbChainID);
                    stmt3.setInt(1,pdbChainID);

                    HashMap<DBRef,Integer> assignedIDs = new HashMap<DBRef,Integer>();
		    
                    Vector<DBRef> dbRefs = h.dbRefs.get(entity);
                    if (dbRefs != null) {
                        for (DBRef d : dbRefs) {
                            // System.out.println("debug: "+d.dbName+" "+d.dbCode+" "+d.dbStart+" "+d.dbEnd);
                            stmt2.setString(2,d.dbName);
                            stmt2.setString(3,d.dbCode);
                            stmt2.setString(4,d.dbAccession);
                            stmt2.setInt(5,d.dbStart);
                            stmt2.setInt(6,d.dbEnd);
                            int ps = RAF.indexOf(rafBody,d.pdbStart,true);
                            if (ps==-1) 
                                stmt2.setNull(7,java.sql.Types.BIGINT);
                            else
                                stmt2.setInt(7,ps);

                            int pe = RAF.indexOf(rafBody,d.pdbEnd,false);
                            if (pe==-1)
                                stmt2.setNull(8,java.sql.Types.BIGINT);
                            else 
                                stmt2.setInt(8,pe);
                            stmt2.executeUpdate();

                            rs = stmt2.getGeneratedKeys();
                            rs.next();
                            int refID = rs.getInt(1);
                            rs.close();
                            assignedIDs.put(d,new Integer(refID));
                        }
                    }

                    Vector<SEQADV> seqadvs = h.seqadvs.get(entity);
                    int ns = 0;
                    if (seqadvs != null)
                        ns = seqadvs.size();
                    SEQADV s1 = null;
                    for (int j=0; j<=ns; j++) {
                        SEQADV s2 = null;
                        if (j<ns) {
                            s2 = seqadvs.get(j);
                            s2.pdbStart = s2.pdbEnd =
                                RAF.indexOf(rafBody,s2.pdbResID,true);
                        }

                        // try to merge s2 into s1
                        boolean merged = false;
                        if ((s1 != null) && (s2 != null)) {
                            if ((s1.category.equals(s2.category)) &&
                                (s1.dbRef.equals(s2.dbRef)) &&
                                (s1.pdbEnd > -1) &&
                                (s1.pdbEnd == s2.pdbStart-1)) {
                                s1.pdbEnd = s2.pdbEnd;
                                s1.seq += s2.seq;
                                merged = true;
                            }
                        }

                        if (!merged) {
                            if (s1 != null) {
                                // save s1
                                stmt3.setInt(2,assignedIDs.get(s1.dbRef));
                                stmt3.setInt(3,lookupOrCreateCategory(s1.category));
                                stmt3.setInt(4,s1.pdbStart);
                                stmt3.setInt(5,s1.pdbEnd);
                                stmt3.setString(6,s1.seq);
                                if ((s1.pdbStart > -1) &&
                                    (s1.pdbEnd > -1))
                                    stmt3.executeUpdate();
                            }
                            s1 = s2;
                        }
                    }
                }
                else
                    rs.close();
            }
        }
	
        stmt.close();
        stmt2.close();
        stmt3.close();
    }

    
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
	    
            ResultSet rs;

            if (argv.length==0) {
                rs = stmt.executeQuery("select pdb_release_id from pdb_local where xml_path is not null and pdb_release_id not in (select distinct c.pdb_release_id from pdb_chain c, pdb_chain_dbref dbr where c.id=dbr.pdb_chain_id)");
                while (rs.next()) {
                    int id = rs.getInt(1);
                    findSeqadv(id);
                }
            }
            else
                rs = stmt.executeQuery("select pdb_release_id from pdb_local where xml_path = \""+argv[0]+"\"");
            while (rs.next()) {
                int id = rs.getInt(1);
                findSeqadv(id);
            }
	    
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
