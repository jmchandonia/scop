# Claude Code session transcript: Nicolin-1

- Session file: `nicolin1_claude_session.jsonl`
- Session id: `a19464ba-fbee-4ce1-ae74-3c0305b552f3`
- Claude Code version: 2.1.307
- Working directory: an otherwise empty workspace containing the two public SCOPe skills
- Model and reasoning effort recorded for the turns: `claude-opus-5-5` / `high`
- User prompts: 2
- Tool calls: `Skill` ×1, `mcp__scope__get_alphafold_decision_evidence` ×1, `mcp__scope__get_alphafold_hits` ×2, `mcp__scope__get_scop_chain` ×1, `mcp__scope__get_scop_service_info` ×1, `mcp__scope__list_scop_children` ×1
- Thinking blocks (not stored in readable form in the log and therefore omitted): 6
- Tool results longer than 1,200 characters are truncated; prompts, agent messages and tool arguments are shown verbatim.

---

#### User (2026-10-22T00:55:16.244Z)

```
I'm a cell biologist working on human Nicolin-1 (gene NICN1, UniProt Q9BSH3). It's a nuclear protein that was recently reported to be a subunit of the tubulin polyglutamylase complex, but there is no experimental structure and I don't know what kind of domain it contains. Using SCOPe, can you tell me whether its AlphaFold model resembles any classified protein domains, how confident I should be in that relationship, and what it does and does not tell me about Nicolin-1's function?
```


**Tool call `Skill` (2026-10-22T00:55:22.317Z):**

```json
{
 "args": "AF-Q9BSH3-F1 human Nicolin-1 (NICN1): does the AlphaFold model resemble any classified SCOPe domains, confidence, and functional implications",
 "skill": "alphafold-scope-homology"
}
```


*Tool result:*

```
Launching skill: alphafold-scope-homology
```


**Tool call `mcp__scope__get_scop_service_info` (2026-10-22T00:55:25.346Z):**

```json
{}
```


*Tool result:*

```
{"release":"2.08","api_urls":["https://scop.berkeley.edu/api/v1/","https://scop.berkeley.edu/api/v1/releases/current"],"fetched_at":"2026-10-22T00:55:25.606795+00:00","service_index":{"service":"SCOPe REST API","version":"v1","status":"available","current_release":{"version":"2.08","old_version":null,"db_name":"SCOPe","is_public":true,"is_comprehensive":false,"freeze_date":"2021-07-28","release_date":"2021-09-22","stable_label":"2.08-stable","latest_periodic_update":{"version":"2.08-2026-08-16","date":"2026-08-16"}},"openapi_url":"/api/v1/openapi.yaml","api_catalog_url":"/.well-known/api-catalog","endpoints":["/api/v1/releases","/api/v1/releases/current","/api/v1/levels","/api/v1/search","/api/v1/downloads","/api/v1/releases/{release}/search","/api/v1/releases/{release}/resolve/{identifier}","/api/v1/releases/{release}/sunids/{sunid}","/api/v1/releases/{release}/sunids/{sunid}/fatcat-chain-statistics","/api/v1/releases/{release}/sids/{sid}/alphafold-structure-matches","/api/v1/releases/{release}/domains","/api/v1/releases/{release}/pdb/{code}","/api/v1/releases/{release}/pdb/{code}/coordinates","/api/v1/releases/{release}/chains/{chain}/hits","/api/v1/releases/{release}/chains/{cha
[... 833 characters omitted ...]
```


**Tool call `mcp__scope__get_alphafold_hits` (2026-10-22T00:55:31.715Z):**

```json
{
 "compact": true,
 "limit": 50,
 "model": "AF-Q9BSH3-F1",
 "release": "2.08"
}
```


*Tool result:*

