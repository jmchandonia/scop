# Benchmark Results

Each run belongs in `runs/<run-id>/`:

```text
runs/<run-id>/
├── manifest.json
├── answers.jsonl
├── evaluation.jsonl
├── summary.md
├── logs/
└── api/                  # suite 4.1 API-enabled runs only
```

`manifest.json` records the suite and input hashes, condition, exact model
snapshot and parameters, harness identity, skill provenance when applicable,
live API release observations and trace hashes when applicable, legacy fixture
provenance, and start/end timestamps. This prevents a run from being scored
against a changed benchmark or skill.

For suite 4.1 API-enabled runs, `api/` contains a trace and raw content-addressed
response bodies for the start/end release observations and every question.
These files are debugging and provenance artifacts, not a replay service.
Transient statuses and transport errors remain in the trace.

`answers.jsonl` contains the exact prompt and answer for each question. Evidence
is recorded as structured references rather than machine-local absolute paths.
`evaluation.jsonl` records the evaluator, biological-reasoning and
factual-support subscores, the secondary gated per-question score, structured
error tags when emitted by the evaluator, and rubric judgment. Biological
reasoning and factual support are reported as separate primary axes. Q19 forms
a separate API-conformance group and is not included in the biological
averages.

All JSON records must conform to the schemas under `../schemas/`. Run
`../../scripts/validate.py` before committing results.

Cross-condition reports are stored beside `runs/`. Reports compare three
replicates per condition by default and list per-question skill-over-API
deltas. The interpretation, limitations, and next-suite recommendations for
suite 4.0 are in [the critical assessment](../CRITICAL_ASSESSMENT.md).
