# Benchmark performance notes

## 2026-09-09 completed evidence-and-caveat completeness run

Suite 1.18.4 / harness 2.9.6 completed all 12 answers and evaluations using the
current MCP source, skill, runner, fixture-manifest, and OpenAPI hashes. Release
validation reports one current-suite run. All 355 fixture responses passed
pre-generation parity, and all 12 accepted answers passed deterministic audits.

The run scored **44/48**, six points above the preceding suite-1.17.2 checker run
and two points above R5. Scientific correctness averaged 3.75/4, evidence
completeness 3.75/4, and calibration/caveats 3.67/4. AFQ002 now includes the
previously hidden alpha/beta representative; AFQ004 and AFQ005 passed their
targeted claim/citation behavior. Residual errors remain in AFQ004's kinase
calibration, AFQ007's region/relationship/calibration selection, and AFQ010's
partner-dependent collagen caveat.

Pass@1 was **10/12**, pass@2 **12/12**, with 26 accepted and 32 all-attempt MCP
calls. Including retries and evaluation, the run used **2,543,864** input tokens
and **1,678.9 seconds** of cumulative model-session time. Compared with R5,
retry-charged median input improved only 9.9%, total input increased 11.1%, and
time increased 29.5%. The 30% input gate fails. This revision recovers scientific
quality but is not a performance promotion. See the
[full comparison](results/runs/2026-09-09_gpt-5-5_pretrained_mcp_skill_r7_completeness_v6/comparison-r5.md).

## 2026-09-09 completed current-hash checker run

Suite 1.17.2 / harness 2.5.9 completed all 12 answers and evaluations using the
current MCP source tree (`6c88b203...`) and skill tree (`1c32315f...`). Release
validation reports one current-suite run, with all 355 fixture responses passing
the pre-generation parity check and all 12 accepted answers passing deterministic
audits. AFQ012 passed on its first attempt, exercising the corrected qualified
superfamily-versus-family wording end to end.

The run scored **38/48**, with **10/12** first-attempt passes and **24** accepted
MCP calls. Including two rejected attempts and evaluation, it used **2,129,872**
input tokens and **1,757.9 seconds** of cumulative model-session time. R5 used
**2,290,612** input tokens and **1,296.8 seconds**. Thus total input fell 7.0%,
but elapsed model-session work increased 35.6%. The gate's retry-charged median
answer input was 140,643, a 21.1% reduction from R5's 178,228 and below the
required 30%.

This result is valid benchmark evidence, but it does not establish the claimed
end-to-end speed improvement. The reduction from 43 to 24 accepted MCP calls is
reported only as a call-count reduction. R5 and R7 remain the performance
baselines, and this revision is not promoted. See the
[full comparison](results/runs/2026-09-09_gpt-5-5_pretrained_mcp_skill_r7_checker_fix_v2/comparison-r5.md).

## 2026-09-08 claim checker and citation compliance

Suite 1.17.2 / harness 2.5.9 removes heading-only claim rejections and recognizes
immediate negative qualifications around claim phrases. Offline regression checks
accept all 17 recorded suite-1.16 attempts, including the five false-positive
rejections, while retaining tests for definitive positive and negative target
claims and negation that must not carry into another assertion.

The contradictory instruction to cite numbered MCP results is removed. The
skill and runner require a clickable source on each E item, with shared Markdown
reference links available to avoid repeating long URLs. A separate audit checks
source presence and provenance against returned API URLs, node identifiers, and
model/SID/stream hit links. All 12 preceding accepted answers have at least one
uncited E item under this new audit, including the four that contained some URLs.

A [fresh three-question diagnostic](results/diagnostics/2026-09-08_checker-citations/README.md)
used GPT-5.5 medium, the revised instructions, and the frozen evidence. AFQ001,
AFQ005, and AFQ009 generated 24 evidence items with source links, using two MCP
calls each. Two initial audits rejected SCOPe's documented path-style URL aliases.
After normalizing those aliases, all three unchanged outputs pass claim and
citation checks. Initial failures and the corrected offline replay remain
separate; no model retries or scientific regrading were performed. The targeted
skill and benchmark regression suite passes all 129 tests.

