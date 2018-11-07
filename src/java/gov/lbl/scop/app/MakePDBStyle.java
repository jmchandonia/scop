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
import org.strbio.local.Program;
import org.strbio.IO;
import gov.lbl.scop.local.*;
import gov.lbl.scop.util.SPACI;

/**
   Make PDB-style files for ASTRAL domains
*/
public class MakePDBStyle {
    /**
       fix last-update date for a given node and file path
    */
    final public static void fixLastUpdate(int nodeID,
                                           String fileName)
        throws Exception {
        Statement stmt = LocalSQL.createStatement();
        // was there an old pdb-style file with same sid/sunid?
        ResultSet rs = stmt.executeQuery("select l.file_path, r.version from scop_node_pdbstyle l, scop_node n1, scop_node n2, scop_release r where n1.release_id=n2.release_id-1 and n2.id="+nodeID+" and n1.sid=n2.sid and n1.level_id=n2.level_id and n1.sunid=n2.sunid and l.node_id=n1.id and l.last_update_id=r.id");
        if (rs.next()) {
            String oldFileName = rs.getString(1);
            String oldUpdateVersion = rs.getString(2);

            Program grep = new Program("grep");
            Program diff = new Program("diff");

            File oldFileData = File.createTempFile("mpdb",null);
            File newFileData = File.createTempFile("mpdb",null);
            File diffFile = File.createTempFile("mpdb",null);

            String[] inputs = new String[6];
            inputs[0] = "-v";
            inputs[1] = "-e";
            inputs[2] = "^REMARK";
            inputs[3] = "-e";
            inputs[4] = "^HEADER";
            inputs[5] = oldFileName;
            grep.setInput(null);
            OutputStream os = new FileOutputStream(oldFileData);
            grep.setOutput(os);
            grep.setError(null);
            grep.run(inputs);
            os.close();

            inputs[5] = fileName;
            os = new FileOutputStream(newFileData);
            grep.setOutput(os);
            grep.run(inputs);
            os.close();

            inputs = new String[3];	    
            inputs[0] = "-q";
            inputs[1] = oldFileData.getPath();
            inputs[2] = newFileData.getPath();
            diff.setInput(null);
            os = new FileOutputStream(diffFile);
            diff.setOutput(os);
            diff.setError(null);
            diff.run(inputs);
            os.close();

            if (diffFile.length()==0) {
                // update data-updated line in new file
                File newFile = new File(fileName);
                File newFileBak = new File(fileName+".bak");
                newFile.renameTo(newFileBak);

                PrintfWriter outfile = new PrintfWriter(fileName);
                BufferedReader infile = IO.openReader(fileName+".bak");
                String buffer = infile.readLine();
                while (buffer != null) {
                    if (buffer.startsWith("REMARK  99 ASTRAL Data-updated-release:")) {
                        outfile.flush();
                        outfile.printf("REMARK  99 ASTRAL Data-updated-release: %s",oldUpdateVersion);
                        outfile.flush();
                    }
                    else 
                        outfile.write(buffer);
                    outfile.write('\n');
                    buffer = infile.readLine();
                }
                outfile.flush();
                outfile.close();

                newFileBak.delete();
            }
		    
            oldFileData.delete();
            newFileData.delete();
            diffFile.delete();
        }
        rs.close();
        stmt.close();
    }

    /**
       Update SCOP to SCOPe, change version numbers in header
    */
    final public static void fixDBName(String fileName)
        throws Exception {

        // update headers in new file
        File newFile = new File(fileName);
        File newFileBak = new File(fileName+".bak");
        newFile.renameTo(newFileBak);

        BufferedReader infile = IO.openReader(fileName+".bak");
        PrintfWriter outfile = new PrintfWriter(fileName);
        String buffer = infile.readLine();
        boolean done = false;
        while (buffer != null) {
            if (!done) {
                if ((buffer.startsWith("REMARK  99")) ||
                    (buffer.startsWith("HEADER"))) {
                    if (buffer.indexOf("SCOPe") > -1)
                        done = true;
                    if (!done) {
                        buffer = StringUtil.replace(buffer,"SCOP","SCOPe");
                        buffer = StringUtil.replace(buffer,"1.75A","2.01");
                        buffer = StringUtil.replace(buffer,"1.75B","2.02");
                        buffer = StringUtil.replace(buffer,"1.75C","2.03");
                        buffer = StringUtil.replace(buffer,"     ","    ");
                    }
                }
                else if (buffer.startsWith("ATOM"))
                    done = true;
            }
            outfile.write(buffer);
            outfile.write('\n');
            buffer = infile.readLine();
        }
        outfile.flush();
        outfile.close();
        infile.close();

        newFileBak.delete();
    }
    
