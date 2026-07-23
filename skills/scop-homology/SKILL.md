---
name: scop-homology
description: Use classic SCOP, ASTRAL, and SCOPe structural-classification data through the public SCOPe REST API to answer questions about protein domains, hierarchy, homology, folds, families, superfamilies, stable releases, ASTRAL quality, and reproducible citations. Use for SCOP/SCOPe/ASTRAL questions only; this skill does not cover SCOP2, CATH, ECOD, or other classification resources.
---

# SCOP Homology

Use the public SCOPe REST API at `https://scop.berkeley.edu/api/v1`.
Do not scrape human-facing SCOPe pages or use internal database identifiers.

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
| Artifact, fragment, repeat, or heterogeneity caveat | Annotation and homology responses |
| Best structural representative | Quality responses for every candidate |
| Historical reclassification | Responses from every compared release |
| Current periodic-release status | Current-release metadata |

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
- Caveats: use `/annotations` and `/homology`.
- Quality: use `/releases/{release}/pdb/{code}/quality`.
- Current release: use `/releases/current`.

Read `references/api-usage.md` for exact endpoint patterns and pagination.

## Answer Discipline

Structure a biological answer around:

1. **Verified SCOPe facts:** release, public identifiers, hierarchy, and
   annotations actually retrieved.
2. **Interpretation:** the relationship justified by the deepest shared
   homology-bearing level and any relevant structural evidence.
3. **Limits:** functions, mechanisms, histories, or rankings not established by
   the retrieved data.
4. **Evidence:** concise release-scoped API paths used.

Do not turn absence of a warning into positive evidence. Do not turn a protein
name, shared ligand, motif, fold, or quality score into a stronger evolutionary
claim than the hierarchy supports.
