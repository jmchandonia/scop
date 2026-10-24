# AlphaFold–SCOPe Homology Benchmark

## Public skill source

The runner, evidence collector, validator, and skill-dependent tests use the
public commit pinned in `../../skill-sources/public-skills.json`, through
`../../scripts/scop_public_skills.py`. Clone the public SCOP repository alongside
this repository, or set `SCOP_PUBLIC_REPO` to its checkout. Fetch the pinned
commit before running; the resolver uses committed files and does not fetch
automatically. No private `skills/` directory is needed.

Historical fixtures and run hashes remain unchanged. On 2026-09-13, this suite
had 123 passing and 20 failing tests both before and after the source migration
(baseline private commit `2807efb`). The failures concern older API response
fields, tool catalog expectations, and recorded artifact hashes. The separate
current MCP suite passes all 218 tests against the pinned public skills.

## Current implementation: evidence and caveat completeness

Suite **1.18.4**, harness **2.9.6**, retains R7 evidence collection and makes the
question contract an explicit evidence-and-caveat ledger. Every requested or
decision-material item must be reported and cited or identified as unavailable;
all applicable caveat categories must be checked. The skill still forbids final
homology, classification, boundary, or function conclusions, while allowing the
qualitative phrase “the evidence supports a strong chance” when the supported
relationship level warrants it.

The runner now follows the skill's citation instructions without the conflicting
instruction to cite MCP result numbers. The citation audit checks each E
item for a clickable source from a successful MCP result, a SCOPe node with a
returned identifier and release, or a hit page for a returned model/SID/stream.
Shared Markdown reference links and both documented SCOPe path-style links and
repository query-style node links are supported. This verifies citation presence
and provenance, not whether each scientific claim follows from its source.

The normal workflow remains two calls. A bounded completeness exception repeats
the frozen combined-hit request with `compact=false` when compact representatives
hide a requested rank window or classification contrast. Public SCOPe node links
must include the pinned release. The claim checker accepts qualified level,
candidate, and family-discriminating-evidence wording while continuing to reject
actual positive and negative target verdicts.

The completed [suite-1.18.4 current-hash run](results/runs/2026-09-09_gpt-5-5_pretrained_mcp_skill_r7_completeness_v6/comparison-r5.md)
passes artifact validation and all 12 deterministic answer audits. It scored
**44/48**, recovering the preceding revision's **38/48** and exceeding R5's
**42/48**. It passed **10/12** answers on the first attempt and used 26 MCP calls
in accepted attempts. It does **not** demonstrate an end-to-end speed improvement:
cumulative model-session time was **1,678.9 s**, versus **1,296.8 s** for R5,
total input was 11.1% higher than R5, and retry-charged median input improved only
9.9%, below the predeclared 30% gate. R5 and R7 therefore remain the performance
baselines; the current revision is a scientific-completeness improvement, not a
promoted speed improvement.

The [fresh generation diagnostic](results/diagnostics/2026-09-08_checker-citations/README.md)
produced three answers with 24 cited evidence items and two MCP calls per answer.
All three unchanged outputs pass the final checker after correcting its handling
of documented SCOPe URL aliases. Initial audits and the corrected replay are
preserved separately; these samples have not been scientifically graded.

The preceding suite-1.16 [layout-revision run](results/runs/2026-09-07_gpt-5-5_pretrained_mcp_skill_r7_layout/comparison-r7.md)
scored **43/48**, compared with R7's **40/48**, using the same model settings.
It retained two MCP calls per accepted answer, but false-positive claim checks
reduced first-attempt passes to **9/12**, and median generation input increased
**44.0%**. It fails the first-attempt and efficiency development gates. R7 remains
the baseline; see the historical [restoration record](R7_RESTORATION.md).

This suite tests whether an agent can identify SCOPe-like regions in AlphaFold
models and choose the strongest defensible relationship level without treating
FATCAT similarity or pLDDT as homology by themselves.

