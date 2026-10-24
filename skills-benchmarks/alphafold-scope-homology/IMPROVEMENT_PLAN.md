# AlphaFold–SCOPe Skill Improvement Plan

## Current revision — 2026-09-09

Suite 1.18.4 / harness 2.9.6 adds a question-scoped evidence/caveat ledger,
release-qualified citations, qualitative strong-chance wording inside the
non-definitive boundary, and a bounded raw-hit fallback when compact output hides
a required rank or classification contrast. The completed current-hash run
validates all 12 answers and scores 44/48, recovering the suite-1.17.2 run's
38/48 and exceeding R5's 42/48. It still fails the 30% median-input gate: the
reduction is 9.9%, total input is 11.1% above R5, and cumulative model-session
time is 1,678.9 seconds versus R5's 1,296.8 seconds. This revision demonstrates
improved scientific completeness, not an end-to-end speed improvement. See the
[measured comparison](results/runs/2026-09-09_gpt-5-5_pretrained_mcp_skill_r7_completeness_v6/comparison-r5.md).

## Preceding instruction revision — 2026-09-07

Suite 1.16.0 / harness 2.5.6 retains R7 evidence collection and adds the requested
question-specific evidence table and response outline. The gold answers use the
same layout with their scientific conclusions and scoring criteria retained.
The completed development run scored 43/48 versus R7's 40/48, but fails the
first-attempt and efficiency gates: 9/12 first-attempt passes and 44.0% higher
median generation input. False-positive claim checks and missing source URLs
remain concrete follow-up issues; R7 remains the baseline. See
[the measured comparison](results/runs/2026-09-07_gpt-5-5_pretrained_mcp_skill_r7_layout/comparison-r7.md).
The restoration and experiment history below describe the baseline and prior
work, not additional authorized implementation.

## Current status — r7 restored, 2026-09-06

The user requested rollback after r11/r12 regressed scientifically and in
end-to-end performance. The active backend, skill, and runner are now the exact
r7 versions (suite 1.5.0 / harness 2.5.1), verified by their original hashes and
offline replay of all 24 recorded tool responses. The post-r7 architecture is
archived, not the next implementation task. See [restoration record](R7_RESTORATION.md).

Keep r7 as the working reference. Any future experiment should be isolated,
introduced one change at a time, and compared with r7 for scientific quality,
call counts, tokens, and latency before adoption. Passing the older minimum
gates alone does not establish non-regression against r7. No new model benchmark
or holdout was run during restoration. The remaining sections preserve the
original plan and experiment history; they do not override this rollback.

## Objective

Improve scientific calibration, first-attempt reliability, and efficiency without
overfitting the skill to the existing 12 benchmark questions. Separate defects in
the skill from defects in the benchmark, frozen evidence, MCP API, and runner.

## Current clean baseline

The complete
`2026-09-04_gpt-5-5_pretrained_mcp_skill_r5` run is the Phase 1 baseline:

- Pass@1 was 12/12, with no rejected attempts.
- The scientific mean was 3.50/4 (42/48): seven scores of 4, four of 3, one of
  2, and no 0 or 1.
- Accepted answers made 43 MCP calls, with zero failed calls; the median and
  maximum were both four calls.
- Answer generation consumed 2,086,027 input tokens (median 178,228 per
  question); evaluation consumed another 204,585.
- Accepted answers averaged 5,567 characters.

The residual errors are scientific and reporting issues rather than fixture
failures: AFQ004 exposed overlapping/envelope-boundary and family-specificity
problems, while AFQ011 exposed chain-benchmark applicability and overly narrow
confidence-envelope reporting. The earlier incomplete r3 run remains a
diagnostic history in `PERFORMANCE_NOTES.md`, not the comparison baseline.

## Phase 1: Measure the corrected baseline — completed 2026-09-04

Run the existing skill and corrected benchmark without any further skill edits.

1. Run answer generation with `--max-attempts=1`.
2. Preserve that result as pass@1 even if some questions fail.
3. If necessary, resume unfinished questions separately to measure pass@k.
4. Run scientific evaluation only after all 12 answers are available.
5. Record per-question calls, failed calls, tokens, duration, answer length, and
   evaluator subscores.

The run is usable as a baseline only if no valid, in-scope MCP request fails
because its frozen REST path is absent. Treat such a failure as a benchmark defect
and abort rather than charging it to the skill.

## Phase 2: Correct scientific decision rules

Make these changes in order. Each change must be supported by a failed benchmark
case or a focused regression test.

