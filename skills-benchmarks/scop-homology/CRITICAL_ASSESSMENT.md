# Critical Assessment of SCOPe Benchmark Suite 4.0

## Scope

Suite 4.0 tests whether SCOPe data and the public `scop-homology` skill improve
biological reasoning about structural classification, remote homology, domain
boundaries, functional divergence, and evidence calibration. It uses three
conditions:

1. `pretrained_only`
2. `pretrained_api`, with frozen REST fixtures
3. `pretrained_skill`, with the same fixtures plus the public skill

There is deliberately no skill-only condition. The skill is an interpretation
and workflow layer over SCOPe data, so release-specific use requires API
access. Each condition was run in three replicates with one fresh Codex session
per question.

## Results

| Condition | Biological reasoning | Factual support | Secondary gated | Run-level gated SD |
| --- | ---: | ---: | ---: | ---: |
| `pretrained_only` | 2.91 | 2.25 | 2.68 | 0.14 |
| `pretrained_api` | 3.65 | 3.87 | 3.71 | 0.11 |
| `pretrained_skill` | 3.75 | 3.90 | 3.79 | 0.06 |

API access produced the dominant improvement over pretrained knowledge:
`+0.74` biological, `+1.62` factual, and `+1.03` gated. Adding the skill to the
same API evidence produced a smaller improvement: `+0.10` biological, `+0.03`
factual, and `+0.08` gated.

The skill gain was concentrated rather than general. The largest biological
improvements were on the same-superfamily/not-same-family distinction,
ferritin-like functional divergence, interpretation of the heterogeneous
homeodomain fold, and classification as an evolutionary hypothesis. Fourteen
of the 23 biological questions showed no mean biological-score difference.
Actin-like ATPase and Rossmann-family questions declined by `0.33` biological
points. The observed skill effect is therefore modest and comparable in size
to run and evaluator variability; it is not evidence of broad superiority.

## Skill Assessment

The revised skill is safer than the version tested in suite 3.0. It now:

- requires inspected API evidence for exact release-specific claims;
- requires all compared quality records before ranking structures;
- separates family/superfamily evidence from fold-level similarity;
- treats different-superfamily classifications as negative SCOPe evidence,
  not proof of analogy, convergence, or absence of homology;
- names missing endpoints or evidence and abstains instead of filling gaps;
- provides a deterministic comparison helper for release and lineage checks.

The results support retaining these changes. Answers generally distinguished
classification evidence from biological interpretation and did not repeat the
suite 3.0 unsupported quality comparison. The remaining losses suggest that
the skill can still over-structure an answer around classification terminology
without fully explaining the functional or mechanistic consequences.

## Benchmark Strengths

- The biological cases use entities absent from the public skill and its
  references; validation checks an explicit per-question entity inventory.
- API and skill conditions receive byte-identical fixture corpora, isolating
  the skill as their intended experimental difference.
- Blind inputs omit gold answers, evidence paths, seed roles, and scoring
  criteria.
- Each answer and each evaluation uses a fresh session. Condition labels are
  removed and answer order is randomized for evaluation.
- Three replicates expose instability hidden by a single run.
- Full answer-session event logs, stderr, manifests, hashes, and evaluator
  outputs are retained.

## Material Limitations

### Evidence and gold-answer alignment

Three questions require evidence that their mounted fixtures do not fully
provide:

- Q19 expects semantics for the periodic-update endpoint that are not stated by
  the current-release response.
- Q20 expects the four-helix topology of the syntaxin domain, which is not in
  the mounted API evidence.
- Q22 expects manuscript-level structural and sequence evidence for the
  collagenase G/Cry3A relationship. The skill appropriately labels that
  rationale as unverified when it is not mounted.

These questions partly reward prior knowledge or unsupported completion rather
than disciplined use of available evidence.

### Retrieval realism

API-enabled agents receive a curated directory containing every fixture needed
by the whole suite. This tests evidence interpretation but makes discovery and
endpoint selection easier than a live REST or MCP workflow. It cannot measure
request planning, pagination, recovery from empty results, or bounded search.

### Evaluator uncertainty

The judge is an LLM and was run once per answer. Aggregate control scores moved
by roughly a tenth of a point across evaluator reruns, which is material
relative to the `+0.08` skill effect. Q23 also exposed a gold metadata defect:
its entity inventory names a HoxA9 alternative fold even though the fixtures
place the compared domains under the Engrailed/Fruit fly homeodomain grouping.
Judge input was sanitized so entity inventory metadata no longer affects
scoring, but the inventory itself should be corrected in the next suite.

### Isolation assurance

The runner creates a temporary `CODEX_HOME`, disables memories, user
configuration, and rules, and mounts only condition-specific inputs. A scan of
all 216 retained answer-session event logs found no non-skill references to the
public skill, this private repository, installed skill paths, or private gold
data. API and skill fixture hashes also match in all replicate pairs.

This is strong audit evidence, not hard filesystem isolation. Codex ran in a
read-only sandbox that could theoretically read paths outside the temporary
workspace. A container or `bwrap` setup exposing only the condition workspace
would turn the policy into an enforceable boundary.

### Rubric shape

Several expected answers are checklist-heavy. This can reward mentioning many
terms over giving a concise, biologically decisive explanation. Alias and
classification-name variation can also make an otherwise correct answer look
incomplete to an automated judge.

## Recommendations for Suite 4.1

1. Align Q19, Q20, and Q22 gold requirements with mounted evidence, or add
   explicit frozen manuscript/reference evidence available to both API and
   skill conditions.
2. Correct the Q23 entity inventory without changing suite 4.0 in place.
3. Replace the all-fixtures directory with captured live REST access so the
   agent must select and execute endpoints while complete request/response
   evidence remains available for debugging.
4. Run multiple independent evaluator replicates or human-adjudicate answers
   whose condition delta is smaller than evaluator variability.
5. Enforce workspace-only filesystem visibility with a container or `bwrap`.
6. Add harder held-out cases that require combining classification lineage,
   boundaries, and external biological evidence rather than merely locating a
   classification label.

Suite 4.0 and its results should remain immutable. These corrections belong in
a new suite version so the checked-in results remain reproducible.

## Suite 4.1 Follow-Up

Suite 4.1 replaced mounted answer fixtures with captured access to the live
SCOPe REST API. Each question now has to construct actual requests. The harness
retains paths, queries, statuses, timing, selected headers, raw response bodies,
hashes, and transport errors, but it does not replay captured traffic.
Start/end current-release observations ensure a stable release does not change
during a run. The suite remains intended to be reviewed and updated for each
new SCOPe release.
