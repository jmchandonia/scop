package gov.lbl.scop.util;

import gov.lbl.scop.local.SCOP;
import org.strbio.IO;
import org.strbio.io.*;
import org.strbio.local.*;

import java.nio.charset.*;
import java.nio.file.*;
import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.regex.*;
import org.xml.sax.*;
import org.xml.sax.helpers.*;
import javax.xml.parsers.*;

/**
    Utilities related to using Ensembl's VEP command line.
    Supports RefSeq and chromosomal coordinate inputs

    Usage example:
    java gov.lbl.scop.util.VEP '2:g.97734708C>T' true

    author: Lindsey Guan
*/
public class VEP {

    /* 
        Runs VEP executable on command line with appropriate inputs.
        Loads VEP output file into VariantHit object.
        Returns an array of VariantHits

        @param input is the search query
        @param is_hg38 is whether we're using hg38 (if not, then hg19)
    */
    public static ArrayList<VariantHit> runVEP(String input, boolean is_hg38) throws Exception {
        String genome_build = "GRCh38";
        if (!is_hg38) {
            genome_build = "GRCh37";
        }

        // Location of VEP executable: /mnt/net/ipa.jmcnet/data/lab/app/vep/ensembl-vep/vep
        String VEPExec = SCOP.getProperty("vep.exec");
        // Location of VEP cache files: /mnt/net/ipa.jmcnet/data/lab/app/vep/cache
        String VEPCache = SCOP.getProperty("vep.cache");
        ProcessBuilder pb = new ProcessBuilder(VEPExec,
                                               "-id", input, "-o", "STDOUT", "--warning_file", "STDERR", "--use_given_ref", 
                                               "--no_stats", "--force_overwrite", "--cache", "--merged", "--assembly", genome_build, "--no_headers", "--numbers",
                                               "--dir_cache",
                                               VEPCache);
        pb.redirectErrorStream(true);
        Process p = pb.start();

        // forces function to wait for process to finish (or else execution of process might have incomplete output)
        p.waitFor();

        ArrayList<VariantHit> hits = new ArrayList<>();

        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line = null;
        while ((line = reader.readLine()) != null) {
            VariantHit var_hit = new VariantHit(); 

            Pattern pattern1 = Pattern.compile("WARNING: Unable to parse HGVS notation.*");
            Pattern pattern2 = Pattern.compile(".*?/ensembl-vep/.*");
            Pattern pattern3 = Pattern.compile("ERROR*");
            Matcher matcher1 = pattern1.matcher(line);
            Matcher matcher2 = pattern2.matcher(line);
            Matcher matcher3 = pattern3.matcher(line);
            if (matcher1.find() || matcher2.find() || matcher3.find()) {
                while ((line = reader.readLine()) != null) {
                    // if reference allele doesn't match inputted allele
                    Pattern p_ref_allele = Pattern.compile("MSG: Reference allele extracted from .*? \\(([a-z]|[A-Z])\\) does not match reference allele given by HGVS notation .*? \\(([a-z]|[A-Z])\\)");
                    Matcher m_ref_allele = p_ref_allele.matcher(line);

                    Pattern p_map_coords = Pattern.compile(".* Unable to map the cDNA coordinates .*");
                    Matcher m_map_coords = p_map_coords.matcher(line);
                    
                    if (m_map_coords.find()) {
                        // could not map cDNA coordinates to genomic coordinates
                        var_hit.input = input;
                        var_hit.error_code = 4;
                        hits.add(var_hit);
                        return hits;
                    }

                    if (m_ref_allele.find()) {
                        // found wrong ref allele
                        var_hit.input = input;
                        var_hit.valid_reference_allele = false;
                        var_hit.expected_reference_allele = m_ref_allele.group(1);
                        var_hit.error_code = 1;
                        hits.add(var_hit);
                        return hits;
                    }
                }
                var_hit.input = input;
                var_hit.error_code = 2;
                hits.add(var_hit);
                return hits;
            }

            // Ignore the warning (this warning shows up every time a gene/protein name is used instead of a reference
            // sequence identifier)
            Pattern pattern4 = Pattern.compile("WARNING: Possible invalid use of gene or protein identifier*");
            Matcher matcher4 = pattern4.matcher(line);
            if (matcher4.find()) {
                continue;
            }

            // START OUTPUT PARSING
            String[] lineItems = line.split("\t");
            var_hit.error_code = 0;
            var_hit.valid_reference_allele = true;

            // Uploaded variation (input)
            var_hit.input = lineItems[0];

            // Location (chromosome and genome_position)
            // Allele (allele_change)
            String[] location_split = lineItems[1].split(":");
            var_hit.chromosome = location_split[0];
            var_hit.genome_position = location_split[1];
            // var_hit.allele_change = m.group(2);

            // Gene (gene)
            var_hit.gene = lineItems[3];

            // Feature (transcript)
            if (!lineItems[5].equals("Transcript")) {
                return new ArrayList<>();
            }
            
            if (!lineItems[4].substring(0, 4).equals("ENST")) {
                continue;
            }
            var_hit.transcript = lineItems[4];
            
            // Consequence (consequence)
            var_hit.consequence = lineItems[6].replace("_", " ");

            // cDNA position (cdna_position)
            var_hit.cdna_position = lineItems[7];

            // CDS position (cds_position)
            var_hit.cds_position = lineItems[8];

            // Protein position (protein_position)
            var_hit.protein_position = lineItems[9];

            // Amino acids (protein_change)
            var_hit.protein_change = lineItems[10].replace("/", ">");

            // Codons (codon_change)
            var_hit.codon_change = lineItems[11].replace("/", ">");

            // Existing variation (existing_variation)
            var_hit.existing_variation = lineItems[12];

            // Extra (strand, intron/exon, distance)
            Pattern pattern5 = Pattern.compile("(.*?STRAND=)(-?[0-9])(.*)");
            Matcher matcher5 = pattern5.matcher(lineItems[13]);
            matcher5.find();
            if (matcher5.group(2).equals("1")) {
                var_hit.strand = "+";
            } else {
                var_hit.strand = "-";
            }
            Pattern pattern6_exon = Pattern.compile(".*?(EXON=-?[0-9]+\\/[0-9]+)(.*)");
            Matcher matcher6_exon = pattern6_exon.matcher(lineItems[13]);
            matcher6_exon.find();
            if (matcher6_exon.matches()) {
                var_hit.exon_number = matcher6_exon.group(1);
            }

            Pattern pattern6_intron = Pattern.compile(".*?(INTRON=-?[0-9]+\\/?[0-9]+)(.*)");
            Matcher matcher6_intron = pattern6_intron.matcher(lineItems[13]);
            matcher6_intron.find();
            if (matcher6_intron.matches()) {
                var_hit.intron_number = matcher6_intron.group(1);
            }

            Pattern pattern7 = Pattern.compile("(.*?DISTANCE=)(-?[0-9]+)(.*)");
            Matcher matcher7 = pattern7.matcher(lineItems[13]);
            matcher7.find();
            if (matcher7.matches()) {
                var_hit.gene_distance = matcher7.group(2);
            }

            hits.add(var_hit);
        }
        return hits;
    }

    /*
        Main method for running VEP pipeline on an input and getting an 
        array of VariantHits

        @param args[0] is the input
        @param args[1] is whether we are using hg38 (hg19 if not)
    */
    public static void main(String[] args) {
        try {
            String input = args[0];
            boolean is_hg38 = Boolean.parseBoolean(args[1]);
            System.out.println(runVEP(input, is_hg38));
        } catch (Exception e){
            System.out.println("Unable to run VEP commands");
            e.printStackTrace();
        }
    }
}
