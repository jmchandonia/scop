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
package gov.lbl.scop.util;

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
import gov.lbl.scop.app.ParsePDBXML;
import gov.lbl.scop.local.LocalSQL;

/**
   Utilities related to creating thumbnails for SCOP

   
*/
public class Thumbnail {
    /**
       Pymol cartoons break on multi-models, so eliminate all but 1st
       model.  Renumber monomers consecutively within each chain, and
       return map of original ids to new ids.  If chainSet is not null,
       eliminate all chains other than those listed.  Returns null
       if no ATOM or HETATM records were found.

       As a kludge, finds CA-only structures (ones that are at least
       50% CA), and sets "caOnly" as a key.
    */
    final public static HashMap<String,String> mungePDB(String inputPath,
                                                        String outputPath,
                                                        String chainSet)
        throws Exception {
        HashMap<String,String> rv = new HashMap<String,String>();
        HashMap<Character,String> nextID = new HashMap<Character,String>();

        BufferedReader infile = IO.openReader(inputPath);
        PrintfWriter outfile = new PrintfWriter(outputPath);
	
        boolean foundModel = false;
        boolean foundAtom = false;
        String buffer = null;
        String lastResCode = null;
        int nAtoms = 0;
        int nCA = 0;
        while (infile.ready()) {
            buffer = infile.readLine();
            if (buffer==null)
                break;
            if (foundModel)
                continue;
            if (buffer.startsWith("ENDMDL")) {
                foundModel = true;
            }
            else {
                if (!buffer.startsWith("MODEL")) {
                    if (((buffer.startsWith("ATOM")) ||
                         (buffer.startsWith("TER")) ||
                         (buffer.startsWith("HETATM"))) &&
                        (buffer.length() >= 27)) {
                        char curChain = buffer.charAt(21);
                        if ((chainSet==null) ||
                            (chainSet.indexOf(curChain) > -1)) {
                            nAtoms++;
                            if (buffer.substring(13,15).equals("CA"))
                                nCA++;
			    
                            foundAtom = true;
                            // remap residue number
                            String oldResCode = buffer.substring(22,27).trim();
                            String newResCode = rv.get(curChain+oldResCode);
                            if (newResCode==null) {
                                newResCode = nextID.get(new Character(curChain));
                                if (newResCode==null)
                                    newResCode = "0";
				
                                // add 1 to last code, using
                                // hybrid-36 encoding
                                int digits = 0;
                                char letter = 'A'-1;
                                if (Character.isDigit(newResCode.charAt(0))) {
                                    digits = StringUtil.atoi(newResCode);
                                }
                                else {
                                    letter = newResCode.charAt(0);
                                    digits = StringUtil.atoi(newResCode,1);
                                }
                                digits++;
                                if ((digits==10000) ||
                                    ((digits == 1000) &&
                                     (letter >= 'A'))) {
                                    letter++;
                                    digits = 0;
                                }
                                if (letter >= 'A') {
                                    char[] ch = new char[4];
                                    StringUtil.sprintf(ch,"%03d",digits);
                                    newResCode = ""+letter+ch;
                                }
                                else {
                                    newResCode = ""+digits;
                                }

                                nextID.put(new Character(curChain),
                                           newResCode);
                                rv.put(curChain+oldResCode,
                                       newResCode);
                                // System.out.println(curChain+oldResCode+" -> "+newResCode);
                                if ((lastResCode != null) &&
                                    (lastResCode.charAt(0)==curChain)) {
                                    rv.put("N"+lastResCode,
                                           curChain+oldResCode);
                                    rv.put("P"+curChain+oldResCode,
                                           lastResCode);
                                    // System.out.println("N"+lastResCode+" -> "+curChain+oldResCode);
                                    // System.out.println("P"+curChain+oldResCode+" -> "+lastResCode);
                                }
                            }
                            lastResCode = curChain+oldResCode;

                            outfile.write(buffer.substring(0,22));
                            outfile.printf("%4s ",newResCode);
                            outfile.write(buffer.substring(27));
                            outfile.write('\n');
                        }
                    }
                }
            }
        }
        infile.close();
        outfile.close();

        if (!foundAtom)
            rv = null;
        else if (nCA >= nAtoms * 0.5)
            rv.put("caOnly","1");

        return rv;
    }

