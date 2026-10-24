# Rubric

Evaluate each answer against the `must_include`, `must_avoid`, and
`scoring_focus` fields from the exact suite version recorded by the run.
Equivalent scientifically correct wording is acceptable.

Record two subscores for every primary biological question:

- `biological_reasoning_score`: whether the answer makes the justified
  evolutionary, structural, functional, or dataset-design inference and states
  its limits.
- `factual_support_score`: whether the classifications, boundaries,
  annotations, releases, identifiers, and numeric evidence supporting that
  inference are correct.

Both subscores use the 0-4 anchors below. Report them separately as the primary
result. The harness also computes a secondary gated score:

```text
weighted = 0.75 * biological_reasoning_score + 0.25 * factual_support_score
factual_gate = [1, 2, 3, 4, 4][factual_support_score]
gated_score = min(weighted, factual_gate)
```

Do not round the gated score. For API-conformance questions, set
`biological_reasoning_score` to `null` and make the
overall score equal the `factual_support_score`. Report those API-conformance
scores separately from the primary biological benchmark.

## Biological Reasoning

### 4: Complete

Answers the biological question directly, uses the correct SCOPe hierarchy
level or annotation semantics, connects the classification to an evolutionary
or structural interpretation, and clearly states what cannot be inferred.

### 3: Mostly Correct

Gives the central biological conclusion and appropriate caveat but omits one
important implication, alternative explanation, or requested evidence type.

### 2: Partial

Recognizes the general issue but confuses an important distinction, such as
family versus superfamily, homology versus analogy, complete domain versus
fragment, or technical quality versus biological relevance.

### 1: Poor

Mostly repeats classifications or generic protein knowledge without answering
the requested biological inference, or gives an unsupported interpretation.

### 0: Incorrect

Reverses the biological conclusion, asserts homology from fold alone, transfers
specific function without support, ignores a decisive caveat, or does not
answer.

## Factual Support

### 4: Complete

Uses all material classifications, boundaries, annotations, releases, public
identifiers, quality values, or other evidence requested by the question. No
material factual errors are present.

### 3: Mostly Correct

The evidence supports the answer but one noncentral identifier, release,
boundary, numeric value, or classification detail is missing or imprecise.

### 2: Partial

Some supporting facts are correct, but an important classification level,
comparison, annotation, or release-specific change is missing or confused.

### 1: Poor

Evidence is sparse, mostly unsupported, uses nonpublic internal identifiers, or
contains major factual errors while retaining a small correct element.

### 0: Incorrect

The supporting classifications or values are wrong, identifiers are fabricated,
or no relevant evidence is supplied.

## Hard Ceilings

- An endpoint-perfect answer that does not make the requested biological
  interpretation has `biological_reasoning_score <= 1`.
- A materially wrong relationship-bearing classification, such as the wrong
  family or superfamily, gives `biological_reasoning_score <= 2`.
- Fabricated release-specific classifications, identifiers, boundaries,
  annotations, or values give `factual_support_score <= 1`; wholly fabricated
  support gives `factual_support_score = 0`.
- Treating fold-level similarity as sufficient proof of common ancestry gives
  `biological_reasoning_score = 0` when that distinction is central.
- Treating AEROSPACI/SPACI as an evolutionary or functional score gives
  `biological_reasoning_score <= 1`.
- Ignoring a decisive artifact, fragment, repeat, or heterogeneous-fold
  annotation gives `biological_reasoning_score <= 2`.

Record every unmet or violated criterion in `rubric_notes`, together with the
evaluator identity and timestamp required by
`schemas/evaluation.schema.json`.

## Error Tags

Also assign sorted, unique `error_tags` for the decisive errors and omissions.
Use an empty array only when the answer has no material issue. Tags are
diagnostic; score from `must_include`, `must_avoid`, `scoring_focus`, and the
rubric above.

- `api_evidence_error`: cites unsupported API evidence, relies on a wrong
  endpoint meaning, or makes API-dependent claims without sufficient evidence.
- `fabricated_annotation`: invents a comment, inconsistency, repeat,
  heterogeneity record, homology warning, or repeat-family flag.
- `fabricated_release_fact`: invents a release-specific SID, SUNID, SCCS,
  boundary, release label, numeric quality value, or historical placement.
- `fragment_similarity_omission`: misses the need to treat fragment coverage,
  partial alignments, or fragment/full-domain score comparisons differently.
- `historical_release_omission`: misses a required before/after release,
  reclassification, boundary, or identifier comparison.
- `identifier_or_boundary_error`: omits or misstates a material public
  identifier, domain boundary, or domain selection.
- `incomplete_answer`: omits a material requested entity, comparison, or
  conclusion not covered by a more specific tag.
- `missing_annotation_caveat`: omits a material retrieved artifact, fragment,
  repeat, heterogeneity, or "not true" caveat.
- `missing_named_unsafe_transfer`: does not explicitly reject an unsafe
  function, template, mechanism, or biological-state transfer named by the
  prompt or gold criteria.
- `missing_quality_context`: gives a representative without material quality
  fields such as AEROSPACI/SPACI, method, resolution, or R factor.
- `missing_required_external_evidence`: omits an external evidence type needed
  for a stronger structural, functional, or evolutionary claim.
- `periodic_release_omission`: mishandles stable versus dated periodic release
  identifiers or citation semantics.
- `quality_ranking_error`: ranks structures with unavailable quality values or
  treats AEROSPACI/SPACI as an evolutionary or functional score.
- `unsafe_function_transfer`: transfers a specific substrate, reaction,
  ligand, regulatory role, oligomeric state, pathway role, whole-protein
  function, or template solely from family or superfamily placement.
- `unsupported_homology_claim`: asserts common ancestry from shared fold or
  class alone.
- `unsupported_nonhomology_claim`: asserts analogy, convergence, independent
  origin, or absence of homology from different superfamilies alone.
- `wrong_classification`: misstates a material family, superfamily, fold,
  class, or hierarchy relationship.
