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
   Import all data for PDB entries in a directory, or "removed" file,
   using the .ent files.
*/
public class ParsePDB {
    final public static SimpleDateFormat pdbDateFormat =
        new SimpleDateFormat ("dd-MMM-yy");
    final public static SimpleDateFormat snapshotDateFormat =
        new SimpleDateFormat ("yyMMdd");
    final public static SimpleDateFormat sqlDateFormat =
        new SimpleDateFormat ("yyyy-MM-dd");
	
    /**
       convert date format:  20-NOV-93 to 1993-11-20
       null if fmt problem
    */
    final public static String convertDate(String d) throws Exception {
        if ((d == null) || (d.length() != 9)) return null;
        if ((d.charAt(2)!= '-') || (d.charAt(6) !='-')) {
            return null;
        }
        java.util.Date tmpD = pdbDateFormat.parse(d);
        return sqlDateFormat.format(tmpD);
    }

    final public static int lookupOrCreateEntry(String pdbCode) throws Exception {
        int id = LocalSQL.lookupPDB(pdbCode);
        if (id==0) {
            Statement stmt = LocalSQL.createStatement();
            stmt.executeUpdate("insert into pdb_entry values (null, \""+
                               pdbCode+"\", \"\", \"0000-00-00\", \"0000-00-00\", null, null, 0)",
                               Statement.RETURN_GENERATED_KEYS);
            ResultSet rs = stmt.getGeneratedKeys();
            rs.next();
            id = rs.getInt(1);
            stmt.close();
        }
        return id;
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
    

    final public static int lookupOrCreateRelease(int pdbID, String revDate, String fileDate) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs;
        int oldRelease = lookupRelease(pdbID, revDate, fileDate);
        if (oldRelease > 0) {
            stmt.close();
            return oldRelease;
        }
        else {
            int lastFile = 0;
            if (fileDate != null)
                lastFile = LocalSQL.getCurrentPDBRelease(pdbID);

            // create new record with this file date
            String revDate1 = "\""+revDate+"\"";
            String fileDate1 = "null";
            if (fileDate!=null)
                fileDate1 = "\""+fileDate+"\"";
            stmt.executeUpdate("insert into pdb_release values (null, "+
                               pdbID+", "+
                               revDate1+", "+
                               fileDate1+", null, null, null, null, null, 0, 0,"+
                               "null, null, null, null, null, null, null)",
                               Statement.RETURN_GENERATED_KEYS);
            rs = stmt.getGeneratedKeys();
            rs.next();
            int newID = rs.getInt(1);

            // if old version, replace it
            if (lastFile > 0) {
                stmt.executeUpdate("update pdb_release set replaced_by="+
                                   newID+
                                   " where id="+
                                   lastFile+
                                   " and replaced_by is null");
            }
	    
            stmt.close();
            return newID;
        }
    }

    final public static int lookupOrCreateMethod(String method) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        method = StringUtil.replace(method,"\"","\\\"");

