package gov.lbl.scop.app;

import java.sql.*;
import java.io.*;
import java.util.*;
import org.strbio.io.*;
import org.strbio.local.*;
import org.strbio.math.*;
import org.strbio.mol.*;
import org.strbio.util.*;
import org.strbio.IO;
import gov.lbl.scop.util.*;
import gov.lbl.scop.local.LocalSQL;

/**
    Creates thumbnails for chains.

    A chain and structure image are generated, each with a small and large version. The chain image shows 
    just the chain, while the structure image shows the chain in context of the whole PDB structure 
    (with non-chain regions in gray).

    Thumbnails are saved in /lab/proj/astral/thumbs/chain. Hash codes use the same format
    as the hashing system in the other thumbs/ directories.
*/
public class MakeThumbnailsChain {
    /**
       create thumbnails for a PDB chain ID
    */
    final public static void makeThumbnailChain(int pdbChainID,
                                                boolean needsCPU) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        ResultSet rs = stmt.executeQuery("select e.code, c.chain from pdb_entry e, pdb_release r, pdb_chain c where e.id = r.pdb_entry_id and r.id = c.pdb_release_id and c.id="+pdbChainID);
        rs.next();
        String description = rs.getString(1)+" "+rs.getString(2)+":";
        rs.close();
        stmt.close();
        makeThumbnailChain(pdbChainID,
                           description,
                           needsCPU);
    }
    
    /**
       create thumbnails for a PDB chain ID, after description is made
    */
    final public static void makeThumbnailChain(int chainID,
                                                String description,
                                                boolean needsCPU) throws Exception {
        Thumbnail.makeForChain(chainID,
                                description,
                                "chain",
                                needsCPU);
        Thumbnail.makeForChain(chainID,
                                description,    
                                "structure",
                                needsCPU);
    }
    
    final public static void main(String argv[]) {
        try {
            LocalSQL.connectRW();
            Statement stmt = LocalSQL.createStatement();
            Statement stmt2 = LocalSQL.createStatement();
            int scopReleaseID = LocalSQL.getLatestSCOPRelease(false);
            ResultSet rs = stmt.executeQuery("select distinct c.id, c.chain, e.code from pdb_entry e, pdb_release r, pdb_chain c, scop_release s, pdb_local l, pdb_chain_source cs, pdb_source so where e.id = r.pdb_entry_id and r.id = c.pdb_release_id and l.pdb_release_id=r.id and s.id = "+scopReleaseID+" and r.file_date <= s.freeze_date and r.revision_date <= s.freeze_date and l.snapshot_date <= s.freeze_date and l.xml_path is not null and r.replaced_by is NULL and c.id = cs.pdb_chain_id and cs.pdb_source_id = so.id and so.scientific_name like '%homo sapien%' and c.id not in (select chain_id from pdb_chain_thumbnail)");
            while (rs.next()) {
                String description = rs.getString(3) + " " + rs.getString(2) + ":";
                System.out.println("Starting job for "+description);
                int chainID = rs.getInt(1);
                LocalSQL.newJob(23,
                                chainID,
                                null,
                                stmt2);
            }
            rs.close();
            stmt.close();
            stmt2.close();
        }
        catch (Exception e) {
            System.out.println("Exception: "+e.getMessage());
            e.printStackTrace();
        }
    }
}
