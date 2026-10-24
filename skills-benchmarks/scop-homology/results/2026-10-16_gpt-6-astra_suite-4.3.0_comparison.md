# SCOPe Benchmark Comparison

- Suite: `4.3.0`
- Answer model: `gpt-6-astra`
- Evaluator: `anthropic/claude-fable-5-1`
- Replicates per condition: 1
- Harness: codex-cli-isolated; one fresh session per question

## Aggregate Results

| Condition | Biological reasoning | Factual support | Secondary gated | Run-level gated SD |
|---|---:|---:|---:|---:|
| `pretrained_only` | 3.43 | 2.09 | 2.88 | 0.00 |
| `pretrained_api` | 3.96 | 3.74 | 3.90 | 0.00 |
| `pretrained_skill` | 4.00 | 3.96 | 3.99 | 0.00 |

## Skill Effect Over API

| Question | Biological delta | Factual delta | Gated delta |
|---|---:|---:|---:|
| `Q018_tal_effector_repeat_lengths` | +1.00 | +1.00 | +1.00 |
| `Q014_paper_example_reclassification_pa1492` | +0.00 | +1.00 | +0.25 |
| `Q021_nad_binding_convergent_evolution` | +0.00 | +1.00 | +0.25 |
| `Q024_fibronectin_fragment_selection` | +0.00 | +1.00 | +0.25 |
| `Q026_protein_kinase_like_function_transfer` | +0.00 | +1.00 | +0.25 |
| `Q001_histone_related_proteins` | +0.00 | +0.00 | +0.00 |
| `Q002_mazg_hise_remote_homology` | +0.00 | +0.00 | +0.00 |
| `Q003_actin_like_atpase_divergence` | +0.00 | +0.00 | +0.00 |
| `Q004_ferritin_like_functional_divergence` | +0.00 | +0.00 | +0.00 |
| `Q005_cupredoxin_function_transfer` | +0.00 | +0.00 | +0.00 |
| `Q006_ubiquitin_like_function_divergence` | +0.00 | +0.00 | +0.00 |
| `Q007_chey_like_function_divergence` | +0.00 | +0.00 | +0.00 |
| `Q008_v1_atp_synthase_bulge_classification` | +0.00 | +0.00 | +0.00 |
| `Q009_heme_bundle_fold_not_homology` | +0.00 | +0.00 | +0.00 |
| `Q010_rossmann_family_function_divergence` | +0.00 | +0.00 | +0.00 |
| `Q012_not_true_family_specific_pair` | +0.00 | +0.00 | +0.00 |
| `Q015_artifact_tag_not_homology` | +0.00 | +0.00 | +0.00 |
| `Q016_pdb_1acc_domain_split` | +0.00 | +0.00 | +0.00 |
| `Q017_prp8_evolutionary_mosaic` | +0.00 | +0.00 | +0.00 |
| `Q020_syntaxin_fragment_reclassification` | +0.00 | +0.00 | +0.00 |
| `Q022_colg_crya_remote_homology` | +0.00 | +0.00 | +0.00 |
| `Q023_homeodomain_heterogeneous_fold` | +0.00 | +0.00 | +0.00 |
| `Q025_classification_as_evolutionary_hypothesis` | +0.00 | +0.00 | +0.00 |

## Error Tags By Condition

| Condition | Error tag | Count |
|---|---|---:|
| `pretrained_only` | `identifier_or_boundary_error` | 17 |
| `pretrained_only` | `incomplete_answer` | 7 |
| `pretrained_only` | `historical_release_omission` | 2 |
| `pretrained_only` | `missing_annotation_caveat` | 2 |
| `pretrained_only` | `wrong_classification` | 2 |
| `pretrained_only` | `api_evidence_error` | 1 |
| `pretrained_only` | `missing_quality_context` | 1 |
| `pretrained_only` | `missing_required_external_evidence` | 1 |
| `pretrained_only` | `periodic_release_omission` | 1 |
| `pretrained_api` | `api_evidence_error` | 3 |
| `pretrained_api` | `historical_release_omission` | 1 |
| `pretrained_api` | `missing_annotation_caveat` | 1 |
| `pretrained_api` | `missing_quality_context` | 1 |
| `pretrained_skill` | `historical_release_omission` | 1 |
| `pretrained_skill` | `periodic_release_omission` | 1 |
