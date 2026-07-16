package gov.lbl.scop.util;

/**
    Class used for storing a variant-mapped amino acid residue,
    its PDB information, and its sunid.
*/
class VariantStructure {
    String pdb_id;
    String pdb_chain;
    int pdb_chain_id;
    int pdb_res;
    int pdb_res_end;
    int length;
    boolean in_range;
    boolean not_missing_res;
    int nearest_atom_bwd;
    int nearest_atom_fwd;
    boolean classified;
    float aerospaci;
    String pdb_path;


    public VariantStructure() {
    }


    public String getPdb_id() {
        return this.pdb_id;
    }

    public String getPdb_chain() {
        return this.pdb_chain;
    }

    public int getPdb_chain_id() {
        return this.pdb_chain_id;
    }

    public int getPdb_res() {
        return this.pdb_res;
    }

    public int getPdb_res_end() {
        return this.pdb_res_end;
    }

    public int getLength() {
        return this.length;
    }

    public boolean getIn_range() {
        return this.in_range;
    }

    public boolean isIn_range() {
        return this.in_range;
    }

    public boolean getNot_missing_res() {
        return this.not_missing_res;
    }

    public boolean isNot_missing_res() {
        return this.not_missing_res;
    }

    public int getNearest_atom_bwd() {
        return this.nearest_atom_bwd;
    }

    public int getNearest_atom_fwd() {
        return this.nearest_atom_fwd;
    }

    public boolean getClassified() {
        return this.classified;
    }

    public boolean isClassified() {
        return this.classified;
    }

    public float getAerospaci() {
        return this.aerospaci;
    }

    public String getPdb_path() {
        return this.pdb_path;
    }
}
