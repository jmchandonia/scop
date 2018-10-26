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
import java.text.*;
import org.strbio.io.*;
import org.strbio.local.*;
import org.strbio.math.*;
import org.strbio.mol.*;
import org.strbio.util.*;
import org.strbio.IO;
import org.xml.sax.*;
import org.xml.sax.helpers.*;
import javax.xml.parsers.*;
import gov.lbl.scop.local.LocalSQL;

/**
   Import all data for PDB entries in a directory, or "removed" file,
   using the .xml files.  This obsoletes ParsePDB.java
*/
public class ParsePDBXML {
    /**
       unbundles a bundle file, returning a File with the
       concatenated ATOM records
    */
    final public static File unBundle(String fileName) throws Exception {
        File tmpDir = File.createTempFile("bundle",null);
        tmpDir.delete();
        tmpDir.mkdir();
	
        Program tar = new Program("/bin/tar");
        InputStream is = IO.openStream("/dev/null");
        FileOutputStream os = new FileOutputStream("/dev/null");
        tar.setOutput(os);
        tar.setError(os);
        tar.setInput(is);
        String[] args = { "xf", fileName };
        tar.run(args, null, tmpDir);
        is.close();
        os.close();

        File outFile = File.createTempFile("pdb",".ent");
        Program cat = new Program("/bin/cat");
        is = IO.openStream("/dev/null");
        os = new FileOutputStream(outFile);
        cat.setOutput(os);
        cat.setError(os);
        cat.setInput(is);
        cat.run("*.pdb", null, tmpDir);

        IO.rmRF(tmpDir);
        return outFile;
    }
    
    final public static SimpleDateFormat snapshotDateFormat =
        new SimpleDateFormat ("yyMMdd");
    final public static SimpleDateFormat sqlDateFormat =
        new SimpleDateFormat ("yyyy-MM-dd");

    public static int curEntryID = 0;
    public static String curFileDate = null;
    public static int curReleaseID = 0;
    public static boolean isLarge = false;
    
    public static class PDBMLHandler extends DefaultHandler {
        private int inElement = 0;
        public String curRevNum = "";
        public String curRevDate = "";
        public String curDepDate = "";
        public String curTitle = "";
        public String curHeader = "";
        public String curMethod = "";
        public String curObsDate = "";
        public String curObsBy = "";

        public Vector<String> revDates = new Vector<String>();
	
        public void startElement(String uri,
                                 String localName,
                                 String qName,
                                 Attributes attributes) {
            if (qName.equals("PDBx:database_PDB_rev")) {
                if (attributes != null)
                    curRevNum = attributes.getValue("num");
                inElement = 1;
                curRevDate = "";
                curDepDate = "";
            }
            else if ((qName.equals("PDBx:date")) &&
                     (inElement==1)) {
                inElement = 2;
            }
            else if ((qName.equals("PDBx:date_original")) &&
                     (inElement==1)) {
                inElement = 3;
            }
            else if (qName.equals("PDBx:pdbx_keywords")) {
                inElement = 4;
            }
            else if (qName.equals("PDBx:struct")) {
                inElement = 5;
            }
            else if (((qName.equals("PDBx:pdbx_title")) ||
                      (qName.equals("PDBx:title"))) &&
                     (inElement==5)) {
                inElement = 6;
            }
            else if (qName.equals("PDBx:exptl")) {
                if (attributes != null) {
                    if (curMethod.length() > 0)
                        curMethod += "; ";
                    curMethod += attributes.getValue("method");
                }
            }
            else if (qName.equals("PDBx:pdbx_database_PDB_obs_spr")) {
                if (attributes != null) {
                    curObsBy = attributes.getValue("pdb_id");
                    try {
                        int obsByID = LocalSQL.lookupPDB(curObsBy);
                        if (obsByID != curEntryID) {
                            checkObsBy(obsByID);
                            inElement = 7;
                        }
                    }
                    catch (Exception e) {
                    }
                }
            }
            else if ((qName.equals("PDBx:date")) &&
                     (inElement==7)) {
                inElement = 8;
            }
        }

        public void endElement(String uri,
                               String localName,
                               String qName) {
            if (qName.equals("PDBx:database_PDB_rev")) {
                inElement = 0;
            }
            else if ((qName.equals("PDBx:date")) &&
                     (inElement==2)) {
                curRevDate = curRevDate.trim();
                revDates.add(curRevDate);
                inElement = 1;
            }
            else if ((qName.equals("PDBx:date_original")) &&
                     (inElement==3)) {
                curDepDate = curDepDate.trim();
                if (!isLarge) checkDepDate(curDepDate);
                inElement = 1;
            }
            else if (qName.equals("PDBx:pdbx_keywords")) {
                inElement = 0;
                curHeader = curHeader.trim();
            }
            else if (((qName.equals("PDBx:pdbx_title")) ||
                      (qName.equals("PDBx:title"))) &&
                     (inElement==6)) {
                curTitle = curTitle.trim();
                inElement = 5;
            }
            else if (qName.equals("PDBx:pdbx_database_PDB_obs_spr")) {
                inElement = 0;
            }
            else if ((qName.equals("PDBx:date")) &&
                     (inElement==8)) {
                curObsDate = curObsDate.trim();
                checkObsDate(curObsDate);
                inElement = 7;
            }
        }
	    
