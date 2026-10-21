package gov.lbl.scop.app;

import java.sql.*;
import gov.lbl.scop.local.LocalSQL;
import java.util.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.File;
import org.apache.commons.io.FileUtils;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.io.IOException;
import java.nio.file.StandardCopyOption;
import java.nio.file.DirectoryStream;

import java.util.logging.Logger;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.SimpleFormatter;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;


public class FatcatPairs {

    /*
    setup for large-scale pairwise structural comparisons--
    go through all representative domains + AlphaFold models, execute pairwise comparisons
    as implemented in FatcatPair.java. parallelize by connection to jobDaemon
     */

    public static LinkedHashMap<Integer, String> alphaFolds;

    public String domain;
    public int node_id;
    public int segment_id;
    public String astralSeqATOM;
    public String astralSeqSEQRES;
    public LinkedHashMap<String, String> rafMap;
    public String rafStart;
    public String rafEnd;
    public int scop_beginning;
    public int num_insertions;
    public int actual_scop_len;

    public String destPathAln;
    public String zipPathAln;
    public String destPathMat;
    public String zipPathMat;

    public Path tempDomain;
    public String scopHash;
    public String subdir;
    public Path logFile;

    public String TempDir;
    public String AlnDir;
    public String MatrixDir;
    public String SQLDir;

    public Logger logger;
    public FileHandler fileHandler;
    public BufferedWriter SQLWriter;