No MCP response, fixture payload, scientific rubric, gold answer, or historical
score changed. These are instruction/audit fixes; historical replay is not a
new measured accuracy gain. The later full run above is the current measurement.

## 2026-09-07 revised-layout benchmark

The completed suite-1.16 run uses GPT-5.5 medium and GPT-5.6-sol low grading,
matching R7's settings. It scored **43/48** versus **40/48**, with improvements
on AFQ003, AFQ005, AFQ006, and AFQ011 and a one-point decline on AFQ007. Every
accepted answer scored at least 3/4 and used two MCP calls.

First-attempt audit passes fell to **9/12**. Five retries were caused entirely
by existing claim-regex false positives, including “does not establish
homology.” AFQ009 required a resumed invocation and four attempts; all attempts
remain charged. Total generation input was **2,305,810** including retries,
versus R7's **1,305,016**. Median input was **144,710.5**, up **44.0%**.

Artifact validation passes, but the first-attempt and efficiency development
gates fail. Eight answers omit HTTP(S) source URLs despite the new citation
instruction. The EGFR calibration penalty also needs scientific review because
the grader insists on the gold's superfamily node while the answer explicitly
assesses the family and retrieves its calibration. Raw grades are unchanged.
See [the comparison and limitations](results/runs/2026-09-07_gpt-5-5_pretrained_mcp_skill_r7_layout/comparison-r7.md).

## 2026-09-07 question-specific evidence and response structure

Suite 1.16.0 adds the requested evidence-requirements table and response outline:
a 1–2 sentence conclusion with qualified trust, statements at function/family/
superfamily/fold level, a cited list of evidence considered, and caveats associated
with each evidence item. Unassessed levels remain explicit rather than triggering
unrelated retrieval. Harness 2.5.6 delegates the answer layout to the skill instead
of imposing the old three-part format.

The MCP implementation, evidence payloads, R7 retrieval limits, scientific
relationship rules, questions, rubric, and historical results are unchanged.
The 12 draft gold answers now use the same four-part layout, retaining every
original evidence observation, scientific conclusion, and scoring criterion.
Fixture-manifest metadata identifies the new suite without changing its inventory
or payload hashes. The separate run record above reports the subsequent
measurement; this instruction change alone did not establish an accuracy gain.

## 2026-09-06 r7 restoration

At the user's request, the active implementation was restored to r7, suite
1.5.0 / harness 2.5.1. The backend source tree, entire public skill tree, runner,
and fixture manifest match r7's original hashes exactly. This is an exact
recovery from the recorded r7 changes, not a reset to its older Git base.

All 24 recorded MCP tool responses replay identically on the frozen fixtures
after excluding only collection timestamps. The replay preserves two calls per
question, requests exactly the recorded REST sources, and introduces no repeated
retrievals. No new model answers, scientific scores, token measurements, or
latency measurements were generated. R7's 40/48 and performance figures below
remain historical observations, not newly measured outcomes.

The experimental source, schemas, policies, tests, and tool profile were removed
from the active path and preserved in a verified recovery archive. R11/r12 and
all frozen evidence remain unchanged. The fixture replay server and quantitative
gate checker remain as offline development utilities, outside the answer path.
See [restoration verification and recovery details](R7_RESTORATION.md).

The sections below are retained development history; their implementation
descriptions are not instructions to re-enable the rolled-back architecture.

## 2026-09-05 compact decision intent

Final development comparison (accepted answer-generation attempts only):

| Run | Scientific points | Pass@1 | Total MCP calls | Median / max calls | Median input tokens | Median answer time |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Frozen r7 | 40/48 | 12/12 | 24 | 2 / 2 | 100,463.5 | 67.68 s |
| r11, full catalog | 31/48 | 12/12 | 44 | 3 / 6 | 153,334 | 102.00 s |
| r12, assessment catalog | 30/48 | 12/12 | 41 | 3 / 6 | 137,296 | 104.21 s |

