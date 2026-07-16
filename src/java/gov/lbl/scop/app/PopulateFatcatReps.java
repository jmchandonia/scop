package gov.lbl.scop.app;

import java.io.IOException;
import java.util.*;
import java.sql.*;
import gov.lbl.scop.local.LocalSQL;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;
import java.io.FileWriter;
import org.apache.commons.io.FileUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import java.util.concurrent.ForkJoinPool;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.*;
import static gov.lbl.scop.local.LocalSQL.getLatestSCOPRelease;
import static gov.lbl.scop.local.LocalSQL.lookupNode;

public class PopulateFatcatReps {

    /*
    populates the scop_node_fatcat_rep and scop_node_fatcat_cluster tables.
     */

    // /h/makelyan/fatcat/fatcat_output to scop_node_fatcat_rep and scop_node_fatcat_cluster

    public static String FILES_DIR = "/lab/proj/astral/fatcat/2.08/structural_representatives";

    public static ArrayList<String> domains = new ArrayList<>();;
    public static ArrayList<String> tempDomains = new ArrayList<>();;

    public static String alignHierarchy = "([a-z]).(\\d+).(\\d+).(\\d+)"; // class.fold.superfamily.family
    public static Pattern pAlignHierarchy = Pattern.compile(alignHierarchy);
    public static String alignCluster = "([de][\\d\\w]+_*)[,\\]]"; // (?:(\d+): )*([\d\w_]+),*
    public static Pattern pAlignCluster = Pattern.compile(alignCluster);

    public static void readFile(Path filePath) throws Exception {

        File file = filePath.toFile();
        Scanner s = new Scanner(file);

        while (s.hasNextLine()) {
            String line = s.nextLine();

            Matcher match1 = pAlignHierarchy.matcher(line); // a.1.2.3 line
            Matcher match2 = pAlignCluster.matcher(line); // domain list lines

            if (line.contains(".") && !domains.isEmpty()) {
                inputCluster();
            } else if (match2.find()) {
                // match domains, and group number (if applicable for this line)
                if (line.contains(":") && !domains.isEmpty()) {
                    inputCluster();
                }
                domains.add(match2.group(1)); // need this bc already called match2.find()
                while (match2.find()) {
                    domains.add(match2.group(1));
                }
            }
        }
    }

    public static void inputCluster() throws Exception {

        // put the first domain in the list into scop_node_fatcat_rep
        // using the id from that, put the rest of the domains in the ArrayList into scop_node_fatcat_cluster

        PreparedStatement stmt = LocalSQL.prepareStatement("insert into scop_node_fatcat_rep (node_id) values " +
                "(?)", Statement.RETURN_GENERATED_KEYS);

        // make sure the domain stored inside scop_node_fatcat_rep actually exists
        int n = 0;
        int SCOPRelease = getLatestSCOPRelease(false);
        int node_id = 0;
        while (!(node_id > 0) && (n < domains.size())) {
            node_id = lookupNode(domains.get(n), SCOPRelease);
            n++;
        }
        if (node_id == 0) { return; } // you went through the entire cluster and no node_id exists --> don't try to insert into the table

        System.out.println("Cluster " + node_id);
        stmt.setInt(1, node_id);
        stmt.executeUpdate();

        // get auto-incremented id from the entry
        ResultSet generatedKeys = stmt.getGeneratedKeys();
        int rep_id = -1;
        if (generatedKeys.next()) { rep_id = generatedKeys.getInt(1); }
        stmt.close();

        for (int i = n + 1; i < domains.size(); i++) {
            node_id = lookupNode(domains.get(i), SCOPRelease);
            if (node_id > 0) {
                PreparedStatement stmt2 = LocalSQL.prepareStatement("insert into scop_node_fatcat_cluster (rep_id, node_id) values " +
                        "(?,?)");
                stmt2.setInt(1, rep_id);
                stmt2.setInt(2, node_id);

                stmt2.executeUpdate();
                stmt2.close();
            }
        }

        domains.clear();
    }

    public static boolean tablesEmpty() throws SQLException {
        String query1 = "SELECT COUNT(*) FROM scop_node_fatcat_rep";
        PreparedStatement stmt1 = LocalSQL.prepareStatement(query1);
        ResultSet rs1 = stmt1.executeQuery();
        boolean isRepTableEmpty = false;

        if (rs1.next()) {
            isRepTableEmpty = rs1.getInt(1) == 0;
        }

        // Check if the second table is empty
        String query2 = "SELECT COUNT(*) FROM scop_node_fatcat_cluster";
        PreparedStatement stmt2 = LocalSQL.prepareStatement(query2);
        ResultSet rs2 = stmt2.executeQuery();
        boolean isClusterTableEmpty = false;

        if (rs2.next()) {
            isClusterTableEmpty = rs2.getInt(1) == 0;
        }

        rs1.close();
        stmt1.close();
        rs2.close();
        stmt2.close();

        return isRepTableEmpty && isClusterTableEmpty;
    }

    public static void readAllFiles() throws Exception {

        if (!tablesEmpty()) {
            System.out.println("tables already populated, aborting");
            return;
        }

        // go through all the .txt files in the directory.
        Path dir = Paths.get(FILES_DIR);
        DirectoryStream<Path> stream = Files.newDirectoryStream(dir);
        for (Path entry : stream) {
            if (Files.isRegularFile(entry)) {
                readFile(entry);
                System.out.println(entry + " completed ---------------------------------------------------------------");
            }
        }
        System.out.println("process completed");
    }

    public static void main(String[] args) throws Exception {
        LocalSQL.connectRW();
        readAllFiles();
    }

}
