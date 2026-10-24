# Typed Scientific Decision Record Architecture

- **Status:** Rolled back; archived experimental design, not the active implementation
- **Date:** 2026-09-04
- **Scope:** AlphaFold–SCOPe evidence retrieval, scientific claim construction,
  deterministic audit, and benchmark evaluation

On 2026-09-06 the user requested restoration of r7 after the completed r11/r12
experiments regressed scientifically and in end-to-end performance. R7's exact
implementation has been recovered. The proposal below is retained as history,
not an instruction to continue implementing or deploy this architecture.
See [restoration verification](R7_RESTORATION.md).

## Decision

Replace the current prose-first scientific workflow with a typed, traceable
decision record backed by a complete-for-purpose evidence packet.

The system will:

1. collect and summarize all material evidence in the configured search window;
2. expose representative competing lineages instead of only the highest-ranked
   compact hits;
3. compute a policy-based ceiling on relationship specificity and state the
   evidence blocking a stronger claim;
4. require one structured record for every reported region;
5. validate claims, numbers, provenance, caveats, and evidence coverage before
   rendering the user-facing answer; and
6. evaluate the change once in a predeclared, independently reviewed blind
   holdout.

This is a reporting and decision-safety architecture. It does not make an
official SCOPe assignment and does not claim to infer evolutionary truth
deterministically.

## Original motivation

R7 solved the reliability and efficiency bottlenecks without producing a
corresponding large scientific gain:

| Run | Score | Scientific correctness | Evidence completeness | Pass@1 | MCP calls | Median answer input |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| r5 | 42/48 (3.50) | 3.83 | 3.42 | 12/12 | 43 | 178,228 |
| r6 | 39/48 (3.25) | 3.92 | 3.25 | 10/12 | 42 | 134,984.5 |
| r7 | 40/48 (3.33) | 3.83 | 3.25 | 12/12 | 24 | 100,463.5 |

R7 used exactly two successful MCP calls for every question and had no rejected
attempts. Relative to r6, calls fell 42.9%, median input fell 25.6%, and the
score gained one point. Relative to r5, calls fell 44.2% and median input fell
43.6%, but the score remained two points lower. Retrieval cost and execution
reliability are therefore no longer the main constraints.

The remaining failures have two architectural causes:

1. **Selection is caller-controlled.** The joined
   `get_alphafold_decision_evidence` tool resolves evidence and calibration only
   for SIDs and relationship levels selected by the caller. It cannot detect a
   material competitor that was not selected, and it cannot determine that the
   requested relationship level is too specific.
2. **Scientific completeness is prose-controlled.** The skill asks for material
   facts, but nothing mechanically requires those facts to survive into the
   answer. Exact ranges, coverage, confidence bands, redundancy, provenance, or
   partner-dependence caveats can be retrieved correctly and still be omitted.

The eight r7 answers below 4/4 illustrate general failure classes rather than a
need for eight new prompt rules:

| General failure class | Development examples | Scientific risk |
| --- | --- | --- |
| A material competing lineage is hidden by compact selection | AFQ002 | A conclusion appears more specific or more certain than the full searched evidence supports. |
| Correlated hits are not explicitly identified as redundant | AFQ003, AFQ006 | Repeated chains or related records appear to be independent support. |
| The caller chooses a relationship level that is too specific | AFQ004 | A family claim is made when the controlling evidence converges only at superfamily. |
| Retrieved provenance or evidence-stream state is omitted | AFQ005, AFQ009 | Readers cannot tell whether sequence evidence was available or whether a negative statement was justified. |
| Region-specific confidence, coverage, or exact ranges are dropped | AFQ009, AFQ010, AFQ011 | The scope and geometric reliability of a local hypothesis are misstated. |
| A scientifically triggered caveat is omitted | AFQ010 | Partner-dependent structure is interpreted from an incomplete single-chain context. |

Adding more prose instructions would leave the same enforcement gap, increase
context cost, and encourage overfitting to these 12 development questions. The
new architecture moves deterministic facts and safety checks into code while
leaving genuinely scientific interpretation explicit and reviewable.

## Goals

- Make every material statement traceable to a release-qualified evidence item.
- Prevent a claim from being more specific than the evidence contract permits.
- Preserve material competing branches from the entire configured result window.
- Distinguish evidence absence from missing, filtered, truncated, or unsearched
  evidence.
- Compute ranges, coverage, confidence summaries, and benchmark applicability
  once, deterministically.
