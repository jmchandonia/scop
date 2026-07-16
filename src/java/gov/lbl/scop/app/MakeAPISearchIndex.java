/*
 * Software to build and maintain SCOPe, https://scop.berkeley.edu/
 *
 * Copyright (C) 2012-2026 The Regents of the University of California
 *
 * For feedback, mailto:scope@compbio.berkeley.edu
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * Version 2.1 of the License, or (at your option) any later version.
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301
 * USA
 */
package gov.lbl.scop.app;

import java.sql.*;
import java.text.Normalizer;
import java.util.*;
import gov.lbl.scop.local.LocalSQL;

/**
   Generates a low-cost inverted token index for the public REST API.

   Unlike MakeIndex, this does not build large searchable text blobs.  It
   precomputes normalized exact tokens so the API can answer common-name and
   autocomplete searches with indexed equality/range lookups instead of
   LIKE '%term%' or MySQL full-text queries.
*/
public class MakeAPISearchIndex {
    private static final int BATCH_SIZE = 1000;

    private static PreparedStatement insertToken = null;
    private static PreparedStatement insertTarget = null;
    private static int tokenBatchSize = 0;
    private static int targetBatchSize = 0;
    private static HashSet<String> currentTargetTokens = null;
    private static HashSet<String> seenPDBTargets = new HashSet<String>();
    private static HashMap<Integer, NodeRow> nodeCache = new HashMap<Integer, NodeRow>();

    private static final Set<String> STOP_WORDS = new HashSet<String>(Arrays.asList(
        "a", "an", "and", "are", "as", "at", "be", "by", "for", "from",
        "in", "into", "is", "of", "on", "or", "the", "to", "with"
    ));

    /**
       Immutable node summary used while indexing a target.
    */
    private static class NodeRow {
        int id;
        int releaseID;
        int levelID;
        int sunid;
        Integer parentNodeID;
        Integer parentSunid;
        String levelDescription;
        String description;
        String sccs;
        String sid;
    }

    private static String nz(String s) {
        if (s == null)
            return null;
        s = s.trim();
        return s.length() == 0 ? null : s;
    }

    private static String nodeTargetID(NodeRow node) {
        return Integer.toString(node.sunid);
    }

    private static String displayName(NodeRow node) {
        if (node.levelID == 8 && node.sid != null)
            return node.sid + " " + node.description;
        return node.description;
    }

    /**
       Normalize descriptive prose for word/phrase tokens.

       This intentionally lower-cases descriptive text.  Identifier tokens are
       handled separately because PDB chain identifiers are case-sensitive.
    */
    private static String normalizeText(String text) {
        if (text == null)
            return "";
        text = Normalizer.normalize(text, Normalizer.Form.NFKD);
        text = text.replaceAll("\\p{M}", "");
        text = text.toLowerCase(Locale.US);
        text = text.replace("&", " and ");
        text = text.replaceAll("[^a-z0-9]+", " ");
        text = text.replaceAll("\\s+", " ").trim();
        return text;
    }

