# pretrained_only Benchmark Result

- Run: `2026-10-13_claude-fable-5-1_pretrained_only_r1`
- Suite: `4.3.0`
- Model: `claude-fable-5-1`
- Evaluator: `anthropic/claude-fable-5-1`
- Questions: 24

## Primary Biological Scores

| Questions | Biological reasoning | Factual support | Secondary gated score |
|---:|---:|---:|---:|
| 23 | 2.48 | 1.57 | 2.18 |

Biological reasoning and factual support are the primary axes. The gated score is secondary and cannot hide weak factual support.

## API Conformance

Factual-support mean: **2.00 / 4.00**.

## Per-Question Scores

| Question | Biological | Factual | Gated | Error tags |
|---|---:|---:|---:|---|
| `Q001_histone_related_proteins` | 2 | 1 | 1.75 | `fabricated_release_fact`, `incomplete_answer`, `wrong_classification` |
| `Q002_mazg_hise_remote_homology` | 2 | 1 | 1.75 | `fabricated_release_fact`, `identifier_or_boundary_error`, `wrong_classification` |
| `Q003_actin_like_atpase_divergence` | 2 | 1 | 1.75 | `fabricated_release_fact`, `identifier_or_boundary_error`, `wrong_classification` |
| `Q004_ferritin_like_functional_divergence` | 4 | 3 | 3.75 | `wrong_classification` |
| `Q005_cupredoxin_function_transfer` | 4 | 2 | 3 | `identifier_or_boundary_error`, `wrong_classification` |
| `Q006_ubiquitin_like_function_divergence` | 4 | 2 | 3 | `api_evidence_error` |
| `Q007_chey_like_function_divergence` | 2 | 1 | 1.75 | `fabricated_release_fact`, `wrong_classification` |
| `Q008_v1_atp_synthase_bulge_classification` | 2 | 1 | 1.75 | `api_evidence_error`, `fabricated_release_fact`, `identifier_or_boundary_error`, `incomplete_answer`, `unsupported_nonhomology_claim` |
| `Q009_heme_bundle_fold_not_homology` | 2 | 1 | 1.75 | `fabricated_release_fact`, `wrong_classification` |
| `Q010_rossmann_family_function_divergence` | 4 | 3 | 3.75 | - |
| `Q012_not_true_family_specific_pair` | 2 | 1 | 1.75 | `identifier_or_boundary_error`, `missing_annotation_caveat` |
| `Q014_paper_example_reclassification_pa1492` | 2 | 1 | 1.75 | `fabricated_release_fact`, `identifier_or_boundary_error`, `wrong_classification` |
| `Q015_artifact_tag_not_homology` | 4 | 3 | 3.75 | - |
| `Q016_pdb_1acc_domain_split` | 1 | 1 | 1.0 | `fabricated_release_fact`, `historical_release_omission`, `identifier_or_boundary_error`, `wrong_classification` |
| `Q017_prp8_evolutionary_mosaic` | 2 | 1 | 1.75 | `api_evidence_error`, `fabricated_release_fact`, `identifier_or_boundary_error`, `wrong_classification` |
| `Q018_tal_effector_repeat_lengths` | 2 | 1 | 1.75 | `fabricated_release_fact`, `identifier_or_boundary_error`, `missing_annotation_caveat` |
| `Q019_stable_periodic_release_semantics` | - | 2 | 2 | `incomplete_answer` |
| `Q020_syntaxin_fragment_reclassification` | 3 | 2 | 2.75 | `api_evidence_error`, `historical_release_omission`, `identifier_or_boundary_error` |
| `Q021_nad_binding_convergent_evolution` | 3 | 2 | 2.75 | `wrong_classification` |
| `Q022_colg_crya_remote_homology` | 0 | 0 | 0.0 | `fabricated_release_fact`, `historical_release_omission`, `identifier_or_boundary_error`, `unsupported_nonhomology_claim`, `wrong_classification` |
| `Q023_homeodomain_heterogeneous_fold` | 1 | 1 | 1.0 | `api_evidence_error`, `incomplete_answer`, `missing_annotation_caveat`, `wrong_classification` |
| `Q024_fibronectin_fragment_selection` | 3 | 1 | 2 | `identifier_or_boundary_error`, `missing_quality_context` |
| `Q025_classification_as_evolutionary_hypothesis` | 4 | 4 | 4.0 | - |
| `Q026_protein_kinase_like_function_transfer` | 2 | 2 | 2.0 | `identifier_or_boundary_error`, `wrong_classification` |

## Error Tags

| Error tag | Count |
|---|---:|
| `wrong_classification` | 14 |
| `identifier_or_boundary_error` | 13 |
| `fabricated_release_fact` | 11 |
| `api_evidence_error` | 5 |
| `incomplete_answer` | 4 |
| `historical_release_omission` | 3 |
| `missing_annotation_caveat` | 3 |
| `unsupported_nonhomology_claim` | 2 |
| `missing_quality_context` | 1 |
