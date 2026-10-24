# Blind Benchmark Inputs

`questions_blind.jsonl` is generated from `../questions.jsonl`. It contains
only the question ID, track, category, and prompt. It omits expected answers,
scoring criteria, evidence paths, the private entity inventory, and seed
identifier metadata.

Regenerate it after every gold-case change:

```bash
python3 benchmarks/scop-homology/scripts/build_blind.py
```

Do not edit the generated file directly.
