/*
 * Software to build and maintain SCOPe, https://scop.berkeley.edu/
 */
package gov.lbl.scop.app;

import gov.lbl.scop.local.LocalSQL;
import org.strbio.IO;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;

/** Generate a create-only, versioned PDB file for a pinned chain binding. */
public class MakeChainPDB {
    public static class NoCoordinatesException extends Exception {
        public NoCoordinatesException(String message) { super(message); }
    }

    private static class CoordinateFingerprint {
        String sha256;
        int atoms;
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte v : b)
            s.append(String.format("%02x",v & 0xff));
        return s.toString();
    }

    public static String sha256(Path p) throws Exception {
        MessageDigest d = MessageDigest.getInstance("SHA-256");
        InputStream in = Files.newInputStream(p);
        byte[] buf = new byte[1024*1024];
        int n;
        while ((n=in.read(buf)) != -1)
            d.update(buf,0,n);
        in.close();
        return hex(d.digest());
    }

    static void extract(String source, char chain, Path target)
        throws Exception {
        BufferedReader in = IO.openReader(source);
        BufferedWriter out = Files.newBufferedWriter(target,
            StandardCharsets.US_ASCII,StandardOpenOption.CREATE_NEW);
        boolean hasModels = false;
        boolean inFirstModel = true;
        boolean firstModelDone = false;
        int atoms = 0;
        String line;
        while ((line=in.readLine()) != null) {
            if (line.startsWith("MODEL")) {
                if (!hasModels) {
                    hasModels = true;
                    inFirstModel = true;
                }
                else if (firstModelDone)
                    inFirstModel = false;
                continue;
            }
            if (line.startsWith("ENDMDL")) {
                if (hasModels && inFirstModel) {
                    firstModelDone = true;
                    inFirstModel = false;
                }
                continue;
            }
            if (hasModels && !inFirstModel)
                continue;
            // Match the protein-chain content written by Maryam's BioJava
            // SplitPDB2Chain exporter.  FATCAT does not need waters, ligands,
            // or other HETATM records, and excluding them permits a validated
            // copy of an existing unversioned chain file.
            boolean atom = line.startsWith("ATOM  ");
            boolean ter = line.startsWith("TER");
            if (!atom && !ter)
                continue;
            char lineChain = line.length() > 21 ? line.charAt(21) : ' ';
            if (lineChain != chain)
                continue;
            out.write(line);
            out.newLine();
            if (atom)
                atoms++;
        }
        out.write("END");
        out.newLine();
        out.close();
        in.close();
        if (atoms == 0)
            throw new NoCoordinatesException("no coordinates found for chain '"+chain+
                                             "' in "+source);
    }

    private static String field(String line, int start, int end) {
        if (line.length()<=start) return "";
        return line.substring(start,Math.min(end,line.length())).trim();
    }

    /**
     * Hash semantically significant ATOM fields rather than PDB whitespace.
     * BioJava left-aligns some atom names that the deposited PDB right-aligns.
     * The values are identical, so that formatting difference must not prevent
     * reuse.  Files containing another chain or HETATM records are rejected.
     */
    private static CoordinateFingerprint coordinateFingerprint(Path path,
                                                               char chain)
        throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        BufferedReader in=Files.newBufferedReader(path,StandardCharsets.US_ASCII);
        int atoms=0;
        String line;
        while ((line=in.readLine())!=null) {
            if (line.startsWith("HETATM")) {
                in.close();
                throw new Exception("legacy file contains HETATM records: "+path);
            }
            if (!line.startsWith("ATOM  ")) continue;
            char foundChain=line.length()>21 ? line.charAt(21) : ' ';
            if (foundChain!=chain) {
                in.close();
                throw new Exception("legacy file contains chain '"+foundChain+
                                    "', expected '"+chain+"': "+path);
            }
            String canonical=field(line,6,11)+'|'+field(line,12,16)+'|'+
                field(line,16,17)+'|'+field(line,17,20)+'|'+foundChain+'|'+
                field(line,22,26)+'|'+field(line,26,27)+'|'+field(line,30,38)+'|'+
                field(line,38,46)+'|'+field(line,46,54)+'|'+field(line,54,60)+'|'+
                field(line,60,66)+'|'+field(line,76,78)+'|'+field(line,78,80)+'\n';
            digest.update(canonical.getBytes(StandardCharsets.US_ASCII));
            atoms++;
        }
        in.close();
        if (atoms==0) throw new Exception("no ATOM records in "+path);
        CoordinateFingerprint answer=new CoordinateFingerprint();
        answer.sha256=hex(digest.digest()); answer.atoms=atoms;
        return answer;
    }

    private static ArrayList<Path> legacyCandidates(String root, String code,
                                                     String sid, char chain) {
        LinkedHashSet<Path> candidates=new LinkedHashSet<Path>();
        Path hash=Paths.get(root,code.substring(1,3));
        candidates.add(hash.resolve(sid+".pdb"));
        candidates.add(hash.resolve(sid.toLowerCase(Locale.ROOT)+".pdb"));
        candidates.add(hash.resolve(code+"_"+Character.toString(chain)+".pdb"));
        return new ArrayList<Path>(candidates);
    }

    private static Path matchingLegacyFile(String root, String code, String sid,
                                           char chain,
                                           CoordinateFingerprint expected)
        throws Exception {
        for (Path candidate:legacyCandidates(root,code,sid,chain)) {
            if (!Files.isRegularFile(candidate)) continue;
            try {
                CoordinateFingerprint found=coordinateFingerprint(candidate,chain);
                if (found.atoms==expected.atoms && found.sha256.equals(expected.sha256))
                    return candidate;
            }
            catch (Exception e) {
                // A legacy path is only an optional optimization.  A malformed,
                // case-collided, or revision-mismatched file falls back to the
                // pinned source instead of failing coordinate generation.
            }
        }
        return null;
    }

    private static void moveCreateOnly(Path stage, Path target) throws Exception {
        try {
            Files.move(stage,target,StandardCopyOption.ATOMIC_MOVE);
        }
        catch (AtomicMoveNotSupportedException e) {
            Files.move(stage,target);
        }
    }

    public static synchronized Path makeChainPDB(long bindingID) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery(
            "select b.base_scop_release_id,b.sid,b.pdb_release_id,b.raf_id," +
            "b.coordinate_file_path,b.coordinate_sha256,b.status,s.version," +
            "e.code,c.chain,l.pdb_path " +
            "from astral_chain_stable_binding b " +
            "join scop_release s on s.id=b.base_scop_release_id " +
            "join pdb_release r on r.id=b.pdb_release_id " +
            "join pdb_entry e on e.id=r.pdb_entry_id " +
            "join pdb_chain c on c.id=b.pdb_chain_id " +
            "join pdb_local l on l.pdb_release_id=r.id where b.id="+bindingID);
        if (!rs.next()) {
            rs.close();
            stmt.close();
            throw new Exception("stable binding not found: "+bindingID);
        }
        String sid = rs.getString(2);
        int pdbReleaseID = rs.getInt(3);
        int rafID = rs.getInt(4);
        String recordedPath = rs.getString(5);
        String recordedChecksum = rs.getString(6);
        String version = rs.getString(8);
        String code = rs.getString(9).toLowerCase();
        String chainString = rs.getString(10);
        char chain = chainString.charAt(0);
        String source = rs.getString(11);
        rs.close();

        String expectedSID = code + (chain == ' ' ? "_" : Character.toString(chain));
        if (!expectedSID.equals(sid)) {
            stmt.close();
            throw new Exception("SID/source case mismatch: "+sid+" vs "+expectedSID);
        }
        if (source == null) {
            stmt.close();
            throw new Exception("no local PDB path for release "+pdbReleaseID);
        }

        String root = System.getProperty("scop.chain.dir","/lab/proj/astral/chains");
        Path finalPath = Paths.get(root,version,code.substring(1,3),sid+".pdb");
        Files.createDirectories(finalPath.getParent());

        boolean recoverExisting=false;
        if (Files.exists(finalPath)) {
            String found = sha256(finalPath);
            if (recordedPath!=null && recordedChecksum!=null &&
                finalPath.toString().equals(recordedPath) &&
                found.equals(recordedChecksum)) {
                stmt.close();
                return finalPath;
            }
            if (recordedPath==null && recordedChecksum==null)
                recoverExisting=true;
            else {
                stmt.executeUpdate("update astral_chain_stable_binding set status='conflict',"+
                                   "error_message='existing coordinate file conflicts with recorded provenance' " +
                                   "where id="+bindingID);
                stmt.close();
                throw new Exception("refusing to overwrite conflicting "+finalPath);
            }
        }

        File unbundled = null;
        String input = source;
        Path stage = Files.createTempFile(finalPath.getParent(),sid+".",".tmp");
        Files.delete(stage); // extract uses CREATE_NEW
        try {
            if (source.endsWith(".pdb-bundle.tar.gz")) {
                unbundled = ParsePDBXML.unBundle(source);
                input = unbundled.getAbsolutePath();
            }
            extract(input,chain,stage);
            CoordinateFingerprint expected=coordinateFingerprint(stage,chain);
            String method="pinned-pdb-filter-v2";
            String checksum;
            if (recoverExisting) {
                CoordinateFingerprint found=coordinateFingerprint(finalPath,chain);
                if (found.atoms!=expected.atoms || !found.sha256.equals(expected.sha256)) {
                    stmt.executeUpdate("update astral_chain_stable_binding set status='conflict',"+
                        "error_message='unrecorded versioned file does not match pinned coordinates' " +
                        "where id="+bindingID);
                    throw new Exception("refusing to adopt conflicting "+finalPath);
                }
                Files.deleteIfExists(stage);
                method="verified-existing-v1";
                checksum=sha256(finalPath);
            }
            else {
                String legacyRoot=System.getProperty("scop.chain.legacy.dir",root);
                Path legacy=matchingLegacyFile(legacyRoot,code,sid,chain,expected);
                if (legacy!=null) {
                    Files.delete(stage);
                    Files.copy(legacy,stage);
                    method="verified-legacy-copy-v1";
                }
                checksum=sha256(stage);
                moveCreateOnly(stage,finalPath);
            }
            PreparedStatement update = LocalSQL.prepareStatement(
                "update astral_chain_stable_binding set coordinate_file_path=?,"+
                "coordinate_sha256=?,coordinate_method=?,"+
                "coordinate_created_at=now(),status='ready',error_message=null,"+
                "validated_at=now() where id=? and pdb_release_id=? and raf_id=? " +
                "and coordinate_file_path is null");
            update.setString(1,finalPath.toString());
            update.setString(2,checksum);
            update.setString(3,method);
            update.setLong(4,bindingID);
            update.setInt(5,pdbReleaseID);
            update.setInt(6,rafID);
            if (update.executeUpdate() != 1)
                throw new Exception("binding provenance changed while writing "+sid);
            update.close();
        }
        catch (Exception e) {
            Files.deleteIfExists(stage);
            throw e;
        }
        finally {
            if (unbundled != null)
                unbundled.delete();
        }
        stmt.close();
        return finalPath;
    }

    /** Read-only source-path test used before a stable binding is applied. */
    public static Path testChainPDB(int astralChainID, int scopReleaseID,
                                    Path root) throws Exception {
        Statement stmt=LocalSQL.createStatement();
        ResultSet rs=stmt.executeQuery(
            "select ac.sid,e.code,c.chain,l.pdb_path,s.version from astral_chain ac " +
            "join raf r on r.id=ac.raf_id join pdb_chain c on c.id=r.pdb_chain_id " +
            "join pdb_release re on re.id=c.pdb_release_id " +
            "join pdb_entry e on e.id=re.pdb_entry_id " +
            "join pdb_local l on l.pdb_release_id=re.id " +
            "join scop_release s on s.id="+scopReleaseID+" where ac.id="+astralChainID);
        if (!rs.next()) throw new Exception("astral chain not found: "+astralChainID);
        String sid=rs.getString(1), code=rs.getString(2).toLowerCase();
        char chain=rs.getString(3).charAt(0); String source=rs.getString(4);
        String version=rs.getString(5); rs.close(); stmt.close();
        String expected=code+(chain==' '?"_":Character.toString(chain));
        if (!sid.equals(expected)) throw new Exception("SID/source case mismatch: "+sid+
                                                        " vs "+expected);
        Path target=root.resolve(version).resolve(code.substring(1,3)).resolve(sid+".pdb");
        Files.createDirectories(target.getParent());
        File unbundled=null; String input=source;
        try {
            if (source.endsWith(".pdb-bundle.tar.gz")) {
                unbundled=ParsePDBXML.unBundle(source); input=unbundled.getAbsolutePath();
            }
            extract(input,chain,target);
        }
        finally { if (unbundled!=null) unbundled.delete(); }
        return target;
    }

    public static void main(String[] argv) {
        try {
            LocalSQL.connectRW();
            if (argv.length==1)
                System.out.println(makeChainPDB(Long.parseLong(argv[0])));
            else if (argv.length==4 && argv[0].equals("test"))
                System.out.println(testChainPDB(Integer.parseInt(argv[1]),
                    Integer.parseInt(argv[2]),Paths.get(argv[3])));
            else throw new Exception("Usage: MakeChainPDB stable_binding_id | test astral_chain_id scop_release_id output_root");
        }
        catch (Exception e) {
            System.err.println("Exception: "+e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
