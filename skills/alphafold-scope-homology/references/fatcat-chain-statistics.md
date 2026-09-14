# FATCAT Chain-Statistics Caveats

Public REST base: `https://scop.berkeley.edu/api/v1`. Append the fallback path
below to this base and replace its placeholders before making a request.

The reference filters and sampling assumptions below describe the existing
assessment workflow. Their use in generating the currently installed aggregates
has not been verified. Keep using retrieved counts and fractions as qualified
context; do not treat passing the reference filters as proof of applicability or
validated accuracy. The API intentionally returns no provenance fields, and no
provenance metadata is required to retrieve a row.


Use the `scop_node_fatcat_chain_benchmark` statistics as a description of
classification consistency within a filtered historical sample of FATCAT hits
to already-classified component domains. Do not generalize the sample scalar
into reliability for the current hit or for protein space as a whole.

Retrieve a row with
`get_scop_fatcat_chain_statistics(requests=[{sunid, minimum_supporting_hits}], release)` or the
REST fallback
`/releases/{release}/sunids/{sunid}/fatcat-chain-statistics?minimum_supporting_hits={2|3|5}`.

## Meaning Of The Fields

Match a row by both `classification_level` and `classification_sunid`. Do not
join or infer a row from a similar name or SCCS string.

- `match_fraction` is the number of statistics cases whose SCOPe
  classification agreed with the aligned component at that row's family,
  superfamily, or fold level, divided by all statistics cases for that node.
- `minimum_supporting_hits` is the statistics construction threshold. Select it
  explicitly from 2, 3, or 5 before inspecting the result and report it. The
  threshold describes historical statistics-case construction; do not derive it
  from the number of current-target hits. If no threshold was prescribed, omit
  calibration unless the question asks for a full 2/3/5 sensitivity comparison.
- `n_cases` is the denominator. Always report it with the fraction.
  A fraction based on a few cases is weakly calibrated and must not be treated
  like the same fraction based on many cases. Because cases can remain
  correlated, this count is not an effective independent sample size.

The fraction is level-specific. A family fraction says nothing directly about
the superfamily or fold fraction unless those rows were separately retrieved.
A missing row means the statistics provide no statistic for that node; it does
not mean a fraction of zero.

## Sampling And Redundancy Bias

The scalar is conditional on the statistics sample, not an unbiased estimate
over protein space. A case could enter only when relevant structures,
classifications, component assignments, and qualifying FATCAT results were
available. The distribution therefore reflects historical study,
structure-determination, database-deposition, and SCOPe-curation choices.
Well-studied families and folds may be overrepresented or more homogeneous;
sparse or understudied nodes may have small denominators or no row. Fractions
from different nodes are not automatically comparable.

The reference sample's 90%-sequence-identity representative chains reduce close
sequence duplication, and same-PDB-chain exclusion reduces one circularity.
They do not guarantee independent observations. Homologs below 90% identity,
multiple related structural representatives, repeated family/fold membership,
and correlations created by shared experimental and curation history can
remain. The returned aggregate does not expose case-level sequence clusters or
structural clusters, so an agent cannot reweight or fully correct these biases
from `match_fraction` and `n_cases` alone.

Structural-representative selection in an upstream comparison can reduce
additional duplication when it is explicitly documented, but it still does
not make the retained cases independent. Report the retrieved representative
method or threshold. Do not assume a structural-representative control that is
not present in the statistics method fields or other current-task provenance.

The FATCAT significance, length-match, support-depth, and ambiguity filters
reduce obvious noise but do not eliminate false-positive structural matches,
especially for recurrent architectures, repeats, simple topologies, or
borderline component assignments. State this residual false-positive risk
whenever the scalar materially enters the discussion.

## Current-Hit Applicability

Before transferring a historical scalar to a current AlphaFold FATCAT hit,
verify that the hit could have entered the statistics. Compact structure hits
from `get_alphafold_hits` expose `chain_statistics_applicability`:

- `outside_statistics_filters` means the current P-value exceeds `1e-4` or a
  known length match is below `0.80`. The scalar is outside its calibration
  domain and must not be reported as reassuring or as "no additional warning."
- `uncertain_missing_filter_inputs` means one or more required inputs are not
  exposed for the current hit. In particular, an alignment's
  `domain_range.length` is not automatically the assigned component's full
  ASTRAL sequence length. Report applicability as uncertain and do not use the
  fraction to preserve a relationship label.
- `within_statistics_filters` means the exposed P-value and length-match inputs
  satisfy the historical filters. It does not remove the sampling, redundancy,
  component-assignment, or AlphaFold-transfer caveats below.

The status is about applicability, not current-hit quality. Continue to
interpret the current P-value, aligned length, coverage, competing lineages,
and predicted-structure confidence directly.

## How To Use It

Treat the statistic as a reliability caveat on classification from FATCAT, not
as another hit and not as evolutionary evidence.

- A low match fraction supported by many observations is a material warning
  against an overconfident family, superfamily, or fold claim based mainly on
  FATCAT hits to that node.
- For a current hit that is within the statistics filters, a high fraction with
  an apparently adequate denominator means only that the
  retained cases for that biased sample were usually classification-consistent.
  Report it as "no additional warning from these statistics," never as positive
  confidence. It cannot strengthen or preserve a label, rescue a weak or
  partial hit, offset false-positive risk, or establish homology.
- Do not apply universal high/low cutoffs. Compare the fraction, denominator,
  alignment extent, and the other retrieved evidence explicitly.
- Do not rank competing nodes by their fractions or treat a larger denominator
  as proof of better calibration; node composition and ascertainment differ.
- Conflicting sequence evidence, incompatible lineages, poor coverage, or low
  local pLDDT remain controlling caveats regardless of the statistics value.

When several shortlisted hits map to different nodes, report the relevant
statistic for each competing node rather than averaging their fractions. Keep
the fractions separate and explicitly say that their samples may not be
exchangeable.

For transparent reporting, give: `matches / n_cases` when the match
count can be recovered without inventing precision, the decimal fraction, the
support threshold, statistics filters, redundancy controls that were present,
residual redundancy and sampling biases, and the exact effect on the label.
Also report the raw and conceptually nonredundant current-hit counts separately;
the statistics denominator is not a substitute for deduplicating current hits.

## Scope And Limitations

The statistics used 90%-identity representative chains from SCOPe release ID 19
and retained FATCAT hits with `P <= 1e-4`; it did not use AlphaFold predictions.
It therefore does not measure AlphaFold model error, pLDDT calibration, or the
accuracy of the current predicted structure. Confirm statistics provenance and
node compatibility before transferring the statistic to a different SCOPe
release.

The statistics retained qualifying FATCAT hits whose alignment length matched
an assigned component's ASTRAL sequence length with:

```text
min(alignment length, component length)
--------------------------------------- >= 0.80
max(alignment length, component length)
```

Do not use its scalar to reassure a short motif, isolated secondary-structure
element, or single-alpha-helix match that lacks comparable domain-length
coverage. Same-PDB-chain hits were excluded to reduce circularity.

Component assignment was based on length. When the two best component scores
differed by less than `0.05`, the observation was marked ambiguous and counted
as a non-match in scalar denominators. This makes the reported fraction
conservative, but length-based assignment can still confuse similarly sized
components and does not prove that an alignment follows exact domain
boundaries.

The statistic does not replace FATCAT P-value, score, RMSD, aligned length,
query coverage, lineage agreement, annotations, homology warnings, sequence
evidence, or local AlphaFold confidence. It is specifically a caveat about the
historical classification consistency of filtered chain-to-domain alignments.