    /**
       try to make a pdb-style file for a given ASTRAL domain
    */
    final public static void makePDBStyle(int domainID)
        throws Exception {
        Statement stmt = LocalSQL.createStatement();

        ResultSet rs = stmt.executeQuery("select style_id, node_id from astral_domain where id="+domainID);
        rs.next();
        int styleID = rs.getInt(1);
        int nodeID = rs.getInt(2);
        rs.close();

        // if ASTEROIDS, do that instead
        if (styleID == 4) {
            rs = stmt.executeQuery("select id from asteroid where domain_id="+domainID);
            if (rs.next())
                makePDBStyleASTEROID(rs.getInt(1));
            rs.close();
            return;
        }
	
        rs = stmt.executeQuery("select sid, sunid, sccs, release_id, description from scop_node where id="+nodeID);
        rs.next();
        String sid = rs.getString(1);
        int sunid = rs.getInt(2);
        String sccs = rs.getString(3);
        int scopReleaseID = rs.getInt(4);
        String description = rs.getString(5);
        rs.close();

        System.out.println("Making PDB-style file for domain "+sid);

        // find pdb release
        rs = stmt.executeQuery("select l.pdb_release_id, l.pdb_path from pdb_local l, pdb_chain c, link_pdb m where l.pdb_release_id=c.pdb_release_id and c.id=m.pdb_chain_id and m.node_id="+nodeID);
        if (!rs.next()) {
            rs.close();
            stmt.close();
            return;
        }
        int pdbReleaseID = rs.getInt(1);
        String pdbPath = rs.getString(2);
        rs.close();
	    
        // set up raf file
        File rafFile = File.createTempFile("raf",null);
        rafFile.delete();
        File rafIndexFile = File.createTempFile("ridx",null);
        rafIndexFile.delete();
        PrintfWriter ow = new PrintfWriter(rafFile.getPath());
        // rs = stmt.executeQuery("select line from raf where pdb_chain_id in (select pdb_chain_id from link_pdb where node_id="+nodeID+") and first_release_id<="+scopReleaseID+" and last_release_id>="+scopReleaseID);
        rs = stmt.executeQuery("select r.line from raf r, link_pdb l where l.node_id="+nodeID+" and l.pdb_chain_id=r.pdb_chain_id and r.first_release_id<="+scopReleaseID+" and r.last_release_id>="+scopReleaseID);
        while (rs.next()) {
            ow.write(rs.getString(1));
            ow.newLine();
        }
        ow.flush();
        ow.close();

        // get comments
        String comments = "";
        rs = stmt.executeQuery("select description from scop_comment where is_autogenerated=0 and node_id="+nodeID);
        while (rs.next())
            comments += rs.getString(1)+" ; ";
        rs.close();
        // System.err.println("comments "+comments);

        // get SPACI scores
        SPACI.SPACILine spacis = new SPACI.SPACILine(pdbReleaseID);
        spacis.lookupFromRelease(stmt);
        // System.err.println("spaci "+spacis.spaci);
        // System.err.println("aerospaci "+spacis.aerospaci);

        // set up pdb-style file name and dir
        rs = stmt.executeQuery("select version from scop_release where id="+scopReleaseID);
        rs.next();
        String scopRelease = rs.getString(1);
        rs.close();
        File hashDir = new File("/lab/proj/astral/pdbstyle/"+scopRelease+"/"+sid.substring(2,4));
        if (!hashDir.isDirectory())
            hashDir.mkdirs();
        File outFile = new File(hashDir+File.separator+sid+".ent");
        File outFile2 = new File(hashDir+File.separator+sid+".ent.gz");
        if (outFile.exists())
            outFile.delete();
        if (outFile2.exists())
            outFile2.delete();

        // deal with bundles
        File unBundled = null;
        if (pdbPath.endsWith(".pdb-bundle.tar.gz")) {
            unBundled = ParsePDBXML.unBundle(pdbPath);
            pdbPath = unBundled.getAbsolutePath();
        }

        // run it
        PDBFilter.makePDBStyle(pdbPath,
                               outFile.getPath(),
                               rafFile.getPath(),
                               rafIndexFile.getPath(),
                               scopRelease,
                               sid,
                               description,
                               sunid,
                               sccs,
                               comments,
                               spacis,
                               null,
                               null);

        if (unBundled != null)
            unBundled.delete();

        /*
        // compress file
        Program gz = new Program("gzip");
        String[] inputs = new String[2];
        inputs[0] = "-9";
        inputs[1] = outFile.getPath();
        gz.setInput(null);
        gz.setOutput(null);
        gz.setError(null);
        gz.run(inputs,null,hashDir);

        if (outFile.exists())
	    throw new Exception("PDB-style file failed to zip");
        */

        rafFile.delete();
        rafIndexFile.delete();

        fixLastUpdate(nodeID,
                      outFile.getPath());

        FindPDBStyle.linkPDBStyle(nodeID,
                                  outFile.getPath());
	    
        stmt.close();
    }

