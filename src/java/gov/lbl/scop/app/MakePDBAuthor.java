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

/**
   Find author of pdb releases w/o authors, for which we have a local
   copy
*/
public class MakePDBAuthor {
    public static class PDBMLHandler extends DefaultHandler {
        private boolean inName = false;
        private String currentName = "";
        public Vector<String> authors = new Vector<String>();

        public void startElement(String uri,
                                 String localName,
                                 String qName,
                                 Attributes attributes) {
            if (qName.equals("PDBx:audit_author")) {
                inName = true;
                if (attributes != null) {
                    String name = attributes.getValue("name");
                    if (name != null)
                        currentName = name;
                }
            }
        }

        public void endElement(String uri,
                               String localName,
                               String qName) {
            if (qName.equals("PDBx:audit_author")) {
                authors.add(currentName.trim());
                currentName = "";
                inName = false;
            }
        }
	
        public void characters(char[] ch,
                               int start,
                               int length) {
            if (inName) {
                currentName += new String(ch, start, length);
            }
        }
    }

    /**
       note:  pdb_author is not case sensitive
    */
    final public static int lookupOrCreateAuthor(String name) throws Exception {
        int rv = 0;
        Statement stmt = LocalSQL.createStatement();
        name = StringUtil.replace(name,"\"","\\\"");
        ResultSet rs = stmt.executeQuery("select id from pdb_author where name=\""+name+"\"");
        if (rs.next()) {
            rv = rs.getInt(1);
        }
        else {
            stmt.executeUpdate("insert into pdb_author values (null, \""+
                               name+"\", null)",
                               Statement.RETURN_GENERATED_KEYS);
            rs = stmt.getGeneratedKeys();
            rs.next();
            rv = rs.getInt(1);
        }
        stmt.close();
        return rv;
    }

    /**
       find authors for an individual PDB release
    */
    final public static void findAuthors(int pdbReleaseID) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select xml_path from pdb_local where pdb_release_id="+pdbReleaseID);
        if (!rs.next()) {
            stmt.close();
            return;
        }
        String xml = rs.getString(1);
        rs.close();

        System.out.println("finding authors for "+xml);
        System.out.flush();

        BufferedReader infile = IO.openReader(xml);
        PDBMLHandler h = new PDBMLHandler();

        SAXParserFactory factory
            = SAXParserFactory.newInstance();
        factory.setValidating(false);
        SAXParser parser = factory.newSAXParser();
	
        parser.parse(new InputSource(infile), h);

        for (String s : h.authors) {
            // System.out.println("author: "+s);
            if ((s!=null) && (s.length() > 0)) {
                int authorID = lookupOrCreateAuthor(s);
                if (authorID > 0) 
                    stmt.executeUpdate("insert into pdb_release_author values (NULL, "+
                                       pdbReleaseID+", "+
                                       authorID+")");
            }
        }
	
        stmt.close();
    }

    
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            Statement stmt2 = LocalSQL.createStatement();
	    
            ResultSet rs = stmt.executeQuery("select pdb_release_id from pdb_local where xml_path is not null and pdb_release_id not in (select distinct pdb_release_id from pdb_release_author)");
            while (rs.next()) {
                int id = rs.getInt(1);
                findAuthors(id);
            }
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
