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
import java.util.HashSet;

public class ChainDomainFatcatPairs {

    // 1 job = 1 chain

    public static LinkedHashMap<Integer, String> domains = new LinkedHashMap<>(); // (node_id: sid)

    public int chain_id;
    public int segment_id;
    public int population_id;
    public int run_id;
    public String sourceChainPath;
    public String chainChecksum;
    public HashSet<Integer> completedNodes = new HashSet<>();

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
        this(chain_id,segment_id,0,0);
    }

    public ChainDomainFatcatPairs(int chain_id, int segment_id,
                                  int population_id, int run_id) throws Exception {

        this.chain = "";
        this.chain_id = chain_id;
        this.segment_id = segment_id;
        this.population_id = population_id;
        this.run_id = run_id;
        this.chain_start_index = "";
        this.chain_end_index = "";
        this.chainRafBody = "";
        this.chainRafSeq = "";
        getChainInfo(chain_id);
        this.chainHash = chain.substring(1, 3);
        this.chainFile = sourceChainPath;

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
        String sourceChainStr = sourceChainPath;
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
        String query;
        if (population_id>0)
            query="select ac.sid,r.line,r.raf_version_id,b.coordinate_file_path,"+
                "b.coordinate_sha256 from astral_chain ac join raf r on ac.raf_id=r.id " +
                "join astral_chain_stable_binding b on b.astral_chain_id=ac.id " +
                "join astral_chain_all_candidate c on c.stable_binding_id=b.id " +
                "where ac.id="+chain_id+" and c.population_id="+population_id;
        else
            query="select astral_chain.sid,raf.line,raf.raf_version_id,null,null " +
                "from astral_chain join raf on astral_chain.raf_id=raf.id " +
                "where astral_chain.id="+chain_id+" order by raf.raf_version_id desc";
        ResultSet rs = stmt.executeQuery(query);
        if (rs.next()) {
            this.chain = rs.getString(1);
            this.chainRafBody = RAF.getRAFBody(rs.getString("line"));
            this.sourceChainPath=rs.getString(4);
            this.chainChecksum=rs.getString(5);
            if (population_id==0)
                this.sourceChainPath=ChainDomainFatcatPair.CHAIN_DIR+
                    chain.substring(1,3)+"/"+chain+".pdb";
            else if (sourceChainPath==null || chainChecksum==null ||
                     !chainChecksum.equals(MakeChainPDB.sha256(Paths.get(sourceChainPath))))
                throw new Exception("versioned chain provenance/checksum failure for "+chain);
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
                "  AND astral_chain_subset_id.release_id = 19\n" +
                "  AND raf.first_release_id <= 19\n" +
                "  AND raf.last_release_id >= 19\n" +
                "  AND astral_chain.source_id = 2\n" +
                "  AND astral_domain.source_id = 1\n" +
                "  AND astral_domain.style_id = 1\n" +
                "  AND scop_node.release_id = 19\n" +
                "GROUP BY astral_chain.sid;");
        while (rs.next()) {
            int chain_id = rs.getInt("chain_id");
            String sid = rs.getString("sid");
            l.put(sid, chain_id);
        }
        rs.close();
        stmt.close();
        return l;
    }

    /** Return representatives from one explicit validated all-chain population. */
    public static LinkedHashMap<String,Integer> getChains(int populationID,
                                                           boolean expanded)
        throws Exception {
        LinkedHashMap<String,Integer> rv=new LinkedHashMap<>();
        Statement stmt=LocalSQL.createStatement();
        ResultSet rs=stmt.executeQuery(
            "select c.sid,s.astral_chain_id from astral_chain_all_subset_id s " +
            "join astral_chain_all_candidate c on c.population_id=s.population_id " +
            "and c.astral_chain_id=s.astral_chain_id " +
            "join astral_chain_all_population p on p.id=s.population_id " +
            "where s.population_id="+populationID+" and s.pct_identical=90 " +
            "and p.status='validated' order by s.representative_rank");
        while (rs.next()) rv.put(rs.getString(1),rs.getInt(2));
        rs.close(); stmt.close(); return rv;
    }

    public static ArrayList<String> getChainSids() throws Exception {
        ArrayList<String> l = new ArrayList<>();
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select distinct astral_chain.id as chain_id, astral_chain.sid from astral_chain_subset_id join astral_chain on astral_chain_subset_id.astral_chain_id = astral_chain.id join raf on astral_chain.raf_id = raf.id join link_pdb on raf.pdb_chain_id = link_pdb.pdb_chain_id join scop_node on link_pdb.node_id = scop_node.id join astral_domain on scop_node.id = astral_domain.node_id join pdb_chain on link_pdb.pdb_chain_id = pdb_chain.id join pdb_release on pdb_chain.pdb_release_id = pdb_release.id join pdb_local on pdb_release.id = pdb_local.pdb_release_id where astral_chain_subset_id.pct_identical = 90 and astral_domain.source_id = 1 and astral_domain.style_id = 1 and scop_node.release_id = 19");
        while (rs.next()) {
            String sid = rs.getString("sid");
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
        if (Files.exists(zipFile) && run_id==0) {
            return;
        }
        if (Files.exists(zipFile) && run_id>0)
            unzipDirs();

        int commit_interval = 250;

        ChainDomainFatcatPair fc;
        LocalSQL.connect();

        logger.info("getting domains");
        getDomains();
        if (run_id>0) loadCompleted();

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
                if (run_id>0 && completedNodes.contains(node_id))
                    continue;
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
                    logger.log(Level.SEVERE,
                        "comparison failed for node "+node_id+" "+domains.get(node_id),e);
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

            Path sqlPath=Paths.get(SQLDir + subdir + ".sql");
            List<String> lines = Files.readAllLines(sqlPath);
            if (lines.isEmpty()) {
                throw new RuntimeException("SQLWriter output file is empty");
            }
            String lastLine = lines.get(lines.size() - 1).trim();
            if (!lastLine.equals("SET autocommit=1;")) {
                throw new RuntimeException(
                        "SQLWriter did not end properly. Expected: SET autocommit=1; Actual: " + lastLine
                );
            }

            // Expanded-population jobs must make their manifest durable before
            // JobDaemon can mark the queue job complete.  The historical
            // runner deliberately left SQL for a later bulk import; that is
            // incompatible with provenance-aware completion and idempotent
            // retries.  Use JobDaemon's existing connectRW() connection rather
            // than embedding another JDBC URL here.
            if (run_id>0)
                importSQLManifest(sqlPath);

        } catch (Exception e) {
            logger.severe(e.getMessage());
            e.printStackTrace();
            throw e;
        }

        // Successful comparisons have already been committed above.  Leave
        // the job in the queue when an individual comparison failed so a
        // retry loads completion state and runs only the missing pairs.
        if (num_errors>0) {
            // Worker-local /tmp is recreated on every retry.  Preserve the
            // detailed stack traces beside the shared node-ID error file so
            // deterministic failures remain diagnosable after the worker or
            // retry exits.
            fileHandler.flush();
            Files.copy(logFile, Paths.get(ErrorDir,subdir+".log"),
                       StandardCopyOption.REPLACE_EXISTING);
            throw new Exception(num_errors+" FATCAT comparisons failed for "+
                                chain+" segment "+segment_id);
        }
        Files.deleteIfExists(Paths.get(ErrorDir,subdir+".log"));


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

    /** Import one generated SQL manifest atomically for an expanded run. */
    private void importSQLManifest(Path sqlPath) throws Exception {
        String sql=new String(Files.readAllBytes(sqlPath),"UTF-8");
        ArrayList<String> statements=new ArrayList<String>();
        StringBuilder current=new StringBuilder();
        boolean single=false, dbl=false, backtick=false, escaped=false;
        for (int i=0;i<sql.length();i++) {
            char ch=sql.charAt(i);
            if (escaped) {
                current.append(ch);
                escaped=false;
                continue;
            }
            if (ch=='\\' && (single || dbl)) {
                current.append(ch);
                escaped=true;
                continue;
            }
            if (ch=='\'' && !dbl && !backtick) single=!single;
            else if (ch=='\"' && !single && !backtick) dbl=!dbl;
            else if (ch=='`' && !single && !dbl) backtick=!backtick;
            if (ch==';' && !single && !dbl && !backtick) {
                String statement=current.toString().trim();
                if (statement.length()>0) statements.add(statement);
                current.setLength(0);
            }
            else current.append(ch);
        }
        if (single || dbl || backtick)
            throw new Exception("unterminated quoted value in "+sqlPath);
        if (current.toString().trim().length()>0)
            throw new Exception("unterminated SQL statement in "+sqlPath);

        Statement stmt=LocalSQL.createStatement();
        if (stmt==null) throw new Exception("could not create SQL import statement");
        int imported=0;
        LocalSQL.setAutoCommit(false);
        try {
            for (String statement:statements) {
                String normalized=statement.trim().toLowerCase();
                if (normalized.startsWith("set autocommit=") ||
                    normalized.startsWith("set foreign_key_checks=") ||
                    normalized.startsWith("set unique_checks=") ||
                    normalized.equals("commit"))
                    continue;
                stmt.executeUpdate(statement);
                imported++;
            }
            LocalSQL.commit();
            logger.info("imported "+imported+" SQL statements from "+sqlPath);
        }
        catch (Exception e) {
            LocalSQL.rollback();
            throw new Exception("rolled back SQL manifest "+sqlPath+": "+
                                e.getMessage(),e);
        }
        finally {
            stmt.close();
            LocalSQL.setAutoCommit(true);
        }
    }

    private void loadCompleted() throws Exception {
        Statement stmt=LocalSQL.createStatement();
        ResultSet rs=stmt.executeQuery("select node_id from " +
            "scop_node_fatcat_chain_completion where run_id="+run_id+
            " and chain_id="+chain_id+
            " and status in ('complete_retained','complete_rejected')");
        while (rs.next()) completedNodes.add(rs.getInt(1));
        rs.close();
        // Legacy retained hits remain authoritative and must not be recomputed.
        // Low-quality legacy rows are intentionally absent after shadow swap;
        // partial chains will therefore create compact rejected-completion rows
        // only for work that still needs to be represented.
        rs=stmt.executeQuery("select domain_id from scop_node_fatcat_chain where "+
            "chain_id="+chain_id+" and p_val is not null and p_val<=0.05 and "+
            "ini_rmsd is not null and ini_rmsd<4");
        while (rs.next()) completedNodes.add(rs.getInt(1));
        rs.close(); stmt.close();
    }

    public static void runComparisons(int targetID, int segmentID) throws Exception {

        // atoi returns an integer, not a string. already converted to int when job is created.
        int chain_id = targetID;
        int segment_id = segmentID;

        ChainDomainFatcatPairs fcs = new ChainDomainFatcatPairs(chain_id, segment_id);
        fcs.runOneSetComparisons_noSQL();

    }

    public static void runComparisons(int targetID, int segmentID,
                                      int populationID, int runID) throws Exception {
        ChainDomainFatcatPairs fcs=new ChainDomainFatcatPairs(targetID,segmentID,
                                                              populationID,runID);
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
                "select astral_chain.sid, scop_node_fatcat_chain.chain_id, count(*) as count " +
                "from scop_node_fatcat_chain " +
                "join astral_chain on scop_node_fatcat_chain.chain_id = astral_chain.id " +
                "group by astral_chain.sid, scop_node_fatcat_chain.chain_id " +
                "having count(*) > 5000;"
        );
        while (rsFinishedSids.next()) {
            String sid = rsFinishedSids.getString("sid");
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

    public static int createRun(int populationID) throws Exception {
        String checksum=MakeChainPDB.sha256(Paths.get(ChainDomainFatcatPair.FATCAT));
        PreparedStatement ps=LocalSQL.prepareStatement(
            "insert ignore into scop_node_fatcat_chain_run " +
            "(population_id,method_name,executable_path,executable_sha256,parameters,created_at) " +
            "values (?,'FATCAT',?,?,'-r -m -t',now())");
        ps.setInt(1,populationID); ps.setString(2,ChainDomainFatcatPair.FATCAT);
        ps.setString(3,checksum); ps.executeUpdate(); ps.close();
        Statement stmt=LocalSQL.createStatement(); ResultSet rs=stmt.executeQuery(
            "select id from scop_node_fatcat_chain_run where population_id="+populationID+
            " and method_name='FATCAT' and executable_sha256='"+checksum+"'");
        rs.next(); int id=rs.getInt(1); rs.close(); stmt.close(); return id;
    }

    /** Record exact reusable legacy coverage without one row per rejected hit. */
    public static int adoptLegacyCoverage(int populationID, int runID)
        throws Exception {
        LinkedHashMap<String,Integer> chains=getChains(populationID,true);
        Statement stmt=LocalSQL.createStatement();
        ResultSet rs=stmt.executeQuery("select count(*) from scop_node_fatcat_chain_run "+
            "where id="+runID+" and population_id="+populationID);
        rs.next(); if (rs.getInt(1)!=1) throw new Exception("run/population mismatch");
        rs.close();
        rs=stmt.executeQuery("select count(*) from scop_node_fatcat_rep");
        rs.next(); int domainCount=rs.getInt(1); rs.close();
        rs=stmt.executeQuery("select coalesce(max(id),0) from scop_node_fatcat_chain");
        rs.next(); long sourceMax=rs.getLong(1); rs.close();

        PreparedStatement upsert=LocalSQL.prepareStatement(
            "insert into scop_node_fatcat_chain_legacy_coverage "+
            "(run_id,chain_id,expected_domain_count,observed_domain_count,"+
            "result_row_count,duplicate_row_count,source_max_result_id,is_complete,"+
            "checked_at) values (?,?,?,?,?,?,?,?,now()) on duplicate key update "+
            "expected_domain_count=values(expected_domain_count),"+
            "observed_domain_count=values(observed_domain_count),"+
            "result_row_count=values(result_row_count),"+
            "duplicate_row_count=values(duplicate_row_count),"+
            "source_max_result_id=values(source_max_result_id),"+
            "is_complete=values(is_complete),checked_at=values(checked_at)");
        ArrayList<Integer> ids=new ArrayList<Integer>(chains.values());
        int complete=0;
        for (int offset=0;offset<ids.size();offset+=500) {
            int end=Math.min(offset+500,ids.size());
            StringBuilder in=new StringBuilder();
            HashMap<Integer,long[]> counts=new HashMap<Integer,long[]>();
            for (int i=offset;i<end;i++) {
                if (i>offset) in.append(','); in.append(ids.get(i));
                counts.put(ids.get(i),new long[]{0,0});
            }
            rs=stmt.executeQuery("select f.chain_id,count(*),count(distinct f.domain_id) "+
                "from scop_node_fatcat_chain f force index(chain_id) join "+
                "scop_node_fatcat_rep d on d.node_id=f.domain_id where f.chain_id in ("+
                in+") and f.id<="+sourceMax+" group by f.chain_id");
            while (rs.next())
                counts.put(rs.getInt(1),new long[]{rs.getLong(2),rs.getLong(3)});
            rs.close();
            for (int i=offset;i<end;i++) {
                int chainID=ids.get(i); long[] n=counts.get(chainID);
                boolean full=n[1]==domainCount;
                if (full) complete++;
                upsert.setInt(1,runID); upsert.setInt(2,chainID);
                upsert.setInt(3,domainCount); upsert.setLong(4,n[1]);
                upsert.setLong(5,n[0]); upsert.setLong(6,n[0]-n[1]);
                upsert.setLong(7,sourceMax); upsert.setInt(8,full?1:0);
                upsert.addBatch();
            }
            upsert.executeBatch();
            System.out.println("legacy coverage "+end+"/"+ids.size()+
                               " complete_chains="+complete);
        }
        upsert.close(); stmt.close(); return complete;
    }

    public static void createJobs(int populationID, int runID) throws Exception {
        LinkedHashMap<String,Integer> chains=getChains(populationID,true);
        Statement stmt=LocalSQL.createStatement();
        ResultSet rs=stmt.executeQuery("select count(*) from scop_node_fatcat_chain_run "+
            "where id="+runID+" and population_id="+populationID);
        rs.next();
        if (rs.getInt(1)!=1) throw new Exception("run/population mismatch");
        rs.close();
        rs=stmt.executeQuery("select count(*) from scop_node_fatcat_rep");
        rs.next(); int domainCount=rs.getInt(1); rs.close();

        HashSet<Integer> legacyComplete=new HashSet<Integer>();
        rs=stmt.executeQuery("select chain_id from "+
            "scop_node_fatcat_chain_legacy_coverage where run_id="+runID+
            " and is_complete=1 and expected_domain_count="+domainCount+
            " and observed_domain_count="+domainCount);
        while (rs.next()) legacyComplete.add(rs.getInt(1));
        rs.close();

        // A legacy job uses only the segment in args; an expanded job appends
        // population and run IDs.  Either form owns the same chain/segment
        // workspace, so never queue an overlapping active job.
        HashSet<String> activeSegments=new HashSet<String>();
        rs=stmt.executeQuery("select target_id,args from job where job_type_id=25");
        while (rs.next()) {
            String args=rs.getString(2);
            if (args==null) continue;
            String[] fields=args.trim().split("\\s+");
            if (fields.length>0 && (fields[0].equals("0") || fields[0].equals("1")))
                activeSegments.add(rs.getInt(1)+":"+fields[0]);
        }
        rs.close();

        PreparedStatement hasCompletion=LocalSQL.prepareStatement(
            "select 1 from scop_node_fatcat_chain_completion where run_id=? "+
            "and chain_id=? and status in ('complete_retained','complete_rejected') limit 1");
        PreparedStatement completedCount=LocalSQL.prepareStatement(
            "select count(*) from ("+
            "select c.node_id from scop_node_fatcat_chain_completion c join "+
            "scop_node_fatcat_rep d on d.node_id=c.node_id where c.run_id=? "+
            "and c.chain_id=? and c.status in ('complete_retained','complete_rejected') "+
            "union select f.domain_id from scop_node_fatcat_chain f force index(chain_id) "+
            "join scop_node_fatcat_rep d on d.node_id=f.domain_id where f.chain_id=? "+
            "and f.p_val is not null and f.p_val<=0.05 and f.ini_rmsd is not null "+
            "and f.ini_rmsd<4) completed");
        int queued=0, skippedLegacy=0, skippedFinished=0, skippedActive=0;
        for (Integer chainID:chains.values()) {
            if (legacyComplete.contains(chainID)) { skippedLegacy++; continue; }

            // The exact union is needed only after this run has generated at
            // least one completion row.  Initially every partial legacy chain
            // necessarily needs work.
            hasCompletion.setInt(1,runID); hasCompletion.setInt(2,chainID);
            rs=hasCompletion.executeQuery(); boolean hasNew=rs.next(); rs.close();
            if (hasNew) {
                completedCount.setInt(1,runID); completedCount.setInt(2,chainID);
                completedCount.setInt(3,chainID); rs=completedCount.executeQuery();
                rs.next(); int complete=rs.getInt(1); rs.close();
                if (complete>=domainCount) { skippedFinished++; continue; }
            }
            for (int segment=0;segment<2;segment++) {
                String key=chainID+":"+segment;
                if (activeSegments.contains(key)) { skippedActive++; continue; }
                int jobID=LocalSQL.newJob(25,chainID,
                    segment+" "+populationID+" "+runID,stmt);
                if (jobID!=0) {
                    queued++;
                    activeSegments.add(key);
                }
            }
        }
        hasCompletion.close(); completedCount.close();
        stmt.close();
        System.out.println("FATCAT jobs queued="+queued+" legacy_complete="+
            skippedLegacy+" run_complete="+skippedFinished+
            " active_segments="+skippedActive);
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

        if (args.length==2 && args[0].equals("create-run"))
            System.out.println(createRun(Integer.parseInt(args[1])));
        else if (args.length==3 && args[0].equals("adopt-legacy"))
            System.out.println("fully reusable legacy chains="+
                adoptLegacyCoverage(Integer.parseInt(args[1]),Integer.parseInt(args[2])));
        else if (args.length==3 && args[0].equals("create-jobs"))
            createJobs(Integer.parseInt(args[1]),Integer.parseInt(args[2]));
        else if (args.length==4 && args[0].equals("run"))
            runComparisons(Integer.parseInt(args[1]),Integer.parseInt(args[2]),
                           Integer.parseInt(args[3]),createRun(Integer.parseInt(args[3])));
        else
            throw new Exception("Usage: ChainDomainFatcatPairs create-run population | adopt-legacy population run | create-jobs population run | run chain segment population");
    }

}