```
{"release":"2.08","subject":{"kind":"alphafold","name":"AF-Q9BSH3-F1","uniprot_accession":"Q9BSH3","description":"Nicolin-1","global_plddt":79.65,"model_residue_count":213,"api_url":"/api/v1/releases/2.08/alphafolds/AF-Q9BSH3-F1"},"limit":50,"sequence_evidence":{"source":"af_model.uniprot_id","status":"stored_blast_results_available","uniprot_id_available":true,"sequence_id_available":true,"stored_blast_result_count":7,"interpretation":"Stored BLAST rows exist for this exact UniProt-linked sequence and release; public sequence hits may still be empty after endpoint filters."},"sequence_hits":[],"sequence_page":{"limit":50,"has_more":false,"filters":{"maximum_log10_evalue":-1,"domain_styles":["original","genetic"],"rejected_sequences_excluded":true}},"structure_hits":[{"kind":"structure","domain":{"sid":"d3k77a_","sunid":179138,"sccs":"b.18.1.8","description":"3k77 A:","comment":"automated match to d1xnaa_"},"p_value":0.000129,"identity":8.44,"similarity":24.03,"score":182.85,"rmsd":2.4,"alphafold_range":{"start":4,"end":139,"length":136},"domain_range":{"start":2,"end":153,"length":152},"rank":1,"model_coverage":{"residues":136,"model_length":213,"fraction":0.638498,"percent":63.85
[... 14,019 characters omitted ...]
```


**Tool call `mcp__scope__get_alphafold_decision_evidence` (2026-10-22T00:55:42.449Z):**

```json
{
 "model": "AF-Q9BSH3-F1",
 "regions": [
  {
   "end": 142,
   "start": 1
  },
  {
   "end": 167,
   "start": 143
  },
  {
   "end": 191,
   "start": 168
  },
  {
   "end": 213,
   "start": 192
  }
 ],
 "release": "2.08",
 "sids": [
  "d3k77a_",
  "d1xpwa_",
  "d1sg3a2",
  "d1k3ia2",
  "d4uyba3",
  "d4i7zc3"
 ]
}
```


*Tool result:*

```
{"release":"2.08","api_urls":["https://scop.berkeley.edu/api/v1/releases/2.08/alphafolds/AF-Q9BSH3-F1","https://scop.berkeley.edu/api/v1/releases/2.08/sids/d3k77a_","https://scop.berkeley.edu/api/v1/releases/2.08/sids/d3k77a_/parents","https://scop.berkeley.edu/api/v1/releases/2.08/sids/d3k77a_/annotations","https://scop.berkeley.edu/api/v1/releases/2.08/sids/d3k77a_/homology","https://scop.berkeley.edu/api/v1/releases/2.08/sids/d1xpwa_","https://scop.berkeley.edu/api/v1/releases/2.08/sids/d1xpwa_/parents","https://scop.berkeley.edu/api/v1/releases/2.08/sids/d1xpwa_/annotations","https://scop.berkeley.edu/api/v1/releases/2.08/sids/d1xpwa_/homology","https://scop.berkeley.edu/api/v1/releases/2.08/sids/d1sg3a2","https://scop.berkeley.edu/api/v1/releases/2.08/sids/d1sg3a2/parents","https://scop.berkeley.edu/api/v1/releases/2.08/sids/d1sg3a2/annotations","https://scop.berkeley.edu/api/v1/releases/2.08/sids/d1sg3a2/homology","https://scop.berkeley.edu/api/v1/releases/2.08/sids/d1k3ia2","https://scop.berkeley.edu/api/v1/releases/2.08/sids/d1k3ia2/parents","https://scop.berkeley.edu/api/v1/releases/2.08/sids/d1k3ia2/annotations","https://scop.berkeley.edu/api/v1/releases/2.08/sids/d1k3ia2
[... 16,407 characters omitted ...]
```


**Tool call `mcp__scope__list_scop_children` (2026-10-22T00:56:03.299Z):**

```json
{
 "identifier": "49784",
 "release": "2.08"
}
```


*Tool result:*

