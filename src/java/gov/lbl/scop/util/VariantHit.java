package gov.lbl.scop.util;

import java.util.*;

/**
    Class used for storing variants mapped to `input`

    author: Lindsey Guan

    Error codes:
    0: no error
    1: invalid reference allele
    2: invalid HGVS expression
    3: no SIFTS hit
    4: could not map cDNA coordinates
    5: could not map to UniProt
    6: could not map to protein position
*/
class VariantHit {
    String input;
    String transcript;
    String gene;
    String strand;
    String chromosome;
    String genome_position;
    String cds_position;
    String cdna_position;
    String protein_position;
    String protein_position_end;
    String allele_change;
    String protein_change;
    String codon_change;
    String consequence;
    String existing_variation;
    String intron_number;
    String exon_number;
    String gene_distance;
    boolean valid_reference_allele;
    String expected_reference_allele;
    int error_code;
    int hgnc_id;
    String unp_accession;
    VariantStructure structure;
    ArrayList<VariantStructure> structure_maps;


    public VariantHit() {
    }

    public String getInput() {
        return this.input;
    }

    public String getTranscript() {
        return this.transcript;
    }

    public String getGene() {
        return this.gene;
    }

    public String getStrand() {
        return this.strand;
    }

    public String getChromosome() {
        return this.chromosome;
    }

    public String getGenome_position() {
        return this.genome_position;
    }

    public String getCds_position() {
        return this.cds_position;
    }

    public String getCdna_position() {
        return this.cdna_position;
    }

    public String getProtein_position() {
        return this.protein_position;
    }

    public String getProtein_position_end() {
        return this.protein_position_end;
    }

    public String getAllele_change() {
        return this.allele_change;
    }

    public String getProtein_change() {
        return this.protein_change;
    }

    public String getCodon_change() {
        return this.codon_change;
    }

    public String getConsequence() {
        return this.consequence;
    }

    public String getExisting_variation() {
        return this.existing_variation;
    }

    public boolean getValid_reference_allele() {
        return this.valid_reference_allele;
    }

    public boolean isValid_reference_allele() {
        return this.valid_reference_allele;
    }

    public String getExpected_reference_allele() {
        return this.expected_reference_allele;
    }

    public String getExon_number() {
        return this.exon_number;
    }

    public String getIntron_number() {
        return this.intron_number;
    }

    public String getGene_distance() {
        return this.gene_distance;
    }

    public int getError_code() {
        return this.error_code;
    }

    public int getHgnc_id() {
        return this.hgnc_id;
    }

    public String getUnp_accession() {
        return this.unp_accession;
    }

    public VariantStructure getStructure() {
        return this.structure;
    }

    public ArrayList<VariantStructure> getStructure_maps() {
        return this.structure_maps;
    }


    @Override
    public String toString() {
        return "{" +
            " input='" + getInput() + "'" +
            ", transcript='" + getTranscript() + "'" +
            ", gene='" + getGene() + "'" +
            ", strand='" + getStrand() + "'" +
            ", chromosome='" + getChromosome() + "'" +
            ", genome_position='" + getGenome_position() + "'" +
            ", cds_position='" + getCds_position() + "'" +
            ", cdna_position='" + getCdna_position() + "'" +
            ", protein_position='" + getProtein_position() + "'" +
            ", protein_position_end='" + getProtein_position_end() + "'" +
            ", allele_change='" + getAllele_change() + "'" +
            ", protein_change='" + getProtein_change() + "'" +
            ", codon_change='" + getCodon_change() + "'" +
            ", consequence='" + getConsequence() + "'" +
            ", existing_variation='" + getExisting_variation() + "'" +
            ", exon_number='" + getExon_number() + "'" +
            ", intron_number='" + getIntron_number() + "'" +
            ", valid_reference_allele='" + isValid_reference_allele() + "'" +
            ", expected_reference_allele='" + getExpected_reference_allele() + "'" +
            ", error_code='" + getError_code() + "'" +
            ", hgnc_id='" + getHgnc_id() + "'" +
            ", unp_accession='" + getUnp_accession() + "'" +
            ", structure='" + getStructure() + "'" +
            ", structure_maps='" + getStructure_maps() + "'" +
            "}";
    }
}
