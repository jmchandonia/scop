# Biological Interpretation With SCOP and SCOPe

Use this reference when answering biological questions from SCOP/SCOPe API data.

The current SCOPe online help is the primary source for the current contents and current behavior of the database. Reference 6, Brenner et al. 1996, is the primary conceptual guide for using SCOP as a protein structure knowledgebase. Newer SCOPe papers supersede older papers where the database architecture or production process changed.

## Source Weighting

Use sources in this order:

1. Current SCOPe online help and API responses for current database semantics.
2. Newer SCOPe papers for modern operation: Fox et al. 2014; Chandonia et al. 2017, 2019, 2022.
3. Brenner et al. 1996 for the core biological reasoning workflow of using SCOP to interpret fold, function, and evolutionary relationships.
4. Older SCOP and ASTRAL papers for historical definitions and rationale.
5. References 14-18 are useful mainly for historical context, benchmarking, sequence-alignment evaluation, and structural genomics novelty analysis; do not treat their web-interface details as current.

Important supersession: ASTRAL was historically described as a separate compendium, but current SCOPe integrates and updates ASTRAL-derived sequence, subset, RAF, coordinate, and quality resources.

## Reference Map

The downloaded reference PDFs were read when preparing this skill. The SCOPe references page lists 18 numbered references; reference 14 also has a supplementary-information PDF.

1. Fox et al. 2014, SCOPe integrating SCOP and ASTRAL data: modern SCOPe integration, stable identifiers, automated classification, release behavior.
2. Chandonia et al. 2022, SCOPe improvements for variant interpretation and machine learning: current SCOPe 2.08 context, heterogeneity annotations, ML/variant motivation.
3. Chandonia et al. 2019, large macromolecular structures: large complexes, manual curation priorities, relation to other classification systems.
4. Chandonia et al. 2017, manual curation and artifact removal: modern manual curation, expression-tag/artifact handling, ASTRAL integration.
5. Murzin et al. 1995, original SCOP: foundational hierarchy and domain-centered classification.
6. Brenner et al. 1996, using SCOP for fold interpretation: primary conceptual guide for biological reasoning with SCOP.
7. Fox et al. 2015, literature survey: how researchers use structure classification data.
8. Lo Conte et al. 2002, SCOP refinements: stable identifiers and updates to accommodate structural genomics.
9. Andreeva et al. 2004, SCOP refinements integrating sequence-family data: growth and curation refinements.
10. Andreeva et al. 2008, SCOP data growth: pre-classification, growth, and effects of structural genomics.
11. Chandonia et al. 2004, ASTRAL in 2004: RAF maps, sequence sets, subsets, AEROSPACI, weekly preliminary classification; superseded operationally by integrated SCOPe.
12. Chandonia et al. 2002, ASTRAL enhancements: genetic domain sequences, subsets, coordinate/sequence mapping; superseded operationally by integrated SCOPe.
13. Brenner et al. 2000, ASTRAL compendium: SPACI, sequence maps, subsets; superseded operationally by integrated SCOPe.
14. Chandonia and Brenner 2006 plus supplement, structural genomics outcomes: historical structural genomics novelty and coverage analysis; useful context but not a current API guide.
15. Hubbard et al. 1997, early SCOP: historical web/database description and hierarchy explanation.
16. Hubbard et al. 1998, SCOP applications: sequence-alignment benchmarking and protein-structure statistics.
17. Hubbard et al. 1999, early SCOP update: historical database status.
18. Lo Conte et al. 2000, early SCOP update: historical database status and ASTRAL sequence-library basis.

## Hierarchy Semantics

SCOP/SCOPe is domain-centered. A PDB chain may contain one domain, many domains, inserted domains, swapped or intertwined regions, tags, or regions not classified as ordinary globular domains. Always interpret at the domain level first.

Core hierarchy, from broad to specific:

- `class`: broad secondary-structure organization.
- `fold`: similar major secondary structures in the same arrangement and topology.
- `superfamily`: families with common structural and functional features inferred to share common evolutionary origin.
- `family`: closer relationship, usually significant sequence similarity or very similar function and structure.
- `protein`: essentially same function across species or isoforms.
- `species`: distinct protein sequence and variants.
- `domain`: leaf domain instance, identified by `sid` and `sunid`.

Family and superfamily are the main homology-bearing levels. Fold and class are structural categories and do not by themselves imply common ancestry. If two domains share only class or fold, describe that as structural similarity unless the API lineage or annotations support a stronger conclusion.

## How To Answer Common Biological Questions

For "what is this protein/domain?":

1. Resolve the identifier.
2. Fetch the node and parent lineage.
3. Report the domain description, SCCS, and lineage from class through family/superfamily.
4. Include release.
5. Check annotations and homology warnings before interpreting special cases.

For "are these two domains homologous?":

1. Fetch both lineages in the same release.
2. Compare family and superfamily nodes.
3. If they share family, state close SCOPe relationship.
4. If they share superfamily but not family, state probable common ancestry at the superfamily level.
5. If they share fold or class only, state structural similarity but do not infer homology from SCOPe alone.
6. Check both `homology` and `annotations` endpoints for caveats.

For "what does this fold mean?":

1. Fetch the fold node by `sunid` or `sccs`.
2. Explain fold as geometric/topological similarity.
3. Avoid saying all superfamilies under the fold are homologous.
4. Mention "not true fold" or heterogeneous fold annotations if present.

For "what domains are in this PDB entry?":

1. Fetch `/pdb/{code}/domains`.
2. For each domain, fetch lineage and annotations if interpretation is needed.
3. Do not assume the whole PDB chain has one classification.
4. If the PDB entry is a large complex or cryo-EM structure, expect many domains, multi-chain context, and possible domain-swap or boundary caveats.

For "what is the best representative structure?":

1. Use ASTRAL quality or subset endpoints when available.
2. Interpret SPACI/AEROSPACI as structure-quality and representative-selection guidance, not biological similarity.
3. Remember AEROSPACI penalizes known aberrant entries.

## Stable Identifiers

Use and report public identifiers:

- `sccs`: concise dot notation for class/fold/superfamily/family, for example `a.39.1.1`.
- `sunid`: numeric identifier for any hierarchy node.
- `sid`: stable domain identifier. It begins with `d`, followed by PDB ID, chain indicator, and a domain disambiguator when needed.

SUNIDs and SCCS identifiers are stable across releases except when classification changes substantially, such as merges, splits, major boundary changes, or obsolete nodes. If a domain is split or its boundaries change substantially, new SIDs and SUNIDs may be assigned. Use release information for reproducibility.

## Releases

SCOP version 1 ended at 1.75. SCOPe continues the classic SCOP hierarchy with new classifications, manual curation, automated classification, corrections, and integrated ASTRAL resources.

Stable releases are intended for reproducibility and benchmarking. Starting with SCOPe 2.02, periodic updates add newly released PDB entries to the current release without affecting older domains. Periodic updates do not replace stable releases. Sequences for newly added chains and domains are not included in ASTRAL subsets until the next stable release.

When answering a scientific question, include the release used, for example "SCOPe 2.08". If the API response indicates a periodic update, preserve that detail for current-release status questions, but stable version selectors such as `2.08` are the normal API input.

## ASTRAL In Current SCOPe

ASTRAL resources aid protein structure and sequence analysis. Current SCOPe incorporates ASTRAL data and derives newer ASTRAL releases from SCOPe domain definitions.

ASTRAL concepts:

- Domain and PDB-chain sequences.
- RAF maps linking PDB SEQRES and ATOM residue records.
- PDB-style domain coordinate files.
- Representative subsets to reduce redundancy.
- SPACI/AEROSPACI quality scores for representative selection.

Sequence sets and subsets historically focused on the first seven ordinary SCOP classes. Special classes such as artifacts or non-globular placeholders may not behave like ordinary homology-bearing classes.