    /**
       Try to make a PDB-style file for an ASTEROID
    */
    final public static void makePDBStyleASTEROID(int asteroidID)
        throws Exception {
        Statement stmt = LocalSQL.createStatement();

        ResultSet rs = stmt.executeQuery("select chain_id, sid, header, description, pfam_release_id, scop_release_id from asteroid where id="+asteroidID);
        rs.next();
        int astralChainID = rs.getInt(1);
        String sid = rs.getString(2);
        String header = rs.getString(3);
        String description = rs.getString(4);
        int pfamReleaseID = rs.getInt(5);
        int scopReleaseID = rs.getInt(6);
        rs.close();

        if (description == null) {
            stmt.close();
            return;
        }

        System.out.println("Making PDB-style file for ASTEROID "+sid);

        // was an alternative description used in checking this ASTEROID?
        rs = stmt.executeQuery("select modified_description from asteroid_match where asteroid_id="+asteroidID);
        if (rs.next())
            description = rs.getString(1);
        rs.close();

        rs = stmt.executeQuery("select version from pfam_release where id="+pfamReleaseID);
        rs.next();
        String pfamRelease = rs.getString(1);
        rs.close();

        // find pdb release
        rs = stmt.executeQuery("select l.pdb_release_id, l.pdb_path, r.line from pdb_local l, pdb_chain c, raf r, astral_chain ac where l.pdb_release_id=c.pdb_release_id and c.id=r.pdb_chain_id and r.id=ac.raf_id and ac.id="+astralChainID);
        if (!rs.next()) {
            rs.close();
            stmt.close();
            return;
        }
        int pdbReleaseID = rs.getInt(1);
        String pdbPath = rs.getString(2);
        String rafLine = rs.getString(3);
        rs.close();
	    
        // set up raf file
        File rafFile = File.createTempFile("raf",null);
        rafFile.delete();
        File rafIndexFile = File.createTempFile("ridx",null);
        rafIndexFile.delete();
        PrintfWriter ow = new PrintfWriter(rafFile.getPath());
        ow.write(rafLine);
        ow.newLine();
        ow.flush();
        ow.close();

        // parse sccs from ASTEROIDS header
        int pos1 = header.indexOf(']');
        int pos2 = header.indexOf(' ',pos1);
        String sccs = header.substring(pos1+1, pos2);

        // remove redundant info from ASTEROIDS header
        pos1 = header.lastIndexOf(" (");
        header = header.substring(0,pos1);
        header = StringUtil.replace(header," ASTEROIDS "," ");

        // get SPACI scores
        SPACI.SPACILine spacis = new SPACI.SPACILine(pdbReleaseID);
        spacis.lookupFromRelease(stmt);

        // set up pdb-style file name and dir
        rs = stmt.executeQuery("select version from scop_release where id="+scopReleaseID);
        rs.next();
        String scopRelease = rs.getString(1);
        rs.close();
        File hashDir = new File("/lab/proj/astral/pdbstyle/"+scopRelease+"/"+sid.substring(2,4));
        if (!hashDir.isDirectory())
            hashDir.mkdirs();
        File outFile = new File(hashDir+File.separator+sid+".ent");
        File outFile2 = new File(hashDir+File.separator+sid+".ent.gz");
        if (outFile.exists())
            outFile.delete();
        if (outFile2.exists())
            outFile2.delete();

        // deal with bundles
        File unBundled = null;
        if (pdbPath.endsWith(".pdb-bundle.tar.gz")) {
            unBundled = ParsePDBXML.unBundle(pdbPath);
            pdbPath = unBundled.getAbsolutePath();
        }

        // run it
        PDBFilter.makePDBStyle(pdbPath,
                               outFile.getPath(),
                               rafFile.getPath(),
                               rafIndexFile.getPath(),
                               scopRelease,
                               sid,
                               description,
                               0,
                               sccs,
                               "",
                               spacis,
                               pfamRelease,
                               header);

        if (unBundled != null)
            unBundled.delete();

        /*
        // compress file
        Program gz = new Program("gzip");
        String[] inputs = new String[2];
        inputs[0] = "-9";
        inputs[1] = outFile.getPath();
        gz.setInput(null);
        gz.setOutput(null);
        gz.setError(null);
        gz.run(inputs,null,hashDir);

        if (outFile.exists())
	    throw new Exception("PDB-style file failed to zip");
        */

        // save path
        stmt.executeUpdate("delete from asteroid_pdbstyle where asteroid_id="+asteroidID);
        stmt.executeUpdate("insert into asteroid_pdbstyle values ("+
                           asteroidID+", \""+
                           outFile.getPath()+"\")");

        rafFile.delete();
        rafIndexFile.delete();
        stmt.close();
    }

