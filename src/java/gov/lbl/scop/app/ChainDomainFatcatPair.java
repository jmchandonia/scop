package gov.lbl.scop.app;

import gov.lbl.scop.local.LocalSQL;
import gov.lbl.scop.util.RAF;
import org.strbio.mol.Alignment;
import org.strbio.mol.Protein;
import org.strbio.mol.lib.*;

import java.io.*;
import java.nio.file.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.logging.*;
import java.util.regex.*;

public class ChainDomainFatcatPair {

    /*
    runs one structural comparison (via FATCAT and PostFATCAT) between a specified astral chain and scop domain.
    stores the results in SQL tables scop_node_fatcat_chain, scop_node_fatcat_chain_gap, and scop_node_fatcat_chain_matrix.
    also stores copies of the resulting .aln files in /alignments/, the resulting .matrix files in /matrices/, deletes superposition files.
    */

    public final static String COMMON_DIR = "/lab/proj/astral/fatcat/chains/2.08/";
    public final static String TEMP_DIR = "/tmp/"; // NEED the first and last slashes!

    public final static String DOMAIN_DIR = "/lab/proj/astral/pdbstyle/2.08/";
    public final static String CHAIN_DIR = "/lab/proj/astral/chains/";

    public final static String ALIGNMENTS_DIR = COMMON_DIR + "alignments/";
    public final static String SQL_DIR = COMMON_DIR + "sql/";
    public final static String MATRICES_DIR = COMMON_DIR + "matrices/";
    public final static String ERRORS_DIR = COMMON_DIR + "errors/";

    public final static String FATCAT = "/lab/app/FATCAT/cur/FATCAT-dist/FATCATMain/FATCAT";
    final static String PostFATCAT = "/lab/app/FATCAT/cur/FATCAT-dist/FATCATMain/PostFATCAT";

    // all global indices are in terms of RAF indices. within functions, these indices first get converted to regular indices, do stuff with it, then convert back.

    String chain; // 1u5ta
    String chainFile; // path to the chain file
    String chainHash;
    int chain_id; // in astral_chain
    String chain_start_index;
    String chain_end_index;
    String chainRafBody;
    String chainRafSeqAlignment; // ONLY WITHIN THE ALIGNMENT
    String chainRafSeq;
    String chainSeq;

    String domain; // d1u5ta1
    String domainFile; // path to the domain file
    String domainHash;
    int domain_id; // in scop_node
    String domain_start_index; // could be i.e. 1A
    String domain_end_index;
    String domainRafBody;
    String domainRafSeqAlignment; // ONLY WITHIN THE ALIGNMENT
    String domainRafSeq;
    String domainSeq;

    String alnSeq;
    String tempDir;

    // FATCAT results
    Integer chain_len; // number of residues in the chain according to FATCAT
    Integer domain_len; // number of residues in the domain according to FATCAT
    Integer ini_len;
    Double ini_rmsd;
    Integer opt_equ;
    Double opt_rmsd;
    Double chain_rmsd;
    Double score;
    Integer align_len;
    Integer gaps;
    Double p_val;
    Integer afp_num;
    Double identity;
    Double similarity;
    boolean shortMatch;
    boolean hasAln;
    boolean hasMatrix;

    String chain_alignment_start;
    Integer chain_alignment_length;
    String domain_alignment_start;
    Integer domain_alignment_length;

    double[][] rotLst;
    double[] transLst;

    // Gap data
    public ArrayList<ChainDomainFatcatPair.Gap> chainGaps;
    public ArrayList<ChainDomainFatcatPair.Gap> domainGaps;

    String alignmentFile;
    String matrixFile;
    String superpositionFile;

    Logger logger;
    public Path logFile;
    public FileHandler fileHandler;
    public BufferedWriter SQLWriter;

    public ChainDomainFatcatPairs fcs;

    static String align1 = "Align.*\\.pdb (\\d+) with .*\\.ent (\\d+)";
    static Pattern pAlign1 = Pattern.compile(align1); // af_len, scop_len
    static String align2 = ".* ini-len (\\d+) ini-rmsd (\\d+\\.\\d+) opt-equ (\\d+) opt-rmsd (\\d+\\.\\d+) chain-rmsd (\\d+\\.\\d+) Score (\\d+\\.\\d+) align-len (\\d+) gaps (\\d+)"; // opt-rmsd, score, align-len, gaps
    static Pattern pAlign2 = Pattern.compile(align2); // ini_len, ini_rmsd, opt_equ, opt_rmsd, chain_rmsd, score, align_len, gaps
    static String align3 = "P-value ([0-9]+.[0-9]+e[+-]*[0-9]+) Afp-num (\\d+) Identity (\\d+\\.\\d+)% Similarity (\\d+\\.\\d+)%";
    static Pattern pAlign3 = Pattern.compile(align3); // p-val, afp_num, identity, similarity
    static String alignIndex = "Chain \\d+:\\s+(-*\\d+[A-Z]*) [-\\w]+";
    static Pattern pAlignIndex = Pattern.compile(alignIndex);

    static String alignDomainRange = "([A-Za-z0-9]):(?:\\d+(?:-(\\d+))?)?";
    static Pattern pAlignDomainRange = Pattern.compile(alignDomainRange);

    public ChainDomainFatcatPair(int chain_id, int domain_id) throws Exception {

        this.chain = null;
        this.chain_id = chain_id;
        this.chain_start_index = null;
        this.chain_end_index = null;
        this.chainSeq = "";
        this.chainRafBody = "";
        this.chainRafSeq = "";
        getChainInfo(chain_id);
        this.chainHash = chain.substring(1, 3);
        this.chainFile = "";

        this.domain = "";
        this.domain_id = domain_id;
        this.domain_start_index = "";
        this.domain_end_index = "";
        this.domainSeq = "";
        this.domainRafBody = "";
        this.domainRafSeq = "";
        getDomainInfo(domain_id);
        this.domainHash = domain.substring(2, 4);
        this.domainFile = DOMAIN_DIR + domainHash + "/" + this.domain + ".ent";

        // FATCAT results
        this.chain_len = null;
        this.domain_len = null;
        this.ini_len = null;
        this.ini_rmsd = null;
        this.opt_equ = null;
        this.opt_rmsd = null;
        this.chain_rmsd = null;
        this.score = null;
        this.align_len = null;
        this.gaps = null;
        this.p_val = null;
        this.afp_num = null;
        this.identity = null;
        this.similarity = null;
        this.shortMatch = false;

        this.chain_alignment_length = 0;
        this.domain_alignment_length = 0;
        this.chain_alignment_start = null;
        this.domain_alignment_start = null;

        // matrix coordinates
        this.rotLst = new double[3][3];
        this.transLst = new double[3];

        this.chainGaps = new ArrayList<>();
        this.domainGaps = new ArrayList<>();

        this.alnSeq = "";
        this.tempDir = TEMP_DIR + chainHash + "/" + this.chain + "/";
        this.alignmentFile = this.chain + "-" + this.domain + ".aln"; // file name only
        this.matrixFile = this.chain + "-" + this.domain + ".matrix"; // in /temp/domain-0/ during, then moved to matrices_dir

        this.logger = setupLogger();

        // copy chain file, put in temp dir
        String sourceChainStr = CHAIN_DIR + chainHash + "/" + this.chain + ".pdb";
        String tempChainStr = tempDir + this.chain + ".pdb";
        this.chainFile = tempChainStr;

        // Ensure parent directories exist
        Path tempPath = Paths.get(tempChainStr);
        Files.createDirectories(tempPath.getParent());

        Files.copy(Paths.get(sourceChainStr), tempPath, StandardCopyOption.REPLACE_EXISTING);

        if (!Files.isRegularFile(tempPath)) {
            throw new Exception("did not successfuly copy to " + tempChainStr);
        }

        this.SQLWriter = new BufferedWriter(new FileWriter(tempDir + chain + ".sql"));

    }