## Artifacts, Tags, and Special Classes

SCOPe separates identifiable cloning/expression tags into class `l: Artifacts` so they do not create spurious homology signals. Do not interpret artifact-class entries as ordinary protein-family evidence.

SCOPe uses automatic and manual tag detection, including SEQADV comments, exact sequence matches to known tags, and UniProt-based terminal sequence comparisons. Where possible, trimmed domains kept stable SIDs and SUNIDs.

Multi-domain, membrane, small-protein, coiled-coil, low-resolution, peptides, designed proteins, and artifact classes can have special meanings. Do not overinterpret them as ordinary fold/family classifications without checking annotations.

## Structural Heterogeneity and "Not True" Warnings

SCOPe 2.08 adds machine-parseable heterogeneity annotations. Check `/annotations` and `/homology` when present, especially before using a family as a homogeneous training or benchmarking set.

Heterogeneity codes from current help include:

- `md`: multiple alternative domain divisions.
- `re`: repeat-based domains with different numbers of repeat units.
- `fr`: fragment.
- `as`: additional subdomains.
- `ie`: additional insertions or extensions.
- `ms`: missing secondary structures.
- `ae`: additional elements.
- `me`: missing elements.
- `nf`: not a true fold.
- `hf`: heterogeneous fold.
- `dn`: different domains include different subsets of subdomains.

If a family, fold, or domain has these annotations, state the caveat plainly. "Not true fold" means curators do not regard the fold as a normal fold-level structural grouping; do not infer family-level homogeneity from it.

## Manual and Automated Classification

SCOP was originally expert-curated. SCOPe uses a combination of manual curation and rigorously benchmarked automated classification. Automation primarily classifies new PDB chains/domains into existing hierarchy regions when sequence/domain evidence is strong enough. Manual curation remains important for new folds, superfamilies, families, artifacts, domain-boundary corrections, structural heterogeneity, and large macromolecular complexes.

Do not equate "automatically classified" with "low quality"; the SCOPe papers describe benchmarking intended to preserve SCOP reliability. Also do not assume that automated methods identify remote homologs that require expert structural interpretation.

## Examples From The Papers Mapped To API Workflows

Brenner et al. 1996 describes using SCOP to interpret a domain by moving through domain definition, class, fold, superfamily, family, and sequence/structure comparison. With the API:

1. Resolve the SID/SUNID/PDB code.
2. Fetch the domain and lineage.
3. Use the family/superfamily lineage to interpret likely evolutionary relationship.
4. Use fold/class only as structural context.
5. Check annotations for boundary, repeat, fragment, artifact, or heterogeneity caveats.

The ASTRAL papers describe sequences, maps, subsets, and quality scores. With the API:

1. Use PDB/domain endpoints for current SCOPe domain definitions.
2. Use ASTRAL quality endpoints for SPACI/AEROSPACI.
3. Use any API-exposed sequence/subset endpoints when available in `openapi.yaml`; if an endpoint is not present, say the current API does not expose it rather than scraping web pages.

The SCOPe 2014, 2017, 2019, and 2022 papers describe integration with SCOP/ASTRAL, automated classification, artifact removal, large complexes, structural heterogeneity, variant interpretation, and machine-learning support. With the API:

1. Use stable release-specific endpoints.
2. Use annotations and homology endpoints to expose caveats that matter for ML or benchmarking.
3. Use paginated collection endpoints for datasets; do not use expensive broad web searches.

## Worked Examples

These examples are based on identifiers and concepts used in the SCOPe help and papers. They show the expected reasoning pattern, not just the API calls.

### Look up the help-page visualization example `d4akea1`

The SCOPe help uses `d4akea1` as a domain-visualization example. Use:

```bash
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sids/d4akea1'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sids/d4akea1/parents'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sids/d4akea1/images'
```

Answer pattern:

`d4akea1` is domain `4ake A:1-121,A:157-214` in SCOPe 2.08. Its lineage is class `c` "Alpha and beta proteins (a/b)", fold `c.37` "P-loop containing nucleoside triphosphate hydrolases", superfamily `c.37.1`, family `c.37.1.1` "Nucleotide and nucleoside kinases", protein "Adenylate kinase", species "Escherichia coli". The images endpoint gives domain, chain, and full-structure views, matching the help-page description of thumbnails in isolation, chain context, and PDB-structure context.

Use this example when a user asks how to inspect a specific domain or retrieve static structural images without scraping the web page.

### Reproduce a parseable-file example: `d1ux8a_`

The help's parseable-file examples include `d1ux8a_`. Use:

```bash
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sids/d1ux8a_'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sids/d1ux8a_/parents'
```

Answer pattern:

`d1ux8a_` is domain `1ux8 A:` in SCOPe 2.08. Its lineage is class `a` "All alpha proteins", fold `a.1` "Globin-like", superfamily `a.1.1` "Globin-like", family `a.1.1.1` "Truncated hemoglobin", protein "Protozoan/bacterial hemoglobin", species "Bacillus subtilis". This is the API equivalent of reading the `dir.des`, `dir.cla`, and `dir.hie` parseable files for this domain.

Use this example when a user asks how to move from a `sid` to the full hierarchy.

### Ask whether two domains are homologous

Reference 6 emphasizes interpreting a domain through class, fold, superfamily, and family. With the API, compare lineages rather than relying on names alone.

Example question: "Are adenylate kinase domain `d1akea1` and shikimate kinase domain `d1shka_` homologous?"

Use:

```bash
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sids/d1akea1/parents'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sids/d1shka_/parents'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sids/d1akea1/homology'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sids/d1shka_/homology'
```

Answer pattern:

Both domains are in class `c`, fold `c.37`, and superfamily `c.37.1` "P-loop containing nucleoside triphosphate hydrolases". They are in different families: `d1akea1` is in family `c.37.1.1` "Nucleotide and nucleoside kinases", while `d1shka_` is in family `c.37.1.2` "Shikimate kinase (AroK)". In SCOPe terminology, sharing the same superfamily supports probable common ancestry, but the different families indicate they are not the closest family-level grouping.

If two domains share only fold `c.37` but not superfamily, answer more cautiously: same fold means structural/topological similarity, not a SCOPe homology assertion by itself.

### Ask whether two entries are closely related

Example question: "How close are `d4akea1` and `d1akea1`?"

Use:

```bash
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sids/d4akea1/parents'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sids/d1akea1/parents'
```

Answer pattern:

Both domains share family `c.37.1.1`, protein "Adenylate kinase", and species "Escherichia coli". In SCOPe terms, they are closely related entries within the same protein/species grouping. Report their specific SIDs and SUNIDs because they are distinct domain instances.

### Use paper examples without scraping

Older papers describe web pages, sequence libraries, RAF maps, or ASTRAL pages that may not correspond one-to-one to current API endpoints. Translate the biological question into API lookups:

- "What is this domain's fold?" -> resolve the SID/SUNID/PDB code, then fetch `/parents`.
- "Is this a new fold or superfamily?" -> compare the node's fold/superfamily lineage and release context; do not infer historical novelty unless the API or a release-history endpoint exposes the needed dates.
- "Which domains are in this PDB entry?" -> use `/releases/{release}/pdb/{code}/domains`.
- "What structure-quality score should I use for a representative?" -> use `/releases/{release}/pdb/{code}/quality` or `/releases/{release}/astral/quality/pdb/{code}` and explain SPACI/AEROSPACI as quality/representative-selection scores.

## Citation Guidance

When presenting results, include enough identifiers for reproducibility:

- Class: release, class name, SUNID.
- Fold/superfamily/family: release, SCCS, name, SUNID.
- Protein/species: release, name, SUNID, SCCS where available.
- Domain: release, SID, SUNID, SCCS.

For publications using SCOP, ASTRAL, or SCOPe data, cite the appropriate SCOPe references from the Help > References page, not only the website.