        public void characters(char[] ch,
                               int start,
                               int length) {
            if (inElement==2)
                curRevDate += new String(ch, start, length);
            else if (inElement==3)
                curDepDate += new String(ch, start, length);
            else if (inElement==4)
                curHeader += new String(ch, start, length);
            else if (inElement==6)
                curTitle += new String(ch, start, length);
            else if (inElement==8)
                curObsDate += new String(ch, start, length);
        }
    }

    final public static void checkRevDate(String date) {
        if (curEntryID==0)
            return;
        try {
            int releaseID = lookupRelease(curEntryID, date, curFileDate);
            if (releaseID==0)
                System.out.println("Error - checkRevDate "+curEntryID+" "+date+" "+curFileDate);
            if (curFileDate != null) {
                curFileDate = null;  // only used for 1st revision in file
                curReleaseID = releaseID;
            }
        }
        catch (Exception e) {
        }
    }

    final public static void checkDepDate(String date) {
        if (curEntryID==0)
            return;
        try {
            Statement stmt = LocalSQL.createStatement();
            ResultSet rs;
            rs = stmt.executeQuery("select id from pdb_entry where id="+curEntryID+" and deposition_date=\""+date+"\"");
            if (!rs.next())
                System.out.println("Error - checkDepDate "+curEntryID+" "+date);
            rs.close();
            stmt.close();
        }
        catch (Exception e) {
        }
    }

    final public static void checkObsDate(String date) {
        if (curEntryID==0)
            return;
        try {
            Statement stmt = LocalSQL.createStatement();
            ResultSet rs;
            String obsDate = null;
            rs = stmt.executeQuery("select obsolete_date from pdb_entry where id="+curEntryID);
            rs.next();
            obsDate = rs.getString(1);
            rs.close();
            stmt.close();
            if (!date.equals(obsDate))
                System.out.println("Error - checkObsDate "+curEntryID+" "+date+" "+obsDate);
        }
        catch (Exception e) {
        }
    }

    final public static void checkDescription(String desc) {
        try {
            Statement stmt = LocalSQL.createStatement();
            ResultSet rs;
            String realDesc = null;
            rs = stmt.executeQuery("select description from pdb_entry where id="+curEntryID);
            rs.next();
            realDesc = rs.getString(1);
            rs.close();
            stmt.close();
            String d1 = StringUtil.replace(desc.toUpperCase()," ","");
            String d2 = StringUtil.replace(realDesc.toUpperCase()," ","");
            if (!d1.equals(d2)) {
                System.out.println("Error - checkDescription "+curEntryID+" '"+desc+"' '"+realDesc+"'");
            }
        }
        catch (Exception e) {
        }
    }
    
    final public static void checkObsBy(int obsByID) {
        try {
            Statement stmt = LocalSQL.createStatement();
            ResultSet rs;
            int correctID = 0;
            rs = stmt.executeQuery("select obsoleted_by from pdb_entry where id="+curEntryID);
            rs.next();
            correctID = rs.getInt(1);
            rs.close();
            stmt.close();
            if (obsByID != correctID)
                System.out.println("Error - checkObsBy "+curEntryID+" "+obsByID+" "+correctID);
        }
        catch (Exception e) {
        }
    }

    final public static void checkMethod(String method) {
        try {
            Statement stmt = LocalSQL.createStatement();
            ResultSet rs;
            int correctID = 0;
            rs = stmt.executeQuery("select method_id from pdb_release where id="+curReleaseID);
            rs.next();
            correctID = rs.getInt(1);
            rs.close();
            method = StringUtil.replace(method,"\"","\\\"");
            int methodID = 0;
            rs = stmt.executeQuery("select id from pdb_method where description=\""+method+"\"");
            if (rs.next())
                methodID = rs.getInt(1);
            rs.close();
            stmt.close();
            if (methodID != correctID)
                System.out.println("Error - checkMethod "+curEntryID+" "+curReleaseID+" "+methodID+" "+correctID+" "+method);
        }
        catch (Exception e) {
        }
    }
    
