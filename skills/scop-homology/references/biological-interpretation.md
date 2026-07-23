# Biological Interpretation With SCOP And SCOPe

Use this reference after retrieving the relevant SCOPe API records. It explains
what the hierarchy can support; it is not a substitute for release-specific
data.

## Hierarchy Semantics

SCOP/SCOPe is domain-centered. A PDB chain can contain one domain, multiple
domains, an inserted domain, noncontiguous domains, swapped elements, tags, or
regions outside ordinary globular classes.

- `class`: broad secondary-structure organization.
- `fold`: similar major secondary structures in the same arrangement and
  topology.
- `superfamily`: families inferred to share common evolutionary origin from
  structural, functional, and sequence evidence.
- `family`: a closer relationship, usually supported by sequence similarity or
  very similar function and structure.
- `protein`: essentially the same function across species or isoforms.
- `species`: a distinct sequence and its variants in SCOPe terminology.
- `domain`: a classified structural instance with a SID and SUNID.

Family and superfamily are homology-bearing levels. Fold and class alone are
structural categories, not positive SCOPe assertions of common ancestry.

## Relationship Decisions

For two domains:

1. Retrieve both lineages in the same release.
2. Identify the deepest shared class, fold, superfamily, and family nodes.
3. Check annotations and homology warnings for both domains.
4. State only the relationship justified by the evidence:
   - shared family: close SCOPe relationship;
   - shared superfamily but different families: probable common ancestry;
   - shared fold but different superfamilies: structural/topological
     similarity, not a SCOPe homology assertion;
   - shared class only: broad architectural similarity.

Do not infer a specific reaction, substrate, ligand, pathway role, phenotype,
oligomeric state, or interchangeable template from a superfamily assignment.
Those conclusions require additional sequence, active-site, ligand,
domain-architecture, genomic-context, or experimental evidence.

## Domain Boundaries And Modularity

A domain need not be contiguous in sequence. Insertions and rearrangements can
place one evolutionary module inside another. Compare domains rather than whole
chains so unrelated inserts, tags, or adjacent domains do not create false
similarity or obscure a real relationship.

Historical boundary changes can reflect newly observed evolutionary contexts.
Do not describe a particular change unless every compared release was queried.

## Special Annotations

Machine-readable heterogeneity annotations can qualify ordinary hierarchy
interpretation:

- `md`: multiple alternative domain divisions.
- `re`: repeat-based domains with different repeat counts.
- `fr`: fragment.
- `as`: additional subdomains.
- `ie`: additional insertions or extensions.
- `ms`: missing secondary structures.
- `ae`: additional elements.
- `me`: missing elements.
- `nf`: not a true fold.
- `hf`: heterogeneous fold.
- `dn`: different domains contain different subsets of subdomains.

Artifacts, synthetic tags, fragments, repeats, heterogeneous families, and
"not true" groupings should not be used as ordinary homology or benchmark
examples without preserving the caveat.

Annotations constrain an interpretation; they do not automatically erase a
family or superfamily relationship. Explain exactly which inference is
affected.

## Representative Selection

ASTRAL subsets reduce sequence redundancy. SPACI and AEROSPACI support
technical-quality comparisons and representative selection. They are not
evolutionary or functional scores.

Retrieve quality records for every candidate. Compare actual values together
with method, resolution, R factor, and available validation fields. A
technically preferred representative does not replace alternative ligand,
conformational, oligomeric, or biological states.

Never rank candidates when the requested quality measure is unavailable for
one or more of them.

## Dataset Design

For remote-homology benchmarks:

- Separate training and test examples by family while keeping positives within
  a verified superfamily.
- Use same-fold, different-superfamily examples as difficult structural
  negatives only when annotations do not invalidate that interpretation.
- Remove sequence leakage with an appropriate ASTRAL nonredundant subset.
- Preserve biologically meaningful diversity rather than selecting only the
  single highest-quality structure.
- Exclude or stratify artifacts, fragments, repeats, and heterogeneous
  groupings.
- Freeze the SCOPe release, selection rules, thresholds, and representative
  choices.

## Automated Classification

SCOPe combines manual curation with validated automated classification.
Automation is useful for placing new structures into established regions of
the hierarchy. Manual curation remains important for new folds and
superfamilies, remote relationships, artifacts, boundary corrections,
heterogeneity, and large complexes.

Do not equate automated classification with low quality. Report automated
status when it affects provenance or interpretation.

## Epistemic Boundaries

Separate four kinds of statement:

1. **Retrieved fact:** directly present in a cited API response.
2. **SCOPe interpretation:** follows from the retrieved hierarchy and
   annotation semantics.
3. **Biological hypothesis:** plausible but requires evidence outside SCOPe.
4. **Unknown from available data:** cannot be determined because a necessary
   record was not retrieved or the API does not expose it.

Use qualified language for hypotheses. Use explicit abstention for unknown
release-specific facts. Do not convert uncertainty into a confident database
claim.
