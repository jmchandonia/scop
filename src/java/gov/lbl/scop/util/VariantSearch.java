package gov.lbl.scop.util;

import gov.lbl.scop.local.LocalSQL;
import gov.lbl.scop.local.SCOP;
import org.strbio.IO;
import org.strbio.io.*;
import org.strbio.local.*;

import java.nio.charset.*;
import java.nio.file.*;
import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.ArrayList;
import java.util.regex.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

/**
    Java pipeline for searching for a variant in SCOPe
    
    Usage:
    java gov.lbl.scop.util.VariantSearch '2:g.97734708C>T' true

    author: Lindsey Guan
*/
public class VariantSearch {
    /**
     * Runs Transvar search pipeline with the given query.
     * Modifies transvar_store and variant_store tables.
     *  
     * NOTE: This is not used in the variant search pipeline.
     * 
     * @param input_query the search query

     JMC: Transvar no longer used, 2021-09-20
     
    public static ArrayList<VariantHit> searchTransvar(String input_query) {
        try {
            ArrayList<VariantHit> hits = Transvar.runTransvar(input_query);
            return hits;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<VariantHit>();
    }
     */ 

    /**
     * Maps a VariantHit to a PDB structure.
     * This function will set relevant variables in `variant_hit`.
     * 
     * @param variant_hit VariantHit object that still needs mapping to PDB structure.
     */
    public static void mapToPDB(VariantHit variant_hit,
                                int scopReleaseID) throws SQLException {
        // Currently only supports maps from Ensembl transcripts to PDB
        variant_hit.structure_maps = new ArrayList<VariantStructure>();
        variant_hit.unp_accession = "-";

        if (!variant_hit.transcript.matches("ENST[0-9]*")) {
            return;
        }

        // 1. Query canonical uniprot accession based on Ensembl gene and transcript ID

        if (variant_hit.transcript.matches("ENST[0-9]*") && variant_hit.gene.matches("ENSG[0-9]*")) {
            Statement stmt2 = LocalSQL.createStatement();
            String query = "select isoform from uniprot_ensembl where ensembl_gene = '" + variant_hit.gene + "' and ensembl_trs = '" + variant_hit.transcript + "';";
            ResultSet uniprot_accs = stmt2.executeQuery(query);
            if (uniprot_accs.next()) {
                Pattern p = Pattern.compile("\\[([a-zA-Z0-9]+)-[0-9]+\\]");
                Matcher m = p.matcher(uniprot_accs.getString(1));
                m.find();
                variant_hit.unp_accession = m.group(1);
            } else {
                variant_hit.error_code = 5;
            }
            uniprot_accs.close();
            
            if (variant_hit.protein_position.matches("([0-9]+$)|([0-9]+-[0-9]+)") && variant_hit.unp_accession != null) {
                Pattern p = Pattern.compile("([0-9]+)-([0-9]+)");
                Matcher m = p.matcher(variant_hit.protein_position);
                boolean protein_position_is_range = false;
                if (m.find()) {
                    variant_hit.protein_position = m.group(1);
                    variant_hit.protein_position_end = m.group(2);
                    protein_position_is_range = true;
                }
                
                int unp_position = Integer.parseInt(variant_hit.protein_position);
                int unp_position_end = -1;
                if (protein_position_is_range) {
                    unp_position_end = Integer.parseInt(variant_hit.protein_position_end);
                }

                // 2. Query pdb_chain_sift using the canonical UniProt accession -> will return PDB id and aligned indices
                Statement stmt4 = LocalSQL.createStatement();
                ResultSet sifts = stmt4.executeQuery("select * from pdb_chain_uniprot_sift where SP_PRIMARY = '" + variant_hit.unp_accession + "'");
                List<Map<String, Object>> sifts_list = resultSetToArrayList(sifts);

                if (sifts_list.size() == 0) {
                    variant_hit.error_code = 3;
                }

                // 3. Get PDB index mapping
                for (Map<String, Object> s : sifts_list) {
                    String pdb_chain = (String) s.get("CHAIN");
                    String pdb_id = (String) s.get("PDB");

                    // we subtract 1 again since UNP is 1-indexed
                    int pdb_res = unp_position - (int) s.get("SP_BEG") + (int) s.get("RES_BEG") - 1;
                    int pdb_res_end = unp_position_end - (int) s.get("SP_BEG") + (int) s.get("RES_BEG") - 1;

                    // in this case, the protein index does not map within the alignment
                    // region for the associated PDB structure
                    if (pdb_res < (int) s.get("RES_BEG") || pdb_res > (int) s.get("RES_END")) {
                        if (protein_position_is_range && (pdb_res_end < (int) s.get("RES_BEG") || pdb_res_end > (int) s.get("RES_END"))) {
                            continue;
                        }
                        continue;
                    }
                    
                    VariantStructure pdb_info = new VariantStructure();
                    pdb_info.nearest_atom_bwd = -1;
                    pdb_info.nearest_atom_fwd = -1;
                    pdb_info.pdb_id = pdb_id;
                    pdb_info.pdb_chain = pdb_chain;
                    
                    // Get pdb_chain_id
                    Statement stmt5 = LocalSQL.createStatement();
                    int pdb_chain_id;
                    int pdb_entry_id; // used for checking aerospaci
                    String pdb_path;

                    ResultSet rs5 = stmt5.executeQuery("select c.id, e.id, l.pdb_path from pdb_entry e, pdb_release r, pdb_chain c, pdb_local l where e.id = r.pdb_entry_id and r.id = c.pdb_release_id and r.id = l.pdb_release_id and e.code = '" + pdb_id + "' and c.chain = '" + pdb_chain + "' order by r.id desc limit 1;");
                    if (rs5.next()) {
                        pdb_chain_id = rs5.getInt(1);
                        pdb_entry_id = rs5.getInt(2);
                        pdb_path = rs5.getString(3);

                        File f = new File(pdb_path);
                        if (!f.exists())
                            pdb_path = pdb_path.replace("/lab/db/pdb","/data/scop/pdb");

                        pdb_info.pdb_chain_id = pdb_chain_id;
                        pdb_info.pdb_path = pdb_path;

                        // Get PDB residue index mapped from RAF
                        Statement stmt6 = LocalSQL.createStatement();
                        // ResultSet rs6 = stmt6.executeQuery("select substring(line, 39 + ("+Integer.toString(pdb_res)+"*7), 4) as pdb_index, floor(length(substring(line, 39)) / 7) as length, substring(line, 39 + ("+Integer.toString(pdb_res)+"*7) + 5, 1) as atom_res, raf_get_body(id) from raf where pdb_chain_id = " + Integer.toString(pdb_chain_id) + " and raf_version_id = 3 and first_release_id<="+scopReleaseID+" and last_release_id>="+scopReleaseID);
                        ResultSet rs6 = stmt6.executeQuery("select substring(line, 39 + ("+Integer.toString(pdb_res)+"*7), 4) as pdb_index, floor(length(substring(line, 39)) / 7) as length, substring(line, 39 + ("+Integer.toString(pdb_res)+"*7) + 5, 1) as atom_res, raf_get_body(id) from raf where pdb_chain_id = " + Integer.toString(pdb_chain_id) + " and raf_version_id = 3");
                        if (rs6.next()) {
                            String mapped_pdb_res_str = rs6.getString(1).trim();
                            String atom_res = rs6.getString(3);
                            if (!mapped_pdb_res_str.equals("")) {
                                pdb_info.pdb_res = Integer.parseInt(mapped_pdb_res_str);
                                pdb_info.in_range = true;
                                if (!atom_res.equals(".")) {
                                    pdb_info.not_missing_res = true;
                                } else {
                                    pdb_info.not_missing_res = false;
                                    String raf_body = rs6.getString(4);
                                    int pdb_raf_index = RAF.indexOf(raf_body, mapped_pdb_res_str, true);
                                    pdb_info.nearest_atom_bwd = Integer.parseInt(RAF.getResID(raf_body, RAF.findNearestATOM(raf_body, pdb_raf_index, false)));
                                    pdb_info.nearest_atom_fwd = Integer.parseInt(RAF.getResID(raf_body, RAF.findNearestATOM(raf_body, pdb_raf_index, true)));
                                }
                            } else {
                                pdb_info.in_range = false;
                            }
                            pdb_info.length = rs6.getInt(2);
                        }

                        if (protein_position_is_range) {
                            // Get PDB residue end index mapped from RAF. Most other parameters are based on the start index (FIXME: may change behavior)
                            Statement stmt6_2 = LocalSQL.createStatement();
                            ResultSet rs6_2 = stmt6_2.executeQuery("select substring(line, 39 + ("+Integer.toString(pdb_res_end)+"*7), 4) as pdb_index, floor(length(substring(line, 39)) / 7) as length, substring(line, 39 + ("+Integer.toString(pdb_res_end)+"*7) + 5, 1) as atom_res, raf_get_body(id) from raf where pdb_chain_id = " + Integer.toString(pdb_chain_id) + " and raf_version_id = 3;");
                            if (rs6_2.next()) {
                                String mapped_pdb_res_end_str = rs6_2.getString(1).trim();
                                String atom_res_end = rs6_2.getString(3);
                                if (!mapped_pdb_res_end_str.equals("")) {
                                    pdb_info.pdb_res_end = Integer.parseInt(mapped_pdb_res_end_str);
                                }
                            }
                        } else {
                            pdb_info.pdb_res_end = pdb_info.pdb_res;
                        }

                        // Check if classified in SCOPe
                        Statement stmt7 = LocalSQL.createStatement();
                        ResultSet rs7 = stmt7.executeQuery("select * from link_pdb l, scop_node s where l.pdb_chain_id = " + Integer.toString(pdb_chain_id) + " and l.node_id = s.id and s.release_id = " + Integer.toString(scopReleaseID));
                        if (rs7.next()) {
                            pdb_info.classified = true;
                        }

                        // Get aerospaci score
                        Statement stmt8 = LocalSQL.createStatement();
                        ResultSet rs8 = stmt8.executeQuery("select aerospaci from aerospaci where pdb_entry_id = " + Integer.toString(pdb_entry_id) + ";");
                        if (rs8.next()) {
                            pdb_info.aerospaci = rs8.getFloat(1);
                        }
                    }
                    variant_hit.structure_maps.add(pdb_info);
                }

                if (variant_hit.structure_maps.size() == 0) {
                    variant_hit.error_code = 3;
                }
            } else {
                variant_hit.error_code = 6;
            }
        }
        if (variant_hit.structure_maps.size() > 0) {
            Comparator<VariantStructure> structure_index_comparator = Comparator.comparing(VariantStructure::getIn_range).
                                                                                    thenComparing(VariantStructure::getClassified).
                                                                                    thenComparing(VariantStructure::getNot_missing_res).
                                                                                    thenComparing(VariantStructure::getLength).
                                                                                    thenComparing(VariantStructure::getAerospaci);
            Collections.sort(variant_hit.structure_maps, structure_index_comparator);
            Collections.reverse(variant_hit.structure_maps);
            variant_hit.structure = variant_hit.structure_maps.get(0);
        }
        return;
    }

