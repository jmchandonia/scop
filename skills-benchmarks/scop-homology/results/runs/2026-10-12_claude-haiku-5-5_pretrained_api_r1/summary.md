# pretrained_api Benchmark Result

- Run: `2026-10-12_claude-haiku-5-5_pretrained_api_r1`
- Suite: `4.3.0`
- Model: `claude-haiku-5-5`
- Evaluator: `anthropic/claude-fable-5-1`
- Questions: 24

## Primary Biological Scores

| Questions | Biological reasoning | Factual support | Secondary gated score |
|---:|---:|---:|---:|
| 23 | 3.30 | 3.09 | 3.23 |

Biological reasoning and factual support are the primary axes. The gated score is secondary and cannot hide weak factual support.

## API Conformance

Factual-support mean: **3.00 / 4.00**.

## Per-Question Scores

| Question | Biological | Factual | Gated | Error tags |
|---|---:|---:|---:|---|
| `Q001_histone_related_proteins` | 3 | 4 | 3.25 | `unsupported_nonhomology_claim` |
| `Q002_mazg_hise_remote_homology` | 4 | 4 | 4.0 | - |
| `Q003_actin_like_atpase_divergence` | 4 | 4 | 4.0 | - |
| `Q004_ferritin_like_functional_divergence` | 4 | 3 | 3.75 | `api_evidence_error` |
| `Q005_cupredoxin_function_transfer` | 4 | 3 | 3.75 | `api_evidence_error` |
| `Q006_ubiquitin_like_function_divergence` | 2 | 2 | 2.0 | `wrong_classification` |
| `Q007_chey_like_function_divergence` | 4 | 3 | 3.75 | `identifier_or_boundary_error` |
| `Q008_v1_atp_synthase_bulge_classification` | 4 | 4 | 4.0 | - |
| `Q009_heme_bundle_fold_not_homology` | 3 | 4 | 3.25 | `unsupported_nonhomology_claim` |
| `Q010_rossmann_family_function_divergence` | 4 | 4 | 4.0 | - |
| `Q012_not_true_family_specific_pair` | 2 | 2 | 2.0 | `missing_annotation_caveat` |
| `Q014_paper_example_reclassification_pa1492` | 4 | 4 | 4.0 | - |
| `Q015_artifact_tag_not_homology` | 4 | 3 | 3.75 | `api_evidence_error` |
| `Q016_pdb_1acc_domain_split` | 4 | 2 | 3 | `api_evidence_error`, `historical_release_omission` |
| `Q017_prp8_evolutionary_mosaic` | 3 | 3 | 3.0 | `incomplete_answer` |
| `Q018_tal_effector_repeat_lengths` | 3 | 3 | 3.0 | `missing_annotation_caveat` |
| `Q019_stable_periodic_release_semantics` | - | 3 | 3 | `incomplete_answer` |
| `Q020_syntaxin_fragment_reclassification` | 2 | 3 | 2.25 | `incomplete_answer`, `missing_annotation_caveat` |
| `Q021_nad_binding_convergent_evolution` | 3 | 3 | 3.0 | `unsupported_nonhomology_claim` |
| `Q022_colg_crya_remote_homology` | 4 | 4 | 4.0 | - |
| `Q023_homeodomain_heterogeneous_fold` | 2 | 2 | 2.0 | `api_evidence_error`, `missing_annotation_caveat` |
| `Q024_fibronectin_fragment_selection` | 2 | 2 | 2.0 | `api_evidence_error`, `fragment_similarity_omission`, `missing_annotation_caveat` |
| `Q025_classification_as_evolutionary_hypothesis` | 4 | 3 | 3.75 | `api_evidence_error` |
| `Q026_protein_kinase_like_function_transfer` | 3 | 2 | 2.75 | `wrong_classification` |

## Error Tags

| Error tag | Count |
|---|---:|
| `api_evidence_error` | 7 |
| `missing_annotation_caveat` | 5 |
| `incomplete_answer` | 3 |
| `unsupported_nonhomology_claim` | 3 |
| `wrong_classification` | 2 |
| `fragment_similarity_omission` | 1 |
| `historical_release_omission` | 1 |
| `identifier_or_boundary_error` | 1 |