Every answer must frame its result as a non-official evidence hypothesis. It
must not definitively state that an AlphaFold target is or is not homologous,
assigned, or classified at a SCOPe node.

## Cases

The 12 questions cover hemoglobin, p53, EGFR, PTEN, BRCA1, collagen, FUS, and
the structure-led Q96LI5 example.
They test domain detection, multi-domain segmentation, weak and partial hits,
bounded-result sensitivity, local versus global confidence, empty hit lists,
exact UniProt/BLAST provenance, node-specific FATCAT benchmark interpretation,
and family-versus-superfamily calibration.

## Conditions

- `pretrained_only`: blind questions with no skill, API fixtures, or MCP.
- `pretrained_api`: blind questions plus the frozen REST fixtures.
- `pretrained_skill`: blind questions plus the public skill, without API or MCP.
- `pretrained_mcp_skill`: blind questions plus the public skill and registered
  MCP tools. This condition must use the exact tool names required by each
  question.

Run generation and evaluation must happen in a context that cannot read
`questions.jsonl`, `gold/`, or `rubric.md`. A session that has seen those files
is not a valid blind run.

Blind records use opaque `AFQ001`-style IDs; semantic labels never appear in
session paths or prompts. The MCP harness embeds the blind question in the generator prompt and also
copies the question and public skill into an externally sandboxed temporary
workspace. Before any answer generation, harness 2.2 hashes every live REST
response in the evidence plan and requires exact parity with the frozen
fixtures. Before an answer is accepted, it verifies the copied input hashes,
the exact target model and release, successful required calls, planned 100-hit
retrieval, per-representative evidence calls, benchmark arguments, MCP-reported
REST source URLs from that parity-checked base, absence of off-target,
foreign-MCP, and web calls, a bounded MCP-call budget, non-definitive claim language,
and source citations on every E item. An input-access,
snapshot-parity, or semantic-audit failure invalidates the attempt instead of
allowing target inference or grading a different evidence snapshot.

Harness 2.3 bounds compact hit results at 12 per stream while prioritizing
distinct query regions, embeds compact model metadata in that same call, omits
raw model sequence and per-residue confidence arrays by default, sends large
MCP payloads only once as structured content,
and retrieves evidence for up to six representative SIDs in one batch call.
Regional confidence and calibration records are batched as well. Harness 2.5.1
added a joined decision-evidence call that resolves calibration nodes from a
representative SID and explicitly requested lineage level, preserves successful
domain peers after per-SID REST errors, and exposes confidence fragmentation and
sustained low-confidence stretches. Its audit recognizes that joined result as
a strict confidence/domain evidence superset when a narrow question legitimately
uses it in place of the standalone domain batch. The generator has an eight-call
ceiling, with a normal regional path of two calls. The model interprets the
retrieved evidence and writes the assessment under the public skill's scientific
and non-definitive-claim rules. Four-way question
concurrency and separate answer/evaluator reasoning
defaults remain available. Generated summaries include the aggregate and score
distribution, harness validity counts, token and timing metrics, four criterion
subscores, and the evaluator's concrete strengths, omissions, and applied
penalties for each question.

R7 remains the completed development baseline: 40/48 scientific points,
12/12 first-attempt audit passes, and exactly two calls per question. The archived
suite-1.6 r11 and suite-1.7 r12 experiments scored 31/48 and 30/48 with worse
end-to-end performance, so their implementation was rolled back. The current
runner uses the full 38-tool r7 catalog and does not accept `--mcp-tool-profile`.
See [the retained development comparison](PERFORMANCE_NOTES.md). Restoring r7 is
not a new scientific evaluation or a claim that its residual errors are solved;
the independently reviewed one-shot holdout remains pending.

## Files

- [Typed Scientific Decision Record Architecture](SCIENTIFIC_DECISION_RECORD_ARCHITECTURE.md):
  archived design for the rolled-back experiment, not the active implementation.
