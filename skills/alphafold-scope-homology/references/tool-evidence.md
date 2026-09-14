# MCP And REST Evidence

Use these MCP tools for bounded release-scoped retrieval and evidence joins.

Public REST base: `https://scop.berkeley.edu/api/v1`.
OpenAPI: `https://scop.berkeley.edu/api/v1/openapi.yaml`.
Append every release-scoped fallback below to that base; these paths are not
relative to the website root. Replace placeholders with public identifiers and
the selected release before requesting a URL.

## AlphaFold

`list_alphafold_domain_candidates(min_regions=1, max_regions=1)` screens
single-region models; use `min_regions=2, max_regions=3` for two or three regions. Every reported region
has at least `min_hits_per_region` qualifying sequence-plus-structure hits,
and `min_region_hit_fraction` sets the minimum fraction of qualifying hits
assigned to the reported regions: `assigned_hit_count / qualifying_hit_count`
in the candidate index. For example, eight assigned hits out of ten qualifying
hits gives `region_hit_fraction=0.8`, regardless of the fraction of residues
covered. This measures regional concentration of hits, not confidence in
homology; redundant matches can contribute. `core_coverage_fraction` separately
reports the fraction of the model spanned by candidate core intervals, including
any short gaps bridged during core construction. Use `max_sequence_hits` together with
`min_structure_hits` to prioritize structure-led candidates with sparse
sequence evidence.

REST fallback:
`/releases/{release}/alphafolds/domain-candidates`.

These are candidate-generation results, not curated boundaries or homology
assignments. Continue with the model, hit, lineage, annotation, homology, and
statistics calls below before forming an evidence hypothesis.

`get_alphafold_model(model, release, compact)` returns compact metadata by
default:

- model name and UniProt accession;
- sequence length and global pLDDT;
- linked SCOPe classifications and evidence URLs.

The raw sequence and `residue_plddt` array require `compact=false`. The REST
endpoint can additionally restore the redundant position map with
`include_plddt_by_position=true`, but normal MCP analysis should prefer
`get_alphafold_regions_confidence` when regional confidence affects a decision.

The model and AlphaFold sequence-hit responses also return
`sequence_evidence`. It resolves the query only through the exact
`af_model.uniprot_id` relation. `stored_blast_result_count > 0` shows that raw
BLAST rows exist for that sequence and release; those rows can all disappear
from the public hit list after significance, target-style, rejection, and
per-SID deduplication filters. A count of zero is not proof that BLAST ran.

REST fallback:
`/releases/{release}/alphafolds/{model}`.

`get_alphafold_regions_confidence(model, regions=[{start, end}], flank_size, release)` uses
that model response to calculate deterministic statistics for a one-based,
inclusive range. It reports model coverage, mean and median pLDDT, counts and
percentages in the `>=90`, `70-<90`, `50-<70`, and `<50` bands, left and right
flank summaries, contiguous `>=90` clusters of at least three residues, and
sustained `<70` and `<50` stretches of at least five residues. Its
`confidence_pattern` states whether several high-pLDDT clusters fragment the
region and counts sustained low-confidence stretches; these are descriptive
geometry-confidence patterns, not inferred boundaries.
It also reports whole-model statistics for a direct global-versus-local
comparison. Use it for every candidate region whose predicted-structure
confidence affects the assessment; do not manually average the pLDDT array
when this tool is available. Its source URL is the model endpoint above.

`get_alphafold_regions_confidence(model, regions, flank_size, release)` returns
the same summaries for 1-6 `{start, end}` ranges while loading the model only
once. Use this batched form for an assessment with one or more known ranges;
a one-element regions list handles a single range.