- Make redundancy, automated annotation, low-confidence geometry, and
  partner-dependence first-class data rather than optional prose.
- Keep the user-facing answer compact.
- Version the schema and scientific policy so saved results remain reproducible.

## Non-goals

- Producing an official SCOPe classification.
- Declaring homology from pLDDT, a structural match, or a historical benchmark
  fraction.
- Replacing expert review or treating a policy ceiling as biological ground
  truth.
- Building a general de novo domain-boundary predictor.
- Returning every raw hit to the language model.
- Adding question-specific checks derived from hidden gold answers.

## Architecture

```text
SCOPe/AlphaFold REST evidence
            |
            v
  Deterministic evidence collector
  - full configured hit window
  - regional coverage and confidence
  - diverse lineage representatives
  - provenance and completeness state
            |
            v
       Evidence packet
       (versioned + hashed)
            |
            +----------------------+
            |                      |
            v                      v
  Scientific claim author   Specificity/caveat policy
            |                      |
            +----------+-----------+
                       v
              Typed decision record
                       |
                       v
             Deterministic validator
              errors + warnings +
              normalized record
                       |
                       v
        Deterministic evidence table and
        compact user-facing explanation
```

The evidence packet contains observations. The decision record contains the
claim being made about those observations. The validator connects the two and
prevents unsupported or incomplete claims from being rendered as finished
answers.

## 1. Deterministic evidence collector

The collector will evolve the existing joined decision-evidence path rather than
introduce a disconnected evidence system.

### Inputs

- AlphaFold model identifier and SCOPe release;
- one to six proposed regional envelopes;
- configured sequence and structure result-window sizes;
- any calibration threshold explicitly prescribed by the question or caller;
- policy and schema versions.

Regional envelopes remain hypotheses. The collector calculates evidence about
them but does not present them as official boundaries.

### Full-window coverage before compaction

Selection must operate over the complete configured result window, currently up
to 100 sequence and structure hits when that window is requested. Compaction
occurs only after the following summaries have been calculated:

- overlap with every proposed region;
- rank-window saturation, including whether ranks 1 through N repeat the same
  region or lineage;
- counts by evidence stream and SCOPe class, fold, superfamily, and family when
  known;
- repeated SID, PDB, chain, interval, and lineage groups;
- one best representative for each material region × evidence-stream × distinct
  lineage branch;
- any branch omitted because an explicit output cap was reached.

A representative is selected by versioned, deterministic ordering based on
materiality, rank, regional overlap, significance, aligned length, and stable
identifier tie-breaking. The ordering must not use benchmark gold answers.

If all material branches cannot be retained, the packet must say
`truncated`. A truncated packet cannot support an unqualified absence claim or a
claim that depends on the omitted branches.

### Evidence-stream state

Each region records sequence and structure streams separately:

- `available_with_material_hits`;
- `available_without_regional_hits`;
- `unavailable`;
- `not_searched`; or
- `truncated`.

Negative statements are always scoped to the searched window. “No returned
regional FATCAT hit” is permitted only when the structure stream is known to
have been searched completely for that window; it is never rendered as “no
structural relationship exists.”

### Deterministic regional facts

For each region, the collector computes:

- material hit union/envelope and representative cores;
- aligned residues, model-length coverage, regional coverage when definable, and
  explicit denominators;
- sequence rank, E-value, identity, and provenance;
- FATCAT score, RMSD, P-value, alignment length, and benchmark-filter
  applicability when available;
- complete lineage through the relevant SCOPe nodes;
- local pLDDT median, four-band distribution, high-confidence fragmentation,
  and sustained stretches below 70 and 50;
- overlap, redundancy, automated-match, repeat, and partner-dependence triggers;
- exact source URLs, release, retrieval time, and evidence hashes; and
- stored query-sequence availability as a separate provenance fact.

The collector must preserve item-level errors. One unavailable representative or
calibration record must not erase successful peers.

## 2. Specificity and caveat policy

The policy produces a **claimability assessment**, not an official
classification. Its result is versioned and reproducible.

For each region and claim kind, it reports:

- the most specific common lineage supported by the controlling evidence;
- the maximum level permitted by the current evidence policy;
- agreement and conflict by sequence and structure stream;
- evidence dependence and redundancy;
- the next stronger level considered; and
- explicit blockers to that stronger level.

Claim kinds are kept distinct:

- `relationship_hypothesis` for a provisional evolutionary interpretation;
- `structural_similarity` for geometry-only resemblance;
- `architectural_similarity` for a fold/class-level architectural comparison;
- `unresolved` when evidence is inadequate or materially conflicted; and
- `not_favored` when the requested hypothesis is not the best explanation of
  the searched evidence.

Core policy invariants are:

- Family-level relationship hypotheses require family-discriminating evidence,
  not merely a representative carrying a family annotation.
- Material family disagreement lowers the permitted relationship level to the
  supported common ancestor or leaves the result unresolved.
- A structure-only match may support a structural-similarity statement but
  cannot by itself be converted into a homology conclusion.
- Redundant chains and closely related records do not count as independent
  support.
- Low pLDDT changes trust in predicted geometry; it is not evolutionary
  evidence.
- An automated SCOPe match is reported as such and is not silently treated as a
  manual assignment.
- A chain-benchmark fraction is calibration context only. It never raises the
  relationship ceiling and is interpreted only within its inclusion filters.
- A claim may always be weaker than the policy ceiling. A stronger claim
  requires an explicit, reviewable override and cannot pass the benchmark's
  deterministic audit automatically.

## 3. Typed scientific decision record

The exact serialization will be defined in a JSON Schema. The following is an
illustrative shape, not yet the final wire contract:

```yaml
schema_version: "2.0"
policy_version: "1.0"
evidence_packet_id: "sha256:..."
model: "AF-..."
release: "2.08"
provenance:
  uniprot_accession: "..."
  query_sequence:
    status: "available"
    source_evidence_id: "..."
regions:
  - region_id: "R1"
    boundary:
      envelope: {start: 262, end: 382, approximate: true}
      cores:
        - {start: 284, end: 370}
      basis_evidence_ids: ["seq:...", "fatcat:..."]
    claim:
      kind: "relationship_hypothesis"
      status: "tentative"
      level: "superfamily"
      node: {sunid: 12345, sccs: "d.58.7", description: "..."}
    claimability:
      maximum_permitted_level: "superfamily"
      most_specific_common_lineage: "d.58.7"
      next_stronger_level: "family"
      blockers: ["material_family_conflict"]
    evidence:
      selected_sequence_ids: ["seq:..."]
      selected_structure_ids: ["fatcat:..."]
      competing_evidence_ids: ["seq:..."]
      redundancy_groups: ["redundancy:..."]
      stream_state:
        sequence: "available_with_material_hits"
        structure: "available_with_material_hits"
    confidence:
      summary_evidence_id: "confidence:R1"
      geometry_interpretation: "mixed"
    calibration:
      requested: true
      prespecified_threshold: 5
      resolved_node: {sunid: 12345, level: "superfamily"}
      status: "available"
      applicability: "outside_benchmark_filters"
      interpretation: "context_only"
    caveat_codes:
      - "approximate_boundary"
      - "automated_scop_match"
      - "correlated_sequence_and_structure_evidence"
    rationale:
      supporting_evidence_ids: ["seq:...", "fatcat:..."]
      contradicting_evidence_ids: ["seq:..."]
      relation_codes:
        - "sequence_structure_agree_at_claimed_level"
        - "stronger_level_blocked_by_material_conflict"
```

Every numeric or categorical assertion is either copied from the evidence packet
or deterministically derived from cited evidence IDs. Rationale uses validated
relation codes rather than unconstrained scientific prose.

## 4. Deterministic validation

Validation is a pure operation over an evidence packet and proposed decision
record. It performs no network writes and returns a normalized record plus
machine-readable errors and warnings.

### Hard errors

- unknown, stale, or mismatched evidence-packet ID;
- release, model, schema, or policy mismatch;
- a residue range, coverage value, metric, SID, SUNID, or SCCS code that does not
  match its cited evidence;
- a claim more specific than the policy ceiling;
- a claimed node outside the representative's retrieved lineage;
- omitted material competing branches;
- an absence claim when the relevant stream is unavailable, unsearched, or
  truncated;
- calibration at a different node or relationship level from the reported
  claim;
- an invented calibration threshold rather than one prescribed by the task;
- omission of a caveat marked mandatory by a deterministic trigger; or
- wording that presents the record as definitive homology or an official SCOPe
  classification.

### Warnings

- weak, partial, compositionally suspect, or geometry-only support;
- a small or unstable benchmark denominator;
- correlated evidence sources;
- approximate or disputed boundaries;
- low or mixed local confidence;
- unresolved family-level disagreement; and
- an explicit scientific override requiring expert review.

