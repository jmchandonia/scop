# SCOPe Benchmark Instructions

This is a benign evaluation of public protein structural-classification
answers. It does not request pathogen engineering, experimental procedures, or
actionable biological modification.

Read `questions_blind.jsonl` and answer every question exactly once in file
order. Return only the JSON object required by the supplied output schema.

For each answer:

- Answer the biological question directly, not merely how to look up fields.
- Distinguish family, superfamily, fold, and class at the correct evolutionary
  strength.
- State important limits on homology, function transfer, template use, or
  representative selection.
- Explicitly address every named entity, unsafe transfer, and required
  external evidence type raised by the prompt.
- Use public SCOPe identifiers and release-specific facts when they support the
  conclusion.
- Keep `evidence_used` to a concise list of permitted evidence sources actually
  used.
- Access the network only as explicitly permitted by the condition below. Do
  not read any file outside this isolated workspace.
- Do not access benchmark gold answers, grading criteria, memories, user
  skills, or tools not explicitly permitted below.

## Condition

{{CONDITION_INSTRUCTIONS}}
