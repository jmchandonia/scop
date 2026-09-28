---
name: alphafold-scope-homology
description: Form non-official, non-definitive SCOPe relationship hypotheses for regions of an AlphaFold model and provide an auditable evidence trace. Combine local AlphaFold pLDDT confidence, top sequence hits, FATCAT structure hits, bias-aware node-specific chain-statistics caveats, release-scoped SCOPe lineages, annotations, and homology warnings. Use for AlphaFold-to-SCOPe hit interpretation, candidate family or superfamily hypotheses, fold-level similarity assessment, conflicting hit analysis, and multi-domain region analysis. Do not use for general AlphaFold prediction, formal SCOPe classification, or function assignment from similarity alone.
---

# AlphaFold–SCOPe Evidence Assessment

Assess each aligned region as an evidence hypothesis, never as an official SCOPe
classification or a definitive homology determination.

## Claim Boundary

Begin every assessment with exactly:

> This is a non-official evidence assessment, not a definitive determination of homology or an official SCOPe classification.

Do not say that the AlphaFold target or a region *is* or *is not* homologous,
belongs to a node, or is assigned or classified. Say what the retrieved evidence
supports, favors, does not support, or leaves inconclusive. Definitive statements
about a retrieved SCOPe representative's own classification are allowed.

Even convergent evidence cannot support a final or complete conclusion about the
target's evolutionary homology, official SCOPe classification, exact domain
boundary, or function. State the strongest defensible evidence hypothesis and
its residual uncertainty. When the evidence is strong at that relationship
level, the qualitative formulation **"the evidence supports a strong chance of
a [node/level] relationship for approximately residues [range]"** is allowed.
It is not a numerical probability or a definitive assignment, and it must be
followed by the material limitations that prevent a stronger conclusion.

All target ranges are approximate evidence-alignment envelopes, not curated
domain boundaries.

## Question Contract

Identify the exact model, pinned release, tested proposition, requested contrasts,
and requested output before selecting evidence. Answer that proposition directly.
Establish the evidence required by the question:

| Question | What evidence must be established | Expected result |
| --- | --- | --- |
| Regional relationship | Material sequence and structure alignments, their coverage, retrieved lineage, credible alternatives, and confidence where geometry affects the inference | Approximate region, supported relationship level, and the observations that justify it |
| Does hypothesis X fit? | Actual evidence for X compared with the strongest alternative over the same region, including differences in extent and strength | Direct comparison explaining which evidence controls the conclusion and why |
| Does a hit limit miss something? | Composition of the requested rank window, its repeated regional signals, and the rank and range where another signal first appears | Explicit explanation of what the limit hides and whether a broader window changes the answer |
| Does low confidence invalidate a match? | Confidence over each matched interval versus the whole model, and which conclusions depend on predicted geometry | Separate conclusions for the affected regions, preserving sequence evidence that does not depend on that geometry |

Use only the rows relevant to the request; combine them when the question asks
for several comparisons. Sequence provenance and calibration requirements below
apply when those evidence types matter or are explicitly requested. A response
heading alone does not require additional retrieval.

Use SCOPe terminology literally. For example, an alpha/beta SCOPe domain means
the alpha-and-beta structural class, not a comparison of hemoglobin subunits.

## Required Evidence And Caveat Ledger

Before retrieval, make an internal checklist from the question. Include every
explicitly named model, SID or hit, region, competing interpretation, rank
window, metric, provenance fact, calibration threshold, requested contrast, and
requested output. Add every returned observation that could strengthen, weaken,
or change the relationship level or regional conclusion. Do not substitute a
nearby or easier representative for one the question explicitly names.

Before returning, reconcile that checklist against the answer:

- Every question-required or decision-material item is either reported and
  traced to a numbered evidence item, or explicitly reported as unavailable
  with the effect of that gap on the conclusion. Never silently omit it.
- Every release-specific factual assertion and every basis for a conclusion in
  the conclusion or level tables points to the supporting or contrary **E**
  item or items. Each **E** item has its own successful-evidence source citation.
- Every question-required or decision-changing caveat is stated under the same
  **E** item it constrains. Address all applicable caveat categories in the
  final completeness check below; do not rely on a generic disclaimer.