    // lookup release, or 0 if not found.
    // prefers unreplaced releases if possible
    final public static int lookupRelease(int pdbID, String revDate, String fileDate) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs;
        if (fileDate==null) {
            rs = stmt.executeQuery("select id from pdb_release where pdb_entry_id="+pdbID+" and revision_date=\""+revDate+"\" and replaced_by is null order by file_date limit 1");
            if (rs.next()) {
                int i = rs.getInt(1);
                stmt.close();
                return i;
            }
            rs = stmt.executeQuery("select id from pdb_release where pdb_entry_id="+pdbID+" and revision_date=\""+revDate+"\" order by file_date limit 1");
        }
        else
            rs = stmt.executeQuery("select id from pdb_release where pdb_entry_id="+pdbID+" and revision_date=\""+revDate+"\" and file_date=\""+fileDate+"\"");
        if (rs.next()) {
            int i = rs.getInt(1);
            stmt.close();
            return i;
        }
        return 0;
    }

    /**
       figure out if we should convert description to all lower case
    */
    final public static boolean hasTooManyCaps(String s) {
        int l = s.length();
        if (l==0)
            return false;
        int consecutiveCaps = 0;
        int totalCaps = 0;
        for (int i=0; i<l; i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c)) {
                totalCaps++;
                consecutiveCaps++;
                if (consecutiveCaps > 8)
                    return true;
            }
            else if (Character.isLowerCase(c)) {
                consecutiveCaps = 0;
            }
            // ignore spaces for purposes of consecutiveness
        }
        if (totalCaps >= l/2)
            return true;

        return false;
    }

    /**
       parse an individual XML file.
    */
    final public static PDBMLHandler parseXMLFile(String fileName) throws Exception {
        BufferedReader infile = IO.openReader(fileName);
        PDBMLHandler h = new PDBMLHandler();

        SAXParserFactory factory
            = SAXParserFactory.newInstance();
        factory.setValidating(false);
        SAXParser parser = factory.newSAXParser();
	
        parser.parse(new InputSource(infile), h);
        String desc = h.curTitle;
        if (desc.length()==0)
            desc = h.curHeader;
        if (hasTooManyCaps(desc))
            desc = desc.toLowerCase();

        if (!isLarge) {
            checkDescription(desc);

            // sort revisions from highest to lowest date
            Collections.sort(h.revDates,Collections.reverseOrder());
            for (String date: h.revDates)
                checkRevDate(date);
            checkMethod(h.curMethod);
        }

        return h;
    }

    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            ResultSet rs;

            BufferedReader infile = null;

            File pdbDir = new File(argv[0]);
            if (!pdbDir.exists()) {
                System.exit(0);
            }
            if (pdbDir.isFile()) {
                System.out.println("working on file "+argv[0]);
                infile = IO.openReader(argv[0]);
                java.util.Date d = new java.util.Date(pdbDir.lastModified());
                String obsDate = sqlDateFormat.format(d);
                String buffer = infile.readLine();
                while (buffer != null) {
                    if ((buffer.endsWith(".xml.gz")) &&
                        (Character.isDigit(buffer.charAt(0)))) {
                        String pdbCode = buffer.substring(0,4);
                        curEntryID = LocalSQL.lookupPDB(pdbCode);
                        checkObsDate(obsDate);
                        /*
                          int id = lookupOrCreateEntry(pdbCode);
                          stmt.executeUpdate("update pdb_entry set obsolete_date=\""+
                          obsDate+
                          "\" where id="+
                          id);
                        */
                    }
                    buffer = infile.readLine();
                }
                System.exit(0);
            }

            // otherwise, normal directory:
            System.out.println("working in "+argv[0]);
            System.out.flush();
            File[] pdbs = pdbDir.listFiles();
            if (pdbs==null)
                System.exit(0);
            for (File pdb : pdbs) {
                String fileName = StringUtil.replace(pdb.getAbsolutePath(),"/mnt/net/imperial.jmcnet/data","");
                fileName = StringUtil.replace(fileName,"/mnt/net/ipa.jmcnet/data","");

                if (fileName.endsWith(".Z"))
                    continue;

                int i = fileName.lastIndexOf(".xml.gz");
                if (i==-1)
                    continue;
                String pdbCode = fileName.substring(i-4,i);
                System.out.println("parsing "+pdbCode);
                System.out.flush();

                // int id = lookupOrCreateEntry(pdbCode);
                curEntryID = LocalSQL.lookupPDB(pdbCode);

                /*
                // make it non-obsolete (pending what's in file)
                stmt.executeUpdate("update pdb_entry set obsolete_date=null where id="+
                id);
                */

                java.util.Date d = new java.util.Date(pdb.lastModified());
                curFileDate = sqlDateFormat.format(d);

                isLarge = false;
                String pdbName = StringUtil.replace(fileName,"/xml-","/");
                pdbName = StringUtil.replace(pdbName,pdbCode+".xml","pdb"+pdbCode+".ent");
                File pdbFile = new File(pdbName);
                if (!pdbFile.exists()) {
                    pdbName = StringUtil.replace(fileName,"/xml-","/bundle-");
                    pdbName = StringUtil.replace(pdbName,"/obs-","/");
                    pdbName = StringUtil.replace(pdbName,".xml","-pdb-bundle.tar");
                    pdbFile = new File(pdbName);
                    if (pdbFile.exists())
                        isLarge = true;
                    else
                        continue; // skip if no PDB-style entry found
                }

                parseXMLFile(fileName);
            }
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
