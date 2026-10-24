# pretrained_mcp Benchmark Result

- Run: `2026-10-13_claude-fable-5-1_pretrained_mcp_r1`
- Suite: `4.3.0`
- Model: `claude-fable-5-1`
- Evaluator: `anthropic/claude-fable-5-1`
- Questions: 24

## Primary Biological Scores

| Questions | Biological reasoning | Factual support | Secondary gated score |
|---:|---:|---:|---:|
| 23 | 3.87 | 3.61 | 3.80 |

Biological reasoning and factual support are the primary axes. The gated score is secondary and cannot hide weak factual support.

## API Conformance

Factual-support mean: **4.00 / 4.00**.

## Per-Question Scores

| Question | Biological | Factual | Gated | Error tags |
|---|---:|---:|---:|---|
| `Q001_histone_related_proteins` | 4 | 4 | 4.0 | - |
| `Q002_mazg_hise_remote_homology` | 4 | 4 | 4.0 | - |
| `Q003_actin_like_atpase_divergence` | 4 | 4 | 4.0 | - |
| `Q004_ferritin_like_functional_divergence` | 4 | 3 | 3.75 | `wrong_classification` |
| `Q005_cupredoxin_function_transfer` | 4 | 4 | 4.0 | - |
| `Q006_ubiquitin_like_function_divergence` | 4 | 4 | 4.0 | - |
| `Q007_chey_like_function_divergence` | 4 | 4 | 4.0 | - |
| `Q008_v1_atp_synthase_bulge_classification` | 4 | 4 | 4.0 | - |
| `Q009_heme_bundle_fold_not_homology` | 4 | 3 | 3.75 | `api_evidence_error` |
| `Q010_rossmann_family_function_divergence` | 4 | 4 | 4.0 | - |
| `Q012_not_true_family_specific_pair` | 2 | 2 | 2.0 | `missing_annotation_caveat` |
| `Q014_paper_example_reclassification_pa1492` | 4 | 4 | 4.0 | - |
| `Q015_artifact_tag_not_homology` | 4 | 4 | 4.0 | - |
| `Q016_pdb_1acc_domain_split` | 4 | 3 | 3.75 | `api_evidence_error`, `historical_release_omission` |
| `Q017_prp8_evolutionary_mosaic` | 4 | 3 | 3.75 | - |
| `Q018_tal_effector_repeat_lengths` | 4 | 4 | 4.0 | - |
| `Q019_stable_periodic_release_semantics` | - | 4 | 4 | - |
| `Q020_syntaxin_fragment_reclassification` | 4 | 4 | 4.0 | - |
| `Q021_nad_binding_convergent_evolution` | 4 | 3 | 3.75 | `wrong_classification` |
| `Q022_colg_crya_remote_homology` | 3 | 3 | 3.0 | `api_evidence_error`, `incomplete_answer` |
| `Q023_homeodomain_heterogeneous_fold` | 4 | 4 | 4.0 | - |
| `Q024_fibronectin_fragment_selection` | 4 | 3 | 3.75 | - |
| `Q025_classification_as_evolutionary_hypothesis` | 4 | 4 | 4.0 | - |
| `Q026_protein_kinase_like_function_transfer` | 4 | 4 | 4.0 | - |

## Error Tags

| Error tag | Count |
|---|---:|
| `api_evidence_error` | 3 |
| `wrong_classification` | 2 |
| `historical_release_omission` | 1 |
| `incomplete_answer` | 1 |
| `missing_annotation_caveat` | 1 |