    public ChainDomainFatcatPair(int domain_id, ChainDomainFatcatPairs fcs) throws Exception {

        this.fcs = fcs;

        this.chain = fcs.chain;
        this.chain_id = fcs.chain_id;
        this.chain_start_index = fcs.chain_start_index;
        this.chain_end_index = fcs.chain_end_index;
        this.chainSeq = "";
        this.chainRafBody = fcs.chainRafBody;
        this.chainRafSeq = fcs.chainRafSeq;
        this.chainHash = fcs.chainHash;
        this.chainFile = fcs.chainFile;

        this.domain = null;
        this.domain_id = domain_id;
        this.domain_start_index = null;
        this.domain_end_index = null;
        this.domainSeq = "";
        this.domainRafBody = "";
        this.domainRafSeq = "";
        getDomainInfo(domain_id);
        this.domainHash = domain.substring(2, 4);
        this.domainFile = DOMAIN_DIR + domainHash + "/" + this.domain + ".ent";

        // FATCAT results
        this.chain_len = null;
        this.domain_len = null;
        this.ini_len = null;
        this.ini_rmsd = null;
        this.opt_equ = null;
        this.opt_rmsd = null;
        this.chain_rmsd = null;
        this.score = null;
        this.align_len = null;
        this.gaps = null;
        this.p_val = null;
        this.afp_num = null;
        this.identity = null;
        this.similarity = null;
        this.shortMatch = false;

        this.chain_alignment_length = 0;
        this.domain_alignment_length = 0;
        this.chain_alignment_start = null;
        this.domain_alignment_start = null;

        this.rotLst = new double[3][3];
        this.transLst = new double[3];

        this.chainGaps = new ArrayList<>();
        this.domainGaps = new ArrayList<>();

        this.alnSeq = "";
        this.tempDir = fcs.TempDir;
        this.alignmentFile = this.chain + "-" + this.domain + ".aln"; // file name only
        this.matrixFile = this.chain + "-" + this.domain + ".matrix"; // in /temp/domain-0/ during, then moved to matrices_dir

        this.logger = fcs.logger;
        this.SQLWriter = fcs.SQLWriter;

    }

    private void getChainInfo(int chain_id) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select astral_chain.sid, raf.line from astral_chain join raf on astral_chain.raf_id = raf.id where astral_chain.id=" + chain_id + " order by raf.raf_version_id desc");
        if (rs.next()) {
            this.chain = rs.getString(1).toLowerCase();
            this.chainRafBody = RAF.getRAFBody(rs.getString(2));
            this.chain_start_index = RAF.getResID(this.chainRafBody, 0); // sourceType 2 = SEQRES, 1 = ATOM
            this.chain_end_index = RAF.getResID(this.chainRafBody, RAF.getSeqLength(this.chainRafBody) - 1);
//            this.chainRafSeq = RAF.wholeChainSeq(this.chainRafBody, 1).getSequence().toUpperCase();
            rs.close();
            stmt.close();
        } else {
            rs.close();
            stmt.close();
            throw new Exception("No chain found for id=" + chain_id);
        }
    }

    private void getDomainInfo(int domain_id) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select scop_node.sid, raf.line, scop_node.description from scop_node join link_pdb on scop_node.id = link_pdb.node_id join pdb_chain on link_pdb.pdb_chain_id = pdb_chain.id join raf on pdb_chain.id = raf.pdb_chain_id  where scop_node.id=" + domain_id + " order by raf.raf_version_id desc");
        if (rs.next()) {
            this.domain = rs.getString(1).toLowerCase();
            this.domainRafBody = RAF.getRAFBody(rs.getString(2));

            String description = rs.getString(3);
            Matcher m = pAlignDomainRange.matcher(description);
            if (m.find()) { // e.g. 1hqc A:243-318
                if (m.groupCount() == 3) {
                    this.domain_start_index = m.group(2);
                    this.domain_end_index = m.group(3);
//                    this.domainRafSeq = RAF.partialChainSeq(
//                            this.domainRafBody, 1,
//                            RAF.rTranslateIndex(this.domainRafBody, 1, domain_start_index),
//                            RAF.rTranslateIndex(this.domainRafBody, 1, domain_end_index)
//                    ).getSequence().toUpperCase();
                } else { // e.g. 1hqc A:
                    this.domain_start_index = RAF.getResID(this.domainRafBody, 0); // sourceType 2 = SEQRES, 1 = ATOM
                    this.domain_end_index = RAF.getResID(this.domainRafBody, RAF.getSeqLength(this.domainRafBody) - 1);
//                    this.domainRafSeq = RAF.wholeChainSeq(this.domainRafBody, 1).getSequence().toUpperCase();
                }
            }
            rs.close();
            stmt.close();
        } else {
            rs.close();
            stmt.close();
            throw new Exception("No domain found for id=" + domain_id);
        }
    }

    /*
    compare fatcatSeq to rafMap, parse ranges of missing residues and post-translational modifications
     */
    public String getRafSeq(String struct) throws Exception {

        // first, get rafMap //
        // get raf line
        String rafLine = "";
        int rafVersion = -1;
        Statement stmtRAF = LocalSQL.createStatement();
        ResultSet rsRAF = null;
        int starting_index = -1;
        int ending_index = -1;
        if (struct.length() == 7) { // domain
            rsRAF = stmtRAF.executeQuery("select raf.raf_version_id, raf.line from raf\n" +
                    "join pdb_chain on raf.pdb_chain_id = pdb_chain.id\n" +
                    "join link_pdb on pdb_chain.id = link_pdb.pdb_chain_id\n" +
                    "join scop_node on link_pdb.node_id = scop_node.id\n" +
                    "where scop_node.sid = '" + this.domain + "'\n" +
                    "and scop_node.release_id = 19\n" +
                    "order by raf_version_id desc\n" +
                    //"and raf_version_id = 2\n" +
                    "limit 1"
            );
        } else if (struct.length() == 5) {
            rsRAF = stmtRAF.executeQuery("select raf.raf_version_id, raf.line from raf\n" +
                    "join astral_chain on raf.id = astral_chain.raf_id\n" +
                    "where astral_chain.id = " + this.chain_id + "\n" +
                    "order by raf_version_id desc\n" +
                    "limit 1"
            );
        }

        boolean hasRsRAF = false;
        while (rsRAF.next()) {
            rafLine = rsRAF.getString("line");
            rafVersion = rsRAF.getInt("raf_version_id");
            hasRsRAF = true;
        }
        if (!hasRsRAF) {
            throw new Exception("rsRAF did not find a rafMap for structure " + struct);
        }
        ArrayList<String[]> rafMap = new ArrayList<>();
        String rafSeq = "";
        String[] rafHeader = rafLine.substring(0, 38).split("\\s+");
        String rafStart = String.valueOf(rafHeader[5]);
        String rafEnd = String.valueOf(rafHeader[6]);
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
                rafMap.add(new String[]{key, val});
                rafSeq += val;
            }
        } else if (rafVersion > 0) {
            int n = 1;
            String key;
            String val;
            for (int i = 0; i < rafLine.length(); i += 7) {
                key = rafLine.substring(i, i + 5).trim();
                if ("B".equals(key) || "M".equals(key) || "E".equals(key)) {
                    key = Integer.toString(n);
                }
                if (rafLine.charAt(i+4) != ' ') {
                    val = "/";
                } else if (rafLine.charAt(i + 5) == '.') {
                    val = ",";
                } else {
                    val = String.valueOf(rafLine.charAt(i + 5));
                }
                rafMap.add(new String[]{key, val});
                rafSeq += val;
                n++;
            }
        }  else {
            stmtRAF.close();
            rsRAF.close();
            throw new Exception("No RAF version found");
        }

        stmtRAF.close();
        rsRAF.close();

        if (struct.length() == 7) { // domain
            if (shortMatch) {
                starting_index = RAF.indexOf(domainRafBody, domain_start_index, false);
                ending_index = RAF.indexOf(domainRafBody, domain_end_index, true);
            } else {
//                starting_index = domain_alignment_start;
//                ending_index = RAF.getResID(domainRafBody, RAF.indexOf(domainRafBody, domain_alignment_start, false) + domain_alignment_length);
                starting_index =
                        Integer.max(
                                RAF.indexOf(domainRafBody, domain_alignment_start, false) - 20,
                                0
                        );
                ending_index =
                        Integer.min(
                                RAF.indexOf(domainRafBody, domain_alignment_start, false) + domain_alignment_length + 20,
                                RAF.indexOf(domainRafBody, domain_end_index, true)
                        );
            }
//            long count = rafSeq.chars().filter(c -> c == 'm').count();
//            domain_alignment_length += count;
//            ending_index = RAF.getResID(domainRafBody, RAF.indexOf(domainRafBody, domain_alignment_start, false) + domain_alignment_length);
        } else if (struct.length() == 5) { // chain
            if (shortMatch) {
                starting_index = RAF.indexOf(chainRafBody, chain_start_index, false);
                ending_index = RAF.indexOf(chainRafBody, chain_end_index, true);
            } else {
                starting_index =
                        Integer.max(
                                RAF.indexOf(chainRafBody, chain_alignment_start, false) - 20,
                                0
                        );
                ending_index =
                        Integer.min(
                                RAF.indexOf(chainRafBody, chain_alignment_start, false) + chain_alignment_length + 20,
                                RAF.indexOf(chainRafBody, chain_end_index, true)
                        );
            }
//            long count = rafSeq.chars().filter(c -> c == 'm').count();
//            chain_alignment_length += count;
//            ending_index = RAF.getResID(chainRafBody, RAF.indexOf(chainRafBody, chain_alignment_start, false) + chain_alignment_length);
        }

        // convert rafMap to rafSeq string
        boolean withinRange = false;
        StringBuilder result = new StringBuilder();