```
{"release":"2.08","api_urls":["https://scop.berkeley.edu/api/v1/releases/2.08/sunids/49784/children?limit=20"],"fetched_at":"2026-10-22T00:56:03.513151+00:00","parent":{"sunid":49784,"sid":null,"sccs":"b.18","description":"Galactose-binding domain-like","level":{"code":"cf","name":"Fold","plural":"Folds"},"release":"2.08","api_url":"/api/v1/releases/2.08/sunids/49784","canonical_url":"/?sunid=49784&ver=2.08","parent":{"sunid":48724,"sid":null,"sccs":"b","api_url":"/api/v1/releases/2.08/sunids/48724"}},"sort":"database","limit":20,"next_cursor":null,"has_more":false,"children":[{"sunid":49785,"sid":null,"sccs":"b.18.1","description":"Galactose-binding domain-like","level":{"code":"sf","name":"Superfamily","plural":"Superfamilies"},"release":"2.08","api_url":"/api/v1/releases/2.08/sunids/49785","canonical_url":"/?sunid=49785&ver=2.08","parent":{"sunid":49784,"sid":null,"sccs":"b.18","api_url":"/api/v1/releases/2.08/sunids/49784"}}]}
```


#### Agent (2026-10-22T00:56:49.540Z)

I'm finished with the SCOPe queries; here are the results.

> This is a non-official evidence assessment, not a definitive determination of homology or an official SCOPe classification.

## 1. Conclusion

Residues ~1–140 of the AlphaFold model of Nicolin-1 (**AF-Q9BSH3-F1**, UniProt Q9BSH3, SCOPe 2.08) look structurally like the **galactose-binding domain-like** jelly-roll β-sandwich (fold b.18, superfamily b.18.1). The evidence supports a moderate structural resemblance, but it does not support a confident evolutionary relationship. All of it comes from structure comparison: no sequence match passes the filter, and the best structural matches are only borderline significant at ~8% identity. It tells you almost nothing specific about function. The C-terminal ~70 residues (~143–213) have no informative SCOPe match.

## 2. What can be said at each level

**Region ~1–140, galactose-binding-like β-sandwich candidate**

| Level | Assessment and ambiguity | Evidence |
|---|---|---|
| Function | **Not established.** The matching domains do very different jobs: binding sugars (galactose-binding domain), APC10/DOC (a subunit of the anaphase-promoting complex, APC/C), the XRCC1 N-terminal domain, and allantoicase repeats (an enzyme). A shared fold doesn't transfer any of these to Nicolin-1. | E1, E2, E3 |
| Family | **Not established.** The top two hits come from different families (XRCC1 N-terminal b.18.1.8; APC10-like b.18.1.9), and b.18.1 hits are spread over ≥11 family labels. Nothing singles out one family. | E2, E3 |
| Superfamily | **Weak candidate** for Galactose-binding domain-like (b.18.1, sunid 49785). It is the only superfamily in fold b.18, so a b.18 match points here by default. But with no sequence support and P-values around 10⁻⁴, the case for homology is not strong. | E1, E2, E4 |
| Fold | **Best-supported claim:** structural similarity to the Galactose-binding domain-like fold (b.18, sunid 49784). The top hits all fall in this fold, and the region is predicted confidently. A shared shape is not proof of shared ancestry. | E2, E3, E5, E6 |

**Region ~143–213:** nothing informative at any level (E7, E8). Function, family, superfamily and fold are all **not established by the retrieved evidence**. That is not evidence that no domain is there.

## 3. Evidence considered

- **E1** — **Sequence search found nothing.** The UniProt link and sequence are present and 7 stored BLAST rows exist, but 0 pass the public filter (E ≤ 0.1). So no classified domain has detectable sequence similarity. Any relationship rests on structure alone, which caps the confidence. Source: [hits][hits]
- **E2** — **Top structural hits are in fold b.18 / superfamily b.18.1, from different families.**
  - Rank 1, [d3k77a_][h1] (b.18.1.8, XRCC1 N-terminal domain): P = 1.29×10⁻⁴, identity 8.4%, RMSD 2.40 Å, score 182.9. Covers model 4–139 (136 of the 142-residue region, ~96%) and reference 2–153 (~89% of its 152-residue span).
  - Rank 2, [d1xpwa_][h2] (b.18.1.9, APC10-like): P = 2.91×10⁻⁴, identity 9.0%, RMSD 2.36 Å. Covers model 1–140 and reference 1–138, essentially end to end.
  - Both cover most of the region and most of the reference domain. That supports a fold-level match, but the two hits name different families. Lineages: [fold 49784][f], [superfamily 49785][sf]. Source: [hits][hits], [decision bundle][dec]
