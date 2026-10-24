# pretrained_only Benchmark Result

- Run: `2026-10-12_gpt-rosalind-5-5_pretrained_only_r1`
- Suite: `4.3.0`
- Model: `gpt-rosalind-5.5`
- Evaluator: `anthropic/claude-fable-5-1`
- Questions: 24

## Primary Biological Scores

| Questions | Biological reasoning | Factual support | Secondary gated score |
|---:|---:|---:|---:|
| 23 | 2.61 | 1.87 | 2.36 |

Biological reasoning and factual support are the primary axes. The gated score is secondary and cannot hide weak factual support.

## API Conformance

Factual-support mean: **2.00 / 4.00**.

## Per-Question Scores

| Question | Biological | Factual | Gated | Error tags |
|---|---:|---:|---:|---|
| `Q001_histone_related_proteins` | 2 | 1 | 1.75 | `api_evidence_error`, `incomplete_answer` |
| `Q002_mazg_hise_remote_homology` | 4 | 2 | 3 | `identifier_or_boundary_error` |
| `Q003_actin_like_atpase_divergence` | 3 | 1 | 2 | `identifier_or_boundary_error`, `wrong_classification` |
| `Q004_ferritin_like_functional_divergence` | 3 | 3 | 3.0 | `identifier_or_boundary_error`, `missing_required_external_evidence` |
| `Q005_cupredoxin_function_transfer` | 1 | 1 | 1.0 | `unsupported_nonhomology_claim`, `wrong_classification` |
| `Q006_ubiquitin_like_function_divergence` | 3 | 2 | 2.75 | `identifier_or_boundary_error`, `missing_named_unsafe_transfer` |
| `Q007_chey_like_function_divergence` | 3 | 2 | 2.75 | `identifier_or_boundary_error` |
| `Q008_v1_atp_synthase_bulge_classification` | 2 | 1 | 1.75 | `identifier_or_boundary_error`, `incomplete_answer` |
| `Q009_heme_bundle_fold_not_homology` | 3 | 4 | 3.25 | `unsupported_nonhomology_claim` |
| `Q010_rossmann_family_function_divergence` | 3 | 2 | 2.75 | `identifier_or_boundary_error`, `missing_required_external_evidence` |
| `Q012_not_true_family_specific_pair` | 2 | 1 | 1.75 | `identifier_or_boundary_error`, `missing_annotation_caveat` |
| `Q014_paper_example_reclassification_pa1492` | 2 | 1 | 1.75 | `fabricated_release_fact`, `identifier_or_boundary_error`, `wrong_classification` |
| `Q015_artifact_tag_not_homology` | 4 | 2 | 3 | `identifier_or_boundary_error` |
| `Q016_pdb_1acc_domain_split` | 3 | 2 | 2.75 | `historical_release_omission`, `identifier_or_boundary_error` |
| `Q017_prp8_evolutionary_mosaic` | 1 | 1 | 1.0 | `identifier_or_boundary_error`, `incomplete_answer`, `wrong_classification` |
| `Q018_tal_effector_repeat_lengths` | 3 | 2 | 2.75 | `missing_annotation_caveat` |
| `Q019_stable_periodic_release_semantics` | - | 2 | 2 | `api_evidence_error`, `incomplete_answer` |
| `Q020_syntaxin_fragment_reclassification` | 2 | 1 | 1.75 | `identifier_or_boundary_error`, `incomplete_answer`, `missing_annotation_caveat` |
| `Q021_nad_binding_convergent_evolution` | 3 | 2 | 2.75 | `incomplete_answer`, `unsupported_nonhomology_claim` |
| `Q022_colg_crya_remote_homology` | 2 | 1 | 1.75 | `identifier_or_boundary_error`, `wrong_classification` |
| `Q023_homeodomain_heterogeneous_fold` | 2 | 2 | 2.0 | `incomplete_answer`, `missing_annotation_caveat` |
| `Q024_fibronectin_fragment_selection` | 3 | 3 | 3.0 | `missing_quality_context` |
| `Q025_classification_as_evolutionary_hypothesis` | 3 | 4 | 3.25 | `incomplete_answer` |
| `Q026_protein_kinase_like_function_transfer` | 3 | 2 | 2.75 | `identifier_or_boundary_error` |

## Error Tags

| Error tag | Count |
|---|---:|
| `identifier_or_boundary_error` | 15 |
| `incomplete_answer` | 8 |
| `wrong_classification` | 5 |
| `missing_annotation_caveat` | 4 |
| `unsupported_nonhomology_claim` | 3 |
| `api_evidence_error` | 2 |
| `missing_required_external_evidence` | 2 |
| `fabricated_release_fact` | 1 |
| `historical_release_omission` | 1 |
| `missing_named_unsafe_transfer` | 1 |
| `missing_quality_context` | 1 |