//        print(struct);
//        print("\n");
//        print(rafMap);
//        print("\n");

        for (int i = 0; i < rafMap.size(); i++) {
            String key = rafMap.get(i)[0];
            String val = rafMap.get(i)[1];
            if (shortMatch) {
                withinRange = true;
            }
            if (i == starting_index) {
                withinRange = true;
            }
            if (withinRange) {
                result.append(val);
            }
//            if (val.equals("m") && withinRange) { // hetatms advance indices in raf, but NOT fatcat...
//                ending_index = RAF.getResID(chainRafBody, RAF.indexOf(chainRafBody, ending_index, false) + 1);
//            }
            if (i == ending_index) {
                withinRange = false;
            }
        }

        if (struct.length() == 7) {
            this.domainRafSeq = rafSeq.toUpperCase();
        } else {
            this.chainRafSeq = rafSeq.toUpperCase();
        }
        return result.toString().toUpperCase();
    }

    private Logger setupLogger() throws Exception {

        String filename = chain + ".log";
        this.logFile = Paths.get(this.tempDir, filename);

        Files.createDirectories(Paths.get(this.tempDir));

        Logger customLogger = Logger.getLogger("Logger-" + chain);
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

    /*
    Run FATCAT, read .aln file.
    */
    public void compare() throws Exception {

        logger.info("compare()");
        File alnFile = new File(this.tempDir + this.alignmentFile);
        File matrixFile = new File(this.tempDir + this.matrixFile);

        if (!new File(this.chainFile).exists()) {
            throw new Exception(this.chainFile + " does not exist");
        }
        if (!new File(this.domainFile).exists()) {
            throw new Exception(this.domainFile + " does not exist");
        }
        if (new File(fcs.AlnDir + this.alignmentFile).exists() && new File(fcs.MatrixDir + this.matrixFile).exists()) { // in the final alignment directory
//            print("found existing aln file, moving from " + fcs.AlnDir + this.alignmentFile + " to " + alnFile);
            Files.move(Paths.get(fcs.AlnDir + this.alignmentFile), Paths.get(alnFile.toURI()), StandardCopyOption.REPLACE_EXISTING);
            Files.move(Paths.get(fcs.MatrixDir + this.matrixFile), Paths.get(matrixFile.toURI()), StandardCopyOption.REPLACE_EXISTING);
            this.readAln(alnFile);
            this.readMatrix(matrixFile);
            return;
        }
//        if (alnFile.exists()) {
//            this.hasAln = true;
//            this.readAln(alnFile);
//
//        }
//        else {

        String[] command = {
                FATCAT,
                "-p1", this.chainFile,
                "-p2", this.domainFile,
                "-o", this.tempDir + this.alignmentFile.replace(".aln", ""), // (2) run FATCAT to tmp
                "-r", "-m", "-t"
        };

//             System.out.println(String.join(" ", command));

        logger.info("running FATCAT");
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(new File("/"));
        Process p = builder.start();
        p.waitFor();
        p.destroy();

        if (!alnFile.exists()) {
            throw new Exception(alnFile + " not generated");
        }
        this.readAln(alnFile);
        if (shortMatch) {
            return;
        }

        if (matrixFile.exists()) {
            this.hasMatrix = true;
            this.readMatrix(matrixFile);
        } else {

            logger.info("running PostFATCAT");
            String[] command2 = {
                    PostFATCAT,
                    "-a", this.tempDir + this.alignmentFile,
                    "-p1", this.chainFile,
                    "-p2", this.domainFile,
                    "-n1", this.chain + ".pdb",
                    "-n2", this.domain + ".ent",
                    "-c", this.tempDir + this.superpositionFile,
                    "-m", this.tempDir + this.matrixFile
            };

            // System.out.println(String.join(" ", command2));

            builder = new ProcessBuilder(command2);
            builder.directory(new File("/"));
            Process p2 = builder.start();
            p2.waitFor();
            p2.destroy();

            // Read the matrix file
            if (!matrixFile.exists()) {
                throw new Exception(matrixFile + " not generated\n" + String.join(" ", command2));
            }
            this.readMatrix(matrixFile);

            int i = 0;
            while (transLst[0] == 0.0 && transLst[1] == 0.0 && transLst[2] == 0.0 && rotLst[0][0] == 0.0 && rotLst[1][1] == 0.0) { // make sure PostFATCAT runs
                if (i > 5) {
                    throw new Exception("PostFATCAT failed for " + this.chainFile + " x " + this.domainFile);
                }
                logger.info("repeating PostFATCAT");
                p2 = builder.start();
                p2.waitFor();
                p2.destroy();
                this.readMatrix(matrixFile);
                i++;
            }
//            }
        }
    }

    /*
    Read FATCAT output .aln file, store values in instance variables.
     */
    public void readAln(File result) throws Exception {

        logger.info("readAln("+ result + ")");
        // get fatcatSeq
        Scanner s2 = new Scanner(result);
        while (s2.hasNextLine()) {
            String line2 = s2.nextLine();
            if (line2.startsWith("Short match")) {
                shortMatch = true;
            }
            else if (line2.startsWith("Chain 1")) {
                // chainFatcatSeq += line2.substring(13);
                s2.nextLine();
                // domainFatcatSeq += s2.nextLine().substring(13);
            }
        }
        s2.close();

        logger.info("reading lines");
        if (!shortMatch) {
            Scanner s = new Scanner(result);

            int i = 0;
            while (s.hasNextLine()) {
                String line = s.nextLine();

                if (i == 0) {
                    Matcher match = pAlign1.matcher(line);
                    if (match.find()) {
                        this.chain_len = Integer.parseInt(match.group(1));
                        this.domain_len = Integer.parseInt(match.group(2));
                    } else if (!match.find()) {
                        throw new Exception("no regex match lens, line " + 300);
                    }
                } else if (i == 1) {
                    Matcher match = pAlign2.matcher(line);
                    if (match.find()) {
                        this.ini_len = Integer.parseInt(match.group(1));
                        this.ini_rmsd = Double.parseDouble(match.group(2));
                        this.opt_equ = Integer.parseInt(match.group(3));
                        this.opt_rmsd = Double.parseDouble(match.group(4));
                        this.chain_rmsd = Double.parseDouble(match.group(5));
                        this.score = Double.parseDouble(match.group(6));
                        this.align_len = Integer.parseInt(match.group(7));
                        this.gaps = Integer.parseInt(match.group(8));
                    } else if (!match.find()) {
                        throw new Exception("no regex match stats, line " + 319);
                    }
                } else if (i == 2) {
                    Matcher match = pAlign3.matcher(line);
                    if (match.find()) {
                        this.p_val = Double.parseDouble(match.group(1));
                        this.afp_num = Integer.parseInt(match.group(2));
                        this.identity = Double.parseDouble(match.group(3));
                        this.similarity = Double.parseDouble(match.group(4));
                    } else if (!match.find()) {
                        throw new Exception("no regex match stats2, line " + 329);
                    }
                }
                if (line.startsWith("Chain 1")) {
                    String line_gap = s.nextLine();
                    String line_domain = s.nextLine();
                    s.nextLine();
                    parseLines(line, line_gap, line_domain);
                }
                i++;
            }
            s.close();
        }
        s2.close();
    }

    /*
    Read PostFATCAT output [chain]_[domain].matrix file, storing the transformation matrix coordinates into rotLst and transLst.
     */
    public void readMatrix(File result) throws Exception {

        logger.info("readMatrix()");
        Scanner s = new Scanner(result);
        double[] Tfatcat = new double[3];
        String[] lst;

        while (s.hasNextLine()) {
            String line = s.nextLine();
            if (line.startsWith("rotate") || line.startsWith("translate")) {

                lst = line.trim().split("\\s+");
                int l = 1;

                if (line.startsWith("rotate")) {
                    for (int i = 0; i < 3; i++) {
                        for (int j = 0; j < 3; j++) {
                            rotLst[i][j] = Double.parseDouble(lst[l]);
                            l++;
                        }
                    }
                } else if (line.startsWith("translate")) { // + extra transformations to get the correct translation vector
                    for (int i = 0; i < 3; i++) {
                        Tfatcat[i] = Double.parseDouble(lst[l]);
                        l++;
                    }
                    for (int i = 0; i < 3; i++) { // Tcorrect = R x -Tfatcat
                        transLst[i] = 0;
                        for (int j = 0; j < 3; j++) {
                            transLst[i] += rotLst[i][j] * -Tfatcat[j];
                        }
                    }
                }
            }
        }
        for (int i = 0; i < 3; i++) {
            transLst[i] = Math.round(transLst[i] * 1000.0) / 1000.0;
        }
        s.close();
    }

    /*
    collect domainSeq, alnSeq, chainSeq, prepare for gap processing.
     */
    public void parseLines(String chain_line, String line2, String domain_line) throws Exception {

        // get starts of alignments in terms of Chain and Domain residue indices
        Matcher matchIndex_chain = pAlignIndex.matcher(chain_line);
        Matcher matchIndex_domain = pAlignIndex.matcher(domain_line);

        String chain_start;
        String domain_start;

        if (matchIndex_chain.find()) {
            chain_start = matchIndex_chain.group(1); // the starting index for the alignment
        } else {
            throw new Exception("no regex match chain_index, line " + 502);
        }
        if (matchIndex_domain.find()) {
            domain_start = matchIndex_domain.group(1);

            //            domain_index = new ArrayList<>(fcs.rafMap.keySet()).indexOf(domain_start_str);
            //            if (domain_start_index < 0) { // if domain_start_index hasnt been set yet
            //                this.domain_start_index = domain_index;
            //            }
        } else {
            throw new Exception("no regex match domain_index, line " + 507);
        }

        // initialize indices
        if (this.chain_alignment_start == null || this.domain_alignment_start == null) {
            this.chain_alignment_start = chain_start;
            this.domain_alignment_start = domain_start;
        }
        // initialize length
        if (this.chain_alignment_length == null || this.domain_alignment_length == null) {
            this.chain_alignment_length = 0;
            this.domain_alignment_length = 0;
        }

        char[] chain_line_arr = chain_line.toCharArray();
        char[] domain_line_arr = domain_line.toCharArray();

        for (int i = 14; i < chain_line.length(); i++) {
            char c_chain = chain_line_arr[i];
            char c_domain = domain_line_arr[i];
            if (c_chain != '-') {
                this.chain_alignment_length++;
            }
            if (c_domain != '-') {
                this.domain_alignment_length++;
            }
        }
        this.chainSeq += new String(chain_line_arr).substring(14).trim();
        this.alnSeq += line2.substring(14);
        this.domainSeq += new String(domain_line_arr).substring(14).trim();

    }

    /*
    execute alignment against fatcatSeq, insert gaps ("="), missing residues (","), insertions ("/") and post-translational modifications ("?")
    accordingly, such that fatcatSeq matches in length one-to-one with the FULL rafMap, but with "=" gaps where an alignment did not occur.
     */
    public void constructNewSeqs() throws Exception {

        this.chainRafSeqAlignment = getRafSeq(this.chain);
        this.domainRafSeqAlignment = getRafSeq(this.domain);

        if (shortMatch) {
            this.chain_len = RAF.indexOf(chainRafBody, chain_end_index, false) - RAF.indexOf(chainRafBody, chain_start_index, true);
            this.domain_len = RAF.indexOf(domainRafBody, domain_end_index, false) - RAF.indexOf(domainRafBody, domain_start_index, true);
            this.gaps = Math.max(this.domain_len, this.chain_len);

            int chain_idx = 0;
            int domain_idx = 0;

            // build overlapping gap region
            while (chain_idx < chain_len && domain_idx < domain_len) {
                chainSeq += chainRafSeq.charAt(chain_idx);
                alnSeq += " ";
                domainSeq += domainRafSeq.charAt(domain_idx);
                chain_idx++;
                domain_idx++;
            }

            // build the rest of the alphafold if its longer
            while (chain_idx < chain_len) {
                chainSeq += chainRafSeq.charAt(chain_idx);
                alnSeq += " ";
                domainSeq += "=";
                chain_idx++;
            }

            // build the rest of the scop if its longer
            while (domain_idx < domain_len) {
                chainSeq += "=";
                alnSeq += " ";
                domainSeq += domainRafSeq.charAt(domain_idx);
                domain_idx++;
            }
            return;
        }

        this.domainSeq = this.domainSeq.replaceAll("-", "=");
        this.chainSeq = this.chainSeq.replaceAll("-", "=");


        // run alignment for CHAIN
        // we need this in order to decide whether a gap in the alignment is a result of a missing/hetatm residue, or if its actually just a regular alignment gap.
        Protein chainRafSeqAlignment_obj = new Protein(this.chainRafSeq); // chainRafSeqAlignment
        Protein chainSeq_obj = new Protein(this.chainSeq);

        AlignmentParameters apChain = new AlignmentParameters(new GapAffine(0.5, 0.0), new ScoreList(new ScoreID()));
        Alignment alnChain = chainRafSeqAlignment_obj.getGlobalAlignment(chainSeq_obj, apChain, null);
        if (alnChain == null) {
            throw new Exception("Sequence alignment chainRafSeq x chainSeq failed for " + this.chainFile);
        }
        char[][] alignedArraysChain = alnChain.toCharArrays();
        String chainRafSeqAlignmentAligned = new String(alignedArraysChain[0]); // rafSeq
        String chainSeqAligned = new String(alignedArraysChain[1]); // scopSeq

        // run alignment for DOMAIN
        Protein domainRafSeqAlignment_obj = new Protein(this.domainRafSeq); // domainRafSeqAlignment
        Protein domainSeq_obj = new Protein(this.domainSeq);

        AlignmentParameters apDomain = new AlignmentParameters(new GapAffine(0.5, 0.0), new ScoreList(new ScoreID()));
        Alignment alnDomain = domainRafSeqAlignment_obj.getGlobalAlignment(domainSeq_obj, apDomain, null);
        if (alnDomain == null) {
            throw new Exception("Sequence alignment rafSeq x scopSeq failed for " + this.domainFile);
        }
        char[][] alignedArraysDomain = alnDomain.toCharArrays();
        String domainRafSeqAlignmentAligned = new String(alignedArraysDomain[0]); // rafSeq
        String domainSeqAligned = new String(alignedArraysDomain[1]); // scopSeq

//        print(chain + "\n"); // BOBS
//        print("chainRafSeqAligned ", chainRafSeqAlignmentAligned); // Alignment = only the raf residues within the structure alignment. Aligned = ran through the seq alignment
//        print("chainSeqAligned    ", chainSeqAligned);
//        print(domain + "\n");
//        print("domainRafSeqAligned", domainRafSeqAlignmentAligned);
//        print("domainSeqAligned   ", domainSeqAligned);

        // cut off starts
        while (chainSeqAligned.charAt(0) != chainRafSeqAlignmentAligned.charAt(0)) { // the rafseqalignment contains +- 10 more residues on each end that we will cut off until the actual alignment
            chainRafSeqAlignmentAligned = chainRafSeqAlignmentAligned.substring(1);
//            chainRafSeqAlignment = chainRafSeqAlignment.substring(1);
            chainSeqAligned = chainSeqAligned.substring(1);
        }
        while (domainSeqAligned.charAt(0) != domainRafSeqAlignmentAligned.charAt(0)) {
            domainRafSeqAlignmentAligned = domainRafSeqAlignmentAligned.substring(1);
//            domainRafSeqAlignment = domainRafSeqAlignment.substring(1);
            domainSeqAligned = domainSeqAligned.substring(1);
        }
        // cut off ends
        while (chainSeqAligned.charAt(chainSeqAligned.length() - 1) != chainRafSeqAlignmentAligned.charAt(chainRafSeqAlignmentAligned.length() - 1)) { // the rafseqalignment contains +- 10 more residues on each end that we will cut off until the actual alignment
            chainRafSeqAlignmentAligned = chainRafSeqAlignmentAligned.substring(0, chainRafSeqAlignmentAligned.length() - 1);
//            chainRafSeqAlignment = chainRafSeqAlignment.substring(0, chainRafSeqAlignment.length() - 1);
            chainSeqAligned = chainSeqAligned.substring(0, chainSeqAligned.length() - 1);
        }
        while (domainSeqAligned.charAt(domainSeqAligned.length() - 1) != domainRafSeqAlignmentAligned.charAt(domainRafSeqAlignmentAligned.length() - 1)) {
            domainRafSeqAlignmentAligned = domainRafSeqAlignmentAligned.substring(0, domainRafSeqAlignmentAligned.length() - 1);
//            domainRafSeqAlignment = domainRafSeqAlignment.substring(0, domainRafSeqAlignment.length() - 1);
            domainSeqAligned = domainSeqAligned.substring(0, domainSeqAligned.length() - 1);
        }

//        print("\n"); BOBS
//        print("chainRafSeqAligned ", chainRafSeqAlignmentAligned); // Alignment = only the raf residues within the structure alignment. Aligned = ran through the seq alignment
//        print("chainSeqAligned    ", chainSeqAligned);
//        print("domainRafSeqAligned", domainRafSeqAlignmentAligned);
//        print("domainSeqAligned   ", domainSeqAligned);

        // adjust alignment length
        int chain_alignment_length = 0;
        for (char c : chainRafSeqAlignmentAligned.toCharArray()) {
            if (Character.isLetter(c)) {
                chain_alignment_length++;
            }
        }
        int domain_alignment_length = 0;
        for (char c : domainRafSeqAlignmentAligned.toCharArray()) {
            if (Character.isLetter(c)) {
                domain_alignment_length++;
            }
        }

        // go index by index through rafSeqAligned, scopSeqAligned, afSeq, alnSeq
        String chain_prefix = "";
        String aln_prefix = "";
        String domain_prefix = "";

        // beginning gap
        int idx_chain = RAF.indexOf(chainRafBody, chain_start_index, true);
        int idx_domain = RAF.indexOf(domainRafBody, domain_start_index, true);

        int dist_to_domain_start = RAF.indexOf(domainRafBody, domain_alignment_start, true) - RAF.indexOf(domainRafBody, domain_start_index, true); // domain_start is the start of the alignment. domain_start_index is the start of the entire domain.
        int dist_to_chain_start = RAF.indexOf(chainRafBody, chain_alignment_start, true) - RAF.indexOf(chainRafBody, chain_start_index, true);

        // adjust if domain is longer than chain
        while (dist_to_domain_start > dist_to_chain_start) {
            chain_prefix += "=";
            aln_prefix += " ";
            domain_prefix += domainRafSeq.charAt(idx_domain);
            idx_domain++;
            dist_to_domain_start--;
        }

        // adjust if chain is longer than domain
        while (dist_to_domain_start < dist_to_chain_start) {
            chain_prefix += chainRafSeq.charAt(idx_chain);
            aln_prefix += " ";
            domain_prefix += "=";
            dist_to_chain_start--;
            idx_chain++;
        }

        // bring both to starts
        assert dist_to_chain_start == dist_to_domain_start;
        while (dist_to_domain_start > 0) {
            chain_prefix += chainRafSeq.charAt(idx_chain);
            aln_prefix += " ";
            domain_prefix += domainRafSeq.charAt(idx_domain);
            idx_domain++;
            dist_to_domain_start--;
            idx_chain++;
            dist_to_chain_start--;
        }

        // middle portion
        // flow: get alignments for chain. go through the chain
        // then: get alignments for domain BASED ON CHANGES MADE FROM CHAIN. go through the domain.
        StringBuilder sbChain = new StringBuilder(chainSeqAligned);
        StringBuilder sbAln = new StringBuilder(alnSeq);
        StringBuilder sbDomain = new StringBuilder(domainSeqAligned);

        StringBuilder sbChainRaf = new StringBuilder(chainRafSeqAlignmentAligned);
        StringBuilder sbDomainRaf = new StringBuilder(domainRafSeqAlignmentAligned);

//                {chainSeqAligned, chainRafSeqAlignmentAligned, sbChain, sbDomain},
//                {domainSeqAligned, domainRafSeqAlignmentAligned, sbDomain, sbChain}

//            String seqAligned = (String) seqsAligned[0];
//            String rafAligned = (String) seqsAligned[1];
//            StringBuilder sbMain = (StringBuilder) seqsAligned[2];
//            StringBuilder sbOther = (StringBuilder) seqsAligned[3];

//        print("sb's initially");
//        print(sbChain.toString());
//        print(sbAln.toString());
//        print(sbDomain.toString());

        int i = 0;

        while (i < chainSeqAligned.length() || i < domainSeqAligned.length()) {

            char c_raf_domain = sbDomainRaf.charAt(i);
            char c_scop_domain = sbDomain.charAt(i);

            char c_raf_chain = sbChainRaf.charAt(i);
            char c_scop_chain = sbChain.charAt(i);

            // missing residues
            if (c_raf_domain == ',') { // in domain
                if (c_scop_domain == '=') {
                    sbDomain.setCharAt(i, ',');
                } else if (c_scop_domain == '.') {
                    sbDomain.setCharAt(i, ',');
                    sbChain.insert(i, '=');
                    sbAln.insert(i, ' ');

                    sbDomainRaf.setCharAt(i, ',');
                    sbChainRaf.insert(i, '=');
                }
            }
            if (c_raf_chain == ',') { // in chain
                if (c_scop_chain == '=') {
                    sbChain.setCharAt(i, ',');
                } else if (c_scop_chain == '.') {
                    sbChain.setCharAt(i, ',');
                    sbDomain.insert(i, '=');
                    sbAln.insert(i, ' ');

                    sbChainRaf.setCharAt(i, ',');
                    sbDomainRaf.insert(i, '=');
                }
            }

            // hetatm
            else if (Character.isLetter(c_raf_domain) && !Character.isLetter(c_scop_domain)) { // in domain
                if (c_scop_domain == '=') {
                    sbDomain.setCharAt(i, '?');
                } else if (c_scop_domain == '.') {
                    sbDomain.setCharAt(i, '?');
                    sbChain.insert(i, '=');
                    sbAln.insert(i, ' ');

                    sbDomainRaf.setCharAt(i, '?');
                    sbChainRaf.insert(i, '=');

                    domain_alignment_length += 1;
                }
            }
            if ((Character.isLetter(c_raf_chain) && !Character.isLetter(c_scop_chain)) || c_raf_chain == 'm') { // in chain
                if (c_scop_chain == '=') {
                    sbChain.setCharAt(i, '?');
                } else if (c_scop_chain == '.') {
                    sbChain.setCharAt(i, '?');
                    sbDomain.insert(i, '=');
                    sbAln.insert(i, ' ');

                    sbChainRaf.setCharAt(i, '?');
                    sbDomainRaf.insert(i, '=');

                    chain_alignment_length += 1;
                }
            }

            // insertions
            if (c_raf_domain == '/') { // in domain
                if (c_scop_domain == '=') {
                    sbDomain.setCharAt(i, '/');
                } else if (c_scop_domain == '.') {
                    sbDomain.setCharAt(i, '/');
                    sbChain.insert(i, '=');
                    sbAln.insert(i, ' ');

                    sbDomainRaf.setCharAt(i, ',');
                    sbChainRaf.insert(i, '=');
                }
            }
            if (c_raf_chain == '/') { // in chain
                if (c_scop_chain == '=') {
                    sbChain.setCharAt(i, '/');
                } else if (c_scop_chain == '.') {
                    sbChain.setCharAt(i, '/');
                    sbDomain.insert(i, '=');
                    sbAln.insert(i, ' ');

                    sbChainRaf.setCharAt(i, '/');
                    sbDomainRaf.insert(i, '=');
                }
            }

            i++;

//            print("\nsbChain    ", sbChain.toString());
//            print("sbAln      ", sbAln.toString());
//            print("sbDomain   ", sbDomain.toString());
//
//            print("sbChainRaf ", sbChainRaf.toString());
//            print("sbAln      ", sbAln.toString());
//            print("sbDomainRaf", sbDomainRaf.toString());

        }

        /*
        for (int i = 0; i < domainSeqAligned.length(); i++) {
            // the regular alignment is already there, just adding in hetatm's / missings / insertions

            char c_raf = domainRafSeqAlignmentAligned.charAt(i);
            char c_scop = domainSeqAligned.charAt(i);

            // missing residues
            if (c_raf == ',') {
                if (c_scop == '=') {
                    sbDomain.setCharAt(i, ',');
                } else if (c_scop == '.') {
                    sbDomain.setCharAt(i, ',');
                    sbChain.insert(i, '=');
                    sbAln.insert(i, ' ');
                }
            }

            // hetatm
            else if (Character.isLetter(c_raf) && !Character.isLetter(c_scop)) {
                if (c_scop == '=') {
                    sbDomain.setCharAt(i, '?');
                } else if (c_scop == '.') {
                    sbDomain.setCharAt(i, '?');
                    sbChain.insert(i, '=');
                    sbAln.insert(i, ' ');
                }
            }

            // insertion
            else if (c_raf == '/') {
                if (c_scop == '=') {
                    sbDomain.setCharAt(i, ',');
                } else if (c_scop == '.') {
                    sbDomain.setCharAt(i, ',');
                    sbChain.insert(i, '=');
                    sbAln.insert(i, ' ');
                }
            }
        }

         */

//        print("\nsb's after first round (chain)");
//        print(sbChain.toString());
//        print(sbAln.toString());
//        print(sbDomain.toString());
//        print("alignments");
//        print("chainRafSeqAligned ", chainRafSeqAlignmentAligned); // Alignment = only the raf residues within the structure alignment. Aligned = ran through the seq alignment
//        print("chainSeqAligned    ", chainSeqAligned);
//        print("domainRafSeqAligned", domainRafSeqAlignmentAligned);
//        print("domainSeqAligned   ", domainSeqAligned);

//        print(sbChainRaf + "\n");
//        print(sbChain + "\n");
//        print(sbAln + "\n");
//        print(sbDomain + "\n");
//        print(sbDomainRaf + "\n");

        // update indices after all the changes above
        for (int n = 0; n < sbChain.length(); n++) {
            char c_chain = sbChain.charAt(n);
            char c_scop = sbDomain.charAt(n);

            if (c_chain != '=') {
                idx_chain++;
            }
            if (c_scop != '=') {
                idx_domain++;
            }
        }

        // end gap
        String chain_suffix = "";
        String aln_suffix = "";
        String domain_suffix = "";

        int dist_to_chain_end = RAF.indexOf(chainRafBody, chain_end_index, false) - ( RAF.indexOf(chainRafBody, chain_alignment_start, true) + chain_alignment_length );
        int dist_to_domain_end = RAF.indexOf(domainRafBody, domain_end_index, false) - ( RAF.indexOf(domainRafBody, domain_alignment_start, true) + domain_alignment_length );

//        print("indexOf domain_end_index", RAF.indexOf(domainRafBody, domain_end_index, false));
//        print("indexOf domain_alignment_start", RAF.indexOf(domainRafBody, domain_alignment_start, false));
//        print("domain_alignment_length", domain_alignment_length);

        // extend both until one finishes
        while (dist_to_chain_end > 0 && dist_to_domain_end > 0) {
            if (idx_chain >= chainRafSeq.length() || idx_domain >= domainRafSeq.length()) {
                if (idx_chain >= chainRafSeq.length()) {
                    dist_to_chain_end = 0;
                }
                if (idx_domain >= domainRafSeq.length()) {
                    dist_to_domain_end = 0;
                }
                break;
            }
            chain_suffix += chainRafSeq.charAt(idx_chain);
            aln_suffix += " ";
            domain_suffix += domainRafSeq.charAt(idx_domain);
            dist_to_chain_end--;
            dist_to_domain_end--;
            idx_chain++;
            idx_domain++;
        }

        // extend just domain until done
        while (dist_to_chain_end == 0 && dist_to_domain_end > 0) {
            if (idx_domain >= domainRafSeq.length()) {
                dist_to_domain_end = 0;
                break;
            }
            chain_suffix += "=";
            aln_suffix += " ";
            domain_suffix += domainRafSeq.charAt(idx_domain);
            dist_to_domain_end--;
            idx_domain++;
        }

        // extend just af until done
        while (dist_to_chain_end > 0 && dist_to_domain_end == 0) {
            if (idx_chain >= chainRafSeq.length()) {
                dist_to_chain_end = 0;
                break;
            }
            chain_suffix += chainRafSeq.charAt(idx_chain);
            aln_suffix += " ";
            domain_suffix += "=";
            dist_to_chain_end--;
            idx_chain++;
        }

        this.chainSeq = chain_prefix + sbChain.toString() + chain_suffix;
        this.alnSeq = aln_prefix + sbAln.toString() + aln_suffix;
        this.domainSeq = domain_prefix + sbDomain.toString() + domain_suffix;

        assert chainSeq.length() == alnSeq.length() && alnSeq.length() == domainSeq.length();

    }

    public void parseGaps() throws Exception {

        // parse through the new gaps //
        Gap g_chain = null;
        Gap g_domain = null;
        int currType = 0;
        int currID = 1;
        int chain_index = -1; // overall index
        int domain_index = -1;

        // process all gaps
        for (int i = 0; i <= this.chainSeq.length(); i++) { // chainSeq and alnSeq and domainSeq should all be the same length.
            if (i == this.chainSeq.length()) {
                if (g_chain != null && g_domain != null) {
                    g_chain.setEnd();
                    g_domain.setEnd();
                }
                break;
            }

            char c_chain = chainSeq.charAt(i);
            char c_aln = alnSeq.charAt(i);
            char c_domain = domainSeq.charAt(i);

            if (c_domain != '=') { domain_index++; }
            if (c_chain != '=') { chain_index++; }

            // if in a gap
            if (c_aln != '1') {
//                print(" " + domain_index);
                int newType = gapType(c_chain, c_domain);

                // if you weren't in a gap before, make a new gap here.
                if (g_chain == null && g_domain == null) {
                    currType = newType;
                    g_chain = new Gap(1, chain_index, currID, currType);
                    g_domain = new Gap(2, domain_index, currID, currType);
                }

                // if you were in a gap before and you're still in the same type of gap -> extend.
                // if you were in a gap before and you're in a new type of gap now -> end the current gap. make a new gap.
                else if (g_chain != null && g_domain != null && currType != newType) {
                    g_chain.setEnd();
                    g_domain.setEnd();
                    currType = newType;
                    g_chain = new Gap(1, chain_index, currID, currType);
                    g_domain = new Gap(2, domain_index, currID, currType);
                }

                // based on type, extend.
                g_chain.extend();
                g_domain.extend();

            } else if (c_aln == '1') {

                // if you were in a gap and it ended, end the current gap. set gaps as null.
                if (g_chain != null && g_domain != null) {
                    g_chain.setEnd();
                    g_domain.setEnd();
                    g_chain = null;
                    g_domain = null;
                    currType = 0;
                    currID++;
                }

                // if you weren't in a gap before, just advance the index pointers.
            }
        }
    }

    /*
    Helper class that contains the starts, ends, and lengths for each Gap.
    */
    public class Gap {

        // index -1 == before the structure
        // index more than the structure == after the structure

        int source; // 1 = chain, 2 = domain
        String rafBody;
        int start; // overall index (will convert to raf when storing)
        int end; // overall index (will convert to raf when storing)
        int len;
        int gap_id;
        int gap_type;
        int num_insertions;
        String gap_start; // raf index or null
        String gap_end; // raf index or null

        ArrayList<Integer> chain_types = new ArrayList<>(Arrays.asList(1, 3, 4, 5, 7, 8, 9, 11, 12, 13, 15)); // gap_types where chain should be extended
        ArrayList<Integer> domain_types = new ArrayList<>(Arrays.asList(2, 3, 5, 6, 7, 9, 10, 11, 13, 14, 15)); // gap_types where domain should be extended

        public Gap(int source, int start, int gap_id) {
            this.source = source;
            if (source == 1) {
                this.rafBody = chainRafBody;
            } else {
                this.rafBody = domainRafBody;
            }
            this.start = start;
            this.end = -1;
            this.len = 0;
            this.gap_id = gap_id;
            this.gap_type = -1;
        }

        public Gap(int source, int start, int end, int gap_id, int gap_type) {
            this.source = source;
            if (source == 1) {
                this.rafBody = chainRafBody;
            } else {
                this.rafBody = domainRafBody;
            }
            this.start = start;
            this.end = end;
            this.len = end - start;
            this.gap_id = gap_id;
            this.gap_type = gap_type;
        }

        public Gap(int source, int start, int gap_id, int gap_type) {
            this.source = source;
            if (source == 1) {
                this.rafBody = chainRafBody;
            } else {
                this.rafBody = domainRafBody;
            }
            this.start = start;
            this.end = -1;
            this.len = 0;
            this.gap_id = gap_id;
            this.gap_type = gap_type;
            this.num_insertions = 0;
        }

        public void setEnd() {
            if ((this.source == 1 && this.len == 0 && this.start == chainRafSeq.length() - 1) || (source == 2 && this.len == 0 && this.start == domainRafSeq.length() - 1)) { // this.start is 1 over chain_len == outside of the chain
                this.start++;
                this.end = this.start;
            } else if (this.len == 0) {
                this.end = this.start;
            } else {
                this.end = this.start + this.len - 1; // -1 because the start and end indices are inclusive
            }
            this.storeGap();
        }

        public void storeGap() {
            try {
                this.gap_start = RAF.getResID(rafBody, this.start);
            } catch (Exception e) {
                this.gap_start = "NULL";
            }

            try {
                this.gap_end = RAF.getResID(rafBody, this.end);
            } catch (Exception e) {
                this.gap_end = "NULL";
            }

            if (this.len == 0 ) {
                this.gap_start = "NULL";
                this.gap_end = "NULL";
            }

            if (source == 1) {
                chainGaps.add(this);
            } else if (source == 2) {
                domainGaps.add(this);
            }
        }

        public void addInsertion() {
            this.num_insertions++;
        }

        public void extend() {
            if ((source == 1 && chain_types.contains(this.gap_type)) || (source == 2 && domain_types.contains(this.gap_type))) {
                this.len++;
            }
        }

        public void setType(int gap_type) {
            this.gap_type = gap_type;
        }

        @Override
        public String toString() {
            return "[" + gap_start + ", " + gap_end + "] len=" + len + " type=" + gap_type;
        }

    }

    /*
    returns the gap type given chainSeq and domainSeq characters
     */
    public int gapType(char c_chain, char c_domain) throws Exception {

        boolean hasChain = c_chain != '=';
        boolean hasDomain = c_domain != '=';

        boolean chainMissing = (c_chain == ',');
        boolean chainHetatm = (c_chain == '?');
        boolean chainInsertion = (c_chain == '/');

        boolean domainMissing = (c_domain == ',');
        boolean domainHetatm = (c_domain == '?');
        boolean domainInsertion = (c_domain == '/');

        // Chain only
        if (hasChain && !hasDomain) {
            if (chainHetatm) return 4;
            if (chainMissing) return 8;
            if (chainInsertion) return 12;
            return 1;
        }

        // Domain only
        if (!hasChain && hasDomain) {
            if (domainHetatm) return 6;
            if (domainMissing) return 10;
            if (domainInsertion) return 14;
            return 2;
        }

        // Both Chain and Domain
        if (hasChain && hasDomain) {
            if (chainHetatm) return 5;
            if (domainHetatm) return 7;

            if (chainMissing) return 9;
            if (domainMissing) return 11;

            if (chainInsertion) return 13;
            if (domainInsertion) return 15;

            return 3;
        }

        throw new Exception("type not found for characters (" + c_chain + ", " + c_domain + ") " + this.chain + " x " + this.domainFile);
    }

    /*
    Store the .aln file and superposed pdb file into their archive directories, and clear the temp directory
     */
    public void archiveAndClear() throws Exception {

        logger.info("archiveAndClear()");
        Path oldAlnPath = Paths.get(this.tempDir + this.alignmentFile);
        Path newAlnPath = Paths.get(fcs.AlnDir + this.alignmentFile);

        Files.move(oldAlnPath, newAlnPath, StandardCopyOption.REPLACE_EXISTING);

        if (!shortMatch) {

            Path oldMatrixPath = Paths.get(this.tempDir + this.matrixFile);
            Path newMatrixPath = Paths.get(fcs.MatrixDir + this.matrixFile);
//            print("moving matrix " + newMatrixPath.toString());
            Files.move(oldMatrixPath, newMatrixPath, StandardCopyOption.REPLACE_EXISTING);

            // clear the temp dir

            String logFileName = this.chain + ".log";

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(fcs.TempDir))) {
                for (Path path : stream) {
                    if (Files.isRegularFile(path)) {
                        String fileName = path.getFileName().toString();

                        // Skip log file, .ent file, and NFS ghost files
                        if (fileName.equals(logFileName) || (fileName.endsWith(".pdb") && !fileName.endsWith(".twist.pdb")) || fileName.startsWith(".nfs")) {
                            continue;
                        }

                        Files.delete(path);
                    }
                }
            }
        }
    }

    public void storeSQL() throws Exception {

        java.util.function.Function<Integer, String> sqlInt =
                v -> (v == null || v < 0) ? "NULL" : v.toString();

        java.util.function.Function<Double, String> sqlDouble =
                v -> (v == null || v < 0) ? "NULL" : String.format("%.6f", v);

        java.util.function.Function<String, String> sqlString =
                v -> (v == null || v.equals("NULL")) ? "NULL" : "'" + v + "'";

        String stmt1 =
                "INSERT INTO scop_node_fatcat_chain VALUES (" +
                        "NULL, " +
                        sqlInt.apply(chain_id) + ", " +
                        sqlInt.apply(domain_id) + ", " +
                        sqlInt.apply(chain_len) + ", " +
                        sqlInt.apply(domain_len) + ", " +
                        sqlInt.apply(ini_len) + ", " +
                        sqlDouble.apply(ini_rmsd) + ", " +
                        sqlInt.apply(opt_equ) + ", " +
                        sqlDouble.apply(opt_rmsd) + ", " +
                        sqlDouble.apply(chain_rmsd) + ", " +
                        sqlDouble.apply(score) + ", " +
                        sqlInt.apply(align_len) + ", " +
                        sqlInt.apply(gaps) + ", " +
                        sqlDouble.apply(p_val) + ", " +
                        sqlInt.apply(afp_num) + ", " +
                        sqlDouble.apply(identity) + ", " +
                        sqlDouble.apply(similarity) + ", " +
                        sqlString.apply(chain_alignment_start) + ", " +
                        sqlInt.apply(chain_alignment_length) + ", " +
                        sqlString.apply(domain_alignment_start) + ", " +
                        sqlInt.apply(domain_alignment_length) +
                        ");";

        String auto_id = "SET @hit_id = LAST_INSERT_ID();";

        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO scop_node_fatcat_chain_gap ");
        sb.append("(hit_id, gap_start_chain, gap_length_chain, gap_start_domain, gap_length_domain, gap_id, gap_type) VALUES\n");

        for (int n = 0; n < chainGaps.size(); n++) {

            String gap_start_chain = sqlString.apply(chainGaps.get(n).gap_start);
            String gap_start_domain = sqlString.apply(domainGaps.get(n).gap_start);

            String gap_length_chain = sqlInt.apply(chainGaps.get(n).len);
            String gap_length_domain = sqlInt.apply(domainGaps.get(n).len);

            String gap_id = sqlInt.apply(chainGaps.get(n).gap_id);
            String gap_type = sqlInt.apply(chainGaps.get(n).gap_type);

            sb.append("(@hit_id, ")
                    .append(gap_start_chain).append(", ")
                    .append(gap_length_chain).append(", ")
                    .append(gap_start_domain).append(", ")
                    .append(gap_length_domain).append(", ")
                    .append(gap_id).append(", ")
                    .append(gap_type).append(")");

            if (n < chainGaps.size() - 1) {
                sb.append(",\n");
            } else {
                sb.append(";");
            }
        }

        String stmt2 = sb.toString();

        sb = new StringBuilder();
        sb.append("INSERT INTO scop_node_fatcat_chain_matrix ");
        sb.append("(hit_id, r11, r12, r13, r21, r22, r23, r31, r32, r33, t1, t2, t3) VALUES\n");

        sb.append("(@hit_id");

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                sb.append(", ").append(String.format("%.10f", rotLst[i][j]));
            }
        }

        for (int m = 0; m < 3; m++) {
            sb.append(", ").append(String.format("%.10f", transLst[m]));
        }

        sb.append(");");
        String stmt3 = sb.toString();

        String sql = stmt1 + "\n" + auto_id + "\n" + stmt2 + "\n" + stmt3 + "\n";

        SQLWriter.write(sql);
        SQLWriter.flush();
    }



    public void printEverything() throws Exception {

        print("chain", chain);
        print("domain", domain);
        print("chain_len", chain_len);
        print("domain_len", domain_len);
        print("ini_len", ini_len);
        print("ini_rmsd", ini_rmsd);
        print("opt_equ", opt_equ);
        print("opt_rmsd", opt_rmsd);
        print("chain_rmsd", chain_rmsd);
        print("score", score);
        print("align_len", align_len);
        print("gaps", gaps);
        print("p_val", p_val);
        print("afp_num", afp_num);
        print("identity", identity);
        print("similarity", similarity);

        print("chain_start_index", chain_start_index);
        print("chain_end_index", chain_end_index);
        print("chainSeq", chainSeq);
        print("chainRafSeq", chainRafSeq);

        print("domain_start_index", domain_start_index);
        print("domain_end_index", domain_end_index);
        print("domainSeq", domainSeq);
        print("domainRafSeq", domainRafSeq);

        print("chain_alignment_start", chain_alignment_start);
        print("chain_alignment_length", chain_alignment_length);
        print("domain_alignment_start", domain_alignment_start);
        print("domain_alignment_length", domain_alignment_length);

        System.out.println("constructNewSeqs:");
        print("chainSeq ", chainSeq);
        print("alnSeq   ", alnSeq);
        print("domainSeq", domainSeq);

//        System.out.println(this.sql);
        // System.out.println("astralSeqs:");
        // System.out.println("astralSeqATOM:   " + fcs.astralSeqATOM);
        // System.out.println("astralSeqSEQRES: " + fcs.astralSeqSEQRES);

        print("chainGaps", chainGaps);
        print("domainGaps", domainGaps);


    }

    public void printSeqs() throws Exception {
        print("chainSeq ", chainSeq);
        print("alnSeq   ", alnSeq);
        print("domainSeq", domainSeq);
//        print("chainGaps", chainGaps);
//        print("domainGaps", domainGaps);
    }

    public static void print(String label, Object value) {
        if (value instanceof Map || value instanceof List) {
            System.out.println(label + ": " + value.toString());
        } else {
            System.out.println(label + ": " + String.valueOf(value));
        }
    }

    public static void print(Object value) {
        if (value instanceof List<?>) {
            List<?> list = (List<?>) value;
            System.out.print("[");
            for (int i = 0; i < list.size(); i++) {
                print(list.get(i));  // recurse into elements
                if (i < list.size() - 1) {
                    System.out.print(", ");
                }
            }
            System.out.print("]");
        } else if (value instanceof String[]) {
            String[] arr = (String[]) value;
            System.out.print("[");
            for (int i = 0; i < arr.length; i++) {
                System.out.print(arr[i]);
                if (i < arr.length - 1) {
                    System.out.print(", ");
                }
            }
            System.out.print("]");
        } else {
            System.out.print(String.valueOf(value));
        }
    }

    public void runComparison() throws Exception {

        this.compare();
        this.constructNewSeqs();
        this.parseGaps();

        if (this.fcs != null) {
            this.archiveAndClear();
            this.storeSQL();
        }

//        this.printSeqs();

    }

    public static void runOneComparison(int chain_id, int domain_id) throws Exception {

        ChainDomainFatcatPair c = new ChainDomainFatcatPair(chain_id, domain_id);
        c.runComparison();

    }

    public static void main(String[] args) throws Exception {

        int chain = Integer.parseInt(args[0]);
        int segment_id = Integer.parseInt(args[1]);
//        String domain = args[1];

//        LocalSQL.connect("jdbc:mysql://doppelbock/scop?user=makelyan&password=masql");
        LocalSQL.connectRW();
        ChainDomainFatcatPairs.runComparisons(chain, segment_id);

//        print("doing sql query");
//
//        ArrayList<Integer> chains = new ArrayList<>();
//        Statement stmt = LocalSQL.createStatement();
//        ResultSet rs = stmt.executeQuery("select distinct astral_chain.id as chain_id from astral_chain_subset_id join astral_chain on astral_chain_subset_id.astral_chain_id = astral_chain.id join raf on astral_chain.raf_id = raf.id join link_pdb on raf.pdb_chain_id = link_pdb.pdb_chain_id join scop_node on link_pdb.node_id = scop_node.id join astral_domain on scop_node.id = astral_domain.node_id join pdb_chain on link_pdb.pdb_chain_id = pdb_chain.id join pdb_release on pdb_chain.pdb_release_id = pdb_release.id join pdb_local on pdb_release.id = pdb_local.pdb_release_id where astral_chain_subset_id.pct_identical = 90 and astral_domain.source_id = 1 and astral_domain.style_id = 1 " +
//                "and scop_node.release_id = 19 limit 10000");
//        while (rs.next()) {
//            int chain_id = rs.getInt("chain_id");
//            chains.add(chain_id);
//        }
//        rs.close();
//        stmt.close();
//
//        print("sql query done");

//        ArrayList<Integer> domains = new ArrayList<>();
//        Statement stmt2 = LocalSQL.createStatement();
//        ResultSet rs2 = stmt2.executeQuery("select node_id from scop_node_fatcat_rep");
//        while (rs2.next()) {
//            int domain_id = rs2.getInt("node_id");
//            domains.add(domain_id);
//        }
//        rs2.close();
//        stmt2.close();

        // 3295190 = 1r6jA (random chain)
        // 3441344 = 2b97A (random chain)

        // 2867556 = d1ryha_ (insertions and missing residues)
        // 2826216 = random regular domain
        // 2827303 = random regular domain

//        long startTime = System.currentTimeMillis();
//
//        int domain = 2689966;
//        int i = 1;
//        for (int chain : chains) {
//            if (i % 10 == 0) {
//                print("\nchain", chain + " " + i);
//                runOneComparison(chain, domain);
//            }
//            i++;
//        }

//        runOneComparison(3719861, domain);

        // 3691271 // COME BACK TO THIS ONE -- RAF SEQ IS NOT MATCHING THE PDB FILE AND CHAIN_LEN INFORMATION!!!
        // 3298760 //

//        long endTime = System.currentTimeMillis();
//        print("duration (s)", (endTime - startTime) / 1000.000);

        // 2877364 = domain with a lot missing
        // = chain with a lot of insertions

    }

}