- **E3** — **Many weaker hits agree, but they are not independent.** 16 of the 45 structural hits fall in b.18.1, across ≥11 family labels. Examples: rank 5 [d1sg3a2][h5] (allantoicase repeat, b.18.1.22), P = 4.1×10⁻³, RMSD 1.78 Å, model 3–138; rank 14 [d1k3ia2][h14] (galactose-binding domain, b.18.1.1), P = 1.6×10⁻², model 2–140. All cover the same ~1–140 stretch, so this agreement adds little beyond E2. The family spread is what rules out a family-level claim. Source: [hits][hits], [decision bundle][dec]
- **E4** — **b.18 contains a single superfamily.** In SCOPe 2.08, fold b.18 has only one child, b.18.1. A b.18 match therefore points to b.18.1 by default, not because the evidence ruled out other superfamilies. Source: [children][ch]
- **E5** — **A competing β-sandwich is much weaker.** Rank 12 [d4uyba3][h12] (b.132.1, SPF C-terminal domain, automated match): P = 1.55×10⁻², RMSD 3.42 Å, identity 3.4%, model 27–142. It is a weak alternative, and b.18 remains clearly favoured. Source: [hits][hits], [decision bundle][dec]
- **E6** — **The model is confident over ~1–142.** Median pLDDT is 92.8 (64% ≥90, 26% 70–<90, 10% 50–<70, 0% <50). The confident stretch is split into six ≥90 clusters; there is a lower dip at 18–27 and the confidence drops at 141–152. Whole model: global pLDDT 79.65, median 86.2. Because the fold-region geometry is well predicted, the FATCAT comparison is easier to trust. Source: [model][model]
- **E7** — **The linker ~143–167 is low confidence.** Median pLDDT 51.2 (0% ≥90, 0% 70–<90, 56% 50–<70, 44% <50), including a <50 stretch at 155–163. It is probably flexible or disordered in the model, and no structural hit falls here. Source: [model][model]
- **E8** — **Matches at ~168–191 are generic helix matches.** Hits at ranks 6, 8–11 and 13 each align only 23–24 residues to unrelated classes: a transmembrane helix (f.23.23, [d4i7zc3][h6]), a.30, f.23.18, g.88 and g.83. P-values are 6×10⁻³ to 1.6×10⁻². The f.23 fold carries a curator warning that it is "not a true fold". This region's median pLDDT is 77.7 (0% ≥90, 79% 70–<90, 21% 50–<70, 0% <50). This looks like a single helix matching other helices, not domain evidence. Region 192–213 (median 56.2; 0% ≥90, 0% 70–<90, 95% 50–<70, 5% <50) has no hits. Source: [hits][hits], [decision bundle][dec], [model][model]

## 4. Caveats by evidence item

- **E1:** An empty filtered result doesn't rule out distant homology. It only means nothing passed the filter in SCOPe 2.08. It is still the main reason this stays a structural, not evolutionary, inference.
- **E2:** FATCAT P-values measure how significant the structural match is, not the probability of homology. ~10⁻⁴ is borderline, and every hit falls outside the filters used for the historical chain statistics (P ≤ 10⁻⁴; length inputs missing). I did not request calibration statistics, and they would not apply here. d3k77a_ is an automated match, so its family label adds no independent family support. d1xpwa_ is a structural genomics target. The reported reference ranges may not equal the full domain lengths, so the coverage figures are approximate.
- **E3:** The hits are redundant and correlated. Compact output omitted ranks 3, 4 and 7, which were accounted for only through the per-classification counts. A larger window wouldn't change the region or level, because the summary counts show the same b.18.1 pattern.
- **E4:** That b.18.1 is the only superfamily is a fact about how SCOPe is organised, not evidence about Nicolin-1. SCOPe 2.08 (frozen 2021) also doesn't cover newer structures that might fit better.
- **E5:** This alternative is an automated-match family. It was ruled out only by comparing strength.
- **E6:** pLDDT measures prediction confidence, not homology. PAE/pTM were not available, so I can't say how the β-sandwich sits relative to the C-terminal helix. AlphaFold uses related sequences and possibly templates, so the model and the structural matches are not fully independent. A single-chain model also leaves out partner contacts that could matter if Nicolin-1 sits inside a complex.
- **E7/E8:** Low confidence weakens any structural reading here. Short helix matches and the "not a true fold" warning mean this region shouldn't be read as a domain. ~140 is an approximate end of the aligned region, not a curated domain boundary.

