# R7 restoration — 2026-09-06 UTC

> Historical restoration record. The current skill and runner prompt have a
> subsequent instruction revision; see [the active implementation](README.md).
> R7 evidence collection and recorded benchmark artifacts remain preserved.

The user requested restoration of r7 after the post-r7 architecture regressed
in scientific scoring, token use, latency, and tool-call count. The active
implementation is restored to suite **1.5.0**, harness **2.5.1**.

## Exact implementation recovered

R7 was benchmarked with uncommitted changes on top of Git commit
`ab759d3c900e804efb40ee779a52fde75fa913e0`; resetting to that commit alone would
have restored the older implementation. The successful edits recorded before
r7 started were replayed in memory on that base, then restored with scoped
patches. Failed historical patch attempts were excluded. The recovered skill
also independently matches its complete text in the r7 answer-session logs.

The actual restored files match the original immutable r7 checkpoint:

| Component | SHA-256 |
| --- | --- |
| MCP source tree | `6c88b20380ff4528cac5782e5ac197354f67798f57e17c829d25bfc1a34139d9` |
| Complete public skill tree | `8a8fe87f1d13eddac268d6f42cb19630c11200d8ef78e9a5c457573648374d3e` |
| Skill entry point | `b973f30619f8f46e1ad3069d4c6daf377b99ed90709b28f1496a6b4000fe5b30` |
| Benchmark runner | `51eced93ca56fe2a2507e36bc3213738d5f85415bad4a78517036cab61d557bd` |
| Fixture manifest | `8a886c3e00a56e2ce2d2de03be03ffcc7719cfb703512f8f6eb1ca0c2d8daddb` |

The active workflow is the original compact hit discovery followed by one joined
regional evidence call (or the domain batch for a narrow question). It retains
r7's confidence summaries, exact lineage-resolved calibration, partial batch
error handling, 12-hit-per-stream cap, and six-region/six-SID limits. The model
again writes the evidence-based assessment. There is no added packet handle,
claimability policy, mandatory third validation call, or deterministic renderer.
The original full catalog has **38 tools**; the assessment-only catalog option
is no longer part of the current runner.

## Verification

- **228 tests pass**, including 18 new restoration checks.
- All **24 recorded r7 tool responses** replay identically on the frozen
  fixtures after excluding only `fetched_at` collection timestamps. Every case
  retains its original two-call workflow. Replayed calls request exactly their
  recorded REST sources, with no repeated retrievals.
- `validate.py --minimum-completed-runs=1` passes, with r7 recognized as the
  current-suite run and all six completed run directories preserved.
- The quantitative gate checker passes against the unchanged archived r7 run.
- All **355 fixture payloads**, the questions, gold answers, rubric, and
  historical run artifacts were preserved. Archive comparison checked **1,218
  protected files** without finding a change. Only fixture-manifest metadata
  was restored to its original r7 bytes.
- **31 unrelated dirty PHP/UI and REST-documentation paths** were unchanged,
  and the existing PHP contract test was separately checked against the backup.
- `git diff --check` passes. No Git reset, commit, push, deployment, dependency
  installation, model answer generation, or holdout run was performed.

These checks establish restoration and deterministic compatibility. R7's
**40/48** score, median **100,463.5** input tokens, and median **67.68 s** answer
time are its original observations, not new measurements. Its residual
scientific limitations and the pending independent holdout remain unchanged.

## Recovery and retained history

The pre-restoration experiment is recoverable from
[the recovery archive](../../.rollback-backups/r7-restore-vh8yjN/pre-restore.tar.gz)
and [its inventory notes](../../.rollback-backups/r7-restore-vh8yjN/README.md).

Archive SHA-256:
`4a217bbe8c76757aef7b7b0e73d3d75976c7ddac847d0e419081151d3060aa29`.

Fifteen experimental files were removed from active source, schema, policy,
test, and deployment-example locations only after verifying their archived
copies byte-for-byte. Extract the archive into a separate scratch directory
for inspection; do not overwrite the live worktree wholesale. The architecture
proposal and r11/r12 results remain in the benchmark as explicitly rolled-back
history. The offline fixture replay utility and quantitative gate checker are
retained outside the answer path.

An additional `r7-restored.tar.gz` in the same recovery directory freezes the
restored implementation, test/support files, and evidence/results for future
recovery without needing the original session history. Checksums are recorded
in `archive-checksums.json` in that directory.

Future changes should be isolated and compared individually with r7 before
adoption. Merely passing the older minimum gates does not demonstrate
scientific or performance non-regression against this restored baseline.
