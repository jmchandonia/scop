---
name: scop-homology
description: Use classic SCOP, ASTRAL, and SCOPe structural-classification data through public SCOPe API tools to answer questions about protein domains, hierarchy, homology, folds, families, superfamilies, PDB-chain BLAST and FATCAT comparisons, stable releases, ASTRAL quality, and reproducible citations. Use for SCOP/SCOPe/ASTRAL questions only; this skill does not cover SCOP2, CATH, ECOD, or other classification resources.
---

# SCOP Homology

Use the public SCOPe REST API at `https://scop.berkeley.edu/api/v1`.
Do not scrape human-facing SCOPe pages or use internal database identifiers.
Prefer the release-scoped SCOP MCP tools when they are available; their results
include the supporting REST URLs.

## Evidence Gates

SCOPe facts are data, not facts to reconstruct from model memory.

- Do not state a release-specific SID, SUNID, SCCS, hierarchy, boundary,
  annotation, periodic label, or numeric value unless it appears in an API
  response inspected during the current task.
- Do not rank structures by SPACI, AEROSPACI, resolution, R factor, or another
  quality measure unless the compared records were retrieved for every
  structure. If a required value is absent, do not rank them by that measure.
- Do not describe a historical classification change unless the relevant
  releases were queried separately or a returned history record supports it.
- Do not assert that two domains share a family or superfamily until both
  lineages have been retrieved in the same release.
- Do not infer a specific substrate, reaction, ligand, phenotype, or
  interchangeable structural template from family or superfamily placement
  alone.
- Do not call domains analogs, convergent, independently evolved, or
  nonhomologous merely because SCOPe places them in different superfamilies.
  That placement means SCOPe makes no positive homology assertion between
  them; it is not proof of a negative evolutionary claim.
- Treat BLAST and FATCAT hits for a PDB chain as comparison evidence, not as a
  SCOPe assignment. Do not say an unclassified chain or region belongs to a
  family, superfamily, or fold solely because it has a retrieved hit.
- Do not infer absence of a relationship from an empty or limit-sized hit page.
  Verify the requested chain and release, preserve API errors, and report the
  bounded search window.

If required evidence is unavailable, say that the claim cannot be verified
from the available data and name the API request needed. Never fill the gap
with a definite answer from memory.

## Core Workflow

1. Select a stable release for reproducible work.
2. Resolve each public identifier or search for the named entity.
3. Retrieve the domain or PDB record and its lineage.
4. Retrieve annotations and homology warnings before interpreting special
   cases.
5. Retrieve any additional evidence required by the question, such as quality
   records or the same entity in an older release.
6. Separate verified SCOPe facts from biological interpretation and from
   conclusions SCOPe does not establish.

For a request about BLAST or FATCAT matches to one PDB chain, follow the
PDB-chain comparison workflow in
`references/chain-comparisons.md`. Keep distinct aligned regions separate,
check whether the chain already has classified domains, and retrieve lineage
and caveat evidence for the representatives that control the interpretation.
The current FATCAT chain ranges use PDB numbering, while BLAST uses sequence
positions. Require a verified mapping before combining their regional evidence.

Use only public identifiers:

- `sunid`: public numeric identifier for a hierarchy node.
- `sid`: public domain identifier.
- `sccs`: public class/fold/superfamily/family string.
- PDB code.
- Public release selector such as `2.08`.

Never expose or invent `node_id`, `release_id`, or other internal IDs.

## Required Evidence By Claim

| Claim | Minimum evidence |
|---|---|
| Domain identity and boundaries | Domain endpoint in the selected release |
| Family, superfamily, fold, or class | Parent lineage in the selected release |
| Relationship between two domains | Both lineages in the same release |
| Analogy, convergence, independent origin, or absence of homology | Evidence outside hierarchy placement; different superfamilies alone are insufficient |
| Artifact, fragment, repeat, or heterogeneity caveat | Annotation and homology responses |
| Best structural representative | Quality responses for every candidate |
| Historical reclassification | Responses from every compared release |
| Current periodic-release status | Current-release metadata |
| Specific structural or functional rationale | Retrieved alignment, structure, paper, or other source that states or demonstrates it |
| Candidate relationship for a PDB-chain region | Chain metadata, sequence and/or FATCAT hits with aligned ranges, and release-matched lineage and caveats for controlling hit domains |
| Historical strong BLAST match | E-value plus the exact target ASTRAL domain-sequence length needed to check N- and C-terminal coverage |
| FATCAT-based relationship hypothesis | FATCAT P-value, score, initial RMSD, competing lineages, and representative-set provenance when available; verified residue mapping and inspected alignment/structure for regional agreement, paired coverage, or topology claims |