    private static List<String> words(String text) {
        ArrayList<String> out = new ArrayList<String>();
        String normalized = normalizeText(text);
        if (normalized.length() == 0)
            return out;
        String[] tokens = normalized.split(" ");
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            if (token.length() < 2)
                continue;
            if (STOP_WORDS.contains(token))
                continue;
            out.add(token);
        }
        return out;
    }

    private static void addToken(int releaseID,
                                 String term,
                                 String termKind,
                                 String source,
                                 String targetKind,
                                 String targetPublicID,
                                 Integer targetLevelID,
                                 int weight) throws Exception {
        term = nz(term);
        if (term == null)
            return;
        if (term.length() > 128)
            term = term.substring(0, 128);
        if (currentTargetTokens != null) {
            String key = releaseID + "\t" + term + "\t" + termKind + "\t" +
                source + "\t" + targetKind + "\t" + targetPublicID + "\t" +
                (targetLevelID == null ? "" : targetLevelID.toString()) +
                "\t" + weight;
            if (currentTargetTokens.contains(key))
                return;
            currentTargetTokens.add(key);
        }
        insertToken.setInt(1, releaseID);
        insertToken.setString(2, term);
        insertToken.setString(3, termKind);
        insertToken.setString(4, source);
        insertToken.setString(5, targetKind);
        insertToken.setString(6, targetPublicID);
        if (targetLevelID == null)
            insertToken.setNull(7, java.sql.Types.TINYINT);
        else
            insertToken.setInt(7, targetLevelID.intValue());
        insertToken.setInt(8, weight);
        insertToken.addBatch();
        tokenBatchSize++;
        if (tokenBatchSize >= BATCH_SIZE)
            flushTokens();
    }

    private static void addIdentifier(NodeRow node,
                                      String term,
                                      String source,
                                      int weight) throws Exception {
        term = nz(term);
        if (term == null)
            return;
        addToken(node.releaseID, term, "identifier", source, "node",
                 nodeTargetID(node), node.levelID, weight);
    }

    private static void addPDBTarget(int releaseID, String code, String title) throws Exception {
        code = nz(code);
        if (code == null)
            return;
        code = code.toLowerCase(Locale.US);
        String key = releaseID + "\t" + code;
        if (seenPDBTargets.contains(key))
            return;
        seenPDBTargets.add(key);

        insertTarget.setInt(1, releaseID);
        insertTarget.setString(2, "pdb_entry");
        insertTarget.setString(3, code);
        insertTarget.setNull(4, java.sql.Types.INTEGER);
        insertTarget.setNull(5, java.sql.Types.VARCHAR);
        insertTarget.setNull(6, java.sql.Types.VARCHAR);
        insertTarget.setNull(7, java.sql.Types.TINYINT);
        insertTarget.setString(8, title == null ? code : title);
        insertTarget.setNull(9, java.sql.Types.INTEGER);
        insertTarget.addBatch();
        targetBatchSize++;
        if (targetBatchSize >= BATCH_SIZE)
            flushTargets();

        addToken(releaseID, code, "identifier", "pdb_code", "pdb_entry",
                 code, null, 1000);
        addToken(releaseID, "pdb:" + code, "identifier", "pdb_code", "pdb_entry",
                 code, null, 1000);
        addText(releaseID, title, "pdb_title", "pdb_entry", code, null, 30,
                false);
    }

    private static void addText(NodeRow node,
                                String text,
                                String source,
                                int baseWeight) throws Exception {
        addText(node.releaseID, text, source, "node", nodeTargetID(node),
                node.levelID, baseWeight);
    }

    private static void addText(int releaseID,
                                String text,
                                String source,
                                String targetKind,
                                String targetPublicID,
                                Integer targetLevelID,
                                int baseWeight) throws Exception {
        addText(releaseID, text, source, targetKind, targetPublicID,
                targetLevelID, baseWeight, false);
    }

    private static void addText(int releaseID,
                                String text,
                                String source,
                                String targetKind,
                                String targetPublicID,
                                Integer targetLevelID,
                                int baseWeight,
                                boolean includePrefixes) throws Exception {
        List<String> w = words(text);
        if (w.size() == 0)
            return;

        String full = join(w, 0, w.size());
        if (full.length() >= 3 && full.length() <= 128)
            addToken(releaseID, full, "phrase", source, targetKind,
                     targetPublicID, targetLevelID, baseWeight * 4);

        for (int i = 0; i < w.size(); i++) {
            String word = w.get(i);
            addToken(releaseID, word, "word", source, targetKind,
                     targetPublicID, targetLevelID, baseWeight);
            if (includePrefixes)
                addPrefixes(releaseID, word, source, targetKind, targetPublicID,
                            targetLevelID, Math.max(1, baseWeight / 5));
        }

        for (int n = 2; n <= 3; n++) {
            if (w.size() < n)
                continue;
            for (int i = 0; i <= w.size() - n; i++) {
                String phrase = join(w, i, i + n);
                addToken(releaseID, phrase, "phrase", source, targetKind,
                         targetPublicID, targetLevelID, baseWeight * (n + 1));
            }
        }
    }

    private static String join(List<String> words, int start, int end) {
        StringBuffer out = new StringBuffer();
        for (int i = start; i < end; i++) {
            if (out.length() > 0)
                out.append(' ');
            out.append(words.get(i));
        }
        return out.toString();
    }

    private static void addPrefixes(int releaseID,
                                    String word,
                                    String source,
                                    String targetKind,
                                    String targetPublicID,
                                    Integer targetLevelID,
                                    int weight) throws Exception {
        if (word.length() < 3)
            return;
        int max = Math.min(20, word.length() - 1);
        for (int len = 3; len <= max; len++)
            addToken(releaseID, word.substring(0, len), "prefix", source,
                     targetKind, targetPublicID, targetLevelID, weight);
    }

    private static NodeRow nodeByID(int nodeID, Statement stmt) throws Exception {
        Integer cacheKey = Integer.valueOf(nodeID);
        NodeRow cached = nodeCache.get(cacheKey);
        if (cached != null)
            return cached;

        ResultSet rs = stmt.executeQuery(
            "select n.id, n.release_id, l.id, l.description, n.description, " +
            "n.sccs, n.sunid, n.sid, n.parent_node_id, p.sunid " +
            "from scop_node n join scop_level l on n.level_id=l.id " +
            "left join scop_node p on p.id=n.parent_node_id " +
            "where n.level_id>1 and n.id=" + nodeID
        );
        if (!rs.next()) {
            rs.close();
            return null;
        }
        NodeRow node = new NodeRow();
        node.id = rs.getInt(1);
        node.releaseID = rs.getInt(2);
        node.levelID = rs.getInt(3);
        node.levelDescription = rs.getString(4);
        node.description = rs.getString(5);
        node.sccs = rs.getString(6);
        node.sunid = rs.getInt(7);
        node.sid = rs.getString(8);
        int parentID = rs.getInt(9);
        node.parentNodeID = rs.wasNull() ? null : Integer.valueOf(parentID);
        int parentSunid = rs.getInt(10);
        node.parentSunid = rs.wasNull() ? null : Integer.valueOf(parentSunid);
        rs.close();
        nodeCache.put(cacheKey, node);
        return node;
    }

    private static void insertTarget(NodeRow node) throws Exception {
        insertTarget.setInt(1, node.releaseID);
        insertTarget.setString(2, "node");
        insertTarget.setString(3, nodeTargetID(node));
        insertTarget.setInt(4, node.sunid);
        insertTarget.setString(5, node.sid);
        insertTarget.setString(6, node.sccs);
        insertTarget.setInt(7, node.levelID);
        insertTarget.setString(8, displayName(node));
        if (node.parentSunid == null)
            insertTarget.setNull(9, java.sql.Types.INTEGER);
        else
            insertTarget.setInt(9, node.parentSunid.intValue());
        insertTarget.addBatch();
        targetBatchSize++;
        if (targetBatchSize >= BATCH_SIZE)
            flushTargets();
    }

    private static void indexIdentifiers(NodeRow node) throws Exception {
        if (node.sunid > 0) {
            addIdentifier(node, Integer.toString(node.sunid), "sunid", 1000);
            addIdentifier(node, "sunid:" + node.sunid, "sunid", 1000);
            addIdentifier(node, "sunid=" + node.sunid, "sunid", 1000);
        }
        if (node.sid != null) {
            String sid = node.sid.toLowerCase(Locale.US);
            addIdentifier(node, sid, "sid", 1000);
            addIdentifier(node, "sid:" + sid, "sid", 1000);
            addIdentifier(node, "sid=" + sid, "sid", 1000);
        }
        if (node.sccs != null) {
            String sccs = node.sccs.toLowerCase(Locale.US);
            addIdentifier(node, sccs, "sccs", node.levelID < 8 ? 950 : 350);
            addIdentifier(node, "sccs:" + sccs, "sccs", 950);
            addIdentifier(node, "sccs=" + sccs, "sccs", 950);
        }
    }

    private static void indexDomainPDBData(NodeRow node, Statement stmt) throws Exception {
        ResultSet rs = stmt.executeQuery(
            "select distinct e.code, e.description " +
            "from pdb_entry e, pdb_release r, pdb_chain c, link_pdb l " +
            "where l.node_id=" + node.id + " and l.pdb_chain_id=c.id " +
            "and c.pdb_release_id=r.id and r.pdb_entry_id=e.id"
        );
        while (rs.next()) {
            String code = rs.getString(1);
            String title = rs.getString(2);
            addPDBTarget(node.releaseID, code, title);
            if (code != null) {
                code = code.toLowerCase(Locale.US);
                addIdentifier(node, code, "pdb_code", 900);
                addIdentifier(node, "pdb:" + code, "pdb_code", 900);
            }
            addText(node, title, "pdb_title", 20);
        }
        rs.close();

        rs = stmt.executeQuery(
            "select e.code, c.chain " +
            "from pdb_entry e, pdb_release r, pdb_chain c, link_pdb l " +
            "where l.node_id=" + node.id + " and l.pdb_chain_id=c.id " +
            "and c.pdb_release_id=r.id and r.pdb_entry_id=e.id"
        );
        while (rs.next()) {
            String code = rs.getString(1);
            String chain = rs.getString(2);
            if (code == null || chain == null)
                continue;
            code = code.toLowerCase(Locale.US);
            chain = chain.trim();
            if (chain.length() == 0)
                continue;
            // PDB chain identifiers are case-sensitive.  Do not lower-case
            // the chain component: 1ux8A and 1ux8a are different tokens.
            addIdentifier(node, code + chain, "pdb_chain", 850);
            addIdentifier(node, "pdb:" + code + chain, "pdb_chain", 850);
        }
        rs.close();

        rs = stmt.executeQuery(
            "select s.scientific_name, s.common_name, s.strain_name, " +
            "s.is_synthetic, s.ncbi_taxid " +
            "from pdb_chain_source pcs, pdb_source s, link_pdb l " +
            "where l.node_id=" + node.id + " and l.pdb_chain_id=pcs.pdb_chain_id " +
            "and pcs.pdb_source_id=s.id group by s.id"
        );
        while (rs.next()) {
            addText(node, rs.getString(1), "species_scientific", 70);
            addText(node, rs.getString(2), "species_common", 45);
            addText(node, rs.getString(3), "species_strain", 35);
            if (rs.getInt(4) == 1)
                addText(node, "synthetic", "species_common", 20);
            int taxid = rs.getInt(5);
            if (taxid > 0) {
                addToken(node.releaseID, Integer.toString(taxid), "taxid",
                         "species_scientific", "node", nodeTargetID(node),
                         node.levelID, 800);
                addToken(node.releaseID, "taxid:" + taxid, "taxid",
                         "species_scientific", "node", nodeTargetID(node),
                         node.levelID, 800);
                addToken(node.releaseID, "taxid=" + taxid, "taxid",
                         "species_scientific", "node", nodeTargetID(node),
                         node.levelID, 800);
            }
        }
        rs.close();

        rs = stmt.executeQuery(
            "select g.gene_name from pdb_chain_gene pcg, pdb_gene g, link_pdb l " +
            "where l.node_id=" + node.id + " and l.pdb_chain_id=pcg.pdb_chain_id " +
            "and pcg.pdb_gene_id=g.id group by g.id"
        );
        while (rs.next()) {
            String gene = nz(rs.getString(1));
            if (gene == null)
                continue;
            addToken(node.releaseID, gene, "gene", "gene", "node",
                     nodeTargetID(node), node.levelID, 700);
            addToken(node.releaseID, "gene:" + gene, "gene", "gene", "node",
                     nodeTargetID(node), node.levelID, 700);
            addText(node.releaseID, gene, "gene", "node", nodeTargetID(node),
                    node.levelID, 80, true);
        }
        rs.close();
    }

    private static void indexSpeciesNodeData(NodeRow node, Statement stmt) throws Exception {
        if (node.levelID != 7)
            return;
        ResultSet rs = stmt.executeQuery(
            "select s.scientific_name, s.common_name, s.details, s.ncbi_taxid " +
            "from species s, link_species l where l.node_id=" + node.id +
            " and l.species_id=s.id"
        );
        while (rs.next()) {
            addText(node, rs.getString(1), "species_scientific", 70);
            addText(node, rs.getString(2), "species_common", 45);
            addText(node, rs.getString(3), "species_strain", 35);
            int taxid = rs.getInt(4);
            if (taxid > 0) {
                addToken(node.releaseID, Integer.toString(taxid), "taxid",
                         "species_scientific", "node", nodeTargetID(node),
                         node.levelID, 800);
                addToken(node.releaseID, "taxid:" + taxid, "taxid",
                         "species_scientific", "node", nodeTargetID(node),
                         node.levelID, 800);
                addToken(node.releaseID, "taxid=" + taxid, "taxid",
                         "species_scientific", "node", nodeTargetID(node),
                         node.levelID, 800);
            }
        }
        rs.close();
    }

    private static void indexXrefs(NodeRow node, Statement stmt) throws Exception {
        HashSet<String> uniprots = new HashSet<String>();
        ResultSet rs = stmt.executeQuery(
            "select uniprot_accession from link_uniprot where node_id=" + node.id
        );
        while (rs.next())
            addAccession(node, rs.getString(1), "uniprot", "uniprot", uniprots);
        rs.close();

        rs = stmt.executeQuery(
            "select r.db_code, r.db_accession from pdb_chain_dbref r, link_pdb l " +
            "where l.node_id=" + node.id + " and l.pdb_chain_id=r.pdb_chain_id " +
            "and r.db_name='UNP'"
        );
        while (rs.next()) {
            addAccession(node, rs.getString(1), "uniprot", "uniprot", uniprots);
            addAccession(node, rs.getString(2), "uniprot", "uniprot", uniprots);
        }
        rs.close();

        rs = stmt.executeQuery(
            "select pfam_accession from link_pfam where node_id=" + node.id
        );
        while (rs.next())
            addAccession(node, rs.getString(1), "pfam", "pfam", null);
        rs.close();
    }

    private static void addAccession(NodeRow node,
                                     String accession,
                                     String source,
                                     String prefix,
                                     Set<String> seen) throws Exception {
        accession = nz(accession);
        if (accession == null)
            return;
        if (seen != null && seen.contains(accession))
            return;
        if (seen != null)
            seen.add(accession);
        addToken(node.releaseID, accession, "accession", source, "node",
                 nodeTargetID(node), node.levelID, 750);
        addToken(node.releaseID, prefix + ":" + accession, "accession", source,
                 "node", nodeTargetID(node), node.levelID, 750);
    }

    private static void indexComments(NodeRow node, Statement stmt) throws Exception {
        ResultSet rs = stmt.executeQuery(
            "select description from scop_comment where node_id=" + node.id
        );
        while (rs.next())
            addText(node, rs.getString(1), "comment", 12);
        rs.close();
    }

    private static void indexLineage(NodeRow node, Statement stmt) throws Exception {
        Integer parentID = node.parentNodeID;
        while (parentID != null) {
            NodeRow parent = nodeByID(parentID.intValue(), stmt);
            if (parent == null)
                return;
            addText(node, parent.description, "lineage_description",
                    parent.levelID >= 5 ? 55 : 35);
            if (parent.sccs != null && parent.levelID < 8)
                addIdentifier(node, parent.sccs.toLowerCase(Locale.US), "sccs",
                              250);
            parentID = parent.parentNodeID;
        }
    }

    private static void openInsertStatements() throws Exception {
        tokenBatchSize = 0;
        targetBatchSize = 0;
        insertToken = LocalSQL.prepareStatement(
            "insert ignore into scop_api_search_token " +
            "(release_id, term, term_kind, source, target_kind, " +
            "target_public_id, target_level_id, weight) " +
            "values (?, ?, ?, ?, ?, ?, ?, ?)"
        );
        insertTarget = LocalSQL.prepareStatement(
            "insert ignore into scop_api_search_target " +
            "(release_id, target_kind, target_public_id, sunid, sid, sccs, " +
            "level_id, display_name, parent_sunid) " +
            "values (?, ?, ?, ?, ?, ?, ?, ?, ?)"
        );
    }

    private static void closeInsertStatements() throws Exception {
        try {
            flushTokens();
            flushTargets();
        }
        finally {
            if (insertToken != null)
                insertToken.close();
            if (insertTarget != null)
                insertTarget.close();
            insertToken = null;
            insertTarget = null;
            tokenBatchSize = 0;
            targetBatchSize = 0;
        }
    }

    /**
       Index a given node using the currently open batch statements.
    */
    private static void indexNodePrepared(int nodeID) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        NodeRow node = nodeByID(nodeID, stmt);
        if (node == null) {
            stmt.close();
            return;
        }

        currentTargetTokens = new HashSet<String>();
        try {
            insertTarget(node);
            indexIdentifiers(node);
            addText(node.releaseID, node.levelDescription + " " +
                    node.description, "node_description", "node",
                    nodeTargetID(node), node.levelID,
                    node.levelID == 8 ? 80 : 120, true);

            if (node.levelID == 8)
                indexDomainPDBData(node, stmt);
            indexSpeciesNodeData(node, stmt);
            indexXrefs(node, stmt);
            indexLineage(node, stmt);
            indexComments(node, stmt);
        }
        finally {
            currentTargetTokens = null;
            stmt.close();
        }
    }

    /**
       Incrementally index a single node for the public REST API search.
    */
    final public static void indexNode(int nodeID) throws Exception {
        Statement stmt = LocalSQL.createStatement();
        NodeRow node = nodeByID(nodeID, stmt);
        if (node == null) {
            stmt.close();
            return;
        }

        String targetPublicID = nodeTargetID(node);
        PreparedStatement deleteToken = LocalSQL.prepareStatement(
            "delete from scop_api_search_token " +
            "where release_id=? and target_kind='node' and target_public_id=?"
        );
        deleteToken.setInt(1, node.releaseID);
        deleteToken.setString(2, targetPublicID);
        deleteToken.executeUpdate();
        deleteToken.close();

        PreparedStatement deleteTarget = LocalSQL.prepareStatement(
            "delete from scop_api_search_target " +
            "where release_id=? and target_kind='node' and target_public_id=?"
        );
        deleteTarget.setInt(1, node.releaseID);
        deleteTarget.setString(2, targetPublicID);
        deleteTarget.executeUpdate();
        deleteTarget.close();

        try {
            seenPDBTargets.clear();
            openInsertStatements();
            indexNodePrepared(nodeID);
        }
        finally {
            closeInsertStatements();
            stmt.close();
        }
    }

    private static String releaseFilter(int releaseID, boolean allReleases) {
        if (allReleases)
            return "";
        return " and release_id=" + releaseID;
    }

    private static void flushTokens() throws Exception {
        if (tokenBatchSize == 0)
            return;
        insertToken.executeBatch();
        tokenBatchSize = 0;
    }

    private static void flushTargets() throws Exception {
        if (targetBatchSize == 0)
            return;
        insertTarget.executeBatch();
        targetBatchSize = 0;
    }

    private static void disableKeys(Statement stmt, String table) {
        try {
            stmt.executeUpdate("alter table " + table + " disable keys");
        }
        catch (Exception e) {
            System.err.println("Warning: could not disable keys for " + table +
                               ": " + e.getMessage());
        }
    }

    private static void enableKeys(Statement stmt, String table) {
        try {
            stmt.executeUpdate("alter table " + table + " enable keys");
        }
        catch (Exception e) {
            System.err.println("Warning: could not enable keys for " + table +
                               ": " + e.getMessage());
        }
    }

    /**
       Rebuild the API search index for one release, unless allReleases is set.
    */
    final public static void rebuildIndex(int releaseID,
                                          boolean allReleases) throws Exception {
        LocalSQL.connectRW();
        Statement stmt = LocalSQL.createStatement();

        stmt.executeUpdate("truncate table scop_api_search_token");
        stmt.executeUpdate("truncate table scop_api_search_target");
        disableKeys(stmt, "scop_api_search_token");
        disableKeys(stmt, "scop_api_search_target");
        seenPDBTargets.clear();
        nodeCache.clear();

        openInsertStatements();

        ResultSet rs = stmt.executeQuery(
            "select id from scop_node where level_id>1" +
            releaseFilter(releaseID, allReleases) + " order by release_id, id"
        );
        int n = 0;
        long startTime = System.currentTimeMillis();
        while (rs.next()) {
            indexNodePrepared(rs.getInt(1));
            n++;
            if ((n % 10000) == 0) {
                long elapsed = Math.max(1L, System.currentTimeMillis() - startTime);
                long nodesPerHour = (long)(((double)n * 3600000.0) / elapsed);
                System.out.println("Indexed " + n + " nodes (" +
                                   nodesPerHour + "/hour)");
            }
        }
        rs.close();

        closeInsertStatements();

        enableKeys(stmt, "scop_api_search_target");
        enableKeys(stmt, "scop_api_search_token");
        stmt.close();
        System.out.println("Indexed " + n + " nodes");
    }

    final public static void rebuildIndex() throws Exception {
        LocalSQL.connectRW();
        int releaseID = LocalSQL.getLatestSCOPRelease(true);
        rebuildIndex(releaseID, false);
    }

    private static void usage() {
        System.out.println("Usage: MakeAPISearchIndex [--latest-public | --release-id ID | --all-releases]");
        System.out.println("Default: --latest-public");
    }

    final public static void main(String argv[]) {
        try {
            int releaseID = 0;
            boolean allReleases = false;
            boolean useLatestPublic = true;

            for (int i = 0; i < argv.length; i++) {
                if (argv[i].equals("--latest-public")) {
                    useLatestPublic = true;
                    allReleases = false;
                }
                else if (argv[i].equals("--release-id")) {
                    if (i + 1 >= argv.length) {
                        usage();
                        return;
                    }
                    releaseID = Integer.parseInt(argv[++i]);
                    useLatestPublic = false;
                    allReleases = false;
                }
                else if (argv[i].equals("--all-releases")) {
                    allReleases = true;
                    useLatestPublic = false;
                }
                else if (argv[i].equals("--help") || argv[i].equals("-h")) {
                    usage();
                    return;
                }
                else {
                    usage();
                    return;
                }
            }

            LocalSQL.connectRW();
            if (useLatestPublic)
                releaseID = LocalSQL.getLatestSCOPRelease(true);

            if (allReleases)
                System.out.println("Indexing all releases");
            else
                System.out.println("Indexing release_id " + releaseID);
            rebuildIndex(releaseID, allReleases);
        }
        catch (Exception e) {
            System.out.println("Exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
