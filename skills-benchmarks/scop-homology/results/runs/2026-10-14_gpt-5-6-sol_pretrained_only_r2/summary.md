# pretrained_only Benchmark Result

- Run: `2026-10-14_gpt-5-6-sol_pretrained_only_r2`
- Suite: `4.3.0`
- Model: `gpt-5.6-sol`
- Evaluator: `anthropic/claude-fable-5-1`
- Questions: 24

## Primary Biological Scores

| Questions | Biological reasoning | Factual support | Secondary gated score |
|---:|---:|---:|---:|
| 23 | 2.87 | 1.78 | 2.45 |

Biological reasoning and factual support are the primary axes. The gated score is secondary and cannot hide weak factual support.

## API Conformance

Factual-support mean: **1.00 / 4.00**.

## Per-Question Scores

| Question | Biological | Factual | Gated | Error tags |
|---|---:|---:|---:|---|
| `Q001_histone_related_proteins` | 2 | 1 | 1.75 | `incomplete_answer`, `wrong_classification` |
| `Q002_mazg_hise_remote_homology` | 4 | 2 | 3 | `identifier_or_boundary_error` |
| `Q003_actin_like_atpase_divergence` | 2 | 1 | 1.75 | `identifier_or_boundary_error`, `wrong_classification` |
| `Q004_ferritin_like_functional_divergence` | 4 | 3 | 3.75 | - |
| `Q005_cupredoxin_function_transfer` | 4 | 3 | 3.75 | `identifier_or_boundary_error` |
| `Q006_ubiquitin_like_function_divergence` | 1 | 1 | 1.0 | `fabricated_release_fact`, `missing_named_unsafe_transfer`, `unsupported_nonhomology_claim`, `wrong_classification` |
| `Q007_chey_like_function_divergence` | 4 | 2 | 3 | `identifier_or_boundary_error` |
| `Q008_v1_atp_synthase_bulge_classification` | 3 | 1 | 2 | `identifier_or_boundary_error`, `incomplete_answer` |
| `Q009_heme_bundle_fold_not_homology` | 3 | 3 | 3.0 | `incomplete_answer`, `missing_required_external_evidence` |
| `Q010_rossmann_family_function_divergence` | 1 | 0 | 0.75 | `identifier_or_boundary_error`, `unsupported_nonhomology_claim`, `wrong_classification` |
| `Q012_not_true_family_specific_pair` | 4 | 3 | 3.75 | `identifier_or_boundary_error` |
| `Q014_paper_example_reclassification_pa1492` | 2 | 1 | 1.75 | `fabricated_release_fact`, `identifier_or_boundary_error`, `wrong_classification` |
| `Q015_artifact_tag_not_homology` | 3 | 1 | 2 | `fabricated_annotation`, `identifier_or_boundary_error` |
| `Q016_pdb_1acc_domain_split` | 3 | 1 | 2 | `historical_release_omission`, `identifier_or_boundary_error` |
| `Q017_prp8_evolutionary_mosaic` | 1 | 1 | 1.0 | `identifier_or_boundary_error`, `incomplete_answer`, `wrong_classification` |
| `Q018_tal_effector_repeat_lengths` | 2 | 2 | 2.0 | `missing_annotation_caveat` |
| `Q019_stable_periodic_release_semantics` | - | 1 | 1 | `api_evidence_error`, `fabricated_release_fact` |
| `Q020_syntaxin_fragment_reclassification` | 2 | 1 | 1.75 | `incomplete_answer`, `missing_annotation_caveat` |
| `Q021_nad_binding_convergent_evolution` | 3 | 2 | 2.75 | `incomplete_answer`, `unsupported_nonhomology_claim` |
| `Q022_colg_crya_remote_homology` | 3 | 2 | 2.75 | `identifier_or_boundary_error` |
| `Q023_homeodomain_heterogeneous_fold` | 3 | 2 | 2.75 | `incomplete_answer` |
| `Q024_fibronectin_fragment_selection` | 4 | 2 | 3 | `missing_quality_context` |
| `Q025_classification_as_evolutionary_hypothesis` | 4 | 4 | 4.0 | - |
| `Q026_protein_kinase_like_function_transfer` | 4 | 2 | 3 | `identifier_or_boundary_error` |

## Error Tags

| Error tag | Count |
|---|---:|
| `identifier_or_boundary_error` | 13 |
| `incomplete_answer` | 7 |
| `wrong_classification` | 6 |
| `fabricated_release_fact` | 3 |
| `unsupported_nonhomology_claim` | 3 |
| `missing_annotation_caveat` | 2 |
| `api_evidence_error` | 1 |
| `fabricated_annotation` | 1 |
| `historical_release_omission` | 1 |
| `missing_named_unsafe_transfer` | 1 |
| `missing_quality_context` | 1 |
| `missing_required_external_evidence` | 1 |