Use `scripts/scop_compare.py` for deterministic domain-lineage and quality
comparisons when local execution and network access are available. The script
fails rather than inventing a comparison when required records are missing.

## Biological Interpretation

- The domain is the fundamental classified unit. A chain can contain multiple,
  inserted, discontinuous, swapped, or intertwined domains.
- A shared family normally indicates the closer SCOPe relationship.
- A shared superfamily supports probable common ancestry, but not exact
  biochemical function.
- A shared fold or class indicates structural similarity. It does not by itself
  establish common ancestry.
- Agreement between BLAST and FATCAT evidence over the same chain region can
  strengthen a non-official relationship hypothesis, but it does not create an
  official classification. A FATCAT match alone establishes structural
  similarity, not homology.
- Protein structure is often conserved after sequence similarity has fallen
  below BLAST detection. Therefore, an extensive FATCAT match can be the most
  informative evidence for a remote-homology hypothesis when no qualifying
  sequence hit is returned. Treat it as a structure-led candidate: examine
  topology, alignment extent, competing superfamilies, and caveats before
  proposing homology, and stop at fold-level similarity when superfamily-level
  ancestry is not discriminated. Current chain-hit summaries do not expose
  residue correspondence or topology; without that evidence, report structural
  candidates and the unresolved checks.
- Different superfamilies within a fold mean that SCOPe does not assert common
  ancestry at the superfamily level. Say exactly that. Do not strengthen it to
  "unrelated," "analogous," or "convergent" without independent evidence.
- Protein and species levels are SCOPe hierarchy levels and must not be treated
  as equivalent to external taxonomy or sequence-database records.
- An automated match is not automatically low quality, but it should be
  reported as automated when that status matters.
- SPACI and AEROSPACI measure structural quality for representative selection;
  they are not homology or functional-similarity scores.
- Stable releases are the normal basis for reproducible analysis. Periodic
  updates do not replace stable releases.

For detailed hierarchy semantics, special annotations, and biological
limitations, read `references/biological-interpretation.md`.

## API Navigation

- Exact identifier: use the corresponding `sids`, `sunids`, or `sccs`
  release-scoped endpoint.
- Unknown identifier type: use `/releases/{release}/resolve/{identifier}`.
- Name or keyword: use `/releases/{release}/search?q=...` with a small limit,
  then resolve the selected result.
- Lineage: use `/parents`.
- Descendants: use `/children?limit=...` and follow opaque cursors.
- PDB domains: use `/releases/{release}/pdb/{code}/domains`.
- PDB chain and its existing classifications: use
  `/releases/{release}/chains/{chain}`.
- Combined BLAST and FATCAT chain comparisons: use
  `/releases/{release}/chains/{chain}/hits?limit=...`.
- One comparison stream only: use `/sequence-hits` or `/structure-hits` below
  that chain path.
- Caveats: use `/annotations` and `/homology`.
- Quality: use `/releases/{release}/pdb/{code}/quality`.
- Current release: use `/releases/current`.

Read `references/api-usage.md` for exact endpoint patterns and pagination. Read
`references/chain-comparisons.md` before deciding whether chain-comparison
evidence supports a regional family, superfamily, or fold hypothesis.

## Answer Discipline

Structure a biological answer around:

1. **Verified SCOPe facts:** release, public identifiers, hierarchy, and
   annotations actually retrieved.
2. **Interpretation:** the relationship justified by the deepest shared
   homology-bearing level and any structural evidence actually inspected.
3. **Limits:** functions, mechanisms, histories, or rankings not established by
   the retrieved data.
4. **Evidence:** concise release-scoped API paths used.

For a PDB-chain comparison, first state whether SCOPe already classifies any
domains on the chain. Then report each material chain region separately, with
the sequence and structure streams, alignment ranges, metrics, retrieved
lineages, conflicts, and search-window limits. Name the coordinate system and
leave cross-stream overlap unresolved when a residue mapping is unavailable.
Label any proposed relationship as non-official and distinguish it from curated
domain boundaries.

Do not turn absence of a warning into positive evidence. Do not turn a protein
name, shared ligand, motif, fold, or quality score into a stronger evolutionary
claim than the hierarchy supports. If a question asks *why* a classification
changed but the available API only shows that it changed, report the
classification change and state that its sequence, structural, or literature
rationale was not verified from the available evidence.