Status 2026-09-04: the r5 corrections were exercised by r6. Applicability,
family discrimination, and range-envelope handling improved, but manual joins
across confidence, domain, and benchmark calls caused wrong-level calibration
and evidence omissions. Suite 1.5 now adds one deterministic decision-evidence
join: callers specify a representative SID and the relationship level they will
report, and the MCP service resolves the exact lineage SUNID before calibration.
R7 confirmed that this removes invalid-SUNID retries, but it does not prevent a
caller from choosing an over-specific relationship level: AFQ004 still selected
the kinase family instead of the better-supported superfamily.

The accepted next architecture is documented in the
[Typed Scientific Decision Record Architecture](SCIENTIFIC_DECISION_RECORD_ARCHITECTURE.md).
It replaces the remaining prose-only completeness and relationship-level checks
with a typed evidence packet, policy-based specificity ceiling, deterministic
validation, and traceable rendering.

### 2.1 Separate local evidence coverage from whole-model coverage

`aligned residues / full model length` describes how much of the entire protein a
hit covers. It is useful for preventing whole-protein extrapolation, but it must
not weaken a local domain hypothesis merely because the target is a long,
multidomain protein.

- Report full-model coverage as scope context.
- Judge a regional hypothesis using aligned length, coverage of the candidate
  region, and coverage of the matched SCOPe domain when those values are
  available.
- Do not use low whole-model coverage as a reason to downgrade an otherwise
  well-covered local domain.
- Add a regression case based on the terminal BRCT and collagen/VWC examples.

### 2.2 Enforce chain-benchmark applicability

Before interpreting a chain-benchmark fraction, compare the current FATCAT hit
with the benchmark's inclusion conditions.

- If the current P-value exceeds the benchmark cutoff, or required length-match
  information is absent or fails, label the scalar as outside or uncertain with
  respect to the current hit's calibration domain.
- Do not describe a high fraction as adding “no warning” when the current hit
  would not have entered the benchmark.
- Prefer exposing a deterministic applicability field from the API rather than
  relying on prose reasoning.
- Add a regression case for AFQ011, whose representative FATCAT P-value is about
  0.004 while the benchmark cutoff is `1e-4`.

### 2.3 Replace the contiguous-`pLDDT >= 90` rule

A hard threshold can split an otherwise credible region into many short clusters.
It is not a direct measure of fold coherence or a domain boundary.

- Use the full local pLDDT distribution and sustained stretches below 70 or 50.
- Treat `>=90` clusters as descriptive only, not a label criterion.
- Use PAE for relative-domain placement when it becomes available.
- Keep the rule that pLDDT modifies trust in predicted geometry and is not
  evolutionary evidence.

### 2.4 Require family-discriminating evidence

A SCOPe representative's family classification does not automatically imply that
the target evidence is family-specific.

- Consider alignment significance, aligned length, identity, low complexity or
  compositional bias, competing families, and sequence/structure dependence.
- Do not promote a single weak or compositionally suspect match to a family-level
  candidate solely because its representative has a family node.
- If the API lacks alignment fields needed for this judgment, report the missing
  information and use a less specific label.

## Phase 3: Improve performance and reliability

Apply performance changes only after Phase 1 provides a clean baseline.

Status 2026-09-04: implemented locally. The skill entrypoint is now 231 lines
and 1,686 words (down from 415 lines and 3,343 words), including a compact
decision-evidence completeness floor. The compact three-part answer remains the
default. A regional assessment now normally uses three MCP calls, including a
zero-REST pure validation call, and both domain
and calibration batches preserve successful peers after an item-level REST
error. The completed r7 comparison remains exactly 24 accepted calls, two for
every question, versus 42 in r6 and 43 in r5.

### 3.1 Compress the skill

The current entrypoint is 415 lines and 3,343 words. Reduce it substantially while
preserving the scientific invariants above.

- Keep the claim boundary, question contract, minimal evidence workflow, label
  logic, and critical scientific prohibitions in `SKILL.md`.
- Move detailed tool fields and specialized calibration explanations to focused
  references.
- Remove duplicated caveats, repeated wording, and requirements already enforced
  by the runner or schema.
- Do not add a new rule for every individual benchmark failure.

### 3.2 Make output proportional to the question

Replace the mandatory eight-section answer with a compact default:

1. Direct answer and one hypothesis per region.
2. A compact evidence table containing only decision-relevant observations.
3. Material limitations and numbered sources.

Retain the full audit-ledger format only when requested or when the evidence is
genuinely complex.

### 3.3 Make batch calls resilient

Change batched domain and calibration tools to preserve valid results when one
item fails.

- Return a result or structured error for each requested SID/node.
- Do not invalidate successful members of a batch.
- Let the agent retry only the failed item when retrying is scientifically useful.

### 3.4 Improve benchmark retry reporting

- Report pass@1 as the primary reliability metric and pass@k separately.
- Do not combine retry cost with accepted-attempt cost without showing both.
- Retry transport or schema failures separately from scientific/semantic failures.
- Preserve every rejected attempt and its audit reason.

