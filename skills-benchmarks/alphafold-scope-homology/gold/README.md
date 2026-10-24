# Gold Evidence

`evidence-summary.json` is generated from frozen API fixtures. `answers.jsonl`
contains reviewer-facing gold conclusions grounded in those fixtures. Gold
answers are draft gold until a domain expert marks them curator verified.

Each answer retains the reference disclosure and benchmark question, then follows
the skill's response structure:

1. A 1–2 sentence conclusion with qualified trust.
2. Function, family, superfamily, and fold assessments for each material region,
   with ambiguity and references to the evidence items.
3. A numbered evidence list (`E1`, `E2`, …), including competing or discounted
   evidence, with clickable SCOPe sources and hit-specific landing pages.
4. Caveats keyed to the same evidence IDs.

API citations identify the release-qualified sources represented by the frozen
fixtures; local confidence summaries derive from the model's residue data.
`evidence_paths` and the fixture manifest retain the frozen retrieval inventory.
The layout revision preserves the original observations, scientific conclusions,
`must_include`/`must_avoid` criteria, and draft status. It does not change the
scoring rubric or rewrite historical model answers.