    public FatcatPairs(int node_id, int segment_id) throws Exception {

        FatcatPair.connectDB();
        this.node_id = node_id;
        this.domain = lookupSidByNode(node_id);
        this.segment_id = segment_id;

        this.destPathAln = FatcatPair.ALIGNMENTS_DIR + this.domain.substring(2, 4) + "/" + this.domain;
        this.zipPathAln = destPathAln + "/" + this.domain + "-" + segment_id + ".aln.zip";
        this.destPathMat = FatcatPair.MATRICES_DIR + this.domain.substring(2, 4) + "/" + this.domain;
        this.zipPathMat = destPathMat + "/" + this.domain + "-" + segment_id + ".matrix.zip";
        
        // populate alphafolds array
        if (alphaFolds == null) {
            alphaFolds = new LinkedHashMap<>();
            Statement modelStmt = LocalSQL.createStatement();
            ResultSet modelRs = modelStmt.executeQuery("select id, name from af_model order by id asc");
            int i = 0;
            while (modelRs.next()) {
                String name = modelRs.getString("name");
                Integer id = modelRs.getInt("id");
                alphaFolds.put(id, name);
                i++;
            }
            modelStmt.close();
            modelRs.close();
        }

        // get raf line
        String rafLine = "";
        int rafVersion = -1;
        this.scop_beginning = 1;
        Statement stmtRAF = LocalSQL.createStatement();
        ResultSet rsRAF = stmtRAF.executeQuery("select raf.raf_version_id, raf.line from raf\n" +
                "join pdb_chain on raf.pdb_chain_id = pdb_chain.id\n" +
                "join link_pdb on pdb_chain.id = link_pdb.pdb_chain_id\n" +
                "join scop_node on link_pdb.node_id = scop_node.id\n" +
                "where scop_node.sid = '" + this.domain + "'\n" +
                "and scop_node.release_id = 19\n" +
                "order by raf_version_id desc\n" +
                //"and raf_version_id = 2\n" +
                "limit 1"
        );

        boolean hasRsRAF = false;
        while (rsRAF.next()) {
            rafLine = rsRAF.getString("line");
            rafVersion = rsRAF.getInt("raf_version_id");
            hasRsRAF = true;
        }
        if (!hasRsRAF) {
            throw new Exception("rsRAF did not find a rafMap for domain " + this.domain);
        }
        this.rafMap = new LinkedHashMap<>();
        String[] rafHeader = rafLine.substring(0, 38).split("\\s+");
        this.rafStart = String.valueOf(rafHeader[5]);
        this.rafEnd = String.valueOf(rafHeader[6]);
        rafLine = rafLine.substring(38); // chop header
        // 0-2 --> empty spaces or number
        // 3 --> index end
        // 4 --> insertion code (if applicable)
        // 5,6 --> ATOM,SEQRES residues

        if (rafVersion == 3) {
            for (int i = 0; i < rafLine.length(); i += 7) {
                String key;
                String val;
                key = rafLine.substring(i, i + 5).trim();
                if (rafLine.charAt(i+4) != ' ') {
                    val = "/";
                } else if (rafLine.charAt(i + 5) == '.') {
                    val = ",";
                } else {
                    val = String.valueOf(rafLine.charAt(i + 5));
                }
                rafMap.put(key, val);
            }
        } else if (rafVersion > 0) {
            int n = 1;
            String key;
            String val;
            for (int i = 0; i < rafLine.length(); i += 7) {
                key = rafLine.substring(i, i + 5).trim();
                if (key == "B" || key == "M" || key == "E") {
                    key = Integer.toString(n);
                }
                if (rafLine.charAt(i+4) != ' ') {
                    val = "/";
                } else {
                    val = String.valueOf(rafLine.charAt(i + 5));
                }
                rafMap.put(key, val);
                n++;
            }
        }  else {
            stmtRAF.close();
            rsRAF.close();
            throw new Exception("No RAF version found");
        }

        boolean beginning_set = false;
        for (Map.Entry<String, String> entry : rafMap.entrySet()) {
            String key = entry.getKey();
            if (key.matches(".*[a-zA-Z].*") || key.equals(String.valueOf(0)) || key.matches(".*-\\d+(\\.\\d+)?.*")) {
                num_insertions++;
            }
            if (!beginning_set) {
                if ((!key.matches(".*[a-zA-Z].*") && !key.equals(String.valueOf(0))) || key.matches(".*-\\d+(\\.\\d+)?.*")) {
                    if (Integer.parseInt(key) > 0) {
                        scop_beginning = Integer.parseInt(key); // set the first non-inserted value as the beginning
                        beginning_set = true;
                    }
                }
            }
        }
        stmtRAF.close();
        rsRAF.close();
        actual_scop_len = rafMap.entrySet().size() + scop_beginning;

        scopHash = this.domain.substring(2, 4);
        subdir = this.domain + "-" + segment_id;

        TempDir = FatcatPair.TEMP_DIR + subdir + "-tmp/"; // temp files (ent, superposition, log, aln, matrix) go here
        AlnDir = FatcatPair.ALIGNMENTS_DIR + scopHash + "/" + this.domain + "/" + subdir + "/"; // aln files go here
        MatrixDir = FatcatPair.MATRICES_DIR + scopHash + "/" + this.domain + "/" + subdir + "/"; // matrix files go here
        SQLDir = FatcatPair.SQL_DIR + scopHash + "/" + this.domain + "/"; // sql files go here
        
        Files.createDirectories(Paths.get(AlnDir));
        Files.createDirectories(Paths.get(MatrixDir));
        Files.createDirectories(Paths.get(TempDir));
        Files.createDirectories(Paths.get(SQLDir));

        this.SQLWriter = new BufferedWriter(new FileWriter(SQLDir + subdir + ".sql"));

        Path sourceDomain = Paths.get(FatcatPair.SCOP_DIR + scopHash + "/" + this.domain + ".ent");
        tempDomain = Paths.get(TempDir + this.domain + ".ent");
        Files.copy(sourceDomain, tempDomain, StandardCopyOption.REPLACE_EXISTING);

        this.logger = setupLogger();
        logger.info("Initializing FatcatPairs object");

        this.destPathAln = AlnDir;  // original location of aln files (in /domain/)
        this.zipPathAln = FatcatPair.ALIGNMENTS_DIR + scopHash + "/" + this.domain + "/" + this.domain + "-" + segment_id +  ".aln.zip"; // new location of zipped aln (in /domain/domain-#.zip
        this.destPathMat = MatrixDir;
        this.zipPathMat = FatcatPair.MATRICES_DIR + scopHash + "/" + this.domain + "/" + this.domain + "-" + segment_id +  ".matrix.zip";

    }

    private Logger setupLogger() throws IOException {
        String filename = domain + "-" + segment_id + ".log";
        logFile = Paths.get(TempDir, filename);

        Files.createDirectories(Paths.get(TempDir));

        Logger customLogger = Logger.getLogger("Logger-" + domain + "-" + segment_id);
        customLogger.setUseParentHandlers(false);
        for (Handler h : customLogger.getHandlers()) {
            h.close();
            customLogger.removeHandler(h);
        }
        try {
            fileHandler = new FileHandler(logFile.toString(), true);
            fileHandler.setFormatter(new SimpleFormatter());
            fileHandler.setLevel(Level.ALL);
            fileHandler.flush(); // force write to disk
            customLogger.addHandler(fileHandler);
            customLogger.setLevel(Level.ALL);
            customLogger.info("Logger successfully initialized at: " + logFile);
            fileHandler.flush();
        } catch (IOException e) {
            System.err.println("Failed to create FileHandler: " + e.getMessage());
            throw e;
        }
        return customLogger;
    }

    private static int extractNumericPart(String key) {
        String numPart = key.replaceAll("[^0-9]", ""); 
        return numPart.isEmpty() ? 0 : Integer.parseInt(numPart);
    }