- `questions.jsonl`: private questions and expected behaviors.
- `inputs/questions_blind.jsonl`: generated prompts without scoring fields.
- `evidence-plan.json`: eight models, 59 domain SIDs (the curated
  representatives plus every SID exposed by compact hit selection), and 103
  predeclared node/threshold benchmark strata. AFQ008 additionally freezes all
  supported 2/3/5 threshold-sensitivity strata.
- `fixtures/api/`: 355 frozen local REST responses plus hashes. Domain and
  benchmark evidence is closed over compact hit representatives so a
  scientifically reasonable shortlist is not rejected merely because its SID
  or lineage node was omitted from the fixture inventory.
- `gold/evidence-summary.json`: generated evidence digest and pLDDT statistics.
- `gold/answers.jsonl`: draft gold answers, each restating the benchmark question
  before a short conclusion, regional function/family/superfamily/fold tables,
  cited evidence items, and caveats keyed to those items. Specific SCOPe levels
  pair names with SCCS identifiers; sources link release-qualified SCOPe pages
  and the corresponding sequence or FATCAT hit landing pages.
- `rubric.md`: scoring rules.

## Rebuild

```bash
python3 benchmarks/alphafold-scope-homology/scripts/build_blind.py
python3 benchmarks/alphafold-scope-homology/scripts/fetch_evidence.py --refresh
python3 benchmarks/alphafold-scope-homology/scripts/build_evidence_summary.py
python3 benchmarks/alphafold-scope-homology/scripts/validate.py \
  --minimum-completed-runs=0
```

Development validation intentionally permits zero current runs while the suite
is being edited:

```bash
python3 benchmarks/alphafold-scope-homology/scripts/validate.py
```

Require an independently generated and evaluated run only for release
validation:

```bash
python3 benchmarks/alphafold-scope-homology/scripts/validate.py \
  --minimum-completed-runs=1
```

Artifact validity alone does not meet the scientific/performance completion
criteria. Check the predeclared quantitative development gates as well:

```bash
python3 benchmarks/alphafold-scope-homology/scripts/check_development_gates.py \
  benchmarks/alphafold-scope-homology/results/runs/RUN_ID
```

The command exits nonzero for incomplete results, failed audits, fewer than
10/12 first-attempt passes, mean score below 3, any score below 2, call-budget
violations, or less than 30% median-input reduction from the frozen r5 baseline.
It does not replace artifact validation, scientific review, or the one-shot
holdout. A smaller token improvement requires explicit acceptance under the
predeclared plan; the checker never silently waives that gate.

The fetcher defaults to the local API at
`http://127.0.0.1:8765/api/v1/index.php`; pass `--base-url` only when testing an
equivalent local deployment. The API must point at the Doppelbock copy that
contains the candidate and chain-benchmark tables. The fixtures are frozen
evidence. Refreshing them changes the benchmark and requires reviewing every
gold answer.

The MCP benchmark runner defaults to the REST base recorded in the fixture
manifest. If the MCP server uses another local base, pass that same value with
`--rest-base-url`; the run will proceed only if its responses are byte-equivalent
after canonical JSON serialization and every accepted MCP result attests URLs
from that base.

For a closed-evidence development run after the mutable database has drifted,
serve the checked-in, digest-verified snapshot on loopback and point both MCP
and the runner at the same replay base. This preserves the frozen suite rather
than silently refreshing its scientific evidence:

```bash
python3 benchmarks/alphafold-scope-homology/scripts/serve_frozen_fixtures.py \
  --port 8766
SCOP_REST_BASE_URL=http://127.0.0.1:8766/api/v1/index.php \
  SCOP_MCP_TRANSPORT=streamable-http mcp/.venv/bin/scop-mcp
python3 benchmarks/alphafold-scope-homology/scripts/run_codex_mcp_benchmark.py \
  --rest-base-url http://127.0.0.1:8766/api/v1/index.php
```

The replay service binds to loopback, accepts only manifest-listed GET/HEAD
routes, verifies every fixture digest before binding, and returns 404 for all
other paths. A live database-backed smoke remains a separate pre-run gate.
