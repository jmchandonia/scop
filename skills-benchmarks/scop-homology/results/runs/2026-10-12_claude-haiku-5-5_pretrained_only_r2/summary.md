# pretrained_only Benchmark Result

- Run: `2026-10-12_claude-haiku-5-5_pretrained_only_r2`
- Suite: `4.3.0`
- Model: `claude-haiku-5-5`
- Evaluator: `anthropic/claude-fable-5-1`
- Questions: 24

## Primary Biological Scores

| Questions | Biological reasoning | Factual support | Secondary gated score |
|---:|---:|---:|---:|
| 23 | 2.35 | 1.35 | 1.99 |

Biological reasoning and factual support are the primary axes. The gated score is secondary and cannot hide weak factual support.

## API Conformance

Factual-support mean: **1.00 / 4.00**.

## Per-Question Scores

| Question | Biological | Factual | Gated | Error tags |
|---|---:|---:|---:|---|
| `Q001_histone_related_proteins` | 3 | 2 | 2.75 | `identifier_or_boundary_error`, `incomplete_answer` |
| `Q002_mazg_hise_remote_homology` | 3 | 1 | 2 | `identifier_or_boundary_error`, `incomplete_answer` |
| `Q003_actin_like_atpase_divergence` | 3 | 1 | 2 | `identifier_or_boundary_error`, `incomplete_answer` |
| `Q004_ferritin_like_functional_divergence` | 3 | 1 | 2 | `identifier_or_boundary_error`, `incomplete_answer` |
| `Q005_cupredoxin_function_transfer` | 2 | 1 | 1.75 | `incomplete_answer`, `wrong_classification` |
| `Q006_ubiquitin_like_function_divergence` | 3 | 2 | 2.75 | `identifier_or_boundary_error`, `incomplete_answer` |
| `Q007_chey_like_function_divergence` | 2 | 1 | 1.75 | `identifier_or_boundary_error`, `incomplete_answer` |
| `Q008_v1_atp_synthase_bulge_classification` | 2 | 0 | 1 | `identifier_or_boundary_error`, `incomplete_answer` |
| `Q009_heme_bundle_fold_not_homology` | 2 | 1 | 1.75 | `incomplete_answer`, `unsupported_nonhomology_claim` |
| `Q010_rossmann_family_function_divergence` | 3 | 2 | 2.75 | `identifier_or_boundary_error` |
| `Q012_not_true_family_specific_pair` | 1 | 0 | 0.75 | `incomplete_answer`, `missing_annotation_caveat` |
| `Q014_paper_example_reclassification_pa1492` | 1 | 0 | 0.75 | `historical_release_omission`, `identifier_or_boundary_error`, `incomplete_answer` |
| `Q015_artifact_tag_not_homology` | 2 | 1 | 1.75 | `incomplete_answer`, `missing_annotation_caveat`, `wrong_classification` |
| `Q016_pdb_1acc_domain_split` | 1 | 1 | 1.0 | `incomplete_answer`, `missing_required_external_evidence` |
| `Q017_prp8_evolutionary_mosaic` | 1 | 1 | 1.0 | `api_evidence_error`, `identifier_or_boundary_error`, `incomplete_answer` |
| `Q018_tal_effector_repeat_lengths` | 3 | 2 | 2.75 | `missing_annotation_caveat` |
| `Q019_stable_periodic_release_semantics` | - | 1 | 1 | `incomplete_answer` |
| `Q020_syntaxin_fragment_reclassification` | 2 | 1 | 1.75 | `identifier_or_boundary_error`, `incomplete_answer`, `missing_annotation_caveat` |
| `Q021_nad_binding_convergent_evolution` | 4 | 3 | 3.75 | - |
| `Q022_colg_crya_remote_homology` | 1 | 1 | 1.0 | `historical_release_omission`, `incomplete_answer` |
| `Q023_homeodomain_heterogeneous_fold` | 2 | 2 | 2.0 | `incomplete_answer`, `missing_annotation_caveat` |
| `Q024_fibronectin_fragment_selection` | 4 | 2 | 3 | `identifier_or_boundary_error` |
| `Q025_classification_as_evolutionary_hypothesis` | 4 | 4 | 4.0 | - |
| `Q026_protein_kinase_like_function_transfer` | 2 | 1 | 1.75 | `incomplete_answer`, `wrong_classification` |

## Error Tags

| Error tag | Count |
|---|---:|
| `incomplete_answer` | 19 |
| `identifier_or_boundary_error` | 12 |
| `missing_annotation_caveat` | 5 |
| `wrong_classification` | 3 |
| `historical_release_omission` | 2 |
| `api_evidence_error` | 1 |
| `missing_required_external_evidence` | 1 |
| `unsupported_nonhomology_claim` | 1 |
