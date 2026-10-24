# Rubric

Score every answer from 0 to 4 against its gold answer, expected behaviors, and
failure modes.

The gold answer is a draft, non-exhaustive evidence reference, not an
exact-match key. Scientifically equivalent representative SIDs, defensible
evidence-supported interval envelopes, and harmless numeric rounding receive
the same credit. A difference from the example gold is penalized only when it
materially contradicts the frozen evidence or misses the tested proposition.

- **4 — Complete:** Correct region-specific evidence hypothesis; sequence,
  FATCAT, lineage,
  exact sequence provenance, local pLDDT, and applicable node-specific
  benchmark context are integrated; release and representative SIDs are
  reported; caveats and the non-official, non-definitive nature of the
assessment are clear.
- **3 — Mostly correct:** Core region and relationship level are correct, but
  one important metric, identifier, confidence detail, or caveat is omitted.
- **2 — Partial:** Some evidence is correct, but regions are merged, the
  relationship level is poorly calibrated, or local confidence is mishandled.
- **1 — Poor:** Mostly generic reasoning, top-hit-only reasoning, or unsupported
  claims with little use of the supplied evidence.
- **0 — Incorrect:** Fabricated evidence, wrong region/classification, pLDDT or
  fold similarity called homology, or empty results treated as proof of absence.

The deterministic pre-grading audit rejects a definitive positive or negative
claim that the AlphaFold target is homologous, not homologous, assigned, or
classified at a SCOPe node, so such an attempt is invalid and must be retried
rather than graded. The answer must instead describe what the evidence supports
as a hypothesis, does not support, or leaves inconclusive. For an accepted
answer, ambiguous official-sounding wording that escaped the linter lowers the
score by one point and must be quoted in the evaluator notes; it does not erase
otherwise correct scientific content with a blanket score ceiling. Cap the
score at 1 when a short helix or high pLDDT is the primary homology argument.
Missing release or material warnings normally lowers an otherwise correct
answer by one point.
For a hypothesis materially driven by FATCAT, missing the planned benchmark or
using its match fraction as homology evidence lowers the answer by at least one
point. For an empty AlphaFold sequence-hit result, ignoring a missing
`af_model.uniprot_id`, missing query sequence, or absent stored BLAST provenance
caps the score at 2.
