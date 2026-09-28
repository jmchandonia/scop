# PDB-Chain BLAST And FATCAT Comparisons

Use this reference when a question asks what SCOPe-like domains may occur in an
experimentally solved PDB chain, why a chain remains unclassified, or how its
BLAST and FATCAT matches should be interpreted. These comparisons are evidence
for curation and hypothesis generation; they are not the SCOPe automatic
classifier and do not create an official classification.

## Historical And Dataset Context

The validated SCOPe automatic-classification pipeline was sequence-based. In
2017, its strict accuracy requirements limited application to about half of
newly solved structures. The earlier published method retained BLAST alignments
at least as significant as `1e-4` that covered nearly all of the target SCOPe
domain, defined as missing at most 10 residues at either target terminus.

The chain-comparison dataset described for SCOPe 2.08 places two evidence
streams side by side:

- BLAST comparisons from a target PDB-chain sequence to SCOPe domain sequences;
- FATCAT comparisons from the target PDB-chain structure to a clustered set of
  structurally representative SCOPe domains.

Pin release `2.08` when the question refers to that dataset and gives no other
release. For another release, use only the comparison records actually returned
for that release and do not assume the same comparison universe or construction
method. The supplied context does not state the chain-inclusion criteria or the
exact structural clustering procedure. If those details are not returned or
provided in a methods source, identify them as unverified instead of inventing
them. Clustering reduces comparisons but does not make retained hits independent
or exhaustive.

Historical sources:

- Fox NK, Brenner SE, Chandonia JM. 2014. *SCOPe: Structural Classification of
  Proteins—extended, integrating SCOP and ASTRAL data and classification of new
  structures.* Nucleic Acids Research 42:D304–D309.
  [SCOPe PDF](https://scop.berkeley.edu/references/fox-2014-nar-scope.pdf),
  [DOI](https://doi.org/10.1093/nar/gkt1240).
- Chandonia JM, Fox NK, Brenner SE. 2017. *SCOPe: Manual Curation and Artifact
  Removal in the Structural Classification of Proteins—extended Database.*
  Journal of Molecular Biology 429:348–355.
  [SCOPe PDF](https://scop.berkeley.edu/references/chandonia-2017-jmb-scope.pdf),
  [DOI](https://doi.org/10.1016/j.jmb.2016.11.023).

## Retrieval Workflow

1. Call `get_scop_chain(chain, release)` and record the normalized identifier,
   chain length, sequence availability, and existing `domains`. Existing domains
   are official release facts; an empty list means only that this endpoint
   returned no classified domain for that chain in the pinned release.
2. Call `get_scop_chain_hits(chain, limit=100, release)` once for the combined
   sequence and structure view. Use a split-stream tool only for a narrow
   sequence-only or structure-only question, or to retry a failed stream.
3. Record the requested limit and returned counts. The endpoint returns a
   bounded best-hit view, not a paginated all-vs-all export. If a stream reaches
   the limit, do not make claims about lower-ranked hits or absence of other
   lineages.
4. Establish the coordinate system before organizing hits into regions. BLAST
   ranges are one-based positions in the compared sequences. Current chain
   FATCAT starts retain PDB residue numbering; its legacy ends are computed from
   residue counts. They are not verified sequence intervals. Follow the
   coordinate and alignment checks below before combining streams or comparing
   their coverage with classified domains.
5. Shortlist the domains that represent each material region, evidence stream,
   and competing lineage. Retrieve their same-release lineages, annotations,
   and homology warnings with `get_scop_domains(...,
   includes=["parents","annotations","homology"])`, or the corresponding
   release-scoped REST endpoints.
6. If the conclusion depends on whether a sequence hit meets the historical
   strong-match coverage rule, retrieve the matched SID's exact ASTRAL SEQRES
   sequence with `get_scop_sequence`. Do this only for the controlling hits.

## Cluster Representatives

The viewer can redirect a clustered PDB chain to its representative. REST and
MCP deliberately retain the requested chain: its `domains`, sequence, coordinates,
and hit arrays are not silently replaced. Inspect `chain.cluster_representative`
in metadata and `subject.cluster_representative` in combined or split hit results.
If it identifies a different chain (`is_requested_chain=false`), retrieve that
identifier with `get_scop_chain_hits` or follow its release-scoped `hits_url`,
`sequence_hits_url`, or `structure_hits_url`. Preserve both identifiers and name
which chain owns each result. Empty original-chain FATCAT hits need not mean
that the representative has no evidence. A null mapping means no mapping is
available for that release; null `similarity` means unmeasured, not zero identity.
Do not transfer the representative's official domains to the requested chain.

For example, in release 2.08, 1ut5A maps to 5hmlA. Their `similarity` has 271
identical residues in 272 alignment columns: 99.63% identity, with 93.47% member
coverage and 100% representative coverage. The aligned sequence intervals are
20–291 and 1–272, respectively. Hits fetched for 5hmlA are evidence calculated
for 5hmlA, not direct comparisons for 1ut5A.

These are new Smith–Waterman local sequence measurements (BLOSUM62; gap opening
−10, extension −0.5; Biopython 1.85), not structural similarity or the original
clustering BLAST statistic. Consequently some newly measured identities can be
below the original 90% clustering threshold. `percent_identity` is 100 times
`identical_residues / alignment_columns`, including internal gap columns and
excluding unaligned termini; literal equal ambiguity symbols count as identical.
Coverage is 100 times `(end - start + 1) / full_sequence_length` for each side.
Check both coverages: high local identity need not cover the whole protein.

`sequence_source=seqres_minus_tags` differs from the normal SEQRES sequence
returned in chain metadata. Positions are **one-based inclusive positions in
these sequences**, not PDB author residue identifiers. The six counts/endpoints
do not encode internal gap positions or a residue-by-residue correspondence.
Retrieve an appropriate alignment/mapping before projecting representative hit
ranges, superpositions or domain hypotheses onto the original chain. Unaligned
regions remain unsupported by the member–representative similarity summary.

## Coordinate And Alignment Checks

For cross-stream regional agreement, retrieve a mapping between the exact
compared chain sequence and PDB residues, including insertion codes and missing
residues, and map the actual aligned residues or blocks. SCOPe RAF mappings or
an inspected equivalent source can provide this evidence when available. Use
the matching structure snapshot and map target-domain residues separately;
their PDB numbering need not start at the beginning of the domain sequence.
Do not repair numbering by adding one, subtracting a constant offset, or
clipping out-of-range values without evidence that the mapping permits it.

The current chain-hit tools do not return that mapping or residue
correspondence. Their FATCAT `chain_range.end` and `domain_range.end` are legacy
arithmetic summaries, not observed terminal residue identifiers. A range length
is the residue count on one side; neither that length nor the smaller of the
two lengths is necessarily the number of paired residues. Do not calculate
paired-residue coverage or infer alignment continuity from these fields.
The integer starts can also lose PDB insertion codes, so they are insufficient
to reconstruct a residue mapping.

If a verified mapping is unavailable, report BLAST sequence intervals and
FATCAT PDB starts/counts separately, label cross-stream regional overlap as
unresolved, and avoid merging or splitting domain hypotheses from those numbers.
If an alignment or inspected structural source is unavailable, explicitly mark
paired coverage and topology as unverified. The summaries can identify
structural candidates for follow-up; they cannot by themselves verify a
domain-scale core match or discriminate superfamily-level ancestry.

With mapped alignments available, preserve separated blocks, overlaps, and
gaps. Do not force the entire chain into one domain or turn alignment endpoints
into exact curated boundaries.

## Sequence Evidence

The chain-hit endpoint is a comparison browser and can return sequence matches
that are weaker than the historical automatic-classification criterion. Test
the criterion from the returned fields; do not equate API inclusion or rank 1
with a pipeline-qualifying match.

For a hit to satisfy the published strong-match component:

```text
log10_evalue <= -4
N-terminal target residues missing = domain_range.start - 1 <= 10
C-terminal target residues missing = target_sequence_length - domain_range.end <= 10
```

Use the length of the exact target ASTRAL domain sequence compared by BLAST.
First require `1 <= domain_range.start <= domain_range.end <= target_sequence_length`;
negative missing counts indicate inconsistent evidence, not a passing check.
The chain length and `chain_range` cannot establish target-terminal coverage.
If the exact target sequence length cannot be matched unambiguously to the hit,
report that the coverage criterion is not verifiable from the retrieved data.

These three checks characterize one strong BLAST match. They do not by
themselves establish that the whole chain met every automatic-classification
rule. Do not conflate the target-domain coverage rule with the later change
that allowed BLAST-derived annotations to be extended up to 15 residues toward
query-chain ends or coordinate gaps.

Report `log10_evalue`, percent identity, chain range, domain range, chain-region
coverage, target-terminal missing counts when verifiable, and the target's
retrieved lineage. Several hits to the same or closely related domains are
correlated support rather than independent evidence.

## FATCAT Evidence

Three-dimensional structure generally changes more slowly than primary
sequence. A homologous domain pair can therefore retain the same core topology
after sequence divergence has passed the sensitivity of BLAST. In a region
with no qualifying sequence match, a strong, domain-scale FATCAT alignment can
be more informative about possible remote homology than the negative sequence
result. Do not use "no BLAST hit" as evidence against remote homology.

This sensitivity does not make every structural match evolutionary. Similar
folds can arise without a demonstrated common ancestor, and recurrent
architectures, repeats, short secondary-structure arrangements, or flexible
alignments can generate significant matches. A structure-led remote-homology
hypothesis is most credible when the alignment covers a substantial domain
core, preserves detailed topology, points consistently to one retrieved SCOPe
superfamily, and survives comparison with the strongest alternative
superfamilies. Motif-sized or fold-only agreement supports structural
similarity, not remote homology.

Interpret FATCAT P-value, score, RMSD, identity/similarity when returned,
residue counts, coordinate provenance, and competing lineages together. In the
current chain API, `rmsd` is the initial RMSD (`ini_rmsd`), not optimized RMSD;
report it by that name. Only report paired length, aligned blocks, or preserved
topology when the retrieved alignment or inspected structure supports them. A
FATCAT P-value measures structural-match significance; it is not the probability
that the chain is homologous or correctly classified. A short local match,
recurrent topology, repeat, or alignment spanning several chain domains can be
misleading even when its P-value is small.

The structural targets form a representative subset, unlike the sequence
stream's domain comparison universe. Do not compare raw hit counts between the
streams as if they had the same sampling frame. Do not treat missing FATCAT hits
as proof that no structural relationship exists, especially when the returned
page is bounded or the representative construction is incompletely documented.

## Combining The Streams

- Sequence and FATCAT hits with verified mapping to the same chain region that
  converge on the same retrieved family or superfamily support a stronger
  non-official regional hypothesis than either stream alone.
- A BLAST hit that satisfies the historical significance and target-coverage
  checks is strong sequence evidence, but still report conflicts, chain
  architecture, special annotations, and whether the chain already has a
  curated classification.
- When BLAST returns no qualifying match, verified domain-scale FATCAT convergence can
  supply the leading evidence for remote homology. It can support a cautious
  superfamily-level candidate when alignment extent, topology, and competing
  lineages discriminate that superfamily; otherwise report only fold-level
  structural similarity. It does not establish homology or an official
  placement. With only the current summary fields, report candidate structural
  matches and the missing alignment/mapping evidence; do not assert that this
  domain-scale or topology check passed.
- Evaluate competing hits on comparable mapped regions and alignment quality.
  API inclusion, a small P-value, or a high rank alone does not make a short or
  poorly superposed match a material contradiction to extensive evidence.
  Material conflicting families within one superfamily normally stop the
  conclusion at a superfamily hypothesis. Conflicting superfamilies within one fold normally
  stop it at fold-level similarity. Material conflicts across folds, poor
  extent, or missing lineage evidence make the region inconclusive.
- Sequence and structure comparisons are not automatically independent: they
  can reuse related SCOPe targets, and structurally representative targets can
  be evolutionarily correlated.

Do not use the separate `scop_node_fatcat_chain_benchmark` aggregate as though
it were another hit for the current chain. That statistic summarizes historical
classification consistency under its own filters; chain-to-domain FATCAT
records are current comparison evidence.

## Reporting

State that any proposed placement is a non-official evidence hypothesis. For
each material region, report:

- chain sequence interval or FATCAT PDB start/count, with its coordinate system;
  regional overlap only when verified by a retrieved mapping;
- controlling BLAST hits and whether each historical strong-match check passed,
  failed, or could not be verified; if none qualified, distinguish sequence
  divergence from missing sequence data or a bounded-result limitation;
- controlling FATCAT hits and their structural metrics, distinguishing initial
  RMSD, per-side residue counts, and any independently verified paired coverage;
- exact same-release SCOPe lineage through the level being discussed, plus
  important alternative lineages;
- existing SCOPe domains on the chain, relevant annotations or homology
  warnings, bounded-result limitations, missing construction provenance, and
  any unresolved residue mapping or topology check;
- the effect of each observation on the conclusion, with the returned REST URL
  for every cited record.

Do not report the chain as classified, use hit ranges as exact domain
boundaries, infer function from a family name alone, or describe an empty hit
list as evidence of no relationship.