R12 is a completed suite-1.7 / harness-2.9.0 development run. Its median input
is 10.5% below r11 and 23.0% below the original r5 baseline (178,228), but remains
36.7% above r7 and misses the required 30% reduction from r5. Median rendered
answer size is 8,717 bytes versus r7's 3,090.5. This single comparison includes
stochastic differences in choices and call counts; it is not a controlled
causal estimate of tool filtering alone. No end-to-end performance preservation
or scientific improvement over r7 is established.

`check_development_gates.py` exits 1 for r12, specifically for mean scientific
score below 3 and median-input reduction below 30%. The other quantitative gates
pass. The completed artifact/fixture validator passes, as do all 241 targeted
tests. R12 remains development-only; the one-shot holdout and promotion remain
pending. The full result is in
[r12 summary](results/runs/2026-09-05_gpt-5-5_pretrained_mcp_skill_r12/summary.md).

Completed r11 (suite 1.6, harness 2.8.1) passed the deterministic audit on all
12 first attempts, with 44 MCP calls (median 3, maximum 6). Its scientific score
was 31/48 (mean 2.58/4), below the predeclared 3/4 threshold and r7's 40/48.
Reliability of a typed record therefore does not establish completeness or
scientific adequacy of the final assessment. This run is not a promotion
candidate; no completion criterion or scoring rule was relaxed.

The evaluator identified general gaps to address with independent synthetic
cases before holdout: retaining the tested proposition and relevant competing
lineages in the renderer; reporting complete stored-BLAST provenance and
whole-model/local confidence contrasts; preserving region/core distinctions;
and reconciling supported claim levels with requested calibration nodes.
Automated-match family placeholders and near-uniform high-confidence regions
also need explicit policy review. These findings are not a license to patch
answers or thresholds around individual development questions.