        ResultSet rs = stmt.executeQuery("select id from pdb_method where description=\""+method+"\"");
        if (rs.next()) {
            int i = rs.getInt(1);
            stmt.close();
            return i;
        }
        else {
            int isXray = 0;
            int isTheory = 0;
            String summary = StringUtil.replace(method," ","");
            if (summary.contains("THEOR")) {
                summary = "THEORY";
                isTheory = 1;
            }
            summary = StringUtil.replace(summary,"NEUTRONDIFFRACTION;XRAY","NEUT+");
            summary = StringUtil.replace(summary,"NEUTRONDIFFRACTION;X-RAYDIFFRACTION","NEUT+");
            summary = StringUtil.replace(summary,"NEUTRONDIFFRACTION,X-RAYDIFFRACTION","NEUT+");
            summary = StringUtil.replace(summary,"NEUTRONDIFFRACTION","NEUT");
            summary = StringUtil.replace(summary,"SINGLE-CRYSTALNEUT","NEUT");
            summary = StringUtil.replace(summary,"X-RAYDIFFRACTION","XRAY");
            summary = StringUtil.replace(summary,"X-RAYDIFRACTION","XRAY");
            summary = StringUtil.replace(summary,"SYNCHROTRONX-RAY","XRAY");
            summary = StringUtil.replace(summary,"SYNCHROTRONXRAY","XRAY");
            summary = StringUtil.replace(summary,"X-RAYPOWDERDIFFRACTION","PDIF");
            summary = StringUtil.replace(summary,"SYNCHROTRONRADIATION","XRAY");
            summary = StringUtil.replace(summary,"XRAY;OTHERDIFFRACTION","XRAY");
            summary = StringUtil.replace(summary,"SINGLE-CRYSTALEDIF","XRAY");
            summary = StringUtil.replace(summary,",SINGLECRYSTAL","");
            summary = StringUtil.replace(summary,",MOLECULARREPLACEMENT","");
            summary = StringUtil.replace(summary,"NMR,NMR","NMR");
            summary = StringUtil.replace(summary,"NMR,MINIMIZEDAVERAGESTRUCTURE;X-RAYDIFFRACTION","XRAY+");
            summary = StringUtil.replace(summary,"XRAY;ELECTRONDIFFRACTION","XRAY+");
            summary = StringUtil.replace(summary,"XRAY,16STRUCTURES","XRAY+");
            summary = StringUtil.replace(summary,"XRAY;","XRAY");
            summary = StringUtil.replace(summary,"XRAY,OXYGENTRANSPORT","XRAY");
            summary = StringUtil.replace(summary,"NMR,MINIMIZEDAVERAGESTRUCTURE","NMRAVE");
            summary = StringUtil.replace(summary,"NMR,MINIMIZEDAVERAGEDSTRUCTURE","NMRAVE");
            summary = StringUtil.replace(summary,"NMR.MINIMIZEDAVERAGEDSTRUCTURE","NMRAVE");
            summary = StringUtil.replace(summary,"NMR.MINIMIZEDAVERAGESTRUCTURE","NMRAVE");
            summary = StringUtil.replace(summary,"NMR,REPRESENTATIVEMINIMIZEDSTRUCTURE","NMRAVE");
            summary = StringUtil.replace(summary,"NMR,MINIMIZEDDGSTRUCTURE,CLOSESTTOAVERAGESTRUCTURE","NMRAVE");
            summary = StringUtil.replace(summary,"NMR,AVERAGEMINIMIZEDSTRUCTURE","NMRAVE");
	
            int i = summary.indexOf("NMR");
            if ((i>=0) &&
                (summary.length()>i+3) &&
                (Character.isDigit(summary.charAt(i+3)))) {
                summary = summary.substring(0,i)+"NMR"+StringUtil.atoi(summary,i+3);
            }
            i = summary.indexOf("NMR,");
            if ((i>=0) &&
                (summary.length()>i+4) &&
                (Character.isDigit(summary.charAt(i+4)))) {
                summary = summary.substring(0,i)+"NMR"+StringUtil.atoi(summary,i+4);
            }
            i = summary.indexOf("NMRSPECTROSCOPY,");
            if ((i>=0) &&
                (summary.length()>i+16) &&
                (Character.isDigit(summary.charAt(i+16)))) {
                summary = summary.substring(0,i)+"NMR"+StringUtil.atoi(summary,i+16);
            }
            summary = StringUtil.replace(summary,"NMR,REPRESENTATIVESTRUCTURE","NMR");
            summary = StringUtil.replace(summary,"NMR,MODELS1-17OF33STRUCTURES","NMR17");
            summary = StringUtil.replace(summary,"NMR,MODELS18-33OF33STRUCTURES","NMR16");
            summary = StringUtil.replace(summary,"NMR,AVERAGESTRUCTURE","NMRAVE");
            summary = StringUtil.replace(summary,"NMR,MINIMIZEDSTRUCTURE","NMRAVE");
            summary = StringUtil.replace(summary,"NMR,REGULARIZEDMEANSTRUCTURE","NMRAVE");
            summary = StringUtil.replace(summary,"NMR,RESTRAINEDREGULARIZEDMEANSTRUCTURE","NMRAVE");
            summary = StringUtil.replace(summary,"NMR,MINIMIZEDMEANSTRUCTURE","NMRAVE");
            summary = StringUtil.replace(summary,"NMR,MINIMIZEDAVERAGESTUCTURE","NMRAVE");
            summary = StringUtil.replace(summary,"NMR,MINIMISEDAVERAGESTRUCTURE","NMRAVE");
            summary = StringUtil.replace(summary,"NMR,RESTRAINEDMINIMIZEDMEANSTRUCTURE","NMRAVE");
            summary = StringUtil.replace(summary,"NMR,SOLIDSTATE,MINIMIZEDAVERAGESTRUCTURE","NMRSAVE");
            summary = StringUtil.replace(summary,"INFRAREDSPECTROSCOPY","FTIR");
            summary = StringUtil.replace(summary,"FTIR,10STRUCTURES","FTIR");
            summary = StringUtil.replace(summary,"FTIR,17STRUCTURES","FTIR");
            summary = StringUtil.replace(summary,"FTIR,19STRUCTURES","FTIR");
            summary = StringUtil.replace(summary,"OTHER,17STRUCTURES","FTIR");
            summary = StringUtil.replace(summary,"MR,26STRUCTURES","NMR26");
            summary = StringUtil.replace(summary,"NMR.20STRUCTURES","NMR20");
            summary = StringUtil.replace(summary,"FIBERDIFFRACTION","FIBER");
            summary = StringUtil.replace(summary,"X-RAYFIBER","FIBER");
            summary = StringUtil.replace(summary,"X-RAYFIBERDIFFRACTION","FIBER");
            summary = StringUtil.replace(summary,"FIBER,FIBER","FIBER");
            summary = StringUtil.replace(summary,"ELECTRONMICROSCOPY","EM");
            summary = StringUtil.replace(summary,"ELECTRONTOMOGRAPHY","EM");
            summary = StringUtil.replace(summary,"EM,CRYO-EMRECONSTRUCTION","EM");
            summary = StringUtil.replace(summary,"EM,HIGHRESOLUTIONMICROSCOPY,CRYO-EMRECONSTRUCTION","EM");
            summary = StringUtil.replace(summary,"CRYO-EM","EM");
            summary = StringUtil.replace(summary,"ELECTRONDIFFRACTION","EDIF");
            summary = StringUtil.replace(summary,"SINGLE-CRYSTALEDIF","EDIF");
            summary = StringUtil.replace(summary,"SINGLE-CRYSTALXRAY","XRAY");
            summary = StringUtil.replace(summary,"FLUORESCENCETRANSFER","FLUOR");
            summary = StringUtil.replace(summary,"RESTRAINEDMOLECULARDYNAMICS","THEORY");
            summary = StringUtil.replace(summary,"SOLUTIONSCATTERING","SAXS");
            summary = StringUtil.replace(summary,"SAXS,4STRUCTURES","SAXS");
            summary = StringUtil.replace(summary,"SAXS,2STRUCTURES","SAXS");
            summary = StringUtil.replace(summary,"POWDERDIFFRACTION","PDIF");
            summary = StringUtil.replace(summary,"XRAYXRAY","XRAY");
            summary = StringUtil.replace(summary,"SOLUTIONNMR","NMR");
            if (summary.contains("XRAY"))
                isXray = 1;

	    
            stmt.executeUpdate("insert into pdb_method values (null, \""+
                               method+"\", \""+
                               summary+"\", "+
                               isXray+", "+
                               isTheory+")",
                               Statement.RETURN_GENERATED_KEYS);
            rs = stmt.getGeneratedKeys();
            rs.next();
            i = rs.getInt(1);
            stmt.close();
            return i;
        }
    }
    
    final public static void setReleaseFile(int releaseID, String fileName) throws Exception {
        int pos = fileName.lastIndexOf("data/");
        java.util.Date tmpD = snapshotDateFormat.parse(fileName.substring(pos+5,pos+11));
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select pdb_path from pdb_local where pdb_release_id="+releaseID);
        if (rs.next()) {
            String oldFileName = rs.getString(1);
            // don't update if same date and size.
            if (oldFileName!=null) {
                File oldFile = new File(oldFileName);
                File newFile = new File(fileName);
                if ((oldFile.exists() && newFile.exists()) &&
                    (oldFile.lastModified()==newFile.lastModified()) &&
                    (oldFile.length()==newFile.length())) {
                    stmt.close();
                    return;
                }
            }
            stmt.executeUpdate("update pdb_local set pdb_path=\""+fileName+"\" where pdb_release_id="+releaseID);
            stmt.executeUpdate("update pdb_local set snapshot_date=\""+sqlDateFormat.format(tmpD)+"\" where pdb_release_id="+releaseID);
        }
        else
            stmt.executeUpdate("insert into pdb_local values ("+
                               releaseID+", \""+
                               fileName+"\", null, 0, \""+
                               sqlDateFormat.format(tmpD)+"\")");
        stmt.close();
    }

    // don't update snapshot date, unless there isn't one.
    final public static void setReleaseXML(int releaseID, String fileName) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select xml_path from pdb_local where pdb_release_id="+releaseID);
        if (rs.next()) {
            String oldFileName = rs.getString(1);
            // don't update if same date and size.
            if (oldFileName!=null) {
                File oldFile = new File(oldFileName);
                File newFile = new File(fileName);
                if ((oldFile.exists() && newFile.exists()) &&
                    (oldFile.lastModified()==newFile.lastModified()) &&
                    (oldFile.length()==newFile.length())) {
                    stmt.close();
                    return;
                }
            }
            stmt.executeUpdate("update pdb_local set xml_path=\""+fileName+"\" where pdb_release_id="+releaseID);
        }
        else {
            int pos = fileName.indexOf("data/");
            java.util.Date tmpD = snapshotDateFormat.parse(fileName.substring(pos+5,pos+11));
            stmt.executeUpdate("insert into pdb_local values ("+
                               releaseID+", null, \""+
                               fileName+"\", 0, \""+
                               sqlDateFormat.format(tmpD)+"\")");
        }
        stmt.close();
    }
    
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            Statement stmt2 = LocalSQL.createStatement();

            BufferedReader infile = null;

            File pdbDir = new File(argv[0]);
            if (!pdbDir.exists()) {
                System.exit(0);
            }
            if (pdbDir.isFile()) {
                System.out.println("working on "+argv[0]);
                infile = IO.openReader(argv[0]);
                java.util.Date d = new java.util.Date(pdbDir.lastModified());
                String obsDate = sqlDateFormat.format(d);
                String buffer = infile.readLine();
                while (buffer != null) {
                    if ((buffer.startsWith("pdb")) &&
                        (buffer.length()>7) &&
                        (Character.isDigit(buffer.charAt(3)))) {
                        String pdbCode = buffer.substring(3,7);
                        int id = lookupOrCreateEntry(pdbCode);
                        stmt.executeUpdate("update pdb_entry set obsolete_date=\""+
                                           obsDate+
                                           "\" where id="+
                                           id);
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
                fileName = StringUtil.replace(fileName,"/mnt/net/dipa.jmcnet/data","");

                if (fileName.endsWith(".Z"))
                    continue;

                int i = fileName.lastIndexOf(".ent");
                if (i==-1)
                    i = fileName.lastIndexOf(".noc");
                if (i==-1)
                    continue;
                String pdbCode = fileName.substring(i-4,i);
                System.out.println("parsing "+pdbCode);
                System.out.flush();

                int id = lookupOrCreateEntry(pdbCode);

                infile = null;
                try {
                    infile = IO.openReader(fileName);
                }
                catch (IOException ioe) {
                    System.out.println("PDB file not found or error:  "+pdbCode);
                    continue;
                }

                // make it non-obsolete (pending what's in file)
                stmt.executeUpdate("update pdb_entry set obsolete_date=null where id="+
                                   id);

                int lastRelease = 0;
                String title = "";
                String headerDescription = "";
                String method = null;
                String depDate = null;
                java.util.Date d = new java.util.Date(pdb.lastModified());
                String fileDate = sqlDateFormat.format(d);

                try {
                    String buffer = infile.readLine();
                    while ((buffer != null) && (infile.ready())) {
                        if (buffer.startsWith("HEADER")) {
                            depDate = convertDate(buffer.substring(50,59));
                            if (depDate==null)
                                depDate = fileDate;
                            stmt.executeUpdate("update pdb_entry set deposition_date=\""+
                                               depDate+
                                               "\" where id="+
                                               id);
                            headerDescription = buffer.substring(10,50).trim().toLowerCase();
                        }
                        if (buffer.startsWith("TITLE")) {
                            if (buffer.length()>80)
                                buffer = buffer.substring(0,80);
			    
                            if (title.length()>0) title += " ";
                            title += buffer.substring(10).trim().toLowerCase();
                        }
                        if (buffer.startsWith("EXPDTA")) {
                            if (buffer.length()>79)
                                buffer = buffer.substring(0,79);
                            if (method==null) method="";
                            else method += " ";
                            method += buffer.substring(10).trim();
                        }
                        if (buffer.startsWith("REVDAT")) {
                            int revDatNum = StringUtil.atoi(buffer,7);
                            if (Character.isDigit(buffer.charAt(13))) {
                                String revDate = convertDate(buffer.substring(13,22));
                                // only update release date if
                                // there isn't already one preent
                                if ((revDatNum == 1) && (revDate != null)) {
                                    stmt.executeUpdate("update pdb_entry set release_date=\""+
                                                       revDate+
                                                       "\" where id="+
                                                       id+
                                                       " and release_date=\"0000-00-00\"");
                                }
                                int relID = 0;
                                if (lastRelease==0) {
                                    relID = lookupOrCreateRelease(id,revDate,fileDate);
                                    setReleaseFile(relID,fileName);
                                    if (method != null) {
                                        int methodID = lookupOrCreateMethod(method);
                                        stmt.executeUpdate("update pdb_release set method_id="+
                                                           methodID+
                                                           " where id="+
                                                           relID);
                                    }

                                    String xmlName = StringUtil.replace(fileName,"/obs","/xml-obs");
                                    xmlName = StringUtil.replace(xmlName,"/hash","/xml-hash");
                                    xmlName = StringUtil.replace(xmlName,"/all","/xml-all");
                                    xmlName = StringUtil.replace(xmlName,"pdb"+pdbCode+".ent",pdbCode+".xml");
                                    xmlName = StringUtil.replace(xmlName,"pdb"+pdbCode+".noc",pdbCode+".xml");
                                    File xmlFile = new File(xmlName);
                                    if (xmlFile.exists())
                                        setReleaseXML(relID,xmlName);
                                    else {
                                        int pos = fileName.indexOf("files");
                                        int pos2 = fileName.indexOf(".ent.gz");
                                        if (pos2==-1) pos2 = fileName.indexOf(".noc.gz");
					
                                        xmlName = fileName.substring(0,pos)+"snapshot/xml-all/"+fileName.substring(pos2-4,pos2)+".xml.gz";
                                        xmlFile = new File(xmlName);
                                        if (xmlFile.exists()) {
                                            xmlName = StringUtil.replace(xmlFile.getCanonicalPath(),"/mnt/net/imperial.jmcnet/data","");
                                            xmlName = StringUtil.replace(xmlName,"/mnt/net/ipa.jmcnet/data","");
                                            xmlName = StringUtil.replace(xmlName,"/mnt/net/dipa.jmcnet/data","");
                                            setReleaseXML(relID,xmlName);
                                        }
                                    }

                                    LocalSQL.newJob(1,relID,null,stmt);
                                    LocalSQL.newJob(2,relID,null,stmt);
                                }
                                else {
                                    relID = lookupOrCreateRelease(id,revDate,null);
                                    stmt.executeUpdate("update pdb_release set replaced_by="+
                                                       lastRelease+
                                                       " where id="+
                                                       relID+
                                                       " and replaced_by is null");
                                }
                                lastRelease = relID;
                            }
                        }
                        if (buffer.startsWith("OBSLTE")) {
                            String obsDate = convertDate(buffer.substring(11,20));
                            if (obsDate==null)
                                obsDate = "0000-00-00";
                            stmt.executeUpdate("update pdb_entry set obsolete_date=\""+
                                               obsDate+
                                               "\" where id="+
                                               id);
                            String obsByCode = buffer.substring(31,35).toLowerCase();
                            if (Character.isDigit(obsByCode.charAt(0))) {
                                int obsByID = lookupOrCreateEntry(obsByCode);
                                stmt.executeUpdate("update pdb_entry set obsoleted_by="+
                                                   obsByID+
                                                   " where id="+
                                                   id);
                            }
                        }
                        if (buffer.startsWith("ATOM")) {
                            buffer = null;
                        }
                        else {
                            buffer = infile.readLine();
                        }
                    }
                    // set description based on title or header.
                    if (title.length()==0)
                        title = headerDescription;
                    stmt.executeUpdate("update pdb_entry set description=\""+
                                       StringUtil.replace(title,"\"","\\\"")+
                                       "\" where id="+
                                       id);

                    // if no releases, make one for deposition date
                    if (lastRelease==0) {
                        stmt.executeUpdate("update pdb_entry set release_date=\""+
                                           depDate+
                                           "\" where id="+
                                           id);

                        int relID = lookupOrCreateRelease(id,depDate,fileDate);
                        setReleaseFile(relID,fileName);
                        if (method != null) {
                            int methodID = lookupOrCreateMethod(method);
                            stmt.executeUpdate("update pdb_release set method_id="+
                                               methodID+
                                               " where id="+
                                               relID);
                        }
                        String xmlName = StringUtil.replace(fileName,"/obs","/xml-obs");
                        xmlName = StringUtil.replace(xmlName,"/hash","/xml-hash");
                        xmlName = StringUtil.replace(xmlName,"/all","/xml-all");
                        xmlName = StringUtil.replace(xmlName,"pdb"+pdbCode+".ent",pdbCode+".xml");
                        xmlName = StringUtil.replace(xmlName,"pdb"+pdbCode+".noc",pdbCode+".xml");
                        File xmlFile = new File(xmlName);
                        if (xmlFile.exists())
                            setReleaseXML(relID,xmlName);
                        else {
                            int pos = fileName.indexOf("files");
                            int pos2 = fileName.indexOf(".ent.gz");
                            if (pos2==-1) pos2 = fileName.indexOf(".noc.gz");
					
                            xmlName = fileName.substring(0,pos)+"snapshot/xml-all/"+fileName.substring(pos2-4,pos2)+".xml.gz";
                            xmlFile = new File(xmlName);
                            if (xmlFile.exists()) {
                                xmlName = StringUtil.replace(xmlFile.getCanonicalPath(),"/mnt/net/imperial.jmcnet/data","");
                                xmlName = StringUtil.replace(xmlName,"/mnt/net/ipa.jmcnet/data","");
                                xmlName = StringUtil.replace(xmlName,"/mnt/net/dipa.jmcnet/data","");
                                setReleaseXML(relID,xmlName);
                            }
                        }

                        LocalSQL.newJob(1,relID,null,stmt);
                        LocalSQL.newJob(2,relID,null,stmt);
                    }
		    
                    infile.close();
                }
                catch (IOException ioe2) {
                    continue;
                }
            }
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
