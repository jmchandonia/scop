# pretrained_only Benchmark Result

- Run: `2026-10-12_claude-sonnet-5-5_pretrained_only_r2`
- Suite: `4.3.0`
- Model: `claude-sonnet-5-5`
- Evaluator: `anthropic/claude-fable-5-1`
- Questions: 24

## Primary Biological Scores

| Questions | Biological reasoning | Factual support | Secondary gated score |
|---:|---:|---:|---:|
| 23 | 2.96 | 1.74 | 2.48 |

Biological reasoning and factual support are the primary axes. The gated score is secondary and cannot hide weak factual support.

## API Conformance

Factual-support mean: **2.00 / 4.00**.

## Per-Question Scores

| Question | Biological | Factual | Gated | Error tags |
|---|---:|---:|---:|---|
| `Q001_histone_related_proteins` | 2 | 2 | 2.0 | `incomplete_answer`, `wrong_classification` |
| `Q002_mazg_hise_remote_homology` | 3 | 2 | 2.75 | `identifier_or_boundary_error` |
| `Q003_actin_like_atpase_divergence` | 4 | 2 | 3 | `identifier_or_boundary_error` |
| `Q004_ferritin_like_functional_divergence` | 4 | 2 | 3 | `wrong_classification` |
| `Q005_cupredoxin_function_transfer` | 4 | 2 | 3 | `identifier_or_boundary_error` |
| `Q006_ubiquitin_like_function_divergence` | 4 | 2 | 3 | `identifier_or_boundary_error` |
| `Q007_chey_like_function_divergence` | 4 | 2 | 3 | `identifier_or_boundary_error` |
| `Q008_v1_atp_synthase_bulge_classification` | 3 | 1 | 2 | `identifier_or_boundary_error`, `incomplete_answer` |
| `Q009_heme_bundle_fold_not_homology` | 2 | 1 | 1.75 | `unsupported_nonhomology_claim`, `wrong_classification` |
| `Q010_rossmann_family_function_divergence` | 3 | 2 | 2.75 | `identifier_or_boundary_error`, `unsupported_homology_claim` |
| `Q012_not_true_family_specific_pair` | 1 | 1 | 1.0 | `identifier_or_boundary_error`, `incomplete_answer`, `missing_annotation_caveat`, `unsupported_nonhomology_claim` |
| `Q014_paper_example_reclassification_pa1492` | 3 | 1 | 2 | `historical_release_omission`, `identifier_or_boundary_error` |
| `Q015_artifact_tag_not_homology` | 3 | 2 | 2.75 | `identifier_or_boundary_error`, `incomplete_answer` |
| `Q016_pdb_1acc_domain_split` | 2 | 1 | 1.75 | `historical_release_omission`, `identifier_or_boundary_error`, `incomplete_answer` |
| `Q017_prp8_evolutionary_mosaic` | 2 | 1 | 1.75 | `identifier_or_boundary_error`, `incomplete_answer`, `wrong_classification` |
| `Q018_tal_effector_repeat_lengths` | 2 | 1 | 1.75 | `incomplete_answer`, `missing_annotation_caveat` |
| `Q019_stable_periodic_release_semantics` | - | 2 | 2 | `incomplete_answer`, `periodic_release_omission` |
| `Q020_syntaxin_fragment_reclassification` | 2 | 1 | 1.75 | `identifier_or_boundary_error`, `missing_annotation_caveat`, `wrong_classification` |
| `Q021_nad_binding_convergent_evolution` | 4 | 3 | 3.75 | - |
| `Q022_colg_crya_remote_homology` | 3 | 2 | 2.75 | `identifier_or_boundary_error` |
| `Q023_homeodomain_heterogeneous_fold` | 2 | 1 | 1.75 | `incomplete_answer`, `missing_annotation_caveat` |
| `Q024_fibronectin_fragment_selection` | 3 | 2 | 2.75 | `identifier_or_boundary_error`, `incomplete_answer` |
| `Q025_classification_as_evolutionary_hypothesis` | 4 | 4 | 4.0 | - |
| `Q026_protein_kinase_like_function_transfer` | 4 | 2 | 3 | `identifier_or_boundary_error` |

## Error Tags

| Error tag | Count |
|---|---:|
| `identifier_or_boundary_error` | 16 |
| `incomplete_answer` | 10 |
| `wrong_classification` | 5 |
| `missing_annotation_caveat` | 4 |
| `historical_release_omission` | 2 |
| `unsupported_nonhomology_claim` | 2 |
| `periodic_release_omission` | 1 |
| `unsupported_homology_claim` | 1 |