    /**
       shortcut to find out whether a domain is CA-only
    */
    final public static boolean isCAOnly(int nodeID) throws Exception {
        String pdbPath = LocalSQL.getPDBStylePath(nodeID);
        if (pdbPath==null)
            return false;
	
        File f = File.createTempFile("caonly",".ent");
        f.delete();
        HashMap<String,String> mm = mungePDB(pdbPath,
                                             f.getPath(),
                                             null);
        f.delete();
        if (mm==null)
            return false;
        if (mm.get("caOnly")!=null)
            return true;
        return false;
    }

    /**
       get the path corresponding to a particular node
    */
    final public static String getPath(int nodeID,
                                       boolean isASTEROID) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs;

        if (isASTEROID)
            rs = stmt.executeQuery("select a.sid, r.version from asteroid a, scop_release r where a.id="+nodeID+" and a.scop_release_id=r.id");
        else
            rs = stmt.executeQuery("select n.sid, r.version from scop_node n, scop_release r where n.id="+nodeID+" and n.release_id=r.id");
        rs.next();
        String sid = rs.getString(1);
        String hash = sid.substring(2,4);
        String release = rs.getString(2);
        rs.close();
        stmt.close();
        return ("/lab/proj/astral/thumbs/"+release+"/"+hash+"/"+sid+"/");
    }

    /**
       make thumbnail for a given asteroid or node.
       whichImage = "domain" "chain" or "structure"
    */
    final public static void makeForDomain(int nodeID,
                                           boolean isASTEROID,
                                           String whichImage,
                                           boolean needsCPU) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs;

        if (isASTEROID)
            rs = stmt.executeQuery("select a.sid, r.version, a.description from asteroid a, scop_release r where a.id="+nodeID+" and a.scop_release_id=r.id");
        else
            rs = stmt.executeQuery("select n.sid, r.version, n.description from scop_node n, scop_release r where n.id="+nodeID+" and n.release_id=r.id");
        rs.next();
        String sid = rs.getString(1);
        String release = rs.getString(2);
        String description = rs.getString(3);
        rs.close();
	
        // was an alternative description used in checking this ASTEROID?
        if (isASTEROID) {
            rs = stmt.executeQuery("select modified_description from asteroid_match where asteroid_id="+nodeID);
            if (rs.next())
                description = rs.getString(1);
            rs.close();
        }
	
        System.out.println("making "+whichImage+" thumbnail for "+sid+" from "+
                           (isASTEROID ? "ASTEROIDS" : "SCOP")+
                           " "+release);
	
        String pdbPath = null;
        String chainSet = null;
        boolean caOnly = false;
        if (whichImage.equals("domain")) {
            if (isASTEROID)
                pdbPath = LocalSQL.getPDBStylePathASTEROID(nodeID);
            else
                pdbPath = LocalSQL.getPDBStylePath(nodeID);
            description = null;
        }
        else if (whichImage.equals("chain")) {
            chainSet = "";
            if (isASTEROID) 
                rs = stmt.executeQuery("select c.chain, l.pdb_path from asteroid a, astral_chain ac, raf r, pdb_chain c, pdb_local l where a.id="+nodeID+" and a.chain_id=ac.id and ac.raf_id=r.id and r.pdb_chain_id=c.id and c.pdb_release_id=l.pdb_release_id");
            else
                rs = stmt.executeQuery("select c.chain, l.pdb_path from link_pdb m, pdb_chain c, pdb_local l where m.node_id="+nodeID+" and m.pdb_chain_id=c.id and c.pdb_release_id=l.pdb_release_id");
            while (rs.next()) {
                chainSet += rs.getString(1);
                pdbPath = rs.getString(2);
            }
            rs.close();
            caOnly = isCAOnly(nodeID);
        }
        else if (whichImage.equals("structure")) {
            if (isASTEROID) 
                rs = stmt.executeQuery("select l.pdb_path from asteroid a, astral_chain ac, raf r, pdb_chain c, pdb_local l where a.id="+nodeID+" and a.chain_id=ac.id and ac.raf_id=r.id and r.pdb_chain_id=c.id and c.pdb_release_id=l.pdb_release_id limit 1");
            else
                rs = stmt.executeQuery("select l.pdb_path from link_pdb m, pdb_chain c, pdb_local l where m.node_id="+nodeID+" and m.pdb_chain_id=c.id and c.pdb_release_id=l.pdb_release_id limit 1");
            if (rs.next())
                pdbPath = rs.getString(1);
            rs.close();
            caOnly = isCAOnly(nodeID);
        }

        String outputPath = getPath(nodeID,isASTEROID);
	
        File f = File.createTempFile(whichImage,".ent");
        f.delete();

        // deal with bundles
        File unBundled = null;
        if (pdbPath.endsWith(".pdb-bundle.tar.gz")) {
            unBundled = ParsePDBXML.unBundle(pdbPath);
            pdbPath = unBundled.getAbsolutePath();
        }

        HashMap<String,String> mm = mungePDB(pdbPath,
                                             f.getPath(),
                                             chainSet);

        if (unBundled != null)
            unBundled.delete();
	
        if (mm==null) {
            f.delete();
            throw new Exception("Couldn't find any atoms for "+whichImage+" "+outputPath);
        }

        if (mm.get("caOnly")!=null)
            caOnly = true;
	
        Ovop o = new Ovop();
        File optimized = o.optimizeView(f);
        if ((optimized == null) || (!optimized.exists())) {
            System.out.println("Warning - ovop crashed on "+whichImage+" "+outputPath);
            optimized = f;
        }
        else {
            f.delete();
        }

        ProteinSet ps = new ProteinSet();
        ps.read(optimized.getPath());

        double angle = optimalRotation(ps);

        f = new File(outputPath);
        f.mkdirs();

        // make one large image of chain, with node hilighted
        makeForPDBFile(optimized.getPath(),
                       angle,
                       f.getPath()+"/"+whichImage+".png",
                       description,
                       mm,
                       needsCPU,
                       false,
                       caOnly);

        // crop and scale it
        makeThumbs(f.getPath()+"/"+whichImage+".png",
                   f.getPath()+"/"+whichImage.charAt(0)+"s.png",
                   f.getPath()+"/"+whichImage.charAt(0)+"l.png");

        saveThumb(nodeID,
                  isASTEROID,
                  whichImage+"_small",
                  f.getPath()+"/"+whichImage.charAt(0)+"s.png");

        saveThumb(nodeID,
                  isASTEROID,
                  whichImage+"_large",
                  f.getPath()+"/"+whichImage.charAt(0)+"l.png");

        File f2 = new File(f.getPath()+"/"+whichImage+".png");
        f2.delete();

        // if domain, make tiny thumbnail as well
        if (whichImage.equals("domain")) {
            // make one large image of chain, with node hilighted
            makeForPDBFile(optimized.getPath(),
                           angle,
                           f.getPath()+"/domain.png",
                           description,
                           mm,
                           needsCPU,
                           true,
                           caOnly);

            // crop and scale it
            makeThumbTiny(f.getPath()+"/domain.png",
                          f.getPath()+"/dt.png");

            saveThumb(nodeID,
                      isASTEROID,
                      "domain_tiny",
                      f.getPath()+"/dt.png");

            f = new File(f.getPath()+"/domain.png");
            f.delete();
        }

        optimized.delete();
        stmt.close();
    }
    
    /**
       make tiny thumbnail for a given node
    */
    final public static void makeForDomainTiny(int nodeID) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs;

        rs = stmt.executeQuery("select n.sid, r.version, n.description from scop_node n, scop_release r where n.id="+nodeID+" and n.release_id=r.id");
        rs.next();
        String sid = rs.getString(1);
        String release = rs.getString(2);
        String description = rs.getString(3);
        rs.close();
	
        System.out.println("making tiny domain thumbnail for "+sid+" from SCOP "+release);
	
        String pdbPath = null;
        String chainSet = null;
        boolean caOnly = false;
        pdbPath = LocalSQL.getPDBStylePath(nodeID);
        description = null;

        String outputPath = getPath(nodeID,false);
	
        File f = File.createTempFile("domain",".ent");
        f.delete();

        HashMap<String,String> mm = mungePDB(pdbPath,
                                             f.getPath(),
                                             chainSet);
        if (mm==null) {
            f.delete();
            throw new Exception("Couldn't find any atoms for domain "+outputPath);
        }

        if (mm.get("caOnly")!=null)
            caOnly = true;
	
        Ovop o = new Ovop();
        File optimized = o.optimizeView(f);
        if ((optimized == null) || (!optimized.exists())) {
            System.out.println("Warning - ovop crashed on domain "+outputPath);
            optimized = f;
        }
        else {
            f.delete();
        }

        ProteinSet ps = new ProteinSet();
        ps.read(optimized.getPath());

        double angle = optimalRotation(ps);

        f = new File(outputPath);
        f.mkdirs();

        // make one large image of chain, with node hilighted
        makeForPDBFile(optimized.getPath(),
                       angle,
                       f.getPath()+"/domain.png",
                       description,
                       mm,
                       true,
                       true,
                       caOnly);

        // crop and scale it
        makeThumbTiny(f.getPath()+"/domain.png",
                      f.getPath()+"/dt.png");

        saveThumb(nodeID,
                  false,
                  "domain_tiny",
                  f.getPath()+"/dt.png");

        f = new File(f.getPath()+"/domain.png");
        f.delete();

        optimized.delete();
        stmt.close();
    }
    
    /**
       copies a thumbnail from another node, which may or may not
       exist
    */
    final public static void copyThumb(int fromNodeID,
                                       int toNodeID,
                                       boolean isASTEROID,
                                       String whichThumbFrom,
                                       String whichThumbTo) throws Exception {
	
        Statement stmt = LocalSQL.createStatement();

        String idName = "node_id";
        String tableName = "scop_node_thumbnail";
        if (isASTEROID) {
            idName = "asteroid_id";
            tableName = "asteroid_thumbnail";
        }
	
        ResultSet rs = stmt.executeQuery("select "+idName+" from "+tableName+" where "+idName+"="+toNodeID);
        if (!rs.next()) {
            rs.close();
            stmt.executeUpdate("insert into "+tableName+"("+idName+") values ("+toNodeID+")");
        }
        else
            rs.close();

        int thumbID = 0;
        rs = stmt.executeQuery("select "+whichThumbFrom+" from "+tableName+" where "+idName+"="+fromNodeID);
        if (rs.next())
            thumbID = rs.getInt(1);
        rs.close();

        if (thumbID != 0) {
            stmt.executeUpdate("update "+tableName+" set "+whichThumbTo+"="+thumbID+" where "+idName+"="+toNodeID);
        }
	
        stmt.close();
    }
    
    final public static void saveThumb(int nodeID,
                                       boolean isASTEROID,
                                       String whichThumb,
                                       String fileName) throws Exception {
        Statement stmt = LocalSQL.createStatement();

        // get actual dimensions
        File tmpFile = File.createTempFile("imid",".txt");
        tmpFile.delete();
        Program id = new Program("/usr/bin/identify");
        OutputStream os = new PrintStream(tmpFile);
        id.setOutput(os);
        String[] args = new String[1];
        args[0] = fileName;
        id.run(args);
        os.flush();
        os.close();

        BufferedReader infile = IO.openReader(tmpFile.getPath());
        String buffer = infile.readLine();
        int pos = buffer.indexOf(" ");
        pos = buffer.indexOf(" ",pos+1);
        int width = StringUtil.atoi(buffer,pos+1);
        pos = buffer.indexOf("x",pos+1);
        int height = StringUtil.atoi(buffer,pos+1);
        infile.close();
        tmpFile.delete();

        stmt.executeUpdate("delete from thumbnail where image_path=\""+
                           fileName+"\"");
	
        stmt.executeUpdate("insert into thumbnail values (null, \""+
                           fileName+"\", "+
                           width+", "+
                           height+")",
                           Statement.RETURN_GENERATED_KEYS);
        ResultSet rs = stmt.getGeneratedKeys();
        rs.next();
        int thumbID = rs.getInt(1);
        rs.close();

        String idName = "node_id";
        String tableName = "scop_node_thumbnail";
        if (isASTEROID) {
            idName = "asteroid_id";
            tableName = "asteroid_thumbnail";
        }

        rs = stmt.executeQuery("select "+idName+" from "+tableName+" where "+idName+"="+nodeID);
        if (!rs.next()) {
            rs.close();
            stmt.executeUpdate("insert into "+tableName+"("+idName+") values ("+nodeID+")");
        }
        else
            rs.close();

        stmt.executeUpdate("update "+tableName+" set "+whichThumb+"="+thumbID+" where "+idName+"="+nodeID);
	
        stmt.close();
    }

    /**
       writes out pymol script for making a greyscale figure with
       domains highlighted
    */
    final public static void writePymolScript(File scriptFile,
                                              String pdbFileName,
                                              double rotationAngle,
                                              String[] descriptions,
                                              HashMap<String,String> mm,
                                              boolean colorBySS,
                                              boolean caOnly) throws Exception {

        int nDomains = descriptions.length;
        String[] pymolRegions = new String[nDomains];
        String[] pymolInnerRegions = new String[nDomains];
        for (int j=0; j<nDomains; j++) {
            String description = descriptions[j];
            if ((description != null) &&
                (mm != null)) {
                // translate description into selection criteria
                String[] regions = description.substring(5).split(",");
                pymolRegions[j] = "(";
                pymolInnerRegions[j] = "(";
                for (int i=0; i<regions.length; i++) {
                    char chain = ' ';
                    String region = regions[i];
                    if (region.indexOf(":")==1) {
                        chain = region.charAt(0);
                        region = region.substring(2);
                    }
                    // start and end of inner and outer region
                    String start1 = null;
                    String start2 = null;
                    String end1 = null;
                    String end2 = null;
                    if (region.length() > 1) { // to ignore "-"
                        int pos = region.indexOf('-');
                        if (pos==0)
                            pos = region.indexOf('-',1); // to skip negative ids
                        if (pos > 0) {
                            String start = region.substring(0,pos).trim();
                            String end = region.substring(pos+1).trim();

                            // translate start and end using map
                            start1 = mm.get(chain+start);
                            end1 = mm.get(chain+end);

                            // fix for 1-residue domains:
                            // try to extend by 1 residue for visualization
                            if ((start1 != null) &&
                                (end1 != null) &&
                                (start1.equals(end1))) {
                                String ts = mm.get("N"+chain+end);
                                if (ts != null)
                                    end1 = mm.get(ts);
                                else {
                                    ts = mm.get("P"+chain+start);
                                    if (ts != null)
                                        start1 = mm.get(ts);
                                }
                            }

                            String startN = mm.get("N"+chain+start);
                            while ((startN!=null) && (start2 == null)) {
                                start2 = mm.get(startN);
                                startN = mm.get("N"+startN);
                            }

                            String endP = mm.get("P"+chain+end);
                            while ((endP!=null) && (end2 == null)) {
                                end2 = mm.get(endP);
                                endP = mm.get("P"+endP);
                            }

                            /*
                              System.out.println("start = "+start);
                              System.out.println("end = "+end);
                              System.out.println("start1 = "+start1);
                              System.out.println("end1 = "+end1);
                              System.out.println("start2 = "+start2);
                              System.out.println("end2 = "+end2);
                              System.out.println("startN = "+mm.get("N"+chain+start));
                              System.out.println("endP = "+mm.get("P"+chain+end));
                            */
                        }
                    }
                    String pymolRegion = null;
                    String pymolInnerRegion = null;
                    if (chain==' ') {
                        pymolRegion = "(chain ''";
                        pymolInnerRegion = "(chain ''";
                    }
                    else {
                        pymolRegion = "(chain "+chain;
                        pymolInnerRegion = "(chain "+chain;
                    }
                    if ((start1 != null) &&
                        (end1 != null)) {
                        pymolRegion += " and resi "+start1+"-"+end1;

                        if ((start2 != null) &&
                            (end2 != null)) {
                            pymolInnerRegion += " and resi "+start2+"-"+end2;
                        }
                    }
                    pymolRegion += ")";
                    pymolInnerRegion += ")";
                    if (i>0) {
                        pymolRegions[j] += " or ";
                        pymolInnerRegions[j] += " or ";
                    }
                    pymolRegions[j] += pymolRegion;
                    pymolInnerRegions[j] += pymolInnerRegion;
                }
                pymolRegions[j] += ")";
                pymolInnerRegions[j] += ")";

                // fix to catch single-residue domains
                if ((pymolRegions[j].indexOf("resi") > -1) &&
                    (pymolInnerRegions[j].indexOf("resi") == -1))
                    pymolInnerRegions[j] = null;

                // System.out.println("debug: "+description+" -> "+pymolRegions[j]+", "+pymolInnerRegions[j]);
            }
        }

        // create pymol script
        PrintfWriter outfile = new PrintfWriter(scriptFile.getPath(),
                                                true);
        outfile.printf("load %s\n",pdbFileName);
        if (rotationAngle != 0.0)
            outfile.printf("turn z,%2.2f\n",rotationAngle);
        if (caOnly)
            outfile.printf("set cartoon_trace_atoms, 1\n");
        outfile.printf("cmd.zoom(\"all\",2.0,0,1)\n");
        if (pymolRegions[0] != null) {
            outfile.printf("v = cmd.get_view()\n");
            outfile.printf("set ignore_case, 0\n");
            for (int j=0; j<nDomains; j++)
                outfile.printf("create domain"+j+", %s\n",pymolRegions[j]);
            outfile.printf("set ignore_case, 1\n");
            outfile.printf("cmd.set_view(v)\n");
            outfile.printf("cmd.hide(\"everything\",\"all\")\n");
            outfile.printf("cmd.color(\"grey20\",\"all\")\n");
            outfile.printf("set cartoon_transparency, 0.8, all\n");
            outfile.printf("cmd.show(\"cartoon\",\"all\")\n");
            outfile.printf("cmd.remove(\"(solvent and (all))\")\n");
            outfile.printf("cmd.remove(\"hydro\")\n");
            outfile.printf("sele het and not resn mse\n");
            outfile.printf("cmd.hide(\"cartoon\",\"sele\")\n");
            outfile.printf("cmd.select(\"sele\",\"(byres (sele extend 1))\")\n");
            outfile.printf("set stick_transparency, 0.8, all\n");
            outfile.printf("cmd.show(\"sticks\",\"sele\")\n");
            for (int j=0; j<nDomains; j++) {
                if (pymolInnerRegions[j] != null) {
                    outfile.printf("set ignore_case, 0\n");
                    outfile.printf("sele %s\n",pymolInnerRegions[j]);
                    outfile.printf("set ignore_case, 1\n");
                    outfile.printf("cmd.hide(\"everything\",\"sele\")\n");
                }
            }
            for (int j=0; j<nDomains; j++)
                outfile.printf("set cartoon_transparency, 0, domain"+j+"\n");
            if (colorBySS) {
                outfile.printf("set_color sorange=[0.7, 0.4, 0]\n");
                outfile.printf("set_color spurple=[0.4, 0, 0.6]\n");
                for (int j=0; j<nDomains; j++) {
                    outfile.printf("cmd.color(\"sorange\", \"domain"+j+" and ss s\")\n");
                    outfile.printf("cmd.color(\"spurple\", \"domain"+j+" and ss h\")\n");
                    outfile.printf("cmd.color(\"grey20\", \"domain"+j+" and ss l+''\")\n");
                }
            }
            else {
                for (int j=0; j<nDomains; j++)
                    outfile.printf("cmd.spectrum(\"count\",selection=\"(domain"+j+")&e. c\")\n");
            }
            for (int j=0; j<nDomains; j++) {
                outfile.printf("cmd.show(\"cartoon\",\"domain"+j+"\")\n");
                outfile.printf("sele (het and domain"+j+" and not resn mse)\n");
                outfile.printf("cmd.hide(\"cartoon\",\"sele\")\n");
                outfile.printf("cmd.select(\"sele\",\"(byres (sele extend 1))\")\n");
                outfile.printf("set stick_transparency, 0, domain"+j+"\n");
                outfile.printf("cmd.show(\"sticks\",\"sele\")\n");
            }
        }
        else { // whole chain
            outfile.printf("cmd.hide(\"everything\",\"all\")\n");
            if (colorBySS) {
                outfile.printf("set_color sorange=[0.7, 0.4, 0]\n");
                outfile.printf("set_color spurple=[0.4, 0, 0.6]\n");
                outfile.printf("cmd.color(\"sorange\", \"ss s\")\n");
                outfile.printf("cmd.color(\"spurple\", \"ss h\")\n");
                outfile.printf("cmd.color(\"grey20\", \"ss l+''\")\n");
            }
            else
                outfile.printf("cmd.spectrum(\"count\",selection=\"(all)&e. c\")\n");
            if ((mm!=null) && (mm.size()==1))
                outfile.printf("cmd.show(\"sticks\",\"all\")\n");
            else
                outfile.printf("cmd.show(\"cartoon\",\"all\")\n");
            outfile.printf("cmd.remove(\"(solvent and (all))\")\n");
            outfile.printf("cmd.remove(\"hydro\")\n");
            outfile.printf("sele het and not resn mse\n");
            outfile.printf("cmd.hide(\"cartoon\",\"sele\")\n");
            outfile.printf("cmd.select(\"sele\",\"(byres (sele extend 1))\")\n");
            outfile.printf("cmd.show(\"sticks\",\"sele\")\n");
        }
        outfile.printf("set opaque_background,0\n");
        outfile.close();
    }
    
    /**
       makes greyscale figure with a particular domain highlighted in color.
       if description is null or map is null, assume domain is whole thing

       caOnly indicates the domain is CA-only; the mungemap
       is checked to see if the whole structure should be cartooned
       as CA-only.
    */
    final public static void makeForPDBFile(String fileName,
                                            double rotationAngle,
                                            String imageFileName,
                                            String description,
                                            HashMap<String,String> mm,
                                            boolean needsCPU,
                                            boolean colorBySS,
                                            boolean caOnly) throws Exception {

        File tmpFile = File.createTempFile("pml",".pml");
        tmpFile.delete();
        PrintfWriter outfile = new PrintfWriter(tmpFile.getPath());
        if (!needsCPU)
            outfile.printf("set max_threads, 1\n");
        outfile.close();

        String[] descriptions = new String[1];
        descriptions[0] = description;

        writePymolScript(tmpFile,
                         fileName,
                         rotationAngle,
                         descriptions,
                         mm,
                         colorBySS,
                         caOnly);

        // finish writing pymol script to raytrace
        outfile = new PrintfWriter(tmpFile.getPath(),true);	
        outfile.printf("util.performance(0)\n");
        outfile.printf("rebuild\n");
        outfile.printf("util.ray_shadows('medium')\n");
        if (colorBySS)
            outfile.printf("cmd.ray(500,500)\n");
        else if (caOnly &&
                 (mm!=null) &&
                 (mm.size() > 5000))
            outfile.printf("cmd.ray(1000,1000)\n");
        else
            outfile.printf("cmd.ray(5000,5000)\n");
        outfile.printf("png %s\n",imageFileName);
        outfile.printf("quit\n");
        outfile.close();

        /*
        if (description != null) {
            System.out.println("pymol script is "+tmpFile.getPath());
            System.exit(0);
        }
        */

        Program pymol = new Program("/usr/bin/pymol");
        pymol.setOutput(null);
        pymol.setError(null);
        String[] args = new String[2];
        args[0] = "-c";
        args[1] = tmpFile.getPath();
        pymol.run(args);

        tmpFile.delete();
    }
    
    final public static void makeThumbs(String fileName,
                                        String smallFileName,
                                        String mediumFileName) throws Exception {
        Program gimp = new Program("/usr/bin/gimp-console");
        gimp.setOutput(null);
        gimp.setError(null);
        String[] args = new String[6];
        args[0] = "-c";
        args[1] = "-i";
        args[2] = "-d";
        args[3] = "-f";
        args[4] = "-b";
        args[5] = "(begin (scop-thumbs \""+fileName+"\" \""+smallFileName+"\" \""+mediumFileName+"\") (gimp-quit 0))";
        gimp.run(args);
    }

    final public static void makeThumbTiny(String fileName,
                                           String tinyFileName) throws Exception {
        Program gimp = new Program("/usr/bin/gimp-console");
        gimp.setOutput(null);
        gimp.setError(null);
        String[] args = new String[6];
        args[0] = "-c";
        args[1] = "-i";
        args[2] = "-d";
        args[3] = "-f";
        args[4] = "-b";
        args[5] = "(begin (scop-thumb-tiny \""+fileName+"\" \""+tinyFileName+"\") (gimp-quit 0))";
        gimp.run(args);
    }

    /**
       Calculates optimal rotation angle (that which minimizes
       height and has the first atom on the left size), given
       a protein set.  Warning - changes protein set coordinates!
       Returns the optimal rotation angle, in degrees,
       which maximizes width to height ratio.
    */
    final public static double optimalRotation(ProteinSet p) {
        int nP = p.n();
	
        // rotate 5 degrees at a time around the z axis
        DMatrix r5 = DMatrix.makeTransform(0.0, 0.0, 0.0,
                                           5.0/180.0 * Math.PI, 0.0, 0.0);

        double optimalYDiff = 0.0;
        int optimalN = 0;
        double optimalRatio = 0.0;
        boolean firstLeft = false;  // is the 1st atom in the left half?
        for (int n=0; n<36; n++) {
            double minX = Double.NaN;
            double minY = Double.NaN;
            double minZ = Double.NaN;
            double maxX = Double.NaN;
            double maxY = Double.NaN;
            double maxZ = Double.NaN;
            double firstX = Double.NaN;
	    
            for (int i=0; i<nP; i++) {
                if (p.p(i) == null)
                    continue;

                Monomer mon;
                for (mon=p.p(i).firstMon(); mon!=null; mon=(Monomer)mon.next()) {
                    if (mon.atoms != null) 
                        for (AtomNode q = (AtomNode)mon.atoms.head(); q!=null; q = q.next)
                            if (q.coord != null) {
                                double x = q.x();
                                double y = q.y();
                                double z = q.z();
                                if (Double.isNaN(firstX))
                                    firstX = x;
                                if ((x < minX) || (Double.isNaN(minX)))
                                    minX = x;
                                if ((y < minY) || (Double.isNaN(minY)))
                                    minY = y;
                                if ((z < minZ) || (Double.isNaN(minZ)))
                                    minZ = z;
                                if ((x > maxX) || (Double.isNaN(maxX)))
                                    maxX = x;
                                if ((y > maxY) || (Double.isNaN(maxY)))
                                    maxY = y;
                                if ((z > maxZ) || (Double.isNaN(maxZ)))
                                    maxZ = z;
                            }
                }
            }

            double xDiff = maxX - minX;
            double yDiff = maxY - minY;
            double zDiff = maxZ - minZ;
            // System.out.println("debug: yDiff = "+yDiff);
            // System.out.println("debug: zDiff = "+zDiff);
	    
            if (n==0) {
                optimalYDiff = yDiff;
                optimalRatio = xDiff / yDiff;
                if (firstX < ((maxX+minX)/2.0))
                    firstLeft = true;
                else
                    firstLeft = false;
            }
            else {
                if (yDiff < optimalYDiff) {
                    optimalYDiff = yDiff;
                    optimalRatio = xDiff / yDiff;
                    optimalN = n;
                    if (firstX < ((maxX+minX)/2.0))
                        firstLeft = true;
                    else
                        firstLeft = false;
                }
            }
	    
            for (int i=0; i<nP; i++)
                if (p.p(i) != null) p.p(i).rotate(r5);
        }

        // rotate 180 degrees, if first atom on right side
        if (!firstLeft)
            optimalN += 36;

        return (double)optimalN*5.0;
    }
}