## What this means for Nicolin-1

- **What it tells you:** Nicolin-1 most likely has an N-terminal (~1–140) jelly-roll β-sandwich of the galactose-binding-domain-like type, followed by a flexible linker and a C-terminal segment with some predicted helix.
- **What it doesn't tell you:**
  - That Nicolin-1 binds sugars.
  - That it acts like APC10 or XRCC1.
  - Anything about enzymatic activity, ligands or its role in the polyglutamylase complex.

  This superfamily is functionally very diverse. In many members the sandwich is a protein- or ligand-binding scaffold, but these data can't show which surface Nicolin-1 would use or what it would bind.
- **Testable ideas:** truncations separating ~1–140 from ~143–213, to see which part is needed to join the complex, and mutations of surface loops on the β-sandwich. The resemblance to APC10/DOC, itself a subunit of a large complex, is a reasonable starting idea but nothing more.

[hits]: https://scop.berkeley.edu/api/v1/releases/2.08/alphafolds/AF-Q9BSH3-F1/hits?limit=50
[model]: https://scop.berkeley.edu/api/v1/releases/2.08/alphafolds/AF-Q9BSH3-F1
[dec]: https://scop.berkeley.edu/api/v1/releases/2.08/sids/d1xpwa_/parents
[ch]: https://scop.berkeley.edu/api/v1/releases/2.08/sunids/49784/children?limit=20
[f]: https://scop.berkeley.edu/sunid=49784&ver=2.08
[sf]: https://scop.berkeley.edu/sunid=49785&ver=2.08
[h1]: https://scop.berkeley.edu/alphafold/af_x_domains/?af=AF-Q9BSH3-F1&mode=struct&hit=d3k77a_
[h2]: https://scop.berkeley.edu/alphafold/af_x_domains/?af=AF-Q9BSH3-F1&mode=struct&hit=d1xpwa_
[h5]: https://scop.berkeley.edu/alphafold/af_x_domains/?af=AF-Q9BSH3-F1&mode=struct&hit=d1sg3a2
[h6]: https://scop.berkeley.edu/alphafold/af_x_domains/?af=AF-Q9BSH3-F1&mode=struct&hit=d4i7zc3
[h12]: https://scop.berkeley.edu/alphafold/af_x_domains/?af=AF-Q9BSH3-F1&mode=struct&hit=d4uyba3
[h14]: https://scop.berkeley.edu/alphafold/af_x_domains/?af=AF-Q9BSH3-F1&mode=struct&hit=d1k3ia2

The per-hit viewer links are built in SCOPe's standard format, but I didn't open them. The hit metrics come from the API results linked as [hits][hits] and the [decision bundle][dec]. The bundle link points to one of the six lineage records I retrieved; the others use the same URL pattern with each hit's ID.


---

#### User (2026-10-22T00:58:00.373Z)

```
Thanks. Before I rely on this, I'd like to look at the structural alignment myself. Where can I see the XRCC1 domain superposed on the Nicolin-1 model, and what should I look for to judge whether the match is convincing?
```


**Tool call `mcp__scope__get_alphafold_hits` (2026-10-22T00:58:08.006Z):**

```json
{
 "compact": false,
 "evidence": "structure",
 "include_transforms": true,
 "limit": 2,
 "model": "AF-Q9BSH3-F1",
 "release": "2.08"
}
```