Completeness is scoped to the question and decision-material evidence. It does
not require dumping unrelated response fields, redundant records, or caveats
that cannot affect the requested assessment.

Apply these question-type checks literally:

- For an empty sequence result, report whether the exact UniProt link and query
  sequence are available, the stored-BLAST status and row count, and the number
  of qualifying/returned hits. Keep stored raw rows distinct from zero filtered
  hits.
- For a requested rank window, inspect and account for every rank in that window;
  a compact representative list that skips redundant ranks is not enough.
- For a global-versus-local confidence question, report the returned global
  pLDDT and whole-model distribution, then the median, all four bands, and
  material cluster/stretch pattern for each distinct region separately. Do not
  merge the confidence summaries of adjacent candidate regions.
- For a classification contrast, retrieve and discuss the strongest available
  evidence on both sides. If a requested class or relationship appears only in
  summary frequencies and no compact representative exposes its metrics, use
  the focused completeness exception below rather than calling it unavailable.

## Minimal Evidence Workflow

Use one public SCOPe release throughout. A normal regional relationship assessment
takes two MCP calls:

1. Call `get_alphafold_hits(model, release, limit=50, compact=true,
   include_transforms=false)` once. Its `alphafold` object contains compact model
   metadata. Do not repeat this evidence through standalone model or hit-stream
   calls. Start with 50 hits per stream; increase the limit up to 100 only when
   a requested rank window or a material unresolved alternative requires more
   evidence. Preserve the returned page limits and `has_more` information.
2. Shortlist at most six SIDs—no more than one per distinct region, evidence
   stream, competing lineage, or warning—and at most six material ranges. Call
   `get_alphafold_decision_evidence` once with both lists. It returns regional
   confidence, compound domain evidence, explicit lineage-by-level maps, and any
   requested calibration. Each `statistics_requests` entry names the representative SID,
   the relationship level you will report (`family`, `superfamily`, or `fold`),
   and a threshold explicitly prescribed by the question; the tool resolves the
   exact lineage SUNID. Never supply a threshold by default. Do not repeat this
   evidence with standalone confidence, domain, or statistics calls.

The compact response is normally sufficient, but it must not hide evidence the
question requires. Repeat `get_alphafold_hits` once with the same model, release,
and bounded limit but `compact=false` only when a requested rank window is not
enumerated, or when summary frequencies show a question-relevant classification
whose representative and metrics are absent from the compact records. Inspect
only the affected stream from that raw response. Do not use this exception
merely to collect more corroborating hits.

For a missing classification contrast, interpret "classification" at the exact
level named by the question. For a class-level contrast, shortlist only the
single highest-ranked raw representative across that class, not one per fold,
superfamily, or family beneath it. Do not put more than one raw-only SID in the
decision bundle unless the question explicitly requires several distinct
alternatives. For a rank-window question, use the
raw records to account for the requested ranks, but do not add raw-only SIDs to
the decision bundle unless one changes the regional relationship and its lineage
is necessary to answer the question. Raw hit metrics remain citable from the
successful combined-hit result.

For pagination, provenance, or another narrow question where confidence cannot
affect the answer, omit the decision bundle. Use `get_scop_domains(...,
includes=["parents","annotations","homology"], compact=true)` only if lineage or
warnings are needed. If the question requests 2/3/5 calibration sensitivity
rather than one prespecified stratum, use
`get_scop_fatcat_chain_statistics` with three requests for the same SUNID at thresholds 2, 3, and 5 and report every available
stratum. Read `references/tool-evidence.md` for focused-tool and REST fallbacks.

For discovery across models, use `list_alphafold_domain_candidates` with
`min_regions=1, max_regions=1` or `min_regions=2, max_regions=3` as a screening tool. For an explicit
interval, `get_alphafold_hits` is a reporting filter, not a boundary or
homology inference. If MCP is unavailable, use only the release-scoped REST
fallbacks in `references/tool-evidence.md`; never query SQL or scrape HTML.

### Evidence hygiene

- Inspect `sequence_evidence` before interpreting an empty sequence list. A true
  filtered result requires the exact UniProt/sequence provenance and stored-BLAST
  status; otherwise report a possible data gap. An empty sequence or structure
  list is not proof of absence.