    /** 
     * Helper method used to convert ResultSet to List (used for storing SIFTS results)
    */
    public static List<Map<String, Object>> resultSetToArrayList(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int columns = md.getColumnCount();
        List<Map<String, Object>> list = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new HashMap<>(columns);
            for (int i = 1; i <= columns; ++i) {
                row.put(md.getColumnName(i), rs.getObject(i));
            }
            list.add(row);
        }
        return list;
    }

    /* 
        Conducts variant search.
        Outputs to terminal all hits in JSON format.

        @param input is the search query
        @param is_hg38 is whether we're using hg38 (if not, then hg19)
    */
    public static void main(String[] args) throws SQLException, IOException, InterruptedException {
        LocalSQL.connect();
        int scopReleaseID = LocalSQL.getLatestSCOPRelease(false);
        
        String input = args[0];
        boolean is_hg38 = Boolean.parseBoolean(args[1]);
        ArrayList<VariantHit> output = new ArrayList<VariantHit>();
        ArrayList<VariantHit> error_output = new ArrayList<VariantHit>();
        try {
            ArrayList<VariantHit> hits = VEP.runVEP(input, is_hg38);
            for (VariantHit h : hits) {
                if (h.error_code != 0) {
                    error_output.add(h);
                    continue;
                }
                try {
                    mapToPDB(h, scopReleaseID);
                    if (h.valid_reference_allele && h.structure_maps.size() > 0) {
                        output.add(h);
                    } else {
                        error_output.add(h);
                    }
                } catch (Exception e) {
                    System.out.println("There was an issue with mapping to PDB");
                    e.printStackTrace();
                }
            } 
        } catch (Exception e) {
            System.out.println("There was an issue with VEP. Input: " + input);
            e.printStackTrace();
        }
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String listAsString;
            if (output.size() > 0) {
                listAsString = objectMapper.writeValueAsString(output);
            } else {
                Comparator<VariantHit> variant_hit_comparator = Comparator.comparing(VariantHit::getUnp_accession).
                                                                           thenComparing(VariantHit::getError_code).
                                                                           thenComparing(VariantHit::getProtein_position);
                Collections.sort(error_output, variant_hit_comparator);
                Collections.reverse(error_output);

                listAsString = objectMapper.writeValueAsString(error_output);
            }
            System.out.println(listAsString);
        } catch (JsonProcessingException e) {
            System.out.println("Unable to generate JSON from output");
            e.printStackTrace();
        }
    }
}
