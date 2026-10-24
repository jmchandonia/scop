# Completeness revision compared with R5

This suite-1.18.4 / harness-2.9.6 run is a complete, release-valid result for
the current implementation. It demonstrates recovery of the measured scientific
decline, but it does not demonstrate an end-to-end speed improvement and is not
a performance promotion.

## Integrity and conditions

- GPT-5.5 medium generated answers; GPT-5.6-sol low evaluated them, with four
  concurrent sessions for each phase, matching R5.
- All 355 frozen REST responses passed canonical parity before generation.
- The manifest records MCP source tree SHA-256
  `6c88b20380ff4528cac5782e5ac197354f67798f57e17c829d25bfc1a34139d9`
  and skill tree SHA-256
  `f21d9d480bc0685a77a66f96943f5b7989e05217be0ced6b8a0c4f31880ac575`.
- Release validation with `--minimum-completed-runs=1` passes and reports one
  current-suite run. All 12 accepted answers pass input, MCP, claim, and citation
  audits.
- The skill uses a question-scoped evidence/caveat ledger, release-qualified
  citations, qualitative strong-chance wording, and a bounded raw-hit fallback
  when compact records hide a required rank or classification contrast.

## Measured result

| Metric | R5 baseline | Current run | Change |
| --- | ---: | ---: | ---: |
| Scientific score | 42/48 | 44/48 | +2 points |
| Pass@1 | 12/12 | 10/12 | -2 |
| Accepted / all-attempt MCP calls | 43 / 43 | 26 / 32 | -39.5% / -25.6% |
| Total input tokens, generation + evaluation | 2,290,612 | 2,543,864 | +11.1% |
| Retry-charged median answer input | 178,228 | 160,582 | -9.9% |
| Cumulative model-session time | 1,296.8 s | 1,678.9 s | +29.5% |

Relative to the preceding current-hash checker run, scientific score rises from
38/48 to 44/48. Nine answers score 4/4, two score 3/4, and one scores 2/4.
AFQ002 now includes the previously omitted class-c representative; AFQ004 uses
the right regional evidence but still selected family rather than superfamily
kinase calibration; AFQ005 fully records the empty-sequence provenance.

Two first attempts made lineage calls for raw-only SIDs outside the closed
fixture inventory. The harness rejected them and the accepted retries stayed
inside the frozen evidence. AFQ007 still undercalls the expected family-level
hypotheses and selects the wrong phosphatase calibration level. AFQ010 omits the
specific collagen partner-chain caveat. These residuals remain visible rather
than being described as solved.

## Gate result

The deterministic-audit, first-attempt, scientific-mean, minimum-score, median-
call, and maximum-call gates pass. The median-input-reduction gate fails because
9.9% is below the required 30%. Total input and model-session time are also above
R5. This revision is evidence for improved scientific completeness, not for an
end-to-end efficiency improvement.