    public static String lookupSidByNode(int nodeID) throws Exception {
        String result;
        int SCOPRelease = LocalSQL.getLatestSCOPRelease(false);
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT sid FROM scop_node WHERE id = " + nodeID + " AND release_id = " + SCOPRelease);
        if (rs.next()) {
            result = rs.getString(1);
            stmt.close();
            rs.close();
            return result;
        } else {
            stmt.close();
            rs.close();
            throw new Exception("No scop release found for node " + nodeID);
        }
    }

    public static Integer lookupIDbyAF(String alphafold) throws Exception {
        for (Map.Entry<Integer, String> entry : alphaFolds.entrySet()) {
            if (entry.getValue().equals(alphafold)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public void zipDirs() throws Exception {

        String zipPath = null;
        String destPath = null;

        System.out.println("zipDirs");

        for (int i = 0; i < 2; i++) {
            if (i == 0) {
                zipPath = this.zipPathAln;
                destPath = this.destPathAln;
            } else if (i == 1) {
                zipPath = this.zipPathMat;
                destPath = this.destPathMat;
            } else {
                break;
            }
            if (!Files.exists(Paths.get(zipPath))) {
                Path zipFile = Files.createFile(Paths.get(zipPath));
                try (ZipOutputStream zs = new ZipOutputStream(Files.newOutputStream(zipFile))) {
                    Path sourceDir = Paths.get(destPath);
                    Files.walk(sourceDir)
                            .filter(path -> !Files.isDirectory(path))
                            .filter(path -> !path.toString().toLowerCase().endsWith(".zip"))
                            .forEach(path -> {
                                ZipEntry zipEntry = new ZipEntry(sourceDir.relativize(path).toString());
                                try {
                                    zs.putNextEntry(zipEntry);
                                    Files.copy(path, zs);
                                    zs.closeEntry();
                                } catch (IOException e) {
                                    System.err.println("Failed to zip file: " + path);
                                    throw new RuntimeException(e);
                                }
                            });
                }
            }
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(destPath))) {
                System.out.println(Paths.get(destPath).toString());
                for (Path path : stream) {
                    if (Files.isRegularFile(path)) {
                        Files.delete(path);
                    }
                }
            }
            Files.delete(Paths.get(destPath));
        }
    }

    public static ArrayList<Integer> getNodeIDs() throws Exception {

        LocalSQL.connectRW();
        ArrayList<Integer> nodeIDs = new ArrayList<Integer>();
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select node_id from scop_node_fatcat_rep");
        while (rs.next()) {
            nodeIDs.add(rs.getInt("node_id"));
        }
        stmt.close();
        rs.close();
        return nodeIDs;
    }

    /* for testing purposes */
    public static void runTestComparisons(int targetID, int segment_id, int amount) throws Exception {

        FatcatPairs fcs = new FatcatPairs(targetID, segment_id);
        alphaFolds = alphaFolds.entrySet().stream()
                .limit(amount)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> b,
                        LinkedHashMap::new
                ));
        fcs.runOneSetComparisons();
    }


    //////

    public static void runComparisons(int targetID, int segment_id) throws Exception {

        FatcatPairs fcs = new FatcatPairs(targetID, segment_id);
        fcs.runOneSetComparisons_noSQL();

    }

    public void runOneSetComparisons() throws Exception {

        FatcatPair fc;
        LocalSQL.connectRW();

        try (Statement stmt = LocalSQL.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT DATABASE()")) {
            if (rs.next()) {
                System.out.println("Connected to DB: " + rs.getString(1));
            }
        }

        System.out.println(this.domain + " " + this.segment_id);

        PreparedStatement stmt1 = null;
        PreparedStatement stmt2 = null;
        PreparedStatement stmt3 = null;
        int i = 0;
        int num_errors = 0;
        for (Integer query_id : alphaFolds.keySet()) {
            if (query_id % 10 == this.segment_id) {
                if (i % 20 == 0) {

                    if (stmt2 != null && stmt3 != null) {
                        logger.info("inserting batches");
                        stmt2.executeBatch();
                        stmt3.executeBatch();
                    }

                    logger.info("closing stmts");
                    if (stmt1 != null) {
                        stmt1.close();
                    }
                    if (stmt2 != null) {
                        stmt2.executeBatch();
                        stmt2.close();
                    }
                    if (stmt3 != null) {
                        stmt3.executeBatch();
                        stmt3.close();
                    }

                    logger.info("re-initializing stmts");
                    LocalSQL.connectRW();
                    stmt1 = LocalSQL.prepareStatement(
                            "INSERT INTO scop_node_fatcat_af " +
                                    "VALUES (NULL,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                            Statement.RETURN_GENERATED_KEYS
                    );
                    stmt2 = LocalSQL.prepareStatement(
                            "INSERT INTO scop_node_fatcat_af_gap (hit_id, gap_start_af, gap_length_af, gap_start_scop, gap_length_scop, gap_id, gap_type) " +
                                    "VALUES (?,?,?,?,?,?,?)"
                    );
                    stmt3 = LocalSQL.prepareStatement(
                            "INSERT INTO scop_node_fatcat_af_matrix (hit_id, r11, r12, r13, r21, r22, r23, r31, r32, r33, t1, t2, t3) " +
                                    "VALUES (?, ?,?,?, ?,?,?, ?,?,?, ?,?,?)"
                    );
                }
                try {
                    System.out.println(alphaFolds.get(query_id) + " " + query_id);
                    logger.info("starting fc.runComparison");
                    fc = new FatcatPair(query_id, stmt1, stmt2, stmt3, this);
                    fc.runComparison();

                } catch (Exception e) {
                    logger.severe(e.getMessage());
                    System.out.println(e.getMessage());
                    stmt2.executeBatch();
                    stmt3.executeBatch();
                    num_errors++;
                }
                i++;
            }
        }
        logger.info("closing stmts");
        if (stmt1 != null) {
            stmt1.close();
        }
        if (stmt2 != null) {
            stmt2.executeBatch();
            stmt2.close();
        }
        if (stmt3 != null) {
            stmt3.executeBatch();
            stmt3.close();
        }
        if (num_errors == 0) {
            logger.info("zipping files");
            zipDirs();
            logger.info("deleting .ent copy");
            Files.delete(tempDomain);
            logger.info("deleting /tmp/domain/domain-#");
            fileHandler.close();
            Files.delete(logFile);
            Files.delete(Paths.get(TempDir));
        }
    }

    public void runOneSetComparisons_noSQL() throws Exception {

        FatcatPair fc;
        FatcatPair.connectDB();

        logger.info("starting .sql file");
        this.SQLWriter.write(
                "SET autocommit=0;\n" +
                        "SET foreign_key_checks=0;\n" +
                        "SET unique_checks=0;\n"
        );

        int i = 0;
        int num_errors = 0;
        for (Integer query_id : alphaFolds.keySet()) {
            if (query_id % 10 == this.segment_id) {
                if (i % 20 == 0) {
                    SQLWriter.write("COMMIT;\n");
                    SQLWriter.flush();
                }
                try {
                    System.out.println(alphaFolds.get(query_id) + " " + query_id);
                    logger.info("starting fc.runComparison");
                    fc = new FatcatPair(query_id, this);
                    fc.runComparison();

                } catch (Exception e) {
                    logger.severe(e.getMessage());
                    e.printStackTrace();
                    num_errors++;
                }
                i++;
            }
        }
        logger.info("ending .sql file");
        this.SQLWriter.write(
                "SET foreign_key_checks=1;\n" +
                        "SET unique_checks=1;\n" +
                        "COMMIT;\n" +
                        "SET autocommit=1;"
        );
        this.SQLWriter.flush();
        this.SQLWriter.close();
        if (num_errors == 0) {
            logger.info("zipping files");
            zipDirs();
            logger.info("deleting .ent copy");
            Files.delete(tempDomain);
            logger.info("deleting /tmp/domain/domain-#");
            fileHandler.close();
            Files.delete(logFile);
            Files.delete(Paths.get(TempDir));
        }
    }

    public static void createJobs() throws Exception {
        ArrayList<Integer> nodeIDsDone = new ArrayList<>();

        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select count(*), node_id from scop_node_fatcat_af group by node_id having count(*) = 23391");
        while (rs.next()) {
            nodeIDsDone.add(rs.getInt("node_id"));
        }
        stmt.close();
        rs.close();

        ArrayList<Integer> nodeIDs = getNodeIDs();
        for (int node_id : nodeIDs) {
            if (!nodeIDsDone.contains(node_id)) {
                for (int i = 0; i < 10; i++) {
                    LocalSQL.newJob(24,
                            node_id,
                            String.valueOf(i)
                    );
                }
            }
        }
    }

    public static void createErroredJobs() throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select target_id , args from job_done where job_type_id = 24 and time_created > '2025-04-28' and n_failures >= 10");
        while (rs.next()) {
            LocalSQL.newJob(24,
                    rs.getInt("target_id"),
                    rs.getString("args")
            );
        }
    }

    public static void createIncompleteJobs() throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select node_id, count(*) from scop_node_fatcat_af group by node_id having count(*) < 23391");
        while (rs.next()) {
            System.out.println("next line in query");
            for (int i = 0; i < 10; i++) {
                System.out.println("creating new job");
                LocalSQL.newJob(24,
                        rs.getInt("node_id"),
                        String.valueOf(i)
                );
            }
        }
    }

    public static void main(String[] args) throws Exception {
        FatcatPair.connectDB();
        // createJobs();
        // createErroredJobs();
        createIncompleteJobs();
    }
}
