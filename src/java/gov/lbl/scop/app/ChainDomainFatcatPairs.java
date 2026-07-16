package gov.lbl.scop.app;

import gov.lbl.scop.local.LocalSQL;
import gov.lbl.scop.util.RAF;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.HashMap;

public class ChainDomainFatcatPairs {

    // 1 job = 1 chain

    public static LinkedHashMap<Integer, String> domains = new LinkedHashMap<>(); // (node_id: sid)

    public int chain_id;
    public int segment_id;

    public String chain; // 1u5ta
    public String chainFile; // path to the chain file
    public String chain_start_index;
    public String chain_end_index;
    public String chainRafBody;
    public String chainRafSeq;
    public ArrayList<String[]> chainRafMap;

    public String destPathAln;
    public String zipPathAln;
    public String destPathMat;
    public String zipPathMat;

    public Path tempChain;
    public String chainHash;
    public String subdir;
    public Path logFile;

    public String TempDir;
    public String AlnDir;
    public String SQLDir;
    public String MatrixDir;
    public String ErrorDir;

    public Logger logger;
    public FileHandler fileHandler;
    public BufferedWriter SQLWriter;

    public BufferedWriter ErrorWriter;

    public ChainDomainFatcatPairs(int chain_id, int segment_id) throws Exception {

        this.chain = "";
        this.chain_id = chain_id;
        this.chain_start_index = "";
        this.chain_end_index = "";
        this.chainRafBody = "";
        this.chainRafSeq = "";
        getChainInfo(chain_id);
        this.chainHash = chain.substring(1, 3);
        this.chainFile = ChainDomainFatcatPair.CHAIN_DIR + chainHash + "/" + this.chain + ".pdb";

        chainHash = this.chain.substring(1, 3);
        subdir = this.chain + "-" + segment_id;

        TempDir = ChainDomainFatcatPair.TEMP_DIR + subdir + "-tmp/"; // temp files (ent, superposition, log, aln, matrix) go here (ex: /h/makelyan/fatcat/common_dir_chain/temp/1ryha-0-tmp/* )
        AlnDir = ChainDomainFatcatPair.ALIGNMENTS_DIR + chainHash + "/" + this.chain + "/" + subdir + "/"; // .aln files go here (ex: /h/makelyan/fatcat/common_dir_chain/alignments/ry/1ryha/1ryha-0/*.aln ) this directory will be zipped down into one file at the very end.
        SQLDir = ChainDomainFatcatPair.SQL_DIR + chainHash + "/" + this.chain + "/"; // .sql files go here (ex: /h/makelyan/fatcat/common_dir_chain/alignments/ry/1ryha/1ryha-*.sql )
        MatrixDir = ChainDomainFatcatPair.MATRICES_DIR + chainHash + "/" + this.chain + "/" + subdir + "/"; // .matrix files go here (ex: /h/makelyan/fatcat/common_dir_chain/matrices/ty/1ryha/1ryha-0/*.matrix )
        ErrorDir = ChainDomainFatcatPair.ERRORS_DIR + chainHash + "/" + this.chain + "/"; // (ex: /h/makelyan/fatcat/common_dir_chain/errors/ry/1ryha/1ryha-0.txt )

        createDirectory(Paths.get(AlnDir), "alignment output");
        Path tempPath_ = Paths.get(TempDir);

        if (Files.exists(tempPath_)) {
            Files.walk(tempPath_)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> p.toFile().delete());
        }
        createDirectory(tempPath_, "temporary work");

        createDirectory(Paths.get(SQLDir), "SQL output");
        createDirectory(Paths.get(MatrixDir), "matrix output");
        createDirectory(Paths.get(ErrorDir), "error output");

        this.logger = setupLogger();
        System.out.println("logger");
        logger.info("Initializing ChainDomainFatcatPairs_NEW object");

        // copy chain file, put in temp dir
        String sourceChainStr = ChainDomainFatcatPair.CHAIN_DIR + chainHash + "/" + this.chain + ".pdb";
        String tempChainStr = TempDir + this.chain + ".pdb";
        this.chainFile = tempChainStr;