Warnings remain visible in the normalized record and renderer. They cannot be
silently discarded for brevity.

## 5. Rendering and audit artifacts

The normalized record, not free-form prose, is the source of truth.

The user-facing response retains the existing compact three-part form:

1. direct answer and one provisional hypothesis per region;
2. a deterministic evidence table containing required metrics, competing
   branches, confidence, and calibration; and
3. limitations, mandatory caveats, and release-qualified sources.

The evidence table, caveat list, and scientific explanation are generated from
the normalized record using templates attached to validated relation and caveat
codes. Formatting may vary, but the benchmark path does not add model-authored
scientific claims after validation. Saved benchmark artifacts contain the
evidence packet hash, typed record, validation result, and rendered answer so
later reviewers can separate retrieval, policy, validation, and rendering
failures.

## API and tool evolution

The intended MCP path is:

1. `get_alphafold_hits` for initial discovery when candidate regions are not
   already supplied;
2. an evolved `get_alphafold_decision_evidence` that returns the versioned,
   full-window-aware evidence packet and claimability facts; and
3. a pure `validate_alphafold_decision_record` call before the final answer.

The third call accepts exactly one of the full version `2.0` record or a compact
version `1.0` `decision_intent`. Intent contains one explicit region ID, claim
kind, and claim level per packet region, with an optional prespecified
calibration threshold. The pure compiler derives all boundaries, nodes,
evidence, confidence, calibration context, caveats, and rationale codes from the
packet, then applies the full record validator. It never selects a stronger
claim or resolves an ambiguous calibration by choosing the most favorable
stratum. Null claims retain collected calibration as context. Compilation uses
the packet timestamp, so the same packet and choices yield the same normalized
record and rendering. This removes mechanical schema authoring from the model's
work while preserving its explicit scientific choices.

The validation call deliberately increases the normal path from two calls to at
most three. Scientific correctness takes priority over preserving the two-call
minimum, while still remaining below the existing five-call median target and
eight-call ceiling.

Existing decision-evidence fields should remain available for one compatibility
version. New responses carry `schema_version`, `policy_version`, coverage
states, stable evidence IDs, and a packet digest. A policy change must create a
new version; it must not silently reinterpret saved runs.

The packet handle must not depend on mutable conversational state. It identifies
the canonical retrieval parameters and packet digest. The joined collector may
recompute a missing hit-window packet from release-qualified read-only evidence
and accept it only when the digest matches. The pure validator never performs
retrieval: a missing regional packet fails closed and directs the caller to
recollect it. The complete packet is stored with the run artifact for
independent replay.

## Scientific invariants

These rules hold across the collector, record, validator, and renderer:

- pLDDT measures confidence in predicted local geometry, not homology;
- structural similarity alone does not establish evolutionary relationship;
- historical match frequency is not target-specific evidence;
- full-model coverage and local-region coverage answer different questions;
- model-to-database evidence is not an official database assignment;
- sequence and AlphaFold-derived structure evidence may be correlated;
- representative domains inherit SCOPe lineage, but the target does not;
- absence is always scoped to a known search and filtering process; and
- boundaries and relationship levels remain appropriately provisional.

## Implementation sequence

1. **Freeze r7 and the 12 current questions as development evidence.** Do not
   revise prompts or gold answers to recover their remaining individual points.
2. **Define the schemas and policy vocabulary.** Add valid and invalid record
   fixtures before changing the MCP response.
3. **Implement full-window evidence coverage.** Add lineage-diverse
   representative selection, rank-window summaries, evidence-stream states, and
   explicit truncation behavior.
4. **Implement claimability and caveat policies.** Use synthetic and
   property-based cases for conflicts, redundancy, missing streams, and
   relationship-level ordering.
5. **Implement the pure validator and deterministic renderer.** Replay saved
   evidence to confirm that numeric mismatches, hidden competitors, wrong-level
   calibration, and missing mandatory caveats are caught.
6. **Integrate the skill and harness.** Keep interpretation compact and make the
   structured audit artifact mandatory.
7. **Run one predeclared paired blind evaluation.** Compare frozen r7 with the
   new architecture on the same unseen holdout without tuning between
   conditions.

