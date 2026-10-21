package gov.lbl.scop.app;

import java.io.*;
import java.sql.*;
import gov.lbl.scop.local.LocalSQL;

import java.util.*;

import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.nio.file.DirectoryStream;
import org.apache.commons.io.FileUtils;
import org.strbio.mol.*;
import org.strbio.mol.lib.AlignmentParameters;
import org.strbio.mol.lib.GapAffine;
import org.strbio.mol.lib.ScoreID;
import org.strbio.mol.lib.ScoreList;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.*;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class FatcatPair {

    /*
    runs one structural comparison (via FATCAT and PostFATCAT) between a specified domain and alphafold model.
    stores the results in SQL tables scop_node_fatcat_af, scop_node_fatcat_af_gap, and scop_node_fatcat_af_matrix.
    also stores copies of the resulting .aln files in /alignments/, deletes superposition files.
     */

    // query = af/pdb/uniprot = seq1 = chain 1
    // hit = scop domain = seq2 = chain 2

    final static String COMMON_DIR = "/lab/proj/astral/fatcat/2.08/";
    final static String TEMP_DIR = "/tmp/";

    final static String SCOP_DIR = "/lab/proj/astral/pdbstyle/2.08/";
    final static String AF_DIR = "/lab/db/model_structures/alphafolddb/v4/pdb/";
    
    final static String ALIGNMENTS_DIR = COMMON_DIR + "alignments/";
    final static String MATRICES_DIR = COMMON_DIR + "matrices/";
    final static String SQL_DIR = COMMON_DIR + "sql/";


    final static String FATCAT = "/lab/app/FATCAT/cur/FATCAT-dist/FATCATMain/FATCAT";
    final static String PostFATCAT = "/lab/app/FATCAT/cur/FATCAT-dist/FATCATMain/PostFATCAT";

    PreparedStatement stmt1;
    PreparedStatement stmt2;
    PreparedStatement stmt3;

    String query; // alphafold
    String hit; // domain
    int query_id;
    int node_id;

    int af_len;
    int scop_len;
    int ini_len;
    double ini_rmsd;
    int opt_equ;
    double opt_rmsd;
    double chain_rmsd;
    double score;
    int align_len;
    int gaps;
    double p_val;
    int afp_num;
    double identity;
    double similarity;
    boolean shortMatch;

    int af_start;
    int af_length;
    String scop_start;
    int scop_start_index;
    int scop_length;

    double[][] rotLst;
    double[] transLst;

    public ArrayList<Gap> queryGaps;
    public ArrayList<Gap> hitGaps;

    String fatcatSeq; // sequence fatcat reads from the domain
    String rafSeq; // sequence of the domain from raf

    FatcatPairs fcs;
    String afSeq;
    String alnSeq;
    String scopSeq;

    String alignmentFile;
    String superpositionFile;
    String matrixFile;
    String scopFile; // will be different depending on the scop domain you're accessing
    String afFile;
    String subdir;

    String afHash;
    String scopHash;

    boolean comparison_completed;
    boolean matrix_parsed;
    boolean alignment_file_read;
    boolean files_archived;
    boolean temp_dir_cleared;

    String sql;
    Logger logger;
    BufferedWriter SQLWriter;

    static String align1 = "Align.*\\.pdb (\\d+) with .*\\.ent (\\d+)";
    static Pattern pAlign1 = Pattern.compile(align1); // af_len, scop_len
    static String align2 = ".* ini-len (\\d+) ini-rmsd (\\d+\\.\\d+) opt-equ (\\d+) opt-rmsd (\\d+\\.\\d+) chain-rmsd (\\d+\\.\\d+) Score (\\d+\\.\\d+) align-len (\\d+) gaps (\\d+)"; // opt-rmsd, score, align-len, gaps
    static Pattern pAlign2 = Pattern.compile(align2); // ini_len, ini_rmsd, opt_equ, opt_rmsd, chain_rmsd, score, align_len, gaps
    static String align3 = "P-value ([0-9]+.[0-9]+e[+-]*[0-9]+) Afp-num (\\d+) Identity (\\d+\\.\\d+)% Similarity (\\d+\\.\\d+)%";
    static Pattern pAlign3 = Pattern.compile(align3); // p-val, afp_num, identity, similarity
    static String alignIndex = "Chain \\d+:\\s+(-*\\d+[A-Z]*) [-\\w]+";
    static Pattern pAlignIndex = Pattern.compile(alignIndex);
    static Pattern pAlignUniprot = Pattern.compile("AF-([\\w\\d]+)-F[\\d]+");

    public FatcatPair(int query_id, PreparedStatement stmt1, PreparedStatement stmt2, PreparedStatement stmt3, FatcatPairs fcs) throws Exception {

        String af = FatcatPairs.alphaFolds.get(query_id);

        this.query = getAfFromPath(af); // pdb or uniprot or chain 1
        this.hit = fcs.domain; // scop domain or chain 2
        this.query_id = query_id;
        this.node_id = fcs.node_id;

        this.stmt1 = stmt1;
        this.stmt2 = stmt2;
        this.stmt3 = stmt3;

        // fatcat stats
        this.af_len = 0;
        this.scop_len = 0;
        this.ini_len = 0;
        this.ini_rmsd = 0;
        this.opt_equ = 0;
        this.opt_rmsd = 0;
        this.chain_rmsd = 0;
        this.score = 0;
        this.align_len = 0;
        this.gaps = 0;
        this.p_val = 0;
        this.afp_num = 0;
        this.identity = 0;
        this.similarity = 0;
        this.shortMatch = false;

        this.af_start = 0;
        this.af_length = 0;
        this.scop_start = "0";
        this.scop_start_index = -1;
        this.scop_length = 0;

        // matrix coordinates
        this.rotLst = new double[3][3];
        this.transLst = new double[3];

        // gap information for both the query pdb and hit scop domain
        this.queryGaps = new ArrayList<>();
        this.hitGaps = new ArrayList<>();

        // progress checks
        this.comparison_completed = false;
        this.matrix_parsed = false;
        this.alignment_file_read = false;
        this.files_archived = false;
        this.temp_dir_cleared = false;

        this.fcs = fcs;
        this.afSeq = "";
        this.alnSeq = "";
        this.scopSeq = "";

        Matcher matcher = pAlignUniprot.matcher(this.query);
        String uniprot = null;
        if (matcher.find()) {
            uniprot = matcher.group(1);
        } else if (!matcher.find()) {
            throw new Exception("no regex match uniprot, line " + 173);
        }

        this.sql = "";
        this.SQLWriter = fcs.SQLWriter;
        this.logger = fcs.logger;

        this.afHash = uniprot.substring(uniprot.length() - 3, uniprot.length() - 1);
        this.scopHash = fcs.scopHash;


        // shortcuts for the aln file name, superposed file name, and file name of the scop domain
        this.alignmentFile = this.query + "_" + this.hit + ".aln"; // in /temp/domain-0/ during, then moved to alignments_dir
        this.matrixFile = this.query + "_" + this.hit + ".matrix"; // in /temp/domain-0/ during, then moved to matrices_dir
        this.scopFile = String.valueOf(fcs.tempDomain); // copy from /pdbstyle/. deleted at the end
        this.afFile = AF_DIR + afHash + "/" + this.query + "-model_v4.pdb";
        this.subdir = fcs.subdir; // subdir between *_DIR and [af]_[scop].*

    }

    public FatcatPair(int query_id, FatcatPairs fcs) throws Exception {

        String af = FatcatPairs.alphaFolds.get(query_id);

        this.query = getAfFromPath(af); // pdb or uniprot or chain 1
        this.hit = fcs.domain; // scop domain or chain 2
        this.query_id = query_id;
        this.node_id = fcs.node_id;

        // fatcat stats
        this.af_len = 0;
        this.scop_len = 0;
        this.ini_len = 0;
        this.ini_rmsd = 0;
        this.opt_equ = 0;
        this.opt_rmsd = 0;
        this.chain_rmsd = 0;
        this.score = 0;
        this.align_len = 0;
        this.gaps = 0;
        this.p_val = 0;
        this.afp_num = 0;
        this.identity = 0;
        this.similarity = 0;
        this.shortMatch = false;

        this.af_start = 0;
        this.af_length = 0;
        this.scop_start = "0";
        this.scop_start_index = -1;
        this.scop_length = 0;

        // matrix coordinates
        this.rotLst = new double[3][3];
        this.transLst = new double[3];

        // gap information for both the query pdb and hit scop domain
        this.queryGaps = new ArrayList<>();
        this.hitGaps = new ArrayList<>();

        // progress checks
        this.comparison_completed = false;
        this.matrix_parsed = false;
        this.alignment_file_read = false;
        this.files_archived = false;
        this.temp_dir_cleared = false;

        this.fcs = fcs;
        this.afSeq = "";
        this.alnSeq = "";
        this.scopSeq = "";

        Matcher matcher = pAlignUniprot.matcher(this.query);
        String uniprot = null;
        if (matcher.find()) {
            uniprot = matcher.group(1);
        } else if (!matcher.find()) {
            throw new Exception("no regex match uniprot, line " + 173);
        }

        this.afHash = uniprot.substring(uniprot.length() - 3, uniprot.length() - 1);
        this.scopHash = fcs.scopHash;

        this.sql = "";
        this.SQLWriter = fcs.SQLWriter;
        this.logger = fcs.logger;

        // shortcuts for the aln file name, superposed file name, and file name of the scop domain
        this.alignmentFile = this.query + "_" + this.hit + ".aln"; // in /temp/domain-0/ during, then moved to alignments_dir
        this.matrixFile = this.query + "_" + this.hit + ".matrix"; // in /temp/domain-0/ during, then moved to matrices_dir
        this.scopFile = String.valueOf(fcs.tempDomain); // copy from /pdbstyle/. deleted at the end
        this.afFile = AF_DIR + afHash + "/" + this.query + "-model_v4.pdb";
        this.subdir = fcs.subdir; // subdir between *_DIR and [af]_[scop].*

    }

    /*
    Run FATCAT and PostFATCAT, read .aln and .matrix files.
    */
    public void compare() throws Exception {

        File alnFile = new File(fcs.TempDir + this.alignmentFile);
        File matrixFile = new File(fcs.TempDir + this.matrixFile);

        if (!new File(this.afFile).exists()) {
            throw new Exception("AF_DIR + " + this.afFile + " does not exist");
        }
        if (!new File(this.scopFile).exists()) {
            throw new Exception("SCOP_DIR + " + this.scopFile + " does not exist");
        }
        if (alnFile.exists()) {
            comparison_completed = true;
            this.readAln(alnFile);

        } else {

            String[] command = {
                    FATCAT,
                    "-p1", this.afFile,
                    "-p2", this.scopFile,
                    "-o", fcs.TempDir + this.alignmentFile.replace(".aln", ""), // (2) run FATCAT to tmp
                    "-r", "-m", "-t"
            };

            // System.out.println(String.join(" ", command));

            logger.info("running command " + String.join(" ", command));
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(new File("/"));
            Process p = builder.start();
            p.waitFor();
            p.destroy();

            if (!alnFile.exists()) {
                throw new Exception(alnFile + " not generated");
            }
            this.readAln(alnFile);
            this.comparison_completed = true;
            if (shortMatch) {
                return;
            }

            String[] command2 = {
                    PostFATCAT,
                    "-a", fcs.TempDir + this.alignmentFile,
                    "-p1", this.afFile,
                    "-p2", this.scopFile,
                    "-n1", this.query + "-model_v4.pdb",
                    "-n2", this.hit + ".ent",
                    "-c", fcs.TempDir + this.superpositionFile,
                    "-m", fcs.TempDir + this.matrixFile
            };

            // System.out.println(String.join(" ", command2));

            logger.info("running command " + String.join(" ", command2));
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
                    throw new Exception("PostFATCAT failed for " + this.query + " x " + this.scopFile);
                }
                System.out.println("repeating PostFATCAT");
                p2 = builder.start();
                p2.waitFor();
                p2.destroy();
                i++;
            }

            this.matrix_parsed = true;
        }
    }


    /*
    Read FATCAT output .aln file, store values in instance variables.
     */
    public void readAln(File result) throws Exception {

        logger.info("readAln " + result.toString());

        // get fatcatSeq
        Scanner s2 = new Scanner(result);
        while (s2.hasNextLine()) {
            String line2 = s2.nextLine();
            if (line2.startsWith("Short match")) {
                shortMatch = true;
                logger.info("readAln found short match");
            }
            else if (line2.startsWith("Chain 1")) {
                s2.nextLine();
                fatcatSeq += s2.nextLine().substring(13);
                logger.info("readAln found seq info");
            }
        }
        s2.close();

        if (!shortMatch) {
            Scanner s = new Scanner(result);
            logger.info("parsing aln");

            int i = 0;
            while (s.hasNextLine()) {
                String line = s.nextLine();

                if (i == 0) {
                    Matcher match = pAlign1.matcher(line);
                    if (match.find()) {
                        this.af_len = Integer.parseInt(match.group(1));
                        this.scop_len = Integer.parseInt(match.group(2));
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
                    String line_scop = s.nextLine();
                    s.nextLine();
                    parseLines(line, line_gap, line_scop);
                }
                i++;
            }
            s.close();
        }
        s2.close();
    }

    /*
    Read PostFATCAT output [AF]_[domain].matrix file, storing the transformation matrix coordinates into rotLst and transLst.
     */
    public void readMatrix(File result) throws Exception {

        logger.info("readMatrix " + result.toString());

        Scanner s = new Scanner(result);
        double[] Tfatcat = new double[3];
        String[] lst;

        while (s.hasNextLine()) {
            String line = s.nextLine();
            if (line.startsWith("rotate") || line.startsWith("translate")) {

                lst = line.split("\\s");
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
    Write insert statements into a .sql file (one per job)
     */
    private boolean shouldStoreDetail() {
        // These are the shared public viewer/API thresholds.  A FATCAT
        // P-value reported as zero is an underflow/serialization case and is
        // therefore retained as the most significant value, not discarded.
        return p_val >= 0.0 && p_val <= 0.05 && ini_rmsd < 4.0 && score > 0.0;
    }

    public void storeSQL() throws Exception {

        // stmt1 (scop_node_fatcat_af)
        String stmt1 = String.format(
                "INSERT INTO scop_node_fatcat_af VALUES (NULL, %d, %d, %d, %d, %d, %.6f, %d, %.6f, %.6f, %.6f, %d, %d, %s, %d, %.6f, %.6f, %d, %d, '%s', %d);",
                query_id,
                node_id,
                af_len,
                scop_len,
                ini_len,
                ini_rmsd,
                opt_equ,
                opt_rmsd,
                chain_rmsd,
                score,
                align_len,
                gaps,
                Double.toString(p_val),
                afp_num,
                identity,
                similarity,
                af_start,
                af_length,
                scop_start,
                scop_length
        );

        String detailSQL = "";
        if (shouldStoreDetail()) {
            // store auto-incremented id from scop_node_fatcat_af insertion
            String auto_id = "SET @hit_id = LAST_INSERT_ID();";

            // stmt2 (scop_node_fatcat_af_gap)
            StringBuilder sb = new StringBuilder();
            if (!queryGaps.isEmpty()) {
                sb.append("INSERT INTO scop_node_fatcat_af_gap ");
                sb.append("(hit_id, gap_start_af, gap_length_af, gap_start_scop, gap_length_scop, gap_id, gap_type) VALUES\n");
                for (int n = 0; n < queryGaps.size(); n++) {
                    int gap_start_af = queryGaps.get(n).start;
                    int gap_length_af = queryGaps.get(n).len;
                    int gap_start_scop = hitGaps.get(n).start;
                    int gap_length_scop = hitGaps.get(n).len;
                    int gap_id = queryGaps.get(n).gap_id;
                    int gap_type = queryGaps.get(n).gap_type;
                    sb.append(String.format(
                            "(@hit_id, %d, %d, %d, %d, %d, %d)",
                            gap_start_af,
                            gap_length_af,
                            gap_start_scop,
                            gap_length_scop,
                            gap_id,
                            gap_type
                    ));
                    if (n < queryGaps.size() - 1) {
                        sb.append(",\n");
                    } else {
                        sb.append(";");
                    }
                }
            }
            String stmt2 = sb.toString();

            // stmt3 (scop_node_fatcat_af_matrix)
            sb = new StringBuilder();
            sb.append("INSERT INTO scop_node_fatcat_af_matrix ");
            sb.append("(hit_id, r11, r12, r13, r21, r22, r23, r31, r32, r33, t1, t2, t3) VALUES\n");
            sb.append("(@hit_id");
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    sb.append(String.format(", %.10f", rotLst[i][j]));
                }
            }
            for (int m = 0; m < 3; m++) {
                sb.append(String.format(", %.10f", transLst[m]));
            }
            sb.append(");");
            String stmt3 = sb.toString();
            detailSQL = auto_id + "\n";
            if (!stmt2.isEmpty()) {
                detailSQL += stmt2 + "\n";
            }
            detailSQL += stmt3 + "\n";
        }

        // formatting
        this.sql = stmt1 + "\n" + detailSQL;
        SQLWriter.write(sql);
        SQLWriter.flush();
    }

    /*
    Store the instance variables / FATCAT values into SQL tables
     */
    public void storeData() throws Exception {

        LocalSQL.connectRW();
        logger.info("storeData");

        // set id
        stmt1.setInt(1, this.query_id);
        stmt1.setInt(2, this.node_id);
        stmt1.setInt(3, this.af_len);
        stmt1.setInt(4, this.scop_len);
        stmt1.setInt(5, this.ini_len);
        stmt1.setDouble(6, this.ini_rmsd);
        stmt1.setInt(7, this.opt_equ);
        stmt1.setDouble(8, this.opt_rmsd);
        stmt1.setDouble(9, this.chain_rmsd);
        stmt1.setDouble(10, this.score);
        stmt1.setInt(11, this.align_len);
        stmt1.setInt(12, this.gaps);
        stmt1.setDouble(13, this.p_val);
        stmt1.setInt(14, this.afp_num);
        stmt1.setDouble(15, this.identity);
        stmt1.setDouble(16, this.similarity);
        stmt1.setInt(17, this.af_start);
        stmt1.setInt(18, this.af_length);
        stmt1.setString(19, this.scop_start);
        stmt1.setInt(20, this.scop_length);

        logger.info("executing stmt1");
        // add batch
        stmt1.executeUpdate();

        // get auto-incremented id from the scop_node_fatcat_af entry
        ResultSet generatedKeys = stmt1.getGeneratedKeys();
        int hit_id = -1;
        if (generatedKeys.next()) {
            hit_id = generatedKeys.getInt(1);
        }
        generatedKeys.close();

        if (!shouldStoreDetail()) {
            return;
        }

        // Insert into scop_node_fatcat_af_gap
        logger.info("adding stmt2 batches");
        for (int n = 0; n < queryGaps.size(); n++) {
            int gap_start_af = queryGaps.get(n).start;
            int gap_length_af = queryGaps.get(n).len;
            int gap_start_scop = hitGaps.get(n).start;
            int gap_length_scop = hitGaps.get(n).len;
            int gap_id = queryGaps.get(n).gap_id;
            int gap_type = queryGaps.get(n).gap_type;

            stmt2.setInt(1, hit_id);
            stmt2.setInt(2, gap_start_af);
            stmt2.setInt(3, gap_length_af);
            stmt2.setInt(4, gap_start_scop);
            stmt2.setInt(5, gap_length_scop);
            stmt2.setInt(6, gap_id);
            stmt2.setInt(7, gap_type);

            stmt2.addBatch();
        }
         stmt2.executeBatch();

        // Insert into scop_node_fatcat_af_matrix
        logger.info("adding stmt3 batch");
        stmt3.setInt(1, hit_id);
        int k = 2;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                stmt3.setDouble(k, rotLst[i][j]);
                k++;
            }
        }
        for (int m = 0; m < 3; m++) {
            stmt3.setDouble(k, transLst[m]);
            k++;
        }
        stmt3.addBatch();
        stmt3.executeBatch();
    }

    /*
    Store the .aln file and superposed pdb file into their archive directories, and clear the temp directory
     */
    public void archiveAndClear() throws Exception {

        Path oldAlnPath = Paths.get(fcs.TempDir + this.alignmentFile);
        Path newAlnPath = Paths.get(fcs.AlnDir + this.alignmentFile);

        logger.info("archiveAndClear--moving aln " + oldAlnPath + " to " + newAlnPath);
        Files.move(oldAlnPath, newAlnPath, StandardCopyOption.REPLACE_EXISTING);

        if (Files.exists(newAlnPath)) {
            if (Files.exists(oldAlnPath)) {
                Files.delete(oldAlnPath);
                if (!Files.exists(oldAlnPath)) {
                    logger.info("File was successfully moved to " + newAlnPath);
                }
                else {
                    logger.severe("failed to delete " + oldAlnPath);
                }
            }
        } else {
            logger.severe("File move failed: " + newAlnPath + " does not exist.");
        }

        if (!shortMatch) {

            Path oldMatrixPath = Paths.get(fcs.TempDir + this.matrixFile);
            Path newMatrixPath = Paths.get(fcs.MatrixDir + this.matrixFile);
            logger.info("archiveAndClear--moving matrix " + oldMatrixPath + " to " + newMatrixPath);
            Files.move(oldMatrixPath, newMatrixPath, StandardCopyOption.REPLACE_EXISTING);

            if (Files.exists(newMatrixPath)) {
                if (Files.exists(oldMatrixPath)) {
                    Files.delete(oldMatrixPath);
                    if (!Files.exists(oldMatrixPath)) {
                        logger.info("File was successfully moved to " + newMatrixPath);
                    } else {
                        logger.severe("failed to delete " + oldMatrixPath);
                    }
                }
            } else {
                logger.severe("File move failed: " + newMatrixPath + " does not exist.");
            }

            // clear the temp dir
            logger.info("clearing temp dir " + fcs.TempDir);

            String logFileName = hit + "-" + fcs.segment_id + ".log";

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(fcs.TempDir))) {
                for (Path path : stream) {
                    if (Files.isRegularFile(path)) {
                        String fileName = path.getFileName().toString();

                        // Skip log file, .ent file, and NFS ghost files
                        if (fileName.equals(logFileName) || fileName.endsWith(".ent") || fileName.startsWith(".nfs")) {
                            continue;
                        }

                        Files.delete(path);
                    }
                }
            }
        }
    }

    /*
    collect scopSeq, alnSeq, afSeq, prepare for gap processing.
     */
    public void parseLines(String af_line, String line2, String scop_line) throws Exception {

        logger.info("parseLines\n" + af_line + "\n" + line2 + "\n" + scop_line);

        // get starts of alignments in terms of AF and SCOP residue indices
        Matcher matchIndex_af = pAlignIndex.matcher(af_line);
        Matcher matchIndex_scop = pAlignIndex.matcher(scop_line);

        int af_index;
        int scop_index;
        String scop_start_str;

        if (matchIndex_af.find()) {
            af_index = Integer.parseInt(matchIndex_af.group(1));
        } else {
            throw new Exception("no regex match af_index, line " + 502);
        }
        if (matchIndex_scop.find()) {
            scop_start_str = matchIndex_scop.group(1);
            scop_index = new ArrayList<>(fcs.rafMap.keySet()).indexOf(scop_start_str);
            if (scop_start_index < 0) {
                scop_start_index = scop_index;
            }
        } else {
            throw new Exception("no regex match scop_index, line " + 507);
        }

        // initialize indices
        if (this.af_start == 0 || this.scop_start == "0") {
            this.af_start = af_index;
            this.scop_start = scop_start_str;
        }

        char[] af_line_arr = af_line.toCharArray();
        char[] scop_line_arr = scop_line.toCharArray();

        for (int i = 14; i < af_line.length(); i++) {
            char c_af = af_line_arr[i];
            char c_scop = scop_line_arr[i];
            if (c_af != '-') {
                af_index++;
            }
            if (c_scop != '-') {
                scop_index++;
            }
        }
        this.afSeq += new String(af_line_arr).substring(14).trim();
        this.alnSeq += line2.substring(14);
        this.scopSeq += new String(scop_line_arr).substring(14).trim();

        this.af_length = af_index - af_start;
        this.scop_length = scop_index - scop_start_index;
    }

    /*
    execute alignment against fatcatSeq, insert gaps ("-"), missing residues (","), insertions ("/") and post-translational modifications ("?")
    accordingly, such that fatcatSeq matches in length one-to-one with the FULL rafMap, but with "-" gaps where an alignment did not occur.
     */
    public void constructNewSeqs() throws Exception {

        logger.info("constructNewSeqs");

        getRafSeq();
        String[] rafMapArray = fcs.rafMap.values().toArray(new String[0]);

        if (shortMatch) {
            this.af_len = (int) Files.lines(Paths.get(this.afFile))
                    .filter(line -> line.startsWith("ATOM"))
                    .map(line -> line.substring(21, 26).trim())
                    .distinct()
                    .count();
            this.scop_len = fcs.rafMap.size();
            this.gaps = Math.max(this.scop_len, this.af_len);

            int af_idx = 0;
            int scop_idx = 0;

            // build overlapping gap region
            while (af_idx < af_len && scop_idx < scop_len) {
                afSeq += "X";
                alnSeq += " ";
                scopSeq += rafMapArray[scop_idx].toUpperCase();
                af_idx++;
                scop_idx++;
            }

            // build the rest of the alphafold if its longer
            while (af_idx < af_len) {
                afSeq += "X";
                alnSeq += " ";
                scopSeq += "=";
                af_idx++;
            }

            // build the rest of the scop if its longer
            while (scop_idx < scop_len) {
                afSeq += "=";
                alnSeq += " ";
                scopSeq += rafMapArray[scop_idx].toUpperCase();
                scop_idx++;
            }
            return;
        }

        this.scopSeq = this.scopSeq.replaceAll("-", "=");
        this.afSeq = this.afSeq.replaceAll("-", "=");

        // run rafSeq x scopSeq alignment
        logger.info("running alignment " + this.rafSeq + " " + this.scopSeq);
        Protein rafSeq_obj = new Protein(this.rafSeq);
        Protein scopSeq_obj = new Protein(this.scopSeq);

        AlignmentParameters ap = new AlignmentParameters(new GapAffine(0.5, 0.0), new ScoreList(new ScoreID()));
        Alignment aln = rafSeq_obj.getGlobalAlignment(scopSeq_obj, ap, null);
        if (aln == null) {
            throw new Exception("Sequence alignment rafSeq x scopSeq failed for " + this.scopFile);
        }
        char[][] alignedArrays = aln.toCharArrays();
        String rafSeqAligned = new String(alignedArrays[0]); // rafSeq
        String scopSeqAligned = new String(alignedArrays[1]); // scopSeq

        // go index by index through rafSeqAligned, scopSeqAligned, afSeq, alnSeq
        logger.info("constructing seqs");
        String af_prefix = "";
        String aln_prefix = "";
        String scop_prefix = "";

        // beginning gap
        int idx_af = 1; 
        int idx_scop = 1;

        int idx_raf = 0;

        int dist_to_scop_start = new ArrayList<>(fcs.rafMap.keySet()).indexOf(String.valueOf(scop_start));
        int dist_to_af_start = af_start - idx_af;

        rafMapArray = fcs.rafMap.values().toArray(new String[0]);

        // adjust if scop is longer than af
        while (dist_to_scop_start > dist_to_af_start) {
            af_prefix += "=";
            aln_prefix += " ";
            scop_prefix += rafMapArray[idx_raf].toUpperCase();
            idx_raf++;
            dist_to_scop_start--;
        }

        // adjust if af is longer than scop
        while (dist_to_scop_start < dist_to_af_start) {
            af_prefix += "B";
            aln_prefix += " ";
            scop_prefix += "=";
            dist_to_af_start--;
            idx_af++;
        }

        // bring both to starts
        assert dist_to_af_start == dist_to_scop_start;
        while (dist_to_scop_start > 0) {
            af_prefix += "B";
            aln_prefix += " ";
            scop_prefix += rafMapArray[idx_raf].toUpperCase();
            idx_raf++;
            dist_to_scop_start--;
            idx_af++;
        }

        // middle portion
        StringBuilder sbAf = new StringBuilder(afSeq);
        StringBuilder sbAln = new StringBuilder(alnSeq);
        StringBuilder sbScop = new StringBuilder(scopSeqAligned);

        for (int i = 0; i < scopSeqAligned.length(); i++) {
            // the regular alignment is already there, just adding in hetatm's / missings / insertions

            char c_raf = rafSeqAligned.charAt(i);
            char c_scop = scopSeqAligned.charAt(i);

            // missing residues
            if (c_raf == ',') {
                if (c_scop == '=') {
                    sbScop.setCharAt(i, ',');
                } else if (c_scop == '.') {
                    sbScop.setCharAt(i, ',');
                    sbAf.insert(i, '=');
                    sbAln.insert(i, ' ');
                }
            }

            // hetatm
            else if (Character.isLetter(c_raf) && !Character.isLetter(c_scop)) {
                if (c_scop == '=') {
                    sbScop.setCharAt(i, '?');
                } else if (c_scop == '.') {
                    sbScop.setCharAt(i, '?');
                    sbAf.insert(i, '=');
                    sbAln.insert(i, ' ');
                }
            }

            // insertion
            else if (c_raf == '/') {
                if (c_scop == '=') {
                    sbScop.setCharAt(i, ',');
                } else if (c_scop == '.') {
                    sbScop.setCharAt(i, ',');
                    sbAf.insert(i, '=');
                    sbAln.insert(i, ' ');
                }
            }
        }

        for (int i = 0; i < sbAf.length(); i++) {
            char c_af = sbAf.charAt(i);
            char c_scop = sbScop.charAt(i);

            if (c_af != '=') {
                idx_af++;
            }
            if (c_scop != '=') {
                idx_raf++;
            }
        }

        // end gap
        String af_suffix = "";
        String aln_suffix = "";
        String scop_suffix = "";

        // idx_af is up-to-date
        // idx_raf is up-to-date

        int dist_to_af_end = af_len - idx_af + 1;
        int dist_to_scop_end = rafMapArray.length - idx_raf;

        // extend both until one finishes
        while (dist_to_af_end > 0 && dist_to_scop_end > 0) {
            af_suffix += "E";
            aln_suffix += " ";
            scop_suffix += rafMapArray[idx_raf].toUpperCase();
            dist_to_af_end--;
            dist_to_scop_end--;
            idx_raf++;
        }

        // extend just scop until done
        while (dist_to_af_end == 0 && dist_to_scop_end > 0) {
            af_suffix += "=";
            aln_suffix += " ";
            scop_suffix += rafMapArray[idx_raf].toUpperCase();
            dist_to_scop_end--;
            idx_raf++;
        }

        // extend just af until done
        while (dist_to_af_end > 0 && dist_to_scop_end == 0) {
            af_suffix += "E";
            aln_suffix += " ";
            scop_suffix += "=";
            dist_to_af_end--;
        }

        this.afSeq = af_prefix + sbAf.toString() + af_suffix;
        this.alnSeq = aln_prefix + sbAln.toString() + aln_suffix;
        this.scopSeq = scop_prefix + sbScop.toString() + scop_suffix;

        logger.info(afSeq);
        logger.info(alnSeq);
        logger.info(scopSeq);
        
    }

    /*
    parse NEW gaps
     */
    public void parseGaps() throws Exception {

        logger.info("parseGaps");

        // initialize new seqs
        constructNewSeqs();

        // parse through the new gaps //
        Gap g_af = null;
        Gap g_scop = null;
        int currType = 0;
        int currID = 1;
        int af_index = 0;
        int scop_index = fcs.scop_beginning - 1;

        // process all gaps
        for (int i = 0; i <= this.afSeq.length(); i++) {
            if (i == this.afSeq.length()) {
                if (g_af != null && g_scop != null) {
                    g_af.setEnd("af");
                    g_scop.setEnd("scop");
                }
                break;
            }

            char c_af = afSeq.charAt(i);
            char c_aln = alnSeq.charAt(i);
            char c_scop = scopSeq.charAt(i);

            if (c_scop != '=' && c_scop != '/') { scop_index++; }
            if (c_af != '=') { af_index++; }

            // if in a gap
            if (c_aln != '1') {
                int newType = gapType(c_af, c_scop);

                // if you weren't in a gap before, make a new gap here.
                if (g_af == null && g_scop == null) {
                    currType = newType;
                    g_af = new Gap(af_index, currID, currType);
                    g_scop = new Gap(scop_index, currID, currType);
                }

                // if you were in a gap before and you're still in the same type of gap -> extend.
                // if you were in a gap before and you're in a new type of gap now -> end the current gap. make a new gap.
                else if (g_af != null && g_scop != null && currType != newType) {
                    g_af.setEnd("af");
                    g_scop.setEnd("scop");
                    currType = newType;
                    g_af = new Gap(af_index, currID, currType);
                    g_scop = new Gap(scop_index, currID, currType);
                }

                // based on type, extend.
                g_af.extend("af");
                g_scop.extend("scop");

            } else if (c_aln == '1') {

                // if you were in a gap and it ended, end the current gap. set gaps as null.
                if (g_af != null && g_scop != null) {
                    g_af.setEnd("af");
                    g_scop.setEnd("scop");
                    g_af = null;
                    g_scop = null;
                    currType = 0;
                    currID++;
                }

                // if you weren't in a gap before, just advance the index pointers.
            }
        }
    }

    /*
    returns the gap type given scopSeq and afSeq characters
     */
    public int gapType(char c_af, char c_scop) throws Exception {

        boolean hasAF = false;
        boolean hasSCOP = false;
        boolean missing = false;
        boolean hetatm = false;
        boolean insertion = false;

        if (c_af != '=') {
            hasAF = true;
        }
        if (c_scop != '=') {
            hasSCOP = true;
            if (c_scop == ',') {
                missing = true;
            } else if (c_scop == '?') {
                hetatm = true;
            } else if (c_scop == '/') {
                insertion = true;
            }
        }

        if (hasAF && !hasSCOP) {
            return 1;
        } else if (hasAF && hasSCOP) {
            if (missing) {
                return 7;
            } else if (hetatm) {
                return 5;
            } else if (insertion) {
                return 9;
            } else {
                return 3;
            }
        } else if (!hasAF && hasSCOP) {
            if (missing) {
                return 6;
            } else if (hetatm) {
                return 4;
            } else if (insertion) {
                return 8;
            } else {
                return 2;
            }
        }
        throw new Exception ("type not found for " + this.query + " x " + this.scopFile);
    }

    /*
    compare fatcatSeq to rafMap, parse ranges of missing residues and post-translational modifications
     */
    public String getRafSeq() throws Exception {

        logger.info("getRafSeq");

        // convert rafMap to rafSeq string
        boolean withinRange = false;
        StringBuilder result = new StringBuilder();

        for (Map.Entry<String, String> entry : fcs.rafMap.entrySet()) {
            String key = entry.getKey();
            if (shortMatch) {
                withinRange = true;
            }
            if (key.equals(String.valueOf(this.scop_start))) {
                withinRange = true;
            }
            if (withinRange) {
                result.append(entry.getValue());
            }
            if (key.equals(String.valueOf(this.scop_start_index + scop_length))) { // questionable -1
                withinRange = false;
            }
        }
        this.rafSeq = result.toString().toUpperCase();
        return rafSeq;
    }


    public void clearTemp() throws Exception {
        FileUtils.cleanDirectory(new File(TEMP_DIR));
    }


    public static void connectDB() throws Exception {
        LocalSQL.connectRW();
    }

    /*
    returns the alphafold identifier given the path
     */
    public String getAfFromPath(String path) throws Exception {

        Pattern pattern = Pattern.compile("AF-[A-Za-z0-9]+-F\\d+");
        Matcher matcher = pattern.matcher(path);
        if (matcher.find()) {
            return matcher.group(0);
        }
        return null;
    }

    /*
    Helper class that contains the starts, ends, and lengths for each Gap.
     */
    public class Gap {

        int start;
        int end;
        int len;
        int gap_id;
        int gap_type;
        int num_insertions;

        ArrayList<Integer> af_types = new ArrayList<>(Arrays.asList(1, 3, 5, 7, 9)); // gap_types where af should be extended
        ArrayList<Integer> scop_types = new ArrayList<>(Arrays.asList(2, 3, 4, 5, 6, 7, 8, 9)); // gap_types where scop should be extended

        public Gap(int start, int gap_id) {
            this.start = start;
            this.end = -1;
            this.len = 0;
            this.gap_id = gap_id;
            this.gap_type = -1;
        }

        public Gap(int start, int end, int gap_id, int gap_type) {
            this.start = start;
            this.end = end;
            this.len = end - start;
            this.gap_id = gap_id;
            this.gap_type = gap_type;
        }

        public Gap(int start, int gap_id, int gap_type) {
            this.start = start;
            this.end = -1;
            this.len = 0;
            this.gap_id = gap_id;
            this.gap_type = gap_type;
            this.num_insertions = 0;
        }

        public void setEnd(String gap) {
            this.end = this.start + this.len;
            if (gap == "af") {
                if (this.len == 0 && this.start == af_len) {
                    this.start++;
                }
                queryGaps.add(this);
            } else if (gap == "scop") {
                if (this.len == 0 && this.start == fcs.actual_scop_len) {
                    this.start++;
                }
                hitGaps.add(this);
            }
        }

        public void addInsertion() {
            this.num_insertions++;
        }

        public void extend(String gap) {
            if ((gap == "af" && af_types.contains(this.gap_type)) || (gap == "scop" && scop_types.contains(this.gap_type))) {
                this.len++;
            }
        }

        public void setType(int gap_type) {
            this.gap_type = gap_type;
        }

        @Override
        public String toString() {
            return "[" + start + ", " + end + "] " + gap_type;
        }

    }

    public void printEverything() throws Exception {

        System.out.println("af_len: " + af_len);
        System.out.println("scop_len: " + scop_len);
        System.out.println("ini_len: " + ini_len);
        System.out.println("ini_rmsd: " + ini_rmsd);
        System.out.println("opt_equ: " + opt_equ);
        System.out.println("opt_rmsd: " + opt_rmsd);
        System.out.println("chain_rmsd: " + chain_rmsd);
        System.out.println("score: " + score);
        System.out.println("align_len: " + align_len);
        System.out.println("gaps: " + gaps);
        System.out.println("p_val: " + p_val);
        System.out.println("afp_num: " + afp_num);
        System.out.println("identity: " + identity);
        System.out.println("similarity: " + similarity);
        System.out.println("af_start: " + af_start);
        System.out.println("af_length: " + af_length);
        System.out.println("scop_start: " + scop_start);
        System.out.println("scop_length: " + scop_length);
        System.out.println("rotLst: " + Arrays.deepToString(rotLst));
        System.out.println("transLst: " + Arrays.toString(transLst));

        System.out.println("constructNewSeqs:");
        System.out.println(this.afSeq);
        System.out.println(this.alnSeq);
        System.out.println(this.scopSeq);

        System.out.println(fcs.rafMap);

        // System.out.println("astralSeqs:");
        // System.out.println("astralSeqATOM:   " + fcs.astralSeqATOM);
        // System.out.println("astralSeqSEQRES: " + fcs.astralSeqSEQRES);


    }

    public void runComparison() throws Exception {
        
        this.compare();
        this.parseGaps();
        this.archiveAndClear();
        // this.storeData();
        this.storeSQL();
        
    }

    public static void main(String[] args) throws Exception {

//        List<Integer> nodeIds = Arrays.asList(
//                2711103, 2711113, 2711118, 2711128, 2711136,
//                2711140, 2711145, 2711147, 2711152, 2711162,
//
//                2711176, 2711179, 2711183, 2711189, 2711196,
//                2711207, 2711216, 2711255, 2711332, 2711352
//        );
//
////        int idx = Integer.parseInt(args[0]);
//        int idx = 0;
//
//        LocalSQL.connectRW();
//
//        ResultSet rs = LocalSQL.createStatement().executeQuery("SHOW GRANTS");
//        while (rs.next()) {
//            System.out.println(rs.getString(1));
//        }
//
//        for (int i = 0; i < 10; i++) {
//            FatcatPairs.runComparisons(nodeIds.get(idx), i);
//        }

        
        LocalSQL.connectRW();
        int nodeID = LocalSQL.lookupNodeBySid("d1ryha_", 19);
        FatcatPairs.runTestComparisons(nodeID, 2, 1);
    }
}