*Tool result:*

```
{"release":"2.08","subject":{"kind":"alphafold","name":"AF-Q9BSH3-F1","uniprot_accession":"Q9BSH3","description":"Nicolin-1","global_plddt":79.65,"model_residue_count":213,"api_url":"/api/v1/releases/2.08/alphafolds/AF-Q9BSH3-F1"},"limit":2,"structure_hits":[{"kind":"structure","domain":{"sid":"d3k77a_","sunid":179138,"sccs":"b.18.1.8","description":"3k77 A:","header":"b.18.1.8 (A:) N-terminal domain of xrcc1 {Human (Homo sapiens) [TaxId: 9606]}","comment":"automated match to d1xnaa_","api_url":"/api/v1/releases/2.08/sids/d3k77a_"},"p_value":0.000129,"identity":8.44,"similarity":24.03,"score":182.85,"rmsd":2.4,"alphafold_range":{"start":4,"end":139,"length":136},"domain_range":{"start":2,"end":153,"length":152},"transform":[[-0.017,-0.89,0.455,5.479],[0.964,0.106,0.243,-35.743],[-0.264,0.443,0.857,-13.573],[0,0,0,1]],"rank":1,"model_coverage":{"residues":136,"model_length":213,"fraction":0.638498,"percent":63.85},"chain_statistics_applicability":{"status":"outside_statistics_filters","p_value_filter":{"observed":0.000129,"maximum":0.0001,"passes":false},"length_match_filter":{"observed":null,"minimum":0.8,"passes":null,"missing_inputs":["alignment_length","component_length"]}},"ove
[... 3,985 characters omitted ...]
```


**Tool call `mcp__scope__get_scop_chain` (2026-10-22T00:58:08.404Z):**

```json
{
 "chain": "3k77A",
 "release": "2.08"
}
```


*Tool result:*

```
{"release":"2.08","api_urls":["https://scop.berkeley.edu/api/v1/releases/2.08/chains/3k77A"],"fetched_at":"2026-10-22T00:58:09.086620+00:00","chain":{"identifier":"3k77A","pdb_code":"3k77","chain":"A","is_polypeptide":true,"description":null,"sequence":"MPEIRLRHVVSCSSQDSTHCAENLLKADTYRKWRAAKAGEKTISVVLQLEKEEQIHSVDIGNDGSAFVEVLVGSSAGGAGEQDYEVLLVTSSFMSPSESRSGSNPNRVRMFGPDKLVRAAAEKRWDRVKIVCSQPYSKDSPFGLSFVRFHSPPDKHHHHHH","sequence_length":161,"sequence_source":"seqres","cluster_representative":{"identifier":"3k77A","is_requested_chain":true,"similarity":{"method":"Smith-Waterman local alignment; BLOSUM62; gap open -10; extension -0.5; Biopython 1.85","sequence_source":"seqres_minus_tags","coordinate_system":"one_based_inclusive_sequence_position","identical_residues":161,"alignment_columns":161,"percent_identity":100,"member_start":1,"member_end":161,"representative_start":1,"representative_end":161,"member_length":161,"representative_length":161,"member_coverage_percent":100,"representative_coverage_percent":100,"interpretation":"Sequence similarity to the cluster representative, not a structural comparison or an official classification. Alignment columns include internal gaps. Ranges sum
[... 2,041 characters omitted ...]
```


#### Agent (2026-10-22T00:58:53.933Z)

# Viewing the XRCC1 superposition on the Nicolin-1 model

## Where to see it

**1. SCOPe's hit viewer (quickest):**
https://scop.berkeley.edu/alphafold/af_x_domains/?af=AF-Q9BSH3-F1&mode=struct&hit=d3k77a_

This link follows SCOPe's standard format for the AlphaFold hit viewer. I can't open web pages from here, so I haven't checked that it loads or shows a 3D overlay.

**2. Your own superposition using SCOPe's stored alignment:** I retrieved the transformation matrix from the alignment stored for this hit, so you can rebuild it in ChimeraX:

```
open alphafold:Q9BSH3                      # Nicolin-1 model → #1
open 3k77                                  # XRCC1 → #2
delete #2 & ~/A                            # keep chain A (the SCOPe domain d3k77a_)
view matrix models #2,-0.017,-0.89,0.455,5.479,0.964,0.106,0.243,-35.743,-0.264,0.443,0.857,-13.573
color bfactor #1 palette alphafold         # colour Nicolin-1 by pLDDT
```

Two things to check about the matrix:
- **Direction:** the API doesn't say whether it moves XRCC1 onto the model or the model onto XRCC1. If the structures don't overlap, run `view matrix models #2,1,0,0,0,0,1,0,0,0,0,1` to reset #2, then apply the same matrix to `#1` instead.
- **FATCAT is "flexible":** it can split an alignment into blocks joined at hinges, and the API doesn't report how many hinges this alignment used. A single matrix may line up one part well and the rest poorly. If so, that may reflect hinges rather than a bad match.

The SCOPe copy of the XRCC1 coordinates is also available at https://scop.berkeley.edu/api/v1/releases/2.08/chains/3k77A/coordinates. Chain 3k77A is human XRCC1 (P18887): 161 residues including a C-terminal His tag. In SCOPe it is an automated match to d1xnaa_, the earlier XRCC1 N-terminal domain structure. The aligned ranges SCOPe reports (model 4–139, XRCC1 2–153) are probably sequence positions, not PDB residue numbers, so expect small offsets.

**3. An independent re-alignment (recommended):** Don't use ChimeraX `matchmaker`. It aligns by sequence first, which is meaningless at about 8% identity. Use a method that aligns by structure alone:
- **TM-align or US-align** on the model and 3k77 chain A.
- **PyMOL `cealign`**.
- **The FATCAT server in rigid mode.** If the match still holds without hinges, that's reassuring.

## What to look for

1. **Strand order and direction (most important).** Two stacked β-sheets are a very common shape and arise independently in unrelated proteins. A convincing match pairs strands one-to-one in the same order, with the same up/down direction and the same assignment to each sheet. Number the strands in both structures and check the pairing. A match covering only one sheet, or one that pairs strands out of order, is much weaker.
2. **Where the misfit is.** Small mismatches in loops are normal between distant relatives. Gaps or misplaced strands inside the core sandwich are not. In the Nicolin-1 model, the lower-confidence parts are residues ~18–27 and ~141–152, so poor fit there matters less than poor fit in the confident core.
3. **pLDDT on the aligned strands.** The model is confident over most of this region (median pLDDT 92.8 for 1–142). Check that the strands that actually pair with XRCC1 are the blue/high-confidence ones.
4. **Scores from your re-alignment:**
   - A TM-score (normalised by the shorter chain) above ~0.5 generally indicates the same fold.
   - ~0.3 is about what unrelated proteins give.
   - Note how many residues are aligned and the RMSD. SCOPe's stored result is 136 residues at 2.4 Å, P = 1.3×10⁻⁴, which is borderline.
5. **Controls.** Superpose the model on the APC10-like domain d1xpwa_ (PDB 1xpw, chain A) the same way. Then do the same with a different kind of β-sandwich, such as SCOPe's weak b.132 hit d4uyba3 (PDB 4uyb chain A, residues 275–400). If the galactose-binding-like domains fit clearly better than the control, the b.18 resemblance gains weight. If they all fit about equally, it's just a generic β-sandwich match.
6. **A wider search.** Running the model through Foldseek or DALI against the whole PDB covers structures newer than SCOPe 2.08 (frozen 2021). If the top hits are consistently galactose-binding-like domains with strong scores (e.g., DALI Z well above ~8), the case is stronger. If they're a mix of unrelated β-sandwiches, it's weaker.

Even if everything checks out, a clean superposition shows that the shapes match well. It doesn't show common ancestry: there's still no detectable sequence similarity to any SCOPe domain. And it doesn't tell you anything about Nicolin-1's function.