After inspecting combined hits, prefer
`get_alphafold_decision_evidence(model, regions, sids, statistics_requests,
flank_size, release)` for a regional relationship assessment. It accepts 1-6
material ranges and 1-6 representative SIDs, retrieves regional confidence and
the representatives' parent/annotation/homology evidence concurrently, and
returns each compact lineage keyed as class, fold, superfamily, and family.
Optional calibration records have exactly `{sid, level,
minimum_supporting_hits}`. `level` is `fold`, `superfamily`, or `family`; the
tool resolves that exact parent SUNID from the retrieved lineage before calling
the statistics. This prevents a representative domain or similarly named node
from being substituted for the relationship level being reported. Shared
node/threshold requests are fetched once and mapped back to each source SID.
The bundle is a deterministic join and does not infer a relationship level or
domain boundary. Do not follow it with redundant confidence, domain, or
statistics calls.

`get_alphafold_hits(model, limit, release, include_transforms, compact)` is the normal starting point. It
returns both ranked sequence hits and ranked FATCAT structure hits in one
response. It also retrieves the model length and attaches a deterministic rank,
inclusive-range coverage count/fraction/percentage, and a within-stream
overlap-plus-exact-SCCS group ID and size to every hit. Each structure hit also
contains `chain_statistics_applicability`, which compares the current P-value
with the historical `P <= 1e-4` filter and, when true alignment/component
length inputs exist, compares their length match with the `>=0.80` filter.
The status is `outside_statistics_filters`, `within_statistics_filters`, or
`uncertain_missing_filter_inputs`. Do not substitute
`domain_range.length` for the component's full ASTRAL sequence length.
`hit_summary` reports
raw and grouped counts. The grouping threshold is at least 80% overlap relative
to the shorter query range; treat it as a reproducible first-pass redundancy
heuristic, not proof of independent evidence. All returned alignment `start`
and `end` positions are one-based and inclusive; `length` is the stored aligned
length. FATCAT transformation matrices are omitted by default because they are
large and are not needed for evidence interpretation. Set
`include_transforms=true` only when the transformation itself is the subject of
the request. With the default `compact=true`, each evidence stream returns at
most 12 hits. It first keeps the best hit for each query interval with less
than 50% overlap to a retained interval, then fills the remaining budget with
ranked overlap-SCCS group representatives. `hit_summary` retains raw/grouped
counts and SCCS frequencies while omitting redundant member lists. Set
`compact=false` only when every raw hit record is necessary.

REST fallback:
`/releases/{release}/alphafolds/{model}/hits?limit={limit}` plus the model
endpoint for its sequence length.

`get_alphafold_hits(model, start=start, end=end, limit=limit,
minimum_overlap_fraction, release)` filters the combined ranked hit streams to
one explicit one-based inclusive interval. A hit is retained when its overlap
divided by the shorter of the hit interval and requested interval meets the
stated threshold. The response reports every retained hit, overlap counts and
fractions, raw counts, overlap-plus-SCCS group counts, and SCCS frequencies.
This is deterministic evidence organization only; it does not infer a domain
boundary, biological relationship, or SCOPe classification. Its REST inputs
are the combined hit and model endpoints above.

`get_alphafold_hits(model, evidence="sequence", compact=false)` returns only the sequence
part, with SID, SCCS, log10 E-value, percent identity, and query and domain
alignment ranges. Its REST fallback is
`/releases/{release}/alphafolds/{model}/sequence-hits?limit={limit}`.

`get_alphafold_hits(model, evidence="structure", compact=false)` returns ranked FATCAT
structure hits with SID, SCCS, P-value, identity, similarity, score, RMSD, and
query and domain alignment ranges.

Its REST fallback is
`/releases/{release}/alphafolds/{model}/structure-hits?limit={limit}`.

Increase either bounded limit only when the first result set does not resolve
competing classifications.

`list_alphafold_uniprot_matching_domains(model, limit, cursor, release)` lists
SCOPe domains connected to the model through its UniProt accession. Use this
as cross-reference context only; it does not report a model alignment and does
not independently validate a sequence or FATCAT hit.

REST fallback:
`/releases/{release}/alphafolds/{model}/uniprot-matching-domains?limit={limit}`.

## SCOPe Domain Evidence

