# Results

The active revision is **1.18.4 / harness 2.9.6**, using R7's evidence collection
with a question-scoped evidence/caveat ledger and bounded raw-hit completeness
fallback. The completed
[current-hash run](runs/2026-09-09_gpt-5-5_pretrained_mcp_skill_r7_completeness_v6/comparison-r5.md)
validates all 12 answers and scores 44/48, recovering the preceding checker
run's 38/48 and exceeding R5's 42/48. It is not a performance promotion: its
1,678.9 seconds of cumulative model-session time exceeds R5's 1,296.8 seconds,
total input is 11.1% higher, and its 9.9% retry-charged median-input reduction
misses the 30% gate. Historical answers and grades are preserved. The suite-1.16
[layout-revision comparison](runs/2026-09-07_gpt-5-5_pretrained_mcp_skill_r7_layout/comparison-r7.md)
scored **43/48**, versus R7's **40/48**, with **9/12** first-attempt audit passes
and **44.0%** higher median generation input. Five false-positive claim checks
caused retries; the run fails the first-attempt and efficiency development gates.
R7 remains the baseline; r11/r12 remain historical experiments.
See [current implementation](../README.md).

Place each run under `runs/<run-id>/` with `manifest.json`, `answers.jsonl`,
`evaluation.jsonl`, and `summary.md`, then run the benchmark validator.

The run ID must match its directory name. Answers and evaluations must each
cover all 12 question IDs exactly once, and their run ID and condition must
match the manifest. For runs using harness 2.0.0 or later, each answer must
include the exact input attestation and the
`non_official_evidence_hypothesis` claim status. The validator checks current
suite runs against exact current question, fixture, and skill hashes while
retaining historical hashes for older suite versions.

Harness 2.9.6 checks local negation, meta-evidence statements, and non-target
relationship-level comparisons around potential claim phrases rather than
rejecting headings, qualified caveats, or family-candidate/discriminating-evidence
phrasing. Its separate citation audit requires a
source link on every numbered evidence item and derives allowed destinations
only from successful MCP results. It accepts inline links, autolinks, bare
HTTP(S) URLs, and resolved Markdown reference links; links inside code or only
in `evidence_used` do not satisfy the answer's citation requirement. It checks
provenance, not scientific entailment. Fresh generation diagnostics belong in
`diagnostics/`; they do not count as completed scientific benchmark runs.

The [2026-09-08 diagnostic](diagnostics/2026-09-08_checker-citations/README.md)
contains three freshly generated answers, all accepted by the final checker with
24/24 evidence items cited. Two initial audits rejected valid SCOPe URL aliases;
the original records and the corrected offline replay are both retained.

Harness 2.2.0 also records the REST/fixture parity preflight, exact prompt and
output hashes, efficiency metrics, and audits the
REST source URLs returned by successful MCP calls. A run must not begin when
the live MCP evidence and evaluator fixtures represent different snapshots.
Harness 2.3.0 adds compact structured-only evidence payloads, batched domain
retrieval, batched confidence and calibration, and an 8-call answer-generation
cap.
Harness 2.3.1 audits undisclosed benchmark nodes by the threshold and number of
strata required by the blind question, while retaining exact SUNID enforcement
when the question names a SUNID. It also rejects calibration calls when the
question prespecifies no threshold.
Harness 2.4.0 adds current-hit chain-benchmark applicability to the compact
FATCAT evidence contract and aligns the generator with the compressed,
three-part skill format, conservative family discrimination, and approximate
range-envelope reporting. Resume aggregation reads durable attempt logs so
pass@1, pass@k, retry costs, and rejected-attempt reasons remain visible across
separate invocations.
Harness 2.5.0 replaces the three post-hit calls for regional assessments with
one joined decision-evidence call. It audits that composite call as model,
representative, confidence, and level-resolved benchmark evidence; the prompt
also applies a compact decision-evidence completeness floor without restoring
the former eight-section response.
Harness 2.5.1 additionally recognizes the joined result as a strict
confidence/domain capability superset. The clean suite-1.5 r7 run achieved
pass@1 12/12, 40/48 scientific points, 24 accepted MCP calls, and 1,305,016
accepted answer-generation input tokens (median 100,463.5).

### Rolled-back experiment history

The following versions describe archived runs, not the current runner or CLI.

Harness 2.6.0 requires the content-addressed hit-window handle to flow through
the joined call, then requires a valid regional packet handle and successful
pure decision-record validation. It audits the exact handle chain and exempts
only the pure validator from REST-source attestation; the existing target,
release, fixture-parity, and eight-call checks still apply.
Harness 2.7.1 also verifies the deterministic renderer version, packet ID, and
answer SHA-256, then requires the submitted answer to equal the validated
rendering byte for byte. Invalid draft records are audited and charged to the
call budget but do not override a later valid rendering.
Harness 2.8.0 asks for compact decision intent in that same validator call. The
server compiles exact packet fields and applies the unchanged scientific
validator, keeping the three-call normal workflow. Harness 2.8.1 batches the
complete public-input reads and requires the joined result's distinct regional
handle. The incomplete r10 run accepted 11/12 answers but is not a scored run.

Suite 1.6 preserves the completed r11 run, with 12/12 first-attempt audit passes
and 31/48 scientific points. Despite reliable validation, it is below the
scientific promotion threshold and r7's 40/48. Suite 1.7 keeps that result
immutable and introduces harness 2.9.0's fixed seven-tool assessment profile.
The profile is saved with the run and checked against required capabilities;
`--mcp-tool-profile all` retains a full-catalog comparison option. The one-shot
holdout remains pending.

The completed suite-1.7 r12 profile comparison records pass@1 12/12, 41 MCP
calls (median 3, maximum 6), 30/48 scientific points, and median accepted-answer
input of 137,296 tokens. It fails the mean-score and 30%-input-reduction gates
and is retained as development evidence, not an approved improvement over r7.

Do not create a run from a session that has read the private questions, gold
answers, expected behaviors, failure modes, or rubric. Use a clean execution
context containing only the resources allowed by the selected condition.

Normal development validation allows zero completed current-suite runs. Release
validation requires at least one real completed run explicitly:

```bash
python3 benchmarks/alphafold-scope-homology/scripts/validate.py \
  --minimum-completed-runs=1
```
