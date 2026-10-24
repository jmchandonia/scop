# SCOPe Benchmark Evaluation Instructions

This is a benign evaluation of public protein structural-classification
answers. It does not request pathogen engineering, experimental procedures, or
actionable biological modification.

Read `question.json`, `rubric.md`, and `answers.jsonl`. The answer labels are
opaque and their order has been randomized. `answers.jsonl` includes a
condition-blinded `evidence_bundle` with successful SCOPe API response bodies
for API paths cited by the answer. Evaluate every answer independently against
the question's `must_include`, `must_avoid`, and `scoring_focus` criteria.

Return exactly one evaluation for every answer using the supplied output
schema. Preserve each `answer_id` and `question_id` exactly.

- For primary biological questions, assign separate integer 0-4
  `biological_reasoning_score` and `factual_support_score` values.
- For API-conformance questions, set `biological_reasoning_score` to `null` and score factual
  support only.
- Return `error_tags` as a sorted unique list of machine-readable tags for the
  decisive omissions or errors. Use `[]` for a complete answer with no
  material error.
- Do not calculate the derived overall score; the harness calculates it.
- Use equivalent scientific wording rather than literal phrase matching.
- Treat `expected_answer` as minimum required content, not an exhaustive list
  of true facts. Do not penalize an extra release-specific API fact when it is
  directly supported by that answer's `evidence_bundle`.
- Use an answer's `evidence_bundle` only to check claims made in that answer.
  Do not credit facts that appear only in the bundle and are omitted from the
  answer.
- Apply the rubric's hard ceilings.
- Use only the error-tag taxonomy in `rubric.md`.
- Make `rubric_notes` concise but specific about the decisive strengths,
  omissions, or errors.
- Do not infer a condition from wording or evidence references, and do not
  compare answers while scoring an individual response.
- Do not access the network or files outside this isolated workspace.
