# SCOPe Benchmark Comparison

- Suite: `4.3.0`
- Answer model: `gpt-rosalind-5.5`
- Evaluator: `openai/gpt-5.6-sol`
- Replicates per condition: 1
- Harness: codex-cli-isolated; one fresh session per question

## Aggregate Results

| Condition | Biological reasoning | Factual support | Secondary gated | Run-level gated SD |
|---|---:|---:|---:|---:|
| `pretrained_only` | 2.78 | 2.30 | 2.62 | 0.00 |
| `pretrained_api` | 3.52 | 3.30 | 3.47 | 0.00 |
| `pretrained_skill` | 3.91 | 3.96 | 3.92 | 0.00 |

## Skill Effect Over API

| Question | Biological delta | Factual delta | Gated delta |
|---|---:|---:|---:|
| `Q012_not_true_family_specific_pair` | +2.00 | +2.00 | +2.00 |
| `Q018_tal_effector_repeat_lengths` | +1.00 | +2.00 | +1.25 |
| `Q009_heme_bundle_fold_not_homology` | +1.00 | +1.00 | +1.00 |
| `Q020_syntaxin_fragment_reclassification` | +1.00 | +1.00 | +1.00 |
| `Q021_nad_binding_convergent_evolution` | +1.00 | +1.00 | +1.00 |
| `Q024_fibronectin_fragment_selection` | +1.00 | +1.00 | +1.00 |
| `Q003_actin_like_atpase_divergence` | +1.00 | +0.00 | +0.75 |
| `Q005_cupredoxin_function_transfer` | +1.00 | +0.00 | +0.75 |
| `Q001_histone_related_proteins` | +0.00 | +1.00 | +0.25 |
| `Q004_ferritin_like_functional_divergence` | +0.00 | +1.00 | +0.25 |
| `Q010_rossmann_family_function_divergence` | +0.00 | +1.00 | +0.25 |
| `Q014_paper_example_reclassification_pa1492` | +0.00 | +1.00 | +0.25 |
| `Q016_pdb_1acc_domain_split` | +0.00 | +1.00 | +0.25 |
| `Q017_prp8_evolutionary_mosaic` | +0.00 | +1.00 | +0.25 |
| `Q023_homeodomain_heterogeneous_fold` | +0.00 | +1.00 | +0.25 |
| `Q002_mazg_hise_remote_homology` | +0.00 | +0.00 | +0.00 |
| `Q006_ubiquitin_like_function_divergence` | +0.00 | +0.00 | +0.00 |
| `Q007_chey_like_function_divergence` | +0.00 | +0.00 | +0.00 |
| `Q008_v1_atp_synthase_bulge_classification` | +0.00 | +0.00 | +0.00 |
| `Q015_artifact_tag_not_homology` | +0.00 | +0.00 | +0.00 |
| `Q022_colg_crya_remote_homology` | +0.00 | +0.00 | +0.00 |
| `Q025_classification_as_evolutionary_hypothesis` | +0.00 | +0.00 | +0.00 |
| `Q026_protein_kinase_like_function_transfer` | +0.00 | +0.00 | +0.00 |

## Error Tags By Condition

| Condition | Error tag | Count |
|---|---|---:|
| `pretrained_only` | `identifier_or_boundary_error` | 13 |
| `pretrained_only` | `incomplete_answer` | 9 |
| `pretrained_only` | `wrong_classification` | 5 |
| `pretrained_only` | `missing_named_unsafe_transfer` | 4 |
| `pretrained_only` | `missing_required_external_evidence` | 4 |
| `pretrained_only` | `missing_annotation_caveat` | 3 |
| `pretrained_only` | `unsupported_nonhomology_claim` | 3 |
| `pretrained_only` | `historical_release_omission` | 2 |
| `pretrained_only` | `missing_quality_context` | 1 |
| `pretrained_only` | `periodic_release_omission` | 1 |
| `pretrained_api` | `api_evidence_error` | 11 |
| `pretrained_api` | `missing_required_external_evidence` | 5 |
| `pretrained_api` | `incomplete_answer` | 3 |
| `pretrained_api` | `missing_annotation_caveat` | 2 |
| `pretrained_api` | `missing_named_unsafe_transfer` | 2 |
| `pretrained_api` | `missing_quality_context` | 1 |
| `pretrained_api` | `periodic_release_omission` | 1 |
| `pretrained_api` | `wrong_classification` | 1 |
| `pretrained_skill` | `incomplete_answer` | 1 |
| `pretrained_skill` | `missing_named_unsafe_transfer` | 1 |
| `pretrained_skill` | `missing_required_external_evidence` | 1 |
| `pretrained_skill` | `periodic_release_omission` | 1 |