        // Ensure parent directories exist
        Path tempPath = Paths.get(tempChainStr);
        createDirectory(tempPath.getParent(), "temporary chain parent");

        Files.copy(Paths.get(sourceChainStr), tempPath, StandardCopyOption.REPLACE_EXISTING);

        if (!Files.isRegularFile(tempPath)) {
            throw new Exception("did not successfuly copy to " + tempChainStr);
        }

        this.SQLWriter = new BufferedWriter(new FileWriter(SQLDir + subdir + ".sql"));
        this.ErrorWriter = new BufferedWriter(new FileWriter(ErrorDir + subdir + ".txt"));

        this.destPathAln = AlnDir;  // original location of aln files (in /chain/chain-#/*)
        this.zipPathAln = ChainDomainFatcatPair.ALIGNMENTS_DIR + chainHash + "/" + this.chain + "/" + this.chain + "-" + segment_id +  ".aln.zip"; // new location of zipped aln (in /domain/domain-#.zip
        this.destPathMat = MatrixDir;
        this.zipPathMat = ChainDomainFatcatPair.MATRICES_DIR + chainHash + "/" + this.chain + "/" + this.chain + "-" + segment_id + ".matrix.zip";

    }

    private static String getEffectiveUser() {
        try {
            Process p = new ProcessBuilder("whoami").start();
            try (java.io.BufferedReader reader =
                         new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                String user = reader.readLine();
                if (user != null && !user.isEmpty()) {
                    return user.trim();
                }
            }
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    private static void createDirectory(Path path, String description) throws IOException {
        String javaUser = System.getProperty("user.name");
        String effectiveUser = getEffectiveUser();
    
        try {
            Files.createDirectories(path);
        } catch (FileAlreadyExistsException e) {
            throw new IOException(
                    "Could not create " + description + " directory " + path +
                            ": " + exceptionPath(e, path) +
                            " already exists and is not a directory" +
                            " (javaUser=" + javaUser + ", effectiveUser=" + effectiveUser + ")",
                    e
            );
        } catch (AccessDeniedException e) {
            throw new IOException(
                    "Could not create " + description + " directory " + path +
                            ": permission denied at " + exceptionPath(e, path) +
                            " (javaUser=" + javaUser + ", effectiveUser=" + effectiveUser + ")",
                    e
            );
        } catch (IOException e) {
            throw new IOException(
                    "Could not create " + description + " directory " + path +
                            ": " + e.getMessage() +
                            " (javaUser=" + javaUser + ", effectiveUser=" + effectiveUser + ")",
                    e
            );
        }
    }

    private static Path createFile(Path path, String description) throws IOException {
        try {
            return Files.createFile(path);
        } catch (FileAlreadyExistsException e) {
            throw new IOException(
                    "Could not create " + description + " file " + path +
                            ": file already exists",
                    e
            );
        } catch (AccessDeniedException e) {
            throw new IOException(
                    "Could not create " + description + " file " + path +
                            ": permission denied at " + exceptionPath(e, path),
                    e
            );
        } catch (IOException e) {
            throw new IOException(
                    "Could not create " + description + " file " + path +
                            ": " + e.getMessage(),
                    e
            );
        }
    }

    private static String exceptionPath(FileSystemException e, Path fallback) {
        String path = e.getFile();
        if (path == null) {
            return fallback.toString();
        }
        return path;
    }

    private void getChainInfo(int chain_id) throws Exception {

        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select astral_chain.sid, raf.line, raf.raf_version_id from astral_chain join raf on astral_chain.raf_id = raf.id where astral_chain.id=" + chain_id + " order by raf.raf_version_id desc");
        if (rs.next()) {
            this.chain = rs.getString("sid").toLowerCase();
            this.chainRafBody = RAF.getRAFBody(rs.getString("line"));
            this.chain_start_index = RAF.getResID(this.chainRafBody, 0); // sourceType 2 = SEQRES, 1 = ATOM
            this.chain_end_index = RAF.getResID(this.chainRafBody, RAF.getSeqLength(this.chainRafBody) - 1);

            StringBuilder rafSeq = new StringBuilder();
            ArrayList<String[]> rafMap = new ArrayList<>();

            int rafVersion = rs.getInt("raf_version_id");
            if (rafVersion == 3) {
                for (int i = 0; i < chainRafBody.length(); i += 7) {
                    String key;
                    String val;
                    key = chainRafBody.substring(i, i + 5).trim();
                    if (chainRafBody.charAt(i+4) != ' ') {
                        val = "/";
                    } else if (chainRafBody.charAt(i + 5) == '.') {
                        val = ",";
                    } else {
                        val = String.valueOf(chainRafBody.charAt(i + 5));
                    }
                    rafMap.add(new String[]{key, val});
                    rafSeq.append(val);
                }
            } else if (rafVersion > 0) {
                int n = 1;
                String key;
                String val;
                for (int i = 0; i < chainRafBody.length(); i += 7) {
                    key = chainRafBody.substring(i, i + 5).trim();
                    if (key == "B" || key == "M" || key == "E") {
                        key = Integer.toString(n);
                    }
                    if (chainRafBody.charAt(i+4) != ' ') {
                        val = "/";
                    } else if (chainRafBody.charAt(i + 5) == '.') {
                        val = ",";
                    } else {
                        val = String.valueOf(chainRafBody.charAt(i + 5));
                    }
                    rafMap.add(new String[]{key, val});
                    rafSeq.append(val);
                    n++;
                }
            }

            this.chainRafSeq = rafSeq.toString();
            this.chainRafMap = rafMap;

            rs.close();
            stmt.close();
        } else {
            rs.close();
            stmt.close();
            throw new Exception("No chain found for id=" + chain_id);
        }
    }

    private Logger setupLogger() throws Exception {

        String filename = chain + ".log";
        this.logFile = Paths.get(TempDir, filename);

        createDirectory(Paths.get(TempDir), "logger temporary work");

        Logger customLogger = Logger.getLogger("Logger-" + chain + "-" + segment_id);
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

    public void zipDirs() throws Exception {

        logger.info("zipping");

        String zipPath = null;
        String destPath = null;

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
                Path zipFile = createFile(Paths.get(zipPath), "zip output");
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
            Path destDir = Paths.get(destPath);
            if (Files.exists(destDir)) {
                Files.walk(destDir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                throw new UncheckedIOException("Failed to delete " + path, e);
                            }
                        });
            }

        }
    }

    public void unzipDirs() throws Exception {

        logger.info("unzipping");

        String zipPath = null;
        String destPath = null;

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

            Path zipFile = Paths.get(zipPath);
            Path targetDir = Paths.get(destPath);

            if (!Files.exists(zipFile)) {
                return;
            }

            createDirectory(targetDir, "zip extraction target");

            try (java.util.zip.ZipInputStream zis =
                         new java.util.zip.ZipInputStream(Files.newInputStream(zipFile))) {

                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path outPath = targetDir.resolve(entry.getName()).normalize();

                    if (!outPath.startsWith(targetDir)) {
                        throw new RuntimeException("Bad zip entry: " + entry.getName());
                    }

                    if (entry.isDirectory()) {
                        createDirectory(outPath, "zip entry");
                    } else {
                        createDirectory(outPath.getParent(), "zip entry parent");
                        Files.copy(zis, outPath, StandardCopyOption.REPLACE_EXISTING);
                    }

                    zis.closeEntry();
                }
            }

            Files.delete(zipFile);
        }
    }

    public static void getDomains() throws Exception {
        LinkedHashMap<Integer, String> d = new LinkedHashMap<>();
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select sid, node_id from scop_node_fatcat_rep join scop_node on scop_node_fatcat_rep.node_id = scop_node.id");
        while (rs.next()) {
            d.put(rs.getInt("node_id"), rs.getString("sid"));
        }
        domains = d;
        rs.close();
        stmt.close();
    }

    public static HashMap<String, Integer> getChains() throws Exception {
        HashMap<String, Integer> l = new HashMap<>();
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT \n" +
                "    MIN(astral_chain.id) AS chain_id,\n" +
                "    astral_chain.sid\n" +
                "FROM astral_chain_subset_id\n" +
                "JOIN astral_chain \n" +
                "  ON astral_chain_subset_id.astral_chain_id = astral_chain.id\n" +
                "JOIN raf \n" +
                "  ON astral_chain.raf_id = raf.id\n" +
                "JOIN link_pdb \n" +
                "  ON raf.pdb_chain_id = link_pdb.pdb_chain_id\n" +
                "JOIN scop_node \n" +
                "  ON link_pdb.node_id = scop_node.id\n" +
                "JOIN astral_domain \n" +
                "  ON scop_node.id = astral_domain.node_id\n" +
                "JOIN pdb_chain \n" +
                "  ON link_pdb.pdb_chain_id = pdb_chain.id\n" +
                "JOIN pdb_release \n" +
                "  ON pdb_chain.pdb_release_id = pdb_release.id\n" +
                "JOIN pdb_local \n" +
                "  ON pdb_release.id = pdb_local.pdb_release_id\n" +
                "WHERE astral_chain_subset_id.pct_identical = 90\n" +
                "  AND astral_domain.source_id = 1\n" +
                "  AND astral_domain.style_id = 1\n" +
                "  AND scop_node.release_id = 19\n" +
                "GROUP BY astral_chain.sid;");
        while (rs.next()) {
            int chain_id = rs.getInt("chain_id");
            String sid = rs.getString("sid").toLowerCase();
            l.put(sid, chain_id);
        }
        rs.close();
        stmt.close();
        return l;
    }

    public static ArrayList<String> getChainSids() throws Exception {
        ArrayList<String> l = new ArrayList<>();
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select distinct astral_chain.id as chain_id, astral_chain.sid from astral_chain_subset_id join astral_chain on astral_chain_subset_id.astral_chain_id = astral_chain.id join raf on astral_chain.raf_id = raf.id join link_pdb on raf.pdb_chain_id = link_pdb.pdb_chain_id join scop_node on link_pdb.node_id = scop_node.id join astral_domain on scop_node.id = astral_domain.node_id join pdb_chain on link_pdb.pdb_chain_id = pdb_chain.id join pdb_release on pdb_chain.pdb_release_id = pdb_release.id join pdb_local on pdb_release.id = pdb_local.pdb_release_id where astral_chain_subset_id.pct_identical = 90 and astral_domain.source_id = 1 and astral_domain.style_id = 1 and scop_node.release_id = 19");
        while (rs.next()) {
            String sid = rs.getString("sid").toLowerCase();
            l.add(sid);
        }
        rs.close();
        stmt.close();
        return l;
    }

    public static ArrayList<Integer> getChains(int limit) throws Exception {
        ArrayList<Integer> l = new ArrayList<>();
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT COUNT(DISTINCT astral_chain.sid) AS chain_count FROM astral_chain_subset_id JOIN astral_chain ON astral_chain_subset_id.astral_chain_id = astral_chain.id JOIN raf ON astral_chain.raf_id = raf.id JOIN link_pdb ON raf.pdb_chain_id = link_pdb.pdb_chain_id JOIN scop_node ON link_pdb.node_id = scop_node.id JOIN astral_domain ON scop_node.id = astral_domain.node_id JOIN pdb_chain ON link_pdb.pdb_chain_id = pdb_chain.id JOIN pdb_release ON pdb_chain.pdb_release_id = pdb_release.id JOIN pdb_local ON pdb_release.id = pdb_local.pdb_release_id WHERE astral_chain_subset_id.pct_identical = 90 AND astral_domain.source_id = 1 AND astral_domain.style_id = 1 AND scop_node.release_id = 19 limit " + limit);
        while (rs.next()) {
            int chain_id = rs.getInt("chain_id");
            l.add(chain_id);
        }
        rs.close();
        stmt.close();
        return l;
    }

    public static ArrayList<String> getZipPrefixes(String rootDir) throws IOException {
        ArrayList<String> prefixes = new ArrayList<>();

        try (java.util.stream.Stream<java.nio.file.Path> paths = java.nio.file.Files.walk(java.nio.file.Paths.get(rootDir))) {
            paths
                    .filter(java.nio.file.Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(".zip"))
                    .forEach(name -> {
                        if (name.length() >= 5) {
                            prefixes.add(name.substring(0, 5));
                        }
                    });
        }

        return prefixes;
    }

    public void runOneSetComparisons_noSQL() throws Exception {

        Path zipFile = Paths.get(this.zipPathAln);
        if (Files.exists(zipFile)) {
            return;
        }

        int commit_interval = 250;

        ChainDomainFatcatPair fc;
        LocalSQL.connect();

        logger.info("getting domains");
        getDomains();

        logger.info("starting .sql file");
        this.SQLWriter.write(
                "SET autocommit=0;\n" +
                        "SET foreign_key_checks=0;\n" +
                        "SET unique_checks=0;\n"
        );


        // System.out.println("unzipping");
        // unzipDirs();

        int i = 0;
        int num_errors = 0;
        for (Integer node_id : domains.keySet()) {
            if (node_id % 2 == this.segment_id) {
                if (i % commit_interval == 0) {
                    SQLWriter.write("COMMIT;\n");
                    SQLWriter.flush();
                }
                try {
                    System.out.println(node_id + " " + domains.get(node_id) + " " + i);
                    logger.info("starting fc.runComparison for " + node_id + " " + domains.get(node_id) + " index =" + i);
                    fc = new ChainDomainFatcatPair(node_id, this);
                    fc.runComparison();

                } catch (Exception e) {
                    logger.severe(e.getMessage());
                    e.printStackTrace(System.err);
                    num_errors++;
                    ErrorWriter.write(node_id + "\n");
                    ErrorWriter.flush();
                }
                i++;
            }
        }

        logger.info("ending .sql file");
        try {
            this.SQLWriter.write(
                    "SET foreign_key_checks=1;\n" +
                            "SET unique_checks=1;\n" +
                            "COMMIT;\n" +
                            "SET autocommit=1;"
            );
            this.SQLWriter.flush();
            this.SQLWriter.close();

            List<String> lines = Files.readAllLines(Paths.get(SQLDir + subdir + ".sql"));
            if (lines.isEmpty()) {
                throw new RuntimeException("SQLWriter output file is empty");
            }
            String lastLine = lines.get(lines.size() - 1).trim();
            if (!lastLine.equals("SET autocommit=1;")) {
                throw new RuntimeException(
                        "SQLWriter did not end properly. Expected: SET autocommit=1; Actual: " + lastLine
                );
            }

        } catch (Exception e) {
            logger.severe(e.getMessage());
            e.printStackTrace();
            throw e;
        }


        logger.info("zipping files");
        try {
            zipDirs();
            if (Files.exists(Paths.get(this.AlnDir)) && !Files.exists(Paths.get(this.destPathAln))) {
                throw new RuntimeException("Files did not zip to " + this.destPathAln);
            }
            if (Files.exists(Paths.get(this.AlnDir))) {
                long count = Files.list(Paths.get(this.AlnDir)).count();
                if (count == 0) {
                    throw new RuntimeException("No alignments were produced in " + this.AlnDir);
                }
            }
        } catch (Exception e) {
            logger.severe(e.getMessage());
            e.printStackTrace();
            throw e;
        }


        logger.info("deleting chain copy");
        try {
            Files.delete(Paths.get(this.chainFile));
            if (Files.exists(Paths.get(this.chainFile))) {
                throw new RuntimeException("File did not delete " + this.chainFile);
            }
        } catch (Exception e) {
            logger.severe(e.getMessage());
            e.printStackTrace();
            throw e;
        }

//        if (num_errors == 0) {
        logger.info("deleting " + TempDir);
        try {
            fileHandler.close();
            Files.delete(logFile);
            Files.delete(Paths.get(TempDir));
        } catch (Exception e){
            if (Files.exists(logFile)) {
                throw new RuntimeException("File did not delete " + logFile);
            }
            if (Files.exists(Paths.get(TempDir))) {
                throw new RuntimeException("File did not delete " + TempDir);
            }
        }

//        }
//        }
//        logger.info("deleting chain copy");
//        Files.delete(Paths.get(this.chainFile));
//        logger.info("deleting " + TempDir);
//        fileHandler.close();
//        Files.delete(logFile);
//        Files.delete(Paths.get(TempDir));
    }

    public static void runComparisons(int targetID, int segmentID) throws Exception {

        // atoi returns an integer, not a string. already converted to int when job is created.
        int chain_id = targetID;
        int segment_id = segmentID;

        ChainDomainFatcatPairs fcs = new ChainDomainFatcatPairs(chain_id, segment_id);
        fcs.runOneSetComparisons_noSQL();

    }


    public static void createJobs() throws Exception {

        Statement stmt = LocalSQL.createStatement();

        System.out.println("getting finishedSids by checking scop_node_fatcat_chain counts (will be skipping already zipped failures later)");
        HashMap<String, Integer> finishedSids = new HashMap<>();
//        ResultSet rsFinishedSids = stmt.executeQuery("SELECT \n" +
//                "    MIN(astral_chain.id) AS chain_id,\n" +
//                "    astral_chain.sid\n" +
//                "FROM job_done\n" +
//                "JOIN astral_chain \n" +
//                "  ON job_done.target_id = astral_chain.id\n" +
//                "WHERE job_type_id = 25\n" +
//                "  AND time_created >= '2026-05-01 00:00:00'\n" +
//                "  AND status IS NULL\n" +
//                "GROUP BY astral_chain.sid;");
        ResultSet rsFinishedSids = stmt.executeQuery("" +
                "select chain_id, count(*) as count " +
                "from scop_node_fatcat_chain " +
                "group by chain_id " +
                "having count > 5000;"
        );
        while (rsFinishedSids.next()) {
            String sid = rsFinishedSids.getString("sid").toLowerCase();
            int id = rsFinishedSids.getInt("chain_id");
            finishedSids.put(sid, id);
        }
        rsFinishedSids.close();
        stmt.close();

        System.out.println("getting allSids");
        HashMap<String, Integer> allSids = getChains();

        System.out.println("filtering for unfinishedIds");
        ArrayList<Integer> unfinishedIDs = new ArrayList<>();
        for (String sid : allSids.keySet()) {
            if (!finishedSids.containsKey(sid)) {
                unfinishedIDs.add(allSids.get(sid));
            }
        }

        System.out.println("making jobs");
        for (int chain_id : unfinishedIDs) {
            for (int i = 0; i < 2; i++) {
                LocalSQL.newJob(25,
                        chain_id,
                        String.valueOf(i)
                );
            }
        }
    }

    public static void createJob(int chain_id, int segment_id) throws Exception {
        LocalSQL.newJob(25,
                chain_id,
                String.valueOf(segment_id)
        );
    }

    public static void runJobsLocal() throws Exception {
        ArrayList<Integer> chainIDsDone = new ArrayList<>();

        ArrayList<Integer> chainIDs = getChains(10);
        for (int chain_id : chainIDs) {
            if (!chainIDsDone.contains(chain_id)) {
                for (int i = 0; i < 2; i++) {
                    runComparisons(chain_id, i);
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        LocalSQL.connectRW();
//        ChainDomainFatcatPairs fcs = new ChainDomainFatcatPairs(3295190, 1);
//        fcs.runOneSetComparisons_noSQL();
//        runComparisons(3295190, 0);
//        runJobsLocal();

       createJobs();
        // createJob(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
    }

}
