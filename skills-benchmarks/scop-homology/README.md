# SCOP/ASTRAL/SCOPe Agent Benchmark

This benchmark evaluates whether an agent can use SCOPe to make biologically
sound inferences about structural classification, evolutionary relationships,
domain boundaries and reuse, structural heterogeneity, representative
selection, and dataset design. SCOPe is treated as the reference
classification; API details and identifiers are supporting evidence rather
than the primary target.

`benchmark.json` is the suite contract. Increment `suite_version` whenever a
question, expected answer, rubric, condition, or fixture changes. Results must
record that version and the SHA-256 hashes of the exact benchmark inputs.
Suite 4.1 changes API-enabled runs from mounted fixtures to captured access to
the live SCOPe REST API. Suite 4.0 results remain retained as historical
fixture-based runs. See [CRITICAL_ASSESSMENT.md](CRITICAL_ASSESSMENT.md) for
interpretation of those results.

## Conditions

Run the same blind questions under these three conditions:

1. `pretrained_only`: pretrained model only; no skill, API, fixtures, or MCP.
2. `pretrained_api`: pretrained model plus captured access to the live REST
   API; no skill or MCP.
3. `pretrained_skill`: pretrained model plus captured access to the same live
   REST API and the public `scop-homology` skill; no MCP.

Suite 4.2 adds two MCP conditions, available through the Claude Code runner:

4. `pretrained_mcp`: pretrained model plus the SCOPe MCP server (stdio
   transport, `mcp/.venv/bin/scop-mcp`) as its only route to SCOPe data; no
   shell, skill, or direct REST access. The server is pointed at the loopback
   capture gateway, so its REST traffic is retained exactly as in the API
   conditions.
5. `pretrained_mcp_skill`: the MCP server plus the public `scop-homology`
   skill read from the workspace; no shell, so the skill's command-line helper
   is unavailable and its guidance is applied through the MCP tools.

There is no skill-only condition because the skill requires SCOPe data access
to answer release-specific questions correctly.

Each API-enabled question receives a loopback capture URL that forwards GET
requests immediately to the configured live API. The gateway never serves
stored responses. It retains the complete path and query, status, selected
headers, elapsed time, raw response body, body hash, and transport error.
Answers may cite only successfully captured requests.

## Tracks

- `conformance`: API-literacy controls reported outside the biological score.
- `held_out`: biological cases whose named entities and answer-bearing
  identifiers are absent from the public skill and references.

Every gold question has an explicit entity inventory. Validation scans both
those entities and answer-bearing identifiers against the complete skill tree.

## Score Reporting

- `biological`: the primary benchmark. It contains every question except Q19.
  Report biological-reasoning and factual-support means as separate primary
  axes.
- `gated score`: a secondary summary calculated as the smaller of
  `0.75 * biological + 0.25 * factual` and the factual-support gate
  `[1, 2, 3, 4, 4][factual]`. It is not rounded.
- `api_conformance`: Q19 only. It tests release literacy and is reported
  separately; biological reasoning is `null`.

Never combine the two reporting groups into one headline average.

## Layout

- `questions.jsonl`: private gold cases, expected evidence, and scoring criteria.
- `inputs/questions_blind.jsonl`: generated agent inputs with gold fields and
  seed-identifier roles removed.
- `fixtures/api/`: suite 4.0 and gold-development snapshots; these are not
  mounted in suite 4.1 answer workspaces.
- `schemas/`: JSON Schemas for benchmark and result records.
- `scripts/`: blind-input generation, live capture, fixture refresh, and
  validation.
- `results/runs/<run-id>/`: run manifest, answers, evaluation, summary, event
  logs, and per-question API traces.

## Workflow

```bash
python3 -m pip install -r benchmarks/scop-homology/requirements.txt
python3 benchmarks/scop-homology/scripts/build_blind.py
python3 benchmarks/scop-homology/scripts/validate.py \
  --skill-dir /scratch/jmc/scop/skills/scop-homology
```

Run and evaluate all three conditions with isolated Codex CLI sessions:

```bash
python3 benchmarks/scop-homology/scripts/run_codex_benchmark.py \
  --model gpt-5.5
python3 benchmarks/scop-homology/scripts/evaluate_codex_runs.py \
  benchmarks/scop-homology/results/runs/*_gpt-5-5_*_r? \
  --model gpt-5.6-sol
```

Run the same conditions, plus the MCP conditions, through Claude Code, and
judge any set of runs with a Claude evaluator when Codex is unavailable:

```bash
python3 benchmarks/scop-homology/scripts/run_claude_benchmark.py \
  --model claude-fable-5-1 --replicates 3 --jobs 4
python3 benchmarks/scop-homology/scripts/evaluate_claude_runs.py \
  benchmarks/scop-homology/results/runs/*_claude-fable-5-1_*_r? \
  --model claude-opus-5-5
```

The Claude runner starts one non-persistent `claude -p` session per question
with no user or project settings, no skills, no CLAUDE.md, and only the
built-in tools the condition allows (`Bash,Read,Glob,Grep` for REST access,
`Read,Glob,Grep` for MCP plus skill, none otherwise). The structured answer
is validated by Claude Code against the harness answer schema. The event log
is the `stream-json` session output; the audit rejects WebFetch/WebSearch,
direct `scop.berkeley.edu` commands, and MCP sessions that made no MCP call.
Citations that cannot be matched to a captured request, a skill file, or an
MCP tool are dropped and listed under `evidence_audit` in the manifest rather
than failing the run. The manifest also records cost, tokens, and tool calls.

The Codex runner creates a fresh temporary `CODEX_HOME` containing authentication
only, disables memories and user configuration, and starts a separate session
for every question. API-enabled sessions are directed to the loopback capture
URL specified by the harness. The runner rejects obvious direct-command
bypasses, and reported API evidence must match a successful captured response.
The runner records
`/releases/current` before and after each condition run and rejects a run if the
stable release changes. It runs three replicates by default. The evaluator
sees the private gold only after answer generation, scores one question per
session, and receives randomized opaque answer labels with condition metadata
removed.

Do not hand-edit `questions_blind.jsonl`, captured API traces, or response
bodies. Refresh the gold-development fixtures only when reviewing a new suite
release. A benchmark change is complete only when validation passes and
existing result manifests still refer to an available suite version. Old runs
must not be silently scored against changed questions.

## Prompting Rules

- Do not scrape SCOPe HTML pages.
- Prefer public identifiers: release, SID, SUNID, SCCS, PDB code, domain range,
  and species name.
- Report the queried release and public identifiers as supporting evidence when
  they are needed to make a claim reproducible. A minor omission must not
  outweigh a correct biological interpretation.
- Treat family and superfamily as evolutionary classification levels. Do not
  infer close evolutionary relationships from fold or class alone.
- Treat AEROSPACI/SPACI as quality and representative-selection evidence, not
  evolutionary evidence.