- Read ranks, ranges, `model_coverage`, raw/grouped counts, and SCCS frequencies
  from the tool. Do not silently substitute a nearby hit or recalculate a metric
  already returned.
- Use overlap-SCCS groups as a reporting heuristic. Same-family, same-protein, or
  overlapping hits are correlated corroboration, not independent observations.
  Report raw versus conceptually nonredundant support only when it affects the
  decision.
- Retrieve lineages and warnings for the representatives actually used. Compare
  nodes by public level and SUNID, not by similar descriptions or SCCS text alone.
- Treat per-SID structured errors as local gaps. Preserve successful peers and
  retry only a failed SID when it can materially change the decision.
- Cite every reported SID, node, metric, warning, annotation, provenance fact,
  and conclusion basis through a numbered evidence item backed by a successful
  MCP result or REST URL from the current task.

## Decision Rules

### Region scope and coverage

Group well-separated alignments into separate regional hypotheses. Preserve each
material hit range and report their union/envelope when it extends beyond the
chosen representative. If neighboring envelopes overlap, name the overlap and
keep their boundaries approximate; never call overlapping envelopes
"non-overlapping."

`aligned residues / full model length` is whole-model scope context. It must not
downgrade an otherwise credible local domain merely because the protein is long.
Judge a local hypothesis using aligned length, coverage of the proposed region,
and coverage of the matched SCOPe component when a true component length is
available. A returned alignment's `domain_range.length` is not automatically the
full component length. Report missing local-coverage information rather than
assuming it is unfavorable.

### Relationship level

Choose the most specific level supported after deduplication and conflict checks:

- **Family-level hypothesis:** family-discriminating sequence evidence and
  structure evidence converge on one retrieved family, with adequate alignment
  extent and no controlling conflict or warning.
- **Family-level candidate:** one family is favored by discriminating alignment
  evidence, but coverage, redundancy, model confidence, or annotations require
  qualification.
- **Superfamily-level hypothesis/candidate:** evidence converges above family, or
  family placement is mixed or insufficiently discriminating.
- **Fold-level structural similarity:** structural evidence agrees only at fold,
  or competing superfamilies remain. Fold similarity is not homology.
- **Inconclusive:** material hits conflict, geometry is unreliable, coverage is
  inadequate, or required lineage evidence is missing.

A representative's family label is not family-discriminating evidence for the
target. Do not promote a single structure-only hit, an automated-match family, or
several redundant same-family hits to family level by itself. Consider sequence
significance, aligned length, identity, candidate-region and matched-component
coverage, low complexity/compositional bias, competing families, and whether
support is genuinely sequence/structure convergent. When these fields are absent
or family labels conflict, stop at superfamily or fold level and say why.

For family-level convergence, an automated or placeholder structural family
label supports only its non-automated higher lineage; it does not independently
confirm the family named by a sequence hit. Require a non-automated,
family-discriminating representative from both sequence and structure, unless a
near-exact, near-full-length sequence alignment independently discriminates the
family and structure at least supports the same superfamily. Conversely, the
non-definitive claim boundary is about certainty, not automatic loss of
specificity: do not downgrade a genuinely supported family hypothesis to
superfamily merely to sound cautious. State the supported level as a hypothesis
or strong chance and attach its caveats.

Annotations and homology warnings can weaken a hypothesis; their absence is not
independent positive evidence. Do not infer exact function, ligand, reaction,
oligomeric state, or experiment from a relationship hypothesis.

### AlphaFold confidence

pLDDT modifies trust in predicted local geometry; it is not evolutionary
evidence and never changes sequence evidence. Use the local median, all four
bands (`>=90`, `70-<90`, `50-<70`, `<50`), and sustained low-confidence stretches.
Treat `>=90` clusters as descriptive only: a hard threshold can fragment a
credible region and does not define fold coherence or a domain boundary.

High local confidence can make a well-covered FATCAT comparison more interpretable
but cannot rescue weak, partial, or conflicting matches. Low local confidence
weakens structure-dependent evidence but does not invalidate strong sequence
evidence. If PAE or pTM is unavailable, do not infer relative placement of domains
from pLDDT alone. Read `references/alphafold-caveats.md` when confidence materially
affects the conclusion.