For one shortlisted SID, call `get_scop_domains(sids=[sid],
includes=["parents","annotations","homology"], release, compact=true)`. For two
or more, call `get_scop_domains(sids, includes=["parents","annotations",
"homology"], release, compact=true)` once. The batch tool accepts 1-6 distinct
SIDs, fetches all domain evidence concurrently, and preserves input order.
Compact records retain lineage nodes, annotations, homology fields, and source
URLs without repeating subresource envelopes. `lineage_by_level` makes the
class, fold, superfamily, and family nodes explicit. Each requested SID returns
either its evidence or a structured error; a failed SID does not discard
successful peers. Retry only the failed item when it is decision-relevant. Select only the needed includes for focused requests; repeating successfully retrieved evidence wastes time and context.

REST fallbacks:

```text
/releases/{release}/sids/{sid}
/releases/{release}/sids/{sid}/parents
/releases/{release}/sids/{sid}/annotations
/releases/{release}/sids/{sid}/homology
```

Compare lineage nodes by public level and SUNID. Use the SID and SCCS embedded
in a hit only to shortlist it; do not use those fields as a replacement for
lineage and warning retrieval.

`list_domain_alphafold_structure_matches(sid, limit, cursor, release)` performs
the reverse lookup when the question starts from a SCOPe domain. It returns one
best stored FATCAT match per AlphaFold model in a bounded page. Follow
`next_cursor` to inspect later pages.

REST fallback:
`/releases/{release}/sids/{sid}/alphafold-structure-matches?limit={limit}`.

## FATCAT Chain-Statistics Context

`get_scop_fatcat_chain_statistics(requests=[{sunid, minimum_supporting_hits}], release)`
returns the node-specific `match_fraction`, `n_cases`, the
selected support threshold, derived match/non-match counts, and the selected node identity for a family, superfamily, or fold SUNID. Call it for the
exact lineage node used by a FATCAT-driven hypothesis and use a threshold that
was specified before inspecting the returned fraction. The threshold describes
historical statistics-case construction, not the number of current-target hits
required to support a claim. A successful response can also report `no_observations`,
`threshold_unavailable`, or `not_applicable`; none means a fraction of zero.

For statistics assessments, prefer one
`get_scop_fatcat_chain_statistics(requests, release)` call. It accepts 1-6
distinct `{sunid, minimum_supporting_hits}` records, runs them concurrently,
preserves each successful result
when another request returns a structured error. A one-element requests list handles a single lookup.

When calibration belongs to an AlphaFold regional assessment, prefer the
decision bundle above. Its output pairs `requested_level` with the exact
`resolved_node`, which should match the relationship level stated in the
answer. Use the raw SUNID batch only when the question itself starts from known
nodes or no AlphaFold representative SID is available.

`get_scop_fatcat_chain_statistics` with three requests for the same SUNID at thresholds 2, 3, and 5 returns the
2, 3, and 5 strata side by side when threshold sensitivity is material and no
single stratum was prespecified. Report the full comparison; do not pick the
largest fraction or treat any stratum as current-target evidence.

REST fallback:
`/releases/{release}/sunids/{sunid}/fatcat-chain-statistics?minimum_supporting_hits={2|3|5}`.

The statistic is classification consistency for the stored sample, not a
homology score or a probability that the current hit is correct. Its
90%-identity representatives and same-chain exclusion reduce only some
redundancy. The aggregate remains conditioned on uneven study, structure
availability, classification, curation, and correlated sequence/structure
sampling, and qualifying FATCAT results can still include false positives. A
high value adds no positive confidence; a low or poorly sampled value may add
caution. Do not compare node fractions as balanced performance scores. Read
`fatcat-chain-statistics.md` before interpreting it.

## Evidence Hygiene

- Keep every call in the same release.
- Record every returned `api_urls` value.
- Do not treat missing fields as zero or false.
- Do not treat absence of warnings as independent positive evidence.
- Do not silently switch identifiers, releases, or AlphaFold model versions.
- Do not claim that linked domains sharing a UniProt accession independently
  validate a FATCAT or sequence hit.