Implementation status on 2026-09-04: r7 and suite 1.5 are frozen. The versioned
evidence-packet, packet-handle, decision-record, and executable-policy contracts
have valid and deliberately invalid synthetic fixtures. The collector scans the
full requested hit window, returns the unchanged 12-hit compact view, and stores
the transform-free full window behind an RFC 8785 plus SHA-256 handle in a
32-entry LRU. The joined call verifies or recomputes that hit packet, spends the
existing six-SID cap on regional and lineage diversity, builds the regional
packet, and derives separate relationship, structural, and architectural
claimability ceilings. The pure validator now rejects mismatched packets,
invented ranges or lineage nodes, over-specific claims, missing controlling or
competing evidence, wrong calibration, and omitted mandatory caveats without a
REST call. Existing handle-free joined callers remain compatible but receive no
claimability result. Valid records now produce renderer-version 1.0 Markdown in
the same pure validator call: a fixed disclosure and direct answer, an exact
evidence table, and limitations with code-backed caveats and numbered sources.
The harness verifies the rendering packet ID and SHA-256 digest and rejects any
model-authored change after validation. The predeclared blind holdout remains
pending.

## Evaluation plan

The holdout should contain approximately 20–30 unseen, independently reviewed
cases, stratified across:

- conflicting families inside a shared superfamily;
- structure-only high-confidence resemblance;
- a material competitor outside the compact top-ranked sample;
- repeated chains and same-template redundancy;
- collagen, coiled-coil, repeat, or other partner-dependent geometry;
- a broad envelope versus a tight core under mixed local confidence;
- missing query sequence or an empty evidence stream;
- benchmark-filter mismatch; and
- multiple local regions separated by unresolved sequence.

Metrics and thresholds are declared before inspecting holdout answers:

| Measure | Acceptance target |
| --- | ---: |
| Relationship-specificity errors | At least 50% lower than the paired frozen-r7 condition |
| Material competing-evidence recall | At least 90% |
| Mandatory scientific-caveat recall | At least 90% |
| Reported range and numeric accuracy | 100% against deterministic evidence |
| Evidence completeness | At least 3.5/4 mean |
| Hard misuse of pLDDT, fold similarity, or benchmark frequency as homology evidence | 0 |
| Definitive or official-classification claims | 0 |
| First-attempt schema/audit success | At least 90% |
| MCP use | Median no more than 4; maximum no more than 8 |

The development-score headroom is useful for scale but is not a forecast:
changing four of r7's eight 3/4 answers to 4/4 would yield 44/48 (3.67/4);
changing six would yield 46/48 (3.83/4). Each recovered question adds 1/12,
or approximately 0.083, to the mean. These numbers must not be used to tune the
frozen development cases.

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| A deterministic specificity policy becomes scientifically overconfident | Call it a policy ceiling, expose blockers, permit weaker claims, and require logged expert review for overrides. |
| Diverse-representative selection still hides a branch | Compute over the full configured window, expose branch counts, and mark capped output as truncated. |
| The structured packet restores the token cost removed in r7 | Return compact evidence IDs and one representative per material branch; keep raw records server-side. |
| The schema makes answers rigid or unreadable | Keep the record rich but render a compact explanation from validated relation and caveat templates. |
| Rules overfit the 12 development questions | Derive rules from general scientific invariants, test synthetic counterexamples, freeze the questions, and evaluate once on unseen proteins. |
| Users mistake the policy output for a database classification | Preserve non-official language in the schema, validator, renderer, and public documentation. |

## Alternatives considered

### Add more skill instructions

Rejected as the primary solution. R7 already contained a compact completeness
floor, yet retrieved facts were still omitted. Additional prose would increase
context and encode question-specific patches without guaranteeing compliance.

### Return every raw hit to the model

Rejected. It would increase context cost and still leave selection,
specificity, arithmetic, and omission decisions unvalidated.

### Rely on evaluator feedback after generation

Rejected. Post hoc scoring diagnoses failures but does not prevent them in
production and cannot make a user-facing answer traceable.

### Fully automate SCOPe assignment

Rejected. The available evidence and project scope support provisional,
calibrated hypotheses, not official classification or deterministic proof of
homology.

## Consequences

This change adds schema, policy, validator, and renderer complexity and will
usually add one MCP call. In return, it converts the dominant r7 failure mode
from an unobservable prose omission into an explicit validation result. It also
separates four questions that are currently conflated: what evidence was
searched, what evidence was selected, what scientific claim is permitted, and
what was communicated to the user.

The architecture is successful only if it improves those scientific outcomes on
the unseen holdout. Lower token use or a passing schema alone is not sufficient.