### PDB-chain representative evidence

When following a PDB-chain example through `get_scop_chain` or
`get_scop_chain_hits`, inspect `cluster_representative` under `chain` or `subject`.
Those tools return the requested chain's evidence; retrieve a different
representative explicitly and label its matches accordingly. The member-to-
representative `similarity` describes local SEQRES-minus-tags sequence identity
and coverage, not FATCAT structural similarity or the historical node-specific
chain-statistics fractions below. It is not evidence about the AlphaFold model.
Its one-based inclusive sequence endpoints do not provide a residue mapping;
do not transfer the representative's ranges or official classifications to the
original chain or model. Null similarity means unavailable, not zero similarity.

### FATCAT and chain-statistics calibration

Interpret FATCAT P-value, score, RMSD, aligned length, coverage, and competing
lineages together. A FATCAT P-value measures structural-match significance, not
the probability of homology. Read `references/fatcat-caveats.md` for a
structure-led or borderline decision.

Before interpreting a historical chain-statistics fraction, inspect the current
hit's `chain_statistics_applicability` returned by `get_alphafold_hits`:

- `outside_statistics_filters`: the current P-value or a known length match fails
  the historical inclusion filters. Report the scalar as outside its calibration
  domain; do not call a high fraction "no warning."
- `uncertain_missing_filter_inputs`: required current-hit length information is
  absent. Report applicability as uncertain; do not use the fraction to reassure
  the hypothesis.
- `within_statistics_filters`: all exposed filters pass, but the fraction remains
  biased historical context, not target evidence.

For an applicable record, report status, fraction and denominator when present,
the prescribed threshold, and the decision bundle's exact `requested_level` and
`resolved_node`. A high fraction adds no positive confidence; a low or sparse
result can add caution or prevent a structure-only upgrade. Never rank nodes by
their fractions, use a fraction to preserve a stronger label, or treat the
denominator as independent observations. `no_observations`,
`threshold_unavailable`, `not_applicable`, and structured per-item errors provide
no favorable value. Read `references/fatcat-chain-statistics.md` whenever
statistics are material.

Request and report calibration for the single strongest relationship level
retained in each regional conclusion. A narrower family mentioned only as
reference context or a qualified candidate does not make family the calibration
level. The calibration `requested_level` and `resolved_node` must match the
retained conclusion, not merely one row present in the level table.

Sequence and predicted-structure evidence are not necessarily independent because
AlphaFold uses homologous sequences and can use structural templates. Qualify
partner-dependent regions when a single-chain model omits contacts that may shape
them.

## Response Structure

After the required opening disclosure, use the following structure. Keep the
response proportional to the question and distinguish each material region.

### 1. Conclusion

Give a 1–2 sentence TLDR that directly answers the question and states how much
trust the evidence warrants, with the main reason for that qualification. Identify
the model, UniProt accession, release, and relevant approximate ranges. Do not
invent a numerical probability of correctness from pLDDT or a stored match fraction.
This is the strongest supported hypothesis, never a final biological conclusion.
When warranted, use the qualitative "strong chance" formulation defined in the
Claim Boundary rather than saying that the target belongs to a classification.

### 2. What can be said at each level

For each material region, give a compact table with columns **Level**, **Assessment
and ambiguity**, and **Evidence**. Include these rows in this order:

| Level | What to report |
| --- | --- |
| Function | Any directly supported functional context, separating a reference's annotation from a target-function hypothesis. State when target function is not established; a family name alone does not establish an exact function. |
| Family | The supported family hypothesis or candidate, competing family interpretations, or why family placement is not established. |
| Superfamily | The supported superfamily hypothesis or candidate, unresolved alternatives, or why this level is not established. |
| Fold | The supported structural interpretation and its ambiguity. Distinguish architectural similarity from evidence of homology. |

Use the relationship rules above; these rows do not independently assign a level.
Identify retrieved classification nodes by name and SCCS/SUNID, and reference the
supporting evidence items below. Explain why the most specific supported level
fits and the next stronger claim does not. Function is a separate inference,
not a SCOPe rank. For a narrow question that does not assess a level, say **not
assessed for this question**; distinguish that from **not established by the
retrieved evidence**. Neither means evidence against the relationship.