## Phase 4: Validate without overfitting

Status 2026-09-04: r7 completed under suite 1.5/harness 2.5.1 at 3.33/4 with
pass@1 12/12, 24 accepted MCP calls, and median accepted-answer input of
100,463.5 tokens. Relative to r6, the score gained one point, calls fell 42.9%,
and median input fell 25.6%; relative to the clean r5 baseline, the score remains
two points lower while median input is 43.6% lower. R7 meets the development-side
numeric criteria, but evidence completeness remained 3.25/4 and the unseen
holdout is still absent. Freeze the development questions now; do not tune on
their remaining case-specific omissions. Implement the typed scientific
decision-record architecture using general invariants, synthetic cases, and
deterministic replay before the one-shot holdout.

Post-r7 implementation status: suite 1.6 leaves the development evidence and r7
artifacts frozen. Versioned evidence-packet, handle, decision-record, and policy
schemas now back a full-window collector, bounded hierarchy-diverse
representative selection, per-claim-kind specificity engine, and pure semantic
validator. The validator rejects overclaims, incomplete evidence and caveats,
incomplete-search negative claims, and calibration drift without a REST call.
Frozen replay exposes AFQ002's omitted class-c competitor with no extra
retrieval or selected-hit payload increase. Renderer version 1.0 now converts
only valid normalized records into hashed three-part Markdown in that same pure
call, and harness 2.7.1 rejects any post-validation answer change while allowing
bounded rejected drafts before a valid rendering. The unseen
holdout remains pending. Harness 2.8.0 adds explicit compact claim intent in the
third call: a pure compiler derives the mechanical record fields and applies
the full validator. This addresses the schema-authoring retries observed in
the stopped r9 development run while preserving the eight-call ceiling.
Harness 2.8.1 also removes paginated input-read overhead and distinguishes
regional handles from discovery handles after the incomplete r10 run exposed
repeated use of a discovery digest during validation.

R11 now completes suite 1.6 with 12/12 first-attempt audit passes, but its 31/48
scientific score fails the promotion threshold. Keep r7 and r11 frozen and do
not run the one-shot holdout yet. The next scientific slice must use independent
synthetic cases to preserve tested propositions, complete provenance and
global/local confidence comparisons in rendering, and review claim/calibration
consistency and automated family placeholders. Suite 1.7 / harness 2.9.0 first
measures a fixed public seven-tool catalog to reduce repeated schema context;
this performance-only change does not resolve the scientific failures.
Completed r12 confirms that distinction: pass@1 12/12 and calls within bounds,
but 30/48 scientific points and only 23.0% median-input reduction from r5.
`check_development_gates.py` explicitly rejects both failed quantitative gates.
The current architecture is not promoted; r7 remains the stronger development
reference while the general scientific defects above are addressed.

1. Run the corrected skill on the existing 12 questions as a development suite.
2. Compare scientific scores, pass@1, calls, tokens, latency, and answer length
   with the Phase 1 baseline.
3. Where causal attribution matters, add a matched MCP-without-skill condition or
   a controlled skill ablation using the same model and settings.
4. Freeze the current questions as development data.
5. Create an independently reviewed holdout containing unseen proteins and cases
   for low-complexity sequence hits, partial domains, conflicting families,
   benchmark-filter mismatch, low local confidence, and multiple domains.
6. Run the holdout once. Do not repeatedly tune against it; report residual
   limitations instead.

## Predeclared completion criteria

`scripts/check_development_gates.py RUN_DIR` now checks the quantitative
development criteria against the actual r5 answer checkpoints and exits
nonzero on failure. Artifact integrity, qualitative scientific review, and the
unseen holdout remain separate requirements.

Stop revising when one closed-evidence run and one unseen holdout run satisfy all
of the following:

- No fixture-inventory or snapshot-parity failure is attributed to the skill.
- At least 10 of 12 development questions pass the deterministic audit on the
  first attempt, with pass@k reported separately.
- Mean scientific score is at least 3 of 4, with no score of 0 or 1 caused by a
  critical scientific error.
- No answer treats pLDDT, a fold match, or a benchmark fraction as direct homology
  evidence.
- No answer uses whole-model coverage to reject a credible local domain solely
  because the protein is long.
- Median MCP use is no more than five calls, no answer exceeds the eight-call
  ceiling, and missing-fixture retries are zero.
- Median input tokens improve by at least 30% from the clean Phase 1 baseline, or
  any smaller improvement is accepted explicitly as the cost of preserving
  scientific quality.

After these criteria are met, freeze the skill and benchmark version. Make future
changes only for new capabilities, newly demonstrated scientific errors, or API
contract changes—not stylistic dissatisfaction with already-correct answers.
