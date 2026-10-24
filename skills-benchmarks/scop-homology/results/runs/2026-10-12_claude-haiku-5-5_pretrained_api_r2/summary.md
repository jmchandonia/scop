# pretrained_api Benchmark Result

- Run: `2026-10-12_claude-haiku-5-5_pretrained_api_r2`
- Suite: `4.3.0`
- Model: `claude-haiku-5-5`
- Evaluator: `anthropic/claude-fable-5-1`
- Questions: 24

## Primary Biological Scores

| Questions | Biological reasoning | Factual support | Secondary gated score |
|---:|---:|---:|---:|
| 23 | 3.65 | 3.48 | 3.61 |

Biological reasoning and factual support are the primary axes. The gated score is secondary and cannot hide weak factual support.

## API Conformance

Factual-support mean: **3.00 / 4.00**.

## Per-Question Scores

| Question | Biological | Factual | Gated | Error tags |
|---|---:|---:|---:|---|
| `Q001_histone_related_proteins` | 3 | 4 | 3.25 | `unsupported_nonhomology_claim` |
| `Q002_mazg_hise_remote_homology` | 4 | 3 | 3.75 | - |
| `Q003_actin_like_atpase_divergence` | 4 | 4 | 4.0 | - |
| `Q004_ferritin_like_functional_divergence` | 4 | 4 | 4.0 | - |
| `Q005_cupredoxin_function_transfer` | 4 | 3 | 3.75 | `api_evidence_error` |
| `Q006_ubiquitin_like_function_divergence` | 4 | 4 | 4.0 | - |
| `Q007_chey_like_function_divergence` | 4 | 3 | 3.75 | - |
| `Q008_v1_atp_synthase_bulge_classification` | 4 | 3 | 3.75 | `incomplete_answer` |
| `Q009_heme_bundle_fold_not_homology` | 4 | 4 | 4.0 | - |
| `Q010_rossmann_family_function_divergence` | 4 | 4 | 4.0 | - |
| `Q012_not_true_family_specific_pair` | 2 | 2 | 2.0 | `missing_annotation_caveat` |
| `Q014_paper_example_reclassification_pa1492` | 4 | 4 | 4.0 | - |
| `Q015_artifact_tag_not_homology` | 4 | 4 | 4.0 | - |
| `Q016_pdb_1acc_domain_split` | 4 | 4 | 4.0 | - |
| `Q017_prp8_evolutionary_mosaic` | 4 | 4 | 4.0 | - |
| `Q018_tal_effector_repeat_lengths` | 3 | 3 | 3.0 | `missing_annotation_caveat` |
| `Q019_stable_periodic_release_semantics` | - | 3 | 3 | `incomplete_answer` |
| `Q020_syntaxin_fragment_reclassification` | 2 | 3 | 2.25 | `incomplete_answer`, `missing_annotation_caveat` |
| `Q021_nad_binding_convergent_evolution` | 3 | 4 | 3.25 | `unsupported_nonhomology_claim` |
| `Q022_colg_crya_remote_homology` | 4 | 4 | 4.0 | - |
| `Q023_homeodomain_heterogeneous_fold` | 4 | 3 | 3.75 | `wrong_classification` |
| `Q024_fibronectin_fragment_selection` | 3 | 2 | 2.75 | `api_evidence_error`, `incomplete_answer` |
| `Q025_classification_as_evolutionary_hypothesis` | 4 | 3 | 3.75 | `api_evidence_error` |
| `Q026_protein_kinase_like_function_transfer` | 4 | 4 | 4.0 | - |

## Error Tags

| Error tag | Count |
|---|---:|
| `incomplete_answer` | 4 |
| `api_evidence_error` | 3 |
| `missing_annotation_caveat` | 3 |
| `unsupported_nonhomology_claim` | 2 |
| `wrong_classification` | 1 |