### 3. Evidence considered

List each distinct piece of evidence used in reaching the conclusion as **E1**,
**E2**, and so on. Include supporting evidence, contrary evidence, and alternatives
examined but discounted. Group redundant hits when appropriate, retaining their
counts and shared provenance rather than treating them as independent support.

For each item, give the observation and its region, the material metrics or counts,
and what it supports, weakens, or leaves unresolved. Include explicit coverage
arithmetic for material hits. Explain the observation's effect on the conclusion;
listing a metric alone is insufficient. Keep counts scoped to their actual region
or retrieved window.

Write each item as `- **E1** — observation and its effect. Source: [label](URL)`
(then E2, etc.). Each item needs its own clickable source reference. To avoid
repeating long URLs, use Markdown reference links such as `[source][model-data]`
and define `[model-data]: URL` once. A list of MCP call numbers is insufficient.

For a sequence or FATCAT hit,
link its SID to the hit-specific landing page:
`https://scop.berkeley.edu/alphafold/af_x_domains/?af={MODEL}&mode={MODE}&hit={SID}`,
using the retrieved model/SID and `MODE=seq` or `MODE=struct`. If that viewer is
unavailable, cite the successful hit result's public `api_urls` and its retrieved
release-scoped SID node URL, and state that the viewer could not be checked.
Cite retrieved SCOPe
node URLs for lineage using the returned SUNID and pinned release. A public node
URL must have exactly the release-qualified form
`https://scop.berkeley.edu/sunid={SUNID}&ver={RELEASE}`; never emit an
unversioned `sunid` link. A returned release-scoped REST node URL is also valid.
For annotations,
confidence, provenance, or calibration, copy the relevant source URL from the
successful result's `api_urls`; local confidence summaries cite the model's
residue-data source. Cite an external source only if it was consulted and supports
the observation. Numbered MCP calls may supplement these links. Do not fetch
extra pages just to cite evidence already retrieved, invent a source, or
substitute a landing page for unreturned metrics. Before returning, check every
E item for a working Markdown link destination or reference definition; URLs
inside code and a separate tool-use list do not supply that item's citation.

### 4. Caveats by evidence item

List the caveats for each evidence item using the same **E1**, **E2**, etc.
State the limitation and which inference it constrains: for example, partial
coverage, correlated records, uncertain boundaries, missing partners, automated
placement, confidence, or statistics applicability. Cite the source of retrieved
warnings. If no additional caveat was identified in the retrieved evidence, say
so without treating the absence of a warning as positive evidence. Keep caveats
specific to the item rather than repeating a generic list for every result.

Check every applicable caveat category before returning: incomplete or filtered
searches; pagination or rank-window limits; partial coverage and uncertain or
overlapping boundaries; correlated/redundant sequence and structure records;
competing lineages; automated, placeholder, fragment, repeat, heterogeneity, or
homology warnings; low or mixed local confidence and missing PAE/pTM; possible
AlphaFold sequence/template dependence; partner-dependent or assembly context;
FATCAT significance and unreported alignment inputs; statistics applicability,
sample size, redundancy, and sampling bias; and non-transfer of reference
function, ligand, or state to the target. Report each category that is required
by the question or can change the assessment, and explicitly state unavailable
decision-critical information and its effect.

Before finalizing, apply this compact completeness floor to each controlling
regional conclusion:

- preserve the material hit union/envelope and overlaps, then distinguish it from
  representative cores;
- include the metrics that make each selected sequence or FATCAT hit material
  (rank, range/coverage, significance, identity, and FATCAT score/RMSD when
  returned);
- report lineage through the claimed node plus any higher-level agreement and
  material competing branch, using `lineage_by_level`;
- include decision-changing confidence patterns, redundancy, automated-match or
  partner/repeat warnings, and exact level-resolved calibration.
- account for every explicitly named representative, contrast, negative result,
  provenance fact, and caveat required by the question.

Omit an item only when it cannot affect the question. Never omit a
question-required item, returned controlling observation, material alternative,
or applicable required caveat merely to shorten the answer.
