package gov.lbl.scop.app;

import java.sql.*;
import gov.lbl.scop.local.LocalSQL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.lang.String;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;


import org.apache.log4j.Logger;
import org.apache.log4j.Level;

import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.mllib.stat.KernelDensity;
import org.apache.spark.sql.execution.columnar.DOUBLE;

import java.io.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class NewEntryConsistencyCheck{
    /**for testing purposes**/
    final static int ERROR = -1;
    /**if the new entry length is inconsistent with the rest of the family member, that is, having a low prob in KDE**/
    final static int RARE_LEN = 1;
    /**if the new entry length has very different atom and seqres record**/
    final static int DIFF_LEN = 2;
    /**if the new entry is greater than the max length by 20 or smaller than the smallest length by 20**/
    final static int EXTR_LEN = 4;
    /**if the original family is already having significant length variation**/
    final static int LEN_VARIATION = 8;
    /**if the family (including the new entry) has very different atom and seqres record**/
    final static int ATOM_VS_SEQRES = 16;
    /**if the word repeat is on the family name or in it's parent classification name
    UPDATE: if the entire original family is already inconsistent such as dn or nf or re**/
    final static int ORG_INCONSISTENT = 32;
    /**the cutoff proability of wether a length is inconsistent with the rest of the members **/
    final static double MIN_PORB = 0.0005;


    /**
        Check whether the original family is inconsistent (containing multiple peaks in the kde of seqres length),
        and whether the new entry length is consistent with the orginal family by checking the prbabily estimate greater than min_prob.
        Take in an arraylist of original family SEQRES length and a double of new SEQRES length
    */

    public static int lenVarWithkde(ArrayList<Double> domainLenS, double seqres) {
        int result = 0;

        //create "linspace" array, samples to draw from later
        double minVal = domainLenS.get(0);
        double maxVal = domainLenS.get(domainLenS.size() - 1);
        double start = Math.max(0, minVal - 30);
        double end = maxVal + 30;
        double step = 10;
        int n = (int) ((int) (end - start) * step);
        double[] array_d = new double[n];
        for (int i = 0; i < n; i++) {
            array_d[i] = start + ((double) i) / step;
        }
        //System.out.println(Arrays.toString(array_d));

        //start KDE

        Logger.getLogger("org").setLevel(Level.OFF);
        Logger.getLogger("akka").setLevel(Level.OFF);        
        SparkConf conf = new SparkConf().setAppName("JavaKernelDensityEstimation").setMaster("local[2]").set("spark.executor.memory", "1g");
        JavaSparkContext jsc = new JavaSparkContext(conf);

        //an RDD of sample data
        JavaRDD<Double> data = jsc.parallelize(domainLenS);

        //default bd
        //KernelDensity kd = new KernelDensity().setSample(data).setBandwidth(5.0);
        KernelDensity kd = new KernelDensity().setSample(data);

        //draw the KDE curve
        double[] densities = kd.estimate(array_d);
        //System.out.println(Arrays.toString(densities));

        //check the new entry
        double[] new_length = new double[]{seqres};
        double[] prob = kd.estimate(new_length);
        if (prob[0] <= MIN_PORB) {
            result += RARE_LEN;
        }
        jsc.stop();

        //getting diff to find peaks/shoulders
        double[] firstDeriv = new double[n - 1];
        for (int i = 0; i < n - 1; i++) {
            firstDeriv[i] = densities[i + 1] - densities[i];
        }
        //System.out.println(Arrays.toString(firstDeriv));
        double[] secondDeriv = new double[n - 2];
        for (int i = 0; i < n - 2; i++) {
            secondDeriv[i] = firstDeriv[i + 1] - firstDeriv[i];
        }
        //System.out.println(Arrays.toString(secondDeriv));

        //find local max and shoulders
        ArrayList<Double> peaks = new ArrayList<Double>();
        for (int i = 1; i < n - 3; i++) {
            if (firstDeriv[i] > 0 && firstDeriv[i + 1] < 0) {
                peaks.add(array_d[i + 2]);
            } else if (firstDeriv[i] > 0 && secondDeriv[i] < 0 && secondDeriv[i + 1] > 0) {
                peaks.add(array_d[i + 2]);
            } else if (firstDeriv[i] < 0 && secondDeriv[i] > 0 && secondDeriv[i + 1] < 0) {
                peaks.add(array_d[i + 2]);
            }
        }
        //add the small length back in if not already accounted
        if (minVal * 1.5 < peaks.get(0)) {
            peaks.add(0, minVal);
        }
        //System.out.println(peaks);

        //flagged families with peaks/shoulder with diff factor >1.6
        if (peaks.size() != 1 && peaks.get(0) != 0) {
            if ((peaks.get(peaks.size() - 1) / peaks.get(0)) > 1.6) {
                //System.out.println(fam_name + " true");
                //return 1;
                result += LEN_VARIATION;
            } /**else {
                System.out.println(fam_name + " false");
            }**/
        } /**else {
            //System.out.println(fam_name + " false");
        }**/

        //if the new entry length is an extreme length
        if ((seqres > maxVal + 20) | (seqres < minVal - 20)) {
            result += EXTR_LEN;
        }

        return result;
    }

    /**
        Check whether the original family is inconsistent (if the longest length / shortest length ration is greater than 1.5),
        and whether the new entry length is consistent with the orginal family by if the new length is +- 10 res or 1.1x within some member
        the small family, member <=5 version of kde.
        Take in an arraylist of original family SEQRES length and a double of new SEQRES length
    */
    public static int lenVarWithoutKDE(ArrayList<Double> domainLenS, double seqres) {
        int result = 0;

        /**for families with 5 or less members, where KDE is meaningless**/
        double minVal = domainLenS.get(0);
        double maxVal = domainLenS.get(domainLenS.size() - 1);
        if (minVal != 0 && maxVal / minVal > 1.5) {
            //System.out.println(fam_name + " true");
            result += LEN_VARIATION;
        } /**else {
            System.out.println(fam_name + " false");
        }**/
        
        /**Test this part**/
        boolean rare = true;
        for (double l : domainLenS) {
            if (((seqres > l-10) && (seqres < l+10)) || ((seqres > l*0.9) && (seqres < l*1.1)) ){
                rare = false;
                break;
            }
        }
        if (rare) {
            result += RARE_LEN;
        }

        if ((seqres > maxVal + 20) || (seqres < minVal - 20)) {
            result += EXTR_LEN;
        }

        return result;
    }

    /**
        Compare ATOM and SEQRES record of the original members of the family
        Take in 2 arraylists of SQRES and ATOM length (ordered pair)
    */

    public static int compareOrg(ArrayList<Double> domainLenS, ArrayList<Double> domainLenA) {
        for (int i = 0; i < domainLenS.size(); i++) {
            double s = domainLenS.get(i);
            double a = domainLenA.get(i);
            if (((s * 0.8) > a) && ((s - a) > 15)) {
                //System.out.println("diff " + s + " " + a);
                return ATOM_VS_SEQRES;
            }
        }
        return 0;
    }

    public static int compareNew(double seqres, double atom) {
        /**
         * Compare ATOM and SEQRES record of the new entry
         * @param seqres the new member's seqres length
         * @param atom the new member's atom length
         * @return inconsistent bit map code as defined above
         */
        if (((seqres * 0.8) > atom) && ((seqres - atom) > 15)) {
                //System.out.println("diff " + s + " " + a);
                return DIFF_LEN;
        } else {
            return 0;
        }
    }

    public static int repeatNameCheck(String family, int release_id) throws SQLException {
         /** Check if a fmialy contains the word repeat in it's or it's parents' names
         * @param family the family sccs (i.e. "a.2.3.1")
         * @return inconsistent bit map code as defined above

        /**the orignal seqres query for the family a.1.1.1 for example
         select sccs, description from scop_node where release_id = 18 and level_id=2 and sccs='a';
         select sccs, description from scop_node where release_id = 18 and level_id=3 and sccs='a.1';
         select sccs, description from scop_node where release_id = 18 and level_id=4 and sccs='a.1.1';
         select sccs, description from scop_node where release_id = 18 and level_id=5 and sccs='a.1.1.1';*/

        //get the parent sccs code
        String class_sccs = family.substring(0, 1);
        int index = family.indexOf(".", 2);
        String fold_sccs = family.substring(0, index);
        index = family.indexOf(".", index+1);
        String super_sccs = family.substring(0, index);
        ArrayList<String> names = new ArrayList<String>();

        Statement stmt = LocalSQL.createStatement();
        ResultSet rs_length = stmt.executeQuery("select description from scop_node s where s.release_id =" + release_id + " and s.level_id = 2 and s.sccs='" + class_sccs + "';");
        if(rs_length.next()){
            names.add(rs_length.getString(1));
        }
        rs_length = stmt.executeQuery("select description from scop_node s where s.release_id =" + release_id + " and s.level_id = 3 and s.sccs='" + fold_sccs + "';");
        if(rs_length.next()){
            names.add(rs_length.getString(1));
        }
        rs_length = stmt.executeQuery("select description from scop_node s where s.release_id =" + release_id + " and s.level_id = 4 and s.sccs='" + super_sccs + "';");
        if(rs_length.next()){
            names.add(rs_length.getString(1));
        }
        rs_length = stmt.executeQuery("select description from scop_node s where s.release_id =" + release_id + " and s.level_id = 5 and s.sccs='" + family + "';");
        if(rs_length.next()){
            names.add(rs_length.getString(1));
        }
        
        for (String n : names) {
            if (n.contains("repeat")) {
                return ORG_INCONSISTENT;
            }
        }
        return 0;

    }

    /**
        Read the sid, seqres, atom length into the given arraylist of the specied family (in sccs)
    */

    private static int queryData(String family, ArrayList<Double> domainLenS, ArrayList<Double> domainLenA, ArrayList<String> domainName, int release_id) throws SQLException {

        Statement stmt = LocalSQL.createStatement();
        ResultSet rs_length =
                stmt.executeQuery("select distinct s.sid, length(a.seq) as seqres, length(b.seq) as atom from astral_seq a, astral_seq b, astral_domain d, astral_domain p, scop_node s"
                    + " where s.release_id =" + release_id + " and s.level_id = 8 and s.sccs='" + family + "'"
                    + " and s.id = d.node_id and d.seq_id  = a.id and (d.style_id = 1 or d.style_id = 3) and d.source_id = 2"
                    + " and s.id = p.node_id and p.seq_id  = b.id and (p.style_id = 1 or p.style_id = 3) and p.source_id = 1"
                    + " order by length(a.seq);");
        while(rs_length.next()){
            //System.out.println(rs_length.getString(1));
            domainName.add(rs_length.getString(1));
            domainLenS.add(rs_length.getDouble(2));
            domainLenA.add(rs_length.getDouble(3));
        }
        if (domainLenS.size() == 0) {
            return -1;
        } else {
            return 0;
        }
    }

    /**
        Read the sid, seqres, atom length into the given arraylist of the specied family (in sccs)
        Keep only the consistent entry (not present in scope_node_inconsistent)
    */
    public static int queryCONSISTData(String family, ArrayList<Double> domainLenS, ArrayList<Double> domainLenA, ArrayList<String> domainName, int release_id) throws SQLException {
        //System.out.println(family);
        queryData(family, domainLenS, domainLenA, domainName, release_id);
        Statement stmt2 = LocalSQL.createStatement();
        ResultSet rs2 =
                stmt2.executeQuery("select distinct s.sid from scop_node as s left join scop_node_inconsistent as i on s.id=i.node_id" 
                    + " where s.release_id=" + release_id + " and s.level_id=8 and s.sccs='" + family + "'"
                    + " and i.node_id is not NULL;");
        while(rs2.next()){
            String sid = rs2.getString(1);
            //System.out.println(sid);
            int index = domainName.indexOf(sid);
            domainName.remove(index);
            domainLenS.remove(index);
            domainLenA.remove(index);
        }
        if (domainLenS.size() == 0) {
            return -1;
        } else {
            return 0;
        }
    }

    /**
        Check if a family is already inconsistent (without new entry) by looking records in scop_node_inconsistent and scop_node_repeat
    */
    public static int originalConsistent(String family, int release_id) throws SQLException {
        //get the parent sccs code
        String class_sccs = family.substring(0, 1);
        int index = family.indexOf(".", 2);
        String fold_sccs = family.substring(0, index);
        index = family.indexOf(".", index+1);
        String super_sccs = family.substring(0, index);
        ArrayList<String> names = new ArrayList<String>();

        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select sccs from scop_node s, scop_node_inconsistent i where s.release_id =" + release_id 
            + " and (s.sccs='" + class_sccs + "' or s.sccs='" + fold_sccs + "' or s.sccs='" + super_sccs + "' or (s.sccs='" + family + "' and level_id=5))"
            + " and s.id=i.node_id;");
        if (rs.next()) {
            return ORG_INCONSISTENT;
        } else {
            Statement stmt2 = LocalSQL.createStatement();
            ResultSet rs2 = stmt2.executeQuery("select * from scop_node_repeat r, scop_node s where s.release_id =" + release_id 
                +" and r.node_id=s.id and s.sccs='" + family +"';");
            if(rs2.next()){
                return ORG_INCONSISTENT;
            }
            return 0;
        }
    }

    /**
        See how many families in the release_id will be flagged with length variation and ATOM vs SEQRES check
    */
    public static ArrayList<String> testMulFamSQL(int release_id) throws SQLException, IOException{
        ArrayList<String> allFam = new ArrayList<String>();
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select distinct sccs from scop_node where (level_id = 4 and strcmp('h', substring(sccs, 1, 1)) > 0 and release_id =" + release_id + ");");
        
        while (rs.next()) {
            String sfSCCS = rs.getString(1);
            Statement stmt_fam = LocalSQL.createStatement();
            ResultSet rs_fam = stmt_fam.executeQuery("select distinct sccs from scop_node where sccs like '" + sfSCCS + ".%' and sccs not like '%.0' and release_id =" + release_id + ");");
            
            // iterate through families
            while (rs_fam.next()) {
                allFam.add(rs_fam.getString(1));
            }
        }
            
        ArrayList<String> flagged = new ArrayList<String>();
        //iterate through all families
        for (String family : allFam) {
            //get data
            ArrayList<Double> domainLenS = new ArrayList<Double>();
            ArrayList<Double> domainLenA = new ArrayList<Double>();
            ArrayList<String> domainName = new ArrayList<String>();

            queryData(family, domainLenS, domainLenA, domainName, LocalSQL.getLatestSCOPRelease(false));
            //System.out.println(domainName);
            //System.out.println(domainLenS);
            //System.out.println(domainLenA);

            int result = 0;
            if (domainLenS.size() > 5) {
                result += lenVarWithkde(domainLenS, 0);
            } else {
                result += lenVarWithoutKDE(domainLenS, 0);
            }
        
            result += compareOrg(domainLenS, domainLenA);
            result += repeatNameCheck(family, release_id);
            
            if ((result != RARE_LEN) && (result != EXTR_LEN) && (result != RARE_LEN + EXTR_LEN) && (result >1)) {
                flagged.add(family);
            }
        }
        //System.out.println(flagged);
        System.out.println("Number of families flagged: " + flagged.size());
        
        File file = new File("flagged.txt");
        file.createNewFile();
        FileWriter writer = new FileWriter(file.getAbsoluteFile());
        for (String f : flagged) {
            writer.write(f + "\n");
        }
        writer.close();

        return flagged;
    }

    /**
        Perform new entry consistency check on the new manual entry in release 18 again original release 17
    */
    public static void testMulNewEntry18(String file) throws IOException, FileNotFoundException, SQLException{
        ArrayList<String> all_newEntryFam = new ArrayList<String>();
        ArrayList<String> all_newEntryName = new ArrayList<String>();
        ArrayList<String> all_newEntrySunID = new ArrayList<String>();

        ArrayList<String> newEntryFam = new ArrayList<String>();
        ArrayList<String> newEntryName = new ArrayList<String>();
        ArrayList<String> newEntrySunID = new ArrayList<String>();
        ArrayList<Integer> newEntryResult = new ArrayList<Integer>();
        
        File myObj = new File(file);
        Scanner myReader = new Scanner(myObj);
        while (myReader.hasNextLine()) {
            String data = myReader.nextLine();
            String[] d = data.split("\\s+");
            all_newEntryFam.add(d[1]);
            all_newEntryName.add(d[2]);
            all_newEntrySunID.add(d[0]);
        }
        System.out.println(all_newEntryName.size());
        myReader.close();

        ArrayList<Integer> list = new ArrayList<Integer>();
        //randomly check 200 of them
        for (int i=0; i < all_newEntrySunID.size(); i++) {
            list.add(new Integer(i));
        }

        /**Collections.shuffle(list);
        for (int j=0; j < 20; j++) {**/
        for (int i=0; i < all_newEntrySunID.size(); i++) {
            /**int i = list.get(j);**/
            String sunid = all_newEntrySunID.get(i);
            Statement stmt = LocalSQL.createStatement();
            ResultSet rs_length =
                    stmt.executeQuery("select length(a.seq) as seqres, length(b.seq) as atom from astral_seq a, astral_seq b, astral_domain d, astral_domain p, scop_node s, scop_curation_type c"
                        + " where s.release_id =" + "18" + " and s.sunid=" + sunid
                        + " and s.id = d.node_id and d.seq_id  = a.id and (d.style_id = 1 or d.style_id = 3) and d.source_id = 2"
                        + " and s.id = p.node_id and p.seq_id  = b.id and (p.style_id = 1 or p.style_id = 3) and p.source_id = 1"
                        + " and s.curation_type_id = c.id and c.description = 'Human-curated';");
            if(!rs_length.next()) {
                System.out.println("\n\n\n***************\n" + sunid + " " + all_newEntryFam.get(i) + " " + all_newEntryName.get(i) + " not found in released 18 \n***************\n\n\n");
                continue;
            }
            
            double seqres = rs_length.getDouble(1);
            double atom = rs_length.getDouble(2);
            System.out.println("\n\n\n***************\n" + sunid + " " + all_newEntryFam.get(i) + " " + all_newEntryName.get(i) + " " + seqres + " " + atom + "\n***************\n\n\n");

            newEntryFam.add(all_newEntryFam.get(i));
            newEntryName.add(all_newEntryName.get(i));
            newEntrySunID.add(all_newEntrySunID.get(i));

            newEntryResult.add(newEntryCheckSQL(all_newEntryFam.get(i), seqres, atom, 17));
        }

        File file_out = new File("test_18.txt");
        file_out.createNewFile();
        FileWriter writer = new FileWriter(file_out.getAbsoluteFile());
        for (int i = 0; i < newEntryFam.size(); i++) {
            writer.write(newEntryFam.get(i) + " " + newEntryName.get(i) + " " + newEntryResult.get(i) + "\n");
        }    
        writer.close();
    }

    /**
        Check a new entry
        Take the family sccs, new entry seqres, atom lenght, and the original release_id
        Includes length variation, seqres vs atom, original inconsistent check, repeat name check
    */
    public static int newEntryCheckSQL(String family, double seqres, double atom, int release_id) throws SQLException, IOException{
        int result = 0;
        ArrayList<Double> domainLenS = new ArrayList<Double>();
        ArrayList<Double> domainLenA = new ArrayList<Double>();
        ArrayList<String> domainName = new ArrayList<String>();
        int sucess = queryCONSISTData(family, domainLenS, domainLenA, domainName, release_id);
        if (sucess == -1) {
            System.out.println("Query failed, this may be a new family");
            return ERROR;
        }
        result += originalConsistent(family, release_id);
        if (domainLenS.size() > 5) {
            result += lenVarWithkde(domainLenS, seqres);
        } else {
            result += lenVarWithoutKDE(domainLenS, seqres);
        }
        result += compareOrg(domainLenS, domainLenA);
        result += compareNew(seqres, atom);
        result += repeatNameCheck(family, release_id);
        return result;
    }

    /**
        Local test
        Get domain length data from local file (insetad of sql query)
        The file should be in the format: one domain per line, each line contains domain name, seqres length, atom length separated by space
    */
    public static void getLocalData(String file, ArrayList<Double> domainLenS, ArrayList<Double> domainLenA, ArrayList<String> domainName) throws FileNotFoundException{
         File myObj = new File(file);
         Scanner myReader = new Scanner(myObj);
         while (myReader.hasNextLine()) {
             String data = myReader.nextLine();
             String[] d = data.split("\\s+");
             domainName.add(d[0]);
             domainLenS.add(Double.parseDouble(d[1]));
             domainLenA.add(Double.parseDouble(d[2]));
         }
         myReader.close();
    }

    /**
        Local test
        See how many families will be flagged with length variation and ATOM vs SEQRES check alone
        file contains all families to be tested
    */
    public static ArrayList<String> testMulFamLocal(String file) throws FileNotFoundException, IOException{
        //get all files(families)
        ArrayList<String> famPath = new ArrayList<String>();
        try {
            File myObj = new File(file);
            Scanner myReader = new Scanner(myObj);
            while (myReader.hasNextLine()) {
                String data = myReader.nextLine();
                famPath.add(data);
            }
            myReader.close();
        } catch (FileNotFoundException e) {
            System.out.println("An error occurred for " + file);
            e.printStackTrace();
        }
        //System.out.println(famPath);

        ArrayList<String> flagged = new ArrayList<String>();
        //iterate through all families
        for (int k = 0; k < famPath.size(); k++) {
            file = famPath.get(k);
            //read data from file
            ArrayList<Double> domainLenS = new ArrayList<Double>();
            ArrayList<Double> domainLenA = new ArrayList<Double>();
            ArrayList<String> domainName = new ArrayList<String>();

            getLocalData(file, domainLenS, domainLenA, domainName);
            //System.out.println(domainName);
            //System.out.println(domainLenS);
            //System.out.println(domainLenA);

            String fam_name = file.substring(file.indexOf("/", 40) + 1, file.length() - 4);
            int result;
            int result_2;
            if (domainLenS.size() > 5) {
                result = lenVarWithkde(domainLenS, 0);
            } else {
                result = lenVarWithoutKDE(domainLenS, 0);
            }
            //result = 0; for testing purposes only
            result_2 = compareOrg(domainLenS, domainLenA);
            //result_2 = 0; for testing purposes only
            if ((result != RARE_LEN) && (result != EXTR_LEN) && (result != RARE_LEN + EXTR_LEN) && (result >1)) {
                flagged.add(fam_name);
                System.out.println("*******************\n" + fam_name + " " + result + "\n*******************");
            }
        }
        //System.out.println(flagged);
        System.out.println("Number of families flagged: " + flagged.size());

        File file_to = new File("/Users/shiangyilin/InconsistentFamilies/flagged.txt");
        file_to.createNewFile();
        FileWriter writer = new FileWriter(file_to.getAbsoluteFile());
        for (String f : flagged) {
            writer.write(f + "\n");
        }
        writer.close();

        return flagged;
    }
    
    /**
        Check a new entry
        Take the family sccs, new entry seqres, atom lenght, and the original release_id
        Includes length variation, seqres vs atom
    */
    public static int newEntryCheckLocal(String family, double seqres, double atom) throws FileNotFoundException{
        int result = 0;
        ArrayList<Double> domainLenS = new ArrayList<Double>();
        ArrayList<Double> domainLenA = new ArrayList<Double>();
        ArrayList<String> domainName = new ArrayList<String>();
        getLocalData(family, domainLenS, domainLenA, domainName);
        if (domainLenS.size() > 5) {
            result += lenVarWithkde(domainLenS, seqres);
        } else {
            result += lenVarWithoutKDE(domainLenS, seqres);
        }
        result += compareOrg(domainLenS, domainLenA);
        result += compareNew(seqres, atom);
        return result;
    }

    public static void printMessages(int code) {
        boolean fine = true;
        System.out.println("inconsistent code is: " + code);

        if (code == -1) {
            System.out.println("an error occurred");
            return;
        } if ((code & RARE_LEN) > 0) {
            System.out.println("this new entry is likely to be inconsistent");
            fine=false;
        } if ((code & DIFF_LEN) > 0) {
            System.out.println("this new entry may be a fragment (ATOM record is significantly less than SEQRES record)");
            fine=false;
        } if ((code & EXTR_LEN) > 0) {
            System.out.println("this new entry may contain additional structures (SEQRES record is significantly longer than other SEQRES records in the family) or be a fragment (SEQRES record is significantly shorter than other SEQRES records in the family)");
            fine=false;
        } if ((code & LEN_VARIATION) > 0) {
            System.out.println("this family has significantly length variation already, cannot tell if this new entry is inconsistent");
            fine=false;
        }if ((code & ORG_INCONSISTENT)>0) {
            System.out.println("this family is already inconsistent");
            fine=false;
        } if (fine) {
            System.out.println("it looks good");
        }
    }


    public static void main(String args[]) throws SQLException, IOException {
        /**input are family sccs code or local file path, new entry's seqres lenght, new entry's atom length**/
        try {
            LocalSQL.connectRW();
            //check a new entry
            if (args[0].equals("checkNewEntry")) {
                int scopReleaseID = LocalSQL.getLatestSCOPRelease(false);
                int result = newEntryCheckSQL(args[1], Double.parseDouble(args[2]), Double.parseDouble(args[3]), scopReleaseID);
                printMessages(result);
            }
            //check all current families
            else if (args[0].equals("checkAll")) {
                int scopReleaseID = LocalSQL.getLatestSCOPRelease(false);
                testMulFamSQL(LocalSQL.getLatestSCOPRelease(false));
            }
            //check a new entry against local file
            else if (args[0].equals("checkNewEntryLocal")) {
                int result = newEntryCheckLocal(args[1], Double.parseDouble(args[2]), Double.parseDouble(args[3]));
                printMessages(result);
            }
            //check all families in given file
            else if (args[0].equals("checkAllLocal")) {
                testMulFamLocal(args[1]);
            } 
            //check all new entries added between release 17 and 18
            else if (args[0].equals("test18")) {
                testMulNewEntry18("/mnt/net/ipa.jmcnet/data/h/slin/test_data_fake/diff_release.txt");
            }
            else {
                System.out.println("wrong format, try again");
            }
            
        } catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
        
    }
}