Suite 1.7 / harness 2.9.0 retains the scientific implementation and frozen data
while adding the same seven-tool assessment profile to every question. The
public client configuration uses the official
[`enabled_tools` setting](https://learn.chatgpt.com/docs/extend/mcp?surface=cli).
The registered catalog is 47,416 serialized bytes; the retained seven tools are
12,482 bytes. The profile, complete tool list, and capability preflight are
recorded with each run; `--mcp-tool-profile all` permits an explicit comparison.
Performance gains from tool filtering must be attributed separately from the
scientific architecture and cannot waive the failed accuracy gate.

The r8/r9 development attempts exposed two integration issues: the stateless
HTTP lifespan initially lost packet handles between requests, and hand-authored
records consumed too many validator retries. The packet LRU now lives at
process scope. In the stopped r9 first batch, the four attempts used 10, 11, 9,
and 9 calls against the unchanged eight-call ceiling; r9 is not an accepted run.

Harness 2.8.0 introduces a compact intent in the existing third call. The model
must choose each region's claim kind and level explicitly. A pure compiler fills
the mechanical fields and runs the full scientific validator before rendering.
Calibration choices must match collected context, and multiple strata require
an explicit selection. The full-record interface remains available. All
existing evidence, cache, response-size, and call limits still apply.

The incomplete r10 run accepted 11/12 development answers on their first
attempt. Those accepted answers used 36 MCP calls (median 3, maximum 5), but
median input was 160,156 tokens. The remaining multi-region case repeatedly
submitted the discovery digest to the regional validator and failed both
attempts. These are development diagnostics, not a completed scored run.

Harness 2.8.1 reads and hashes the two public inputs together through EOF. This
removes an extra round trip seen when the 241-line skill was read in 240-line
pages. The joined tool now places the regional handle and claimability first in
its response, without adding bytes or dropping evidence. The validator's input
schema requires `regional_decision`, and a cached discovery digest receives a
specific wrong-packet error rather than misleading cache-miss guidance.

A paired, alternating 1,000-loop synthetic one-region microcheck measured:

| Pure path | Median | p95 | Input bytes excluding handle |
| --- | ---: | ---: | ---: |
| Full record: validate and render | 3.484 ms | 3.763 ms | 1,946 |
| Compact intent: compile, validate, render | 6.582 ms | 7.054 ms | 151 |

Both paths produced exactly the same 4,414-byte answer. The compiler adds about
3.1 ms locally and reduces this synthetic submission by 92.2%; it performs zero
REST calls. This microcheck does not establish end-to-end benchmark performance.
The combined targeted test suites pass 236 tests, and the live REST/MCP smoke
passed all 19 required tools, including the compact intent path.

Mutable database evidence differed from six of the 355 frozen fixture routes.
Development evaluation therefore uses the digest-verified loopback fixture
server, with exact parity checked before generation. No evidence fixture, gold
answer, question, rubric, or holdout was refreshed.

## 2026-09-04 post-r7 packet, claimability, validator, and renderer slice

No model generation, evaluator call, blind run, fixture refresh, or change to
the 12 development questions, gold answers, and rubric was made in this slice.
Harness 2.7.1 implements the next predeclared architecture layer:

- `get_alphafold_hits` stores the transform-free complete requested window in a
  32-entry process-local LRU and returns a small, canonical
  `RFC8785+SHA-256` handle. A normal cache hit introduces no additional REST
  retrieval; a hit-packet cache miss performs one release-qualified replay and
  accepts it only if the digest is unchanged.
- `get_alphafold_decision_evidence` consumes that handle, automatically retains
  material regional/lineage competitors within the existing six-SID cap, stores
  a schema-valid regional packet, and derives separate relationship,
  structural-similarity, architectural-similarity, unresolved, and not-favored
  policy assessments. Family conflicts lower relationship claimability to the
  common superfamily, while structure-only or low-confidence geometry cannot be
  promoted into an evolutionary relationship claim.
- `validate_alphafold_decision_record` is a pure cache lookup and deterministic
  computation with zero REST calls. It rejects packet or version mismatch,
  incorrect inclusive ranges, overclaims, incomplete controlling/competing
  evidence or rationale roles, unsupported or missing caveats, incomplete-search
  absence claims, and wrong calibration node/level/threshold. Malformed records
  fail closed with structured errors instead of raising. Valid records are
  rendered in the same call as versioned, SHA-256-attested Markdown with a fixed
  disclosure, three sections, exact selected-hit/confidence/calibration fields,
  all policy warnings, and numbered release-qualified sources. Invalid records
  receive no rendering. Rejected draft records remain visible and consume calls,
  but no longer invalidate an attempt that subsequently returns exactly one
  valid rendering.
- The complete packet remains server-side. The public authoring projection is
  capped at 24 evidence IDs per region and six representatives; the existing
  12-hit-per-stream discovery view and 8-MiB tool-response ceiling are
  unchanged. If the SID cap omits a material branch, truncation is scoped to
  that exact region and stream rather than contaminating unrelated regions.
  The normal regional path is now three MCP calls rather than r7's
  two, but remains below the predeclared five-call median target and eight-call
  hard ceiling. The third call repeats no REST evidence and uses structured
  content only.
- A local 1,000-loop synthetic one-region validation-plus-render microcheck
  measured a 3.457 ms median and 3.698 ms p95, producing 4,414 UTF-8 bytes.
  Rendering therefore adds no MCP/REST round trip and negligible local latency
  relative to evidence retrieval and model generation; this is an engineering
  microcheck, not a blind benchmark result.

Focused policy cases cover family conflict, structure-only evidence, low local
confidence, malformed records, incomplete-search negative claims, cache replay,
a 30-branch packet whose public projection remains capped, rendering
determinism, artifact digest verification, and rejection of post-validation
answer edits. The independently reviewed holdout remains pending, so no
scientific-score improvement is claimed yet.

## 2026-09-04 post-r7 full-window diversity slice

No model calls or changes to the 12 development questions, frozen REST evidence,
gold answers, or rubric were made. Suite 1.6 starts a new implementation state so
the completed suite-1.5 r7 provenance remains immutable.

- Versioned JSON Schemas now define the evidence-packet and scientific-decision
  record envelopes, and a machine-readable policy vocabulary fixes relationship
  levels, stream states, blockers, caveats, rationale codes, and mandatory
  caveat triggers. Synthetic valid fixtures and invalid mutations cover an
  over-specific claim, missing mandatory caveats, an unresolved claim retaining
  a lineage assertion, an invented threshold, and inconsistent truncation state.
- Compact selection still returns no more than 12 records per evidence stream
  and makes no additional REST or MCP call. It now scans the complete requested
  window and allocates successive quarters of that fixed budget to class, fold,
  superfamily, and family diversity after preserving distinct query regions.
- Deterministic replay across all 16 frozen model/evidence streams changed only
  the AF-P69905-F1 structure stream: rank-17 class-c representative `d4aa7a1`
  replaces rank-15 same-class-a representative `d5yghb_`. This directly exposes
  the material contrast omitted in r7 AFQ002. Serialized selected-hit data fell
  from 37,332 to 37,329 bytes (3 bytes, 0.008%); call counts and the cap are
  unchanged. A local 300-loop microcheck measured about 0.13 ms per stream for
  the new selector, an immaterial fraction of network/model latency.

This was the first collector slice. Its then-pending claimability and semantic
validation and rendering work is implemented in the subsequent slice above;
the unseen holdout remains pending.

## 2026-09-04 r7 suite 1.5 blind development run

`2026-09-04_gpt-5-5_pretrained_mcp_skill_r7` completed under harness
2.5.1 against the unchanged 12 questions, 355 frozen fixtures, gold answers,
and rubric. The clean run used one attempt per question, `gpt-5.5` at medium
reasoning for answers, and `gpt-5.6-sol` at low reasoning for evaluation.

- Pass@1 was 12/12 with no rejected attempts, restoring r5 reliability and
  improving on r6's 10/12. Every accepted answer used exactly two MCP calls:
  24 total, down 42.9% from r6's 42 accepted calls and 44.2% from r5's 43.
- The scientific mean was 3.33/4 (40/48): four scores of 4 and eight of 3,
  with no 0, 1, or 2. This is one point above r6's 39/48 but two points below
  r5's 42/48. Task resolution rose from 3.67 to 3.92 versus r6; scientific
  correctness fell from 3.92 to 3.83, while evidence completeness remained
  3.25 and calibration/caveats remained 3.83.
- Accepted-answer generation used 1,305,016 input tokens with a median of
  100,463.5. That is 17.1% fewer total and 25.6% fewer median tokens than r6,
  and 37.4% fewer total and 43.6% fewer median tokens than the r5 baseline.
  The 30% median-reduction completion target is therefore exceeded.
- Including evaluation, r7 used 1,502,626 input tokens and 1,062.7 cumulative
  model-session seconds, respectively 26.6% and 17.6% below r6.
- AFQ009 and AFQ011 both passed deterministic audit on their first attempt, so
  lineage-resolved calibration removed the r6 invalid-SUNID failure class.
  AFQ001, AFQ002, and AFQ012 each gained one evaluator point over r6; AFQ006
  and AFQ009 each lost one; the other seven scores were unchanged.
- The joined tool did not by itself solve relationship-level selection or
  reporting completeness. AFQ004 still requested and reported kinase family
  `d.144.1.7` (229/229) instead of the better-supported superfamily `d.144.1`
  (458/458). AFQ011 requested a one-residue-short 262-381 broad envelope and
  retrieved six clusters, but still omitted the expected 262-382, 121/526
  coverage and broad four-band distribution from the answer. Evidence
  completeness consequently did not improve from r6.

A preliminary harness-2.5.0 diagnostic rejected AFQ002 solely because it used
`get_alphafold_decision_evidence` rather than the older `get_scop_domains`
name, even though the joined result contains the required domain evidence and
all source/audit checks passed. Harness 2.5.1 recognizes the joined tool as a
strict confidence/domain capability superset without changing the prompt,
skill, fixtures, questions, or scientific scoring. The official r7 run was
then restarted cleanly; diagnostic attempts are not included in its metrics.

R7 satisfies every development-side numeric completion threshold, but it does
not beat the r5 scientific score and no unseen holdout has run. Freeze these 12
questions as development data rather than tuning on their remaining omissions;
the next evidentiary step is the independently reviewed holdout required by
Phase 4. All 45 benchmark tests pass, and release validation accepts all four
recorded runs, including this one current-suite run.

## 2026-09-04 post-r6 decision-evidence integration

This pass used no model calls and leaves the 12 questions, 355 frozen fixtures,
gold answers, and rubric unchanged. It versions the new execution contract as
suite 1.5 and harness 2.5; r6 remains a suite 1.4 development result.

- `get_alphafold_decision_evidence` now joins 1-6 regional confidence summaries
  with full compact evidence for 1-6 representative SIDs. Optional calibration
  requests use `{sid, level, minimum_supporting_hits}`; the service resolves the
  exact family, superfamily, or fold SUNID from the retrieved lineage. Shared
  node/threshold requests are fetched once and mapped back to each SID.
- Compact domains expose `lineage_by_level` for class, fold, superfamily, and
  family. The domain batch now returns a result or structured error per SID and
  preserves successful peers, completing the Phase 3.3 resilience contract.
- Regional confidence now reports exact coverage arithmetic, a fragmentation
  summary for descriptive `pLDDT >= 90` clusters, and sustained `<70` and `<50`
  stretches. This makes the full-range confidence pattern visible without using
  a hard high-pLDDT threshold as a label or boundary rule.
- The skill retains the compact three-part answer but adds a decision-evidence
  completeness floor for material envelopes/overlaps, core hit metrics, full
  lineage through the claimed node, competing branches, confidence patterns,
  warnings, redundancy, and exact level-resolved calibration.
- The normal regional workflow falls from four MCP calls with calibration to
  two: combined hits followed by the joined decision-evidence call. This should
  remove two context replays while preventing the invalid representative-domain
  SUNID requests seen in AFQ009 and AFQ011 r6 attempt 1.

Validation: the skill package validator passed; 52 focused MCP/service/contract
tests and all 44 then-current benchmark tests passed; all 355 frozen fixtures and 12 gold
answers validated. The full MCP suite passed 153 of 154 tests; the sole failure
is the pre-existing, unrelated PHP chain-page coordinate-route contract already
recorded below. This pre-run state is retained here to distinguish implementation
validation from the r7 measurements above.

## 2026-09-04 r6 Phase 2 development run

`2026-09-04_gpt-5-5_pretrained_mcp_skill_r6` ran against the exact 355
frozen fixtures under suite 1.4 and harness 2.4.0. The live Doppelbock preflight
first detected six changed model/hit responses, so the run used a read-only
fixture-backed REST server rather than mixing the newer BLAST cache with the
r5 snapshot.

- Pass@1 was 10/12 and pass@2 was 12/12. AFQ009 and AFQ011 initially requested
  calibration for representative domain SUNIDs rather than family,
  superfamily, or fold nodes; their second attempts passed.
- The scientific mean was 3.25/4 (39/48), down from r5's 3.50/4. Scientific
  correctness rose from 3.83 to 3.92, but evidence completeness fell from 3.42
  to 3.25.
- AFQ004 improved from 2/4 to 3/4: overlaps and the wider kinase envelope were
  handled correctly, but the answer still calibrated family nodes instead of
  the reported superfamilies. AFQ011 remained 3/4: benchmark-filter handling
  improved, but the broad 262–382 confidence envelope was still omitted.
- Accepted answers used 1,574,647 input tokens, with median 134,984.5 per
  question—about 24% below r5 on both measures. Mean answer length fell from
  5,567 to 3,037 characters.
- Including two rejected attempts, answer generation used 1,849,815 input
  tokens and 52 MCP calls. Evaluation added 196,847 input tokens, for 2,046,662
  total input tokens.

This is mixed rather than a successful final improvement: the intended
scientific safeguards worked, and answers became much shorter, but pass@1 and
overall score regressed. Before another blind run, node-level calibration
selection and a small decision-evidence completeness floor should be corrected
without restoring the old eight-section format.

## 2026-09-04 post-r5 correction pass

This pass used no model calls. It versioned the unchanged 12-question
development suite as 1.4 and the corrected harness as 2.4.0, preserving r5
(suite 1.3) as the clean Phase 1 baseline.

- Compact FATCAT hits now expose whether the current P-value and available
  length-match inputs are within the historical chain-benchmark filters.
  AFQ011-like `P=0.0041` evidence is deterministically marked
  `outside_benchmark_filters`; a hit passing `P <= 1e-4` but lacking full
  component-length input is marked `uncertain_missing_filter_inputs`.
- The skill now requires family-discriminating sequence/structure convergence
  before a family-level hypothesis. A single structure-only hit, an automated
  family label, or redundant same-family hits cannot supply that distinction.
- Material hit unions/envelopes and overlaps must be preserved as approximate
  boundaries. The old contiguous-`pLDDT >= 90` label rule was replaced by the
  local median, four-band distribution, and sustained low-confidence stretches.
- The entrypoint shrank from 415 lines/3,343 words to 199 lines/1,422 words, and
  the default output shrank from eight mandatory sections to three compact
  parts.

Validation: the skill package validator passed; 66 focused MCP/harness tests
passed; all 39 benchmark tests and the 355-fixture development validator
passed. The full MCP suite passed 148 of 149 tests; its sole failure is the
pre-existing, unrelated PHP chain-page coordinate-route contract, which still
finds `brass.lbl.gov/~makelyan` in the page. The subsequent r6 result is
recorded above.

## 2026-09-04 r5 clean Phase 1 baseline

`2026-09-04_gpt-5-5_pretrained_mcp_skill_r5` completed all 12 questions on
the first attempt under harness 2.3.1:

- Pass@1: 12/12; rejected or retried attempts: 0.
- Scientific score: 3.50/4 mean, 42/48 total, with scores
  `7x4, 4x3, 1x2`.
- MCP use: 43 successful calls, 0 failed; median 4 and maximum 4 per question.
- Answer generation: 2,086,027 input tokens; median 178,228 per question.
- Evaluation: 204,585 additional input tokens.
- Mean accepted-answer length: 5,567 characters.

This is the clean matched baseline for a future r6. AFQ004's 2/4 result exposed
overlapping/envelope-boundary reporting and insufficiently discriminating
family-level evidence. AFQ011's 3/4 result omitted the broad 262–382 confidence
envelope and interpreted a 46/46 historical scalar even though its
representative FATCAT P-value (about 0.004) exceeds the benchmark's `1e-4`
entry filter.

## 2026-09-04 post-r4 correction pass

The correction pass used saved run artifacts, local fixtures, mocked tests, and
three read-only REST probes; it made no model calls.

- AFQ004's two saved answers calibrated the family or superfamily nodes they
  actually reported at the public question's required thresholds. The old
  audit nevertheless required different, undisclosed gold SUNIDs. Harness
  2.3.1 now requires the public release/threshold/count contract and enforces an
  exact SUNID only when the blind question names it. Replaying AFQ004 attempt 2
  passes the corrected audit.
- AFQ003 had no prespecified threshold, but both attempts silently defaulted to
  threshold 2. Live REST probes confirmed that the three target-related nodes
  return valid `available` or `no_observations` responses; the problem was not
  the API contract. The generator prompt and audit now explicitly reject any
  calibration call when the blind question supplies no threshold. No arbitrary
  AFQ003 fixtures were added.
- The batched calibration MCP function now returns either a result or a
  structured error for every request and preserves successful peers when one
  REST request fails.
- Focused replay leaves AFQ003 rejected for its unprescribed call and accepts
  AFQ004. The complete AlphaFold benchmark test suite (38 tests), MCP test suite
  (147 tests), and development validator pass.

## 2026-09-04 pre-run optimization

No benchmark was rerun during this work. Measurements below come from the
interrupted harness-2.2 diagnostic attempts and deterministic replay of the
frozen REST fixtures through the updated service functions.

### What was expensive

Across 21 completed answer attempts, one question consumed 192,412-445,718
input tokens (median 235,402) and made 7-21 MCP calls (median 10). The largest
repeated payload sources were:

| Tool | Successful calls | Total result bytes | Largest result |
| --- | ---: | ---: | ---: |
| `get_alphafold_model` | 20 | 1,072,193 | 111,559 |
| `get_alphafold_hits` | 21 | 1,060,160 | 136,426 |
| `get_scop_domain` | 68 | 799,245 | 12,229 |
| `get_alphafold_region_confidence` | 59 | 308,957 | 7,543 |

The causes were cumulative:

1. The MCP SDK returned structured JSON and an additional pretty-printed text
   copy of the same result.
2. Every model response contained the sequence and two complete copies of the
   pLDDT track: an array and a position-keyed object.
3. Compact hit selection retained the top ten records plus every later
   overlap-SCCS group. A diverse result could therefore remain nearly
   unbounded, and group summaries repeated every member SID.
4. Domain evidence was requested once per SID. Each MCP turn replayed the
   growing prior context, and each result repeated node and source envelopes in
   its lineage, annotation, and homology sections.
5. Regional confidence fetched and rescanned the same model once per region.
6. Calibration records were retrieved separately and repeated identical method
   metadata.
7. The 24-call ceiling and prompt allowed sequential calls and progress turns.
   Skill references could also be loaded eagerly even when their details were
   not material.

### Changes made

- Compact hit output is hard-capped at 12 records per evidence stream. It
  preserves the best hit for each materially distinct query region first, then
  uses remaining slots for ranked overlap-SCCS representatives. Raw counts,
  group counts, and SCCS frequencies remain available; member lists do not.
- The REST combined-hit subject now includes sequence length and compact model
  metadata. MCP uses it directly, eliminating a separate full model request.
- Normal MCP model output omits sequence and per-residue tracks. The REST API
  now returns only the canonical `residue_plddt` array by default;
  `include_plddt_by_position=true` restores the redundant map for compatibility.
- Large analysis tools use MCP `structuredContent` without a second text copy.
- `get_scop_domains` retrieves 1-6 compact domain records concurrently in one
  MCP call.
- `get_alphafold_regions_confidence` computes 1-6 region summaries after one
  model fetch and one whole-model scan.
- `get_scop_fatcat_chain_benchmarks` retrieves 1-6 prespecified calibration
  records concurrently and emits shared method metadata once.
- The generator's normal path is now three calls without calibration and four
  with calibration. The hard ceiling is eight calls. The harness audit maps
  legacy per-record requirements to the batch tools.
- Reference loading is conditional instead of eager, and the prompt prohibits
  redundant progress turns and repeated evidence calls.

### Frozen-fixture measurement

The prior hardest attempt, AFQ004 attempt 1, used 445,718 input tokens, 20 MCP
calls, and 227,748 serialized MCP-result bytes. Replaying the same frozen
evidence through the intended updated four-call path produced:

| Updated call | Serialized result bytes |
| --- | ---: |
| `get_alphafold_hits` | 8,924 |
| `get_alphafold_regions_confidence` | 8,960 |
| `get_scop_domains` | 9,563 |
| `get_scop_fatcat_chain_benchmarks` | 3,991 |
| **Total** | **31,438** |

This is an 80% call-count reduction and an 86.2% result-byte reduction for
that case before accounting for the much larger benefit of replaying prior
context four times instead of twenty. Across all eight frozen models available
in the interrupted logs, the new combined-hit wire payload was 63.0%-91.6%
smaller than the observed prior response. A real post-change token and latency
comparison remains intentionally unmeasured until the next authorized blind
run.

### Unexpected issues and remaining risks

- Removing duplicate MCP text initially made the deployment smoke test report
  a false failure because it required legacy text content. It now accepts a
  valid structured payload and exercises the new batch tools.
- Changing the REST/OpenAPI and skill contracts invalidated snapshot provenance
  hashes. The eight model fixtures and manifest hashes were updated without
  changing any pLDDT value or biological evidence.
- Structured-only MCP results require a client that consumes advertised
  `structuredContent`. The benchmark client does; very old text-only clients
  would need an adapter.
- Compact hit output deliberately omits many raw records. The complete raw
  counts and SCCS distribution remain, and `compact=false` is an explicit
  escape hatch, but the next blind run should verify that later low-ranked
  competing regions are still represented as intended.
- Batch domain and calibration tools reduce MCP turns and run their REST calls
  concurrently; they do not reduce the number of underlying database-backed
  REST requests. Native REST batch endpoints would be a further backend-load
  optimization if needed.
