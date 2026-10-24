# Skill benchmarks

Benchmarks of the public SCOPe skills (`../skills/`) and of agent access to
SCOPe through the REST API and the MCP server. Each directory holds one
benchmark: its questions and private gold records, rubric, harness
instructions, schemas, scripts and tests, and under `results/` every run of the
current suite with the agent's answers, the judges' evaluations, the session
logs and the captured API traffic.

- `scop-homology/`: suite 4.3.0. Twenty-four questions on SCOPe domain
  relationships, answered by seven models (OpenAI through the Codex CLI,
  Anthropic through Claude Code) in five conditions with three replicates, and
  scored by two judges (`claude-fable-5-1` in `evaluation.jsonl`, with the
  `gpt-5.6-sol` judge's reports kept as `*_judge-gpt-5-6-sol.md`).
- `alphafold-scope-homology/`: suite 1.18.4. Twelve questions on locating
  SCOPe-like regions in AlphaFold models, with the current-suite run.