    /**
       check that all pdb-style files actually exist
    */
    final public static void purgeDeletedPDBStyle() throws Exception {
        Statement stmt = LocalSQL.createStatement();
        Statement stmt2 = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select node_id, file_path from scop_node_pdbstyle");
        while (rs.next()) {
            int nodeID = rs.getInt(1);
            String path = rs.getString(2);
            File f = new File(path);
            if (!f.exists())
                stmt2.executeUpdate("delete from scop_node_pdbstyle where node_id="+nodeID);
        }
        rs.close();
        rs = stmt.executeQuery("select asteroid_id, file_path from asteroid_pdbstyle");
        while (rs.next()) {
            int asteroidID = rs.getInt(1);
            String path = rs.getString(2);
            File f = new File(path);
            if (!f.exists())
                stmt2.executeUpdate("delete from asteroid_pdbstyle where asteroid_id="+asteroidID);
        }
        rs.close();
        stmt.close();
        stmt2.close();
    }
    
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            Statement stmt2 = LocalSQL.createStatement();
            ResultSet rs;

            // makePDBStyle(1288271);
            // makePDBStyle(1551704);
            // System.exit(0);

            if (argv[0].startsWith("A")) {
                int asteroidID = StringUtil.atoi(argv[0],1);
                makePDBStyleASTEROID(asteroidID);
                System.exit(0);
            }
            else if (argv[0].startsWith("D")) {
                int domainID = StringUtil.atoi(argv[0],1);
                makePDBStyle(domainID);
                System.exit(0);
            }
            else if (argv[0].startsWith("N")) {
                int nodeID = StringUtil.atoi(argv[0],1);
                rs = stmt.executeQuery("select id from astral_domain where node_id="+nodeID);
                while (rs.next()) {
                    int domainID = rs.getInt(1);
                    makePDBStyle(domainID);
                }
                System.exit(0);
            }
            else if (argv[0].startsWith("F")) {
                int nodeID = StringUtil.atoi(argv[0],1);
                rs = stmt.executeQuery("select file_path from scop_node_pdbstyle where node_id="+nodeID);
                rs.next();
                String fileName = rs.getString(1);
                fixLastUpdate(nodeID,
                              fileName);
                FindPDBStyle.linkPDBStyle(nodeID,
                                          fileName);
                System.exit(0);
            }
            else if (argv[0].startsWith("E")) {
                int nodeID = StringUtil.atoi(argv[0],1);
                rs = stmt.executeQuery("select file_path from scop_node_pdbstyle where node_id="+nodeID);
                rs.next();
                String fileName = rs.getString(1);
                fixDBName(fileName);
                System.exit(0);
            }
            else if (argv[0].equals("dbname")) {
                int scopReleaseID = LocalSQL.lookupSCOPRelease(argv[1]);
                rs = stmt.executeQuery("select distinct(f.file_path) from scop_node_pdbstyle f, scop_node n where f.node_id=n.id and n.release_id="+scopReleaseID);
                while (rs.next()) {
                    String fileName = rs.getString(1);
                    fixDBName(fileName);
                }
                System.exit(0);
            }    
            else if (argv[0].equals("purge")) {
                purgeDeletedPDBStyle();
                System.exit(0);
            }

            int scopReleaseID = LocalSQL.lookupSCOPRelease(argv[0]);
            if (scopReleaseID==0)
                throw new Exception("SCOP version not found: "+argv[0]);

            // include rejects, if they have any sequence:
            rs = stmt.executeQuery("select d.id from astral_domain d, scop_node n, astral_seq s where d.seq_id=s.id and d.node_id=n.id and length(s.seq) > 0 and d.source_id=2 and (d.style_id=1 or d.style_id=3) and n.id not in (select node_id from scop_node_pdbstyle) and n.release_id="+scopReleaseID);
            while (rs.next()) {
                LocalSQL.newJob(17,
                                rs.getInt(1),
                                null,
                                stmt2);
            }
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
