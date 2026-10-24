# SCOPe Benchmark Comparison

- Suite: `4.3.0`
- Answer model: `gpt-6-astra`
- Evaluator: `anthropic/claude-fable-5-1`
- Replicates per condition: 3
- Harness: codex-cli-isolated; one fresh session per question

## Aggregate Results

| Condition | Biological reasoning | Factual support | Secondary gated | Run-level gated SD |
|---|---:|---:|---:|---:|
| `pretrained_only` | 3.30 | 2.01 | 2.79 | 0.09 |
| `pretrained_api` | 3.93 | 3.81 | 3.90 | 0.01 |
| `pretrained_skill` | 4.00 | 3.93 | 3.98 | 0.01 |
| `pretrained_mcp` | 3.91 | 3.90 | 3.91 | 0.01 |
| `pretrained_mcp_skill` | 3.97 | 3.90 | 3.95 | 0.05 |

## Skill Effect Over API

| Question | Biological delta | Factual delta | Gated delta |
|---|---:|---:|---:|
| `Q009_heme_bundle_fold_not_homology` | +0.67 | +0.00 | +0.50 |
| `Q016_pdb_1acc_domain_split` | +0.67 | -0.33 | +0.42 |
| `Q014_paper_example_reclassification_pa1492` | +0.00 | +1.00 | +0.25 |
| `Q024_fibronectin_fragment_selection` | +0.00 | +1.00 | +0.25 |
| `Q018_tal_effector_repeat_lengths` | +0.33 | -0.33 | +0.17 |
| `Q022_colg_crya_remote_homology` | +0.00 | +0.67 | +0.17 |
| `Q007_chey_like_function_divergence` | +0.00 | +0.33 | +0.08 |
| `Q020_syntaxin_fragment_reclassification` | +0.00 | -0.33 | -0.08 |
| `Q021_nad_binding_convergent_evolution` | +0.00 | +0.33 | +0.08 |
| `Q026_protein_kinase_like_function_transfer` | +0.00 | +0.33 | +0.08 |
| `Q001_histone_related_proteins` | +0.00 | +0.00 | +0.00 |
| `Q002_mazg_hise_remote_homology` | +0.00 | +0.00 | +0.00 |
| `Q003_actin_like_atpase_divergence` | +0.00 | +0.00 | +0.00 |
| `Q004_ferritin_like_functional_divergence` | +0.00 | +0.00 | +0.00 |
| `Q005_cupredoxin_function_transfer` | +0.00 | +0.00 | +0.00 |
| `Q006_ubiquitin_like_function_divergence` | +0.00 | +0.00 | +0.00 |
| `Q008_v1_atp_synthase_bulge_classification` | +0.00 | +0.00 | +0.00 |
| `Q010_rossmann_family_function_divergence` | +0.00 | +0.00 | +0.00 |
| `Q012_not_true_family_specific_pair` | +0.00 | +0.00 | +0.00 |
| `Q015_artifact_tag_not_homology` | +0.00 | +0.00 | +0.00 |
| `Q017_prp8_evolutionary_mosaic` | +0.00 | +0.00 | +0.00 |
| `Q023_homeodomain_heterogeneous_fold` | +0.00 | +0.00 | +0.00 |
| `Q025_classification_as_evolutionary_hypothesis` | +0.00 | +0.00 | +0.00 |

## Skill Effect Over MCP

| Question | Biological delta | Factual delta | Gated delta |
|---|---:|---:|---:|
| `Q009_heme_bundle_fold_not_homology` | +0.67 | +0.00 | +0.50 |
| `Q016_pdb_1acc_domain_split` | +0.67 | +0.00 | +0.50 |
| `Q024_fibronectin_fragment_selection` | +0.00 | +1.00 | +0.25 |
| `Q020_syntaxin_fragment_reclassification` | +0.00 | -0.33 | -0.08 |
| `Q022_colg_crya_remote_homology` | +0.00 | -0.33 | -0.08 |
| `Q025_classification_as_evolutionary_hypothesis` | +0.00 | -0.33 | -0.08 |
| `Q001_histone_related_proteins` | +0.00 | +0.00 | +0.00 |
| `Q002_mazg_hise_remote_homology` | +0.00 | +0.00 | +0.00 |
| `Q003_actin_like_atpase_divergence` | +0.00 | +0.00 | +0.00 |
| `Q004_ferritin_like_functional_divergence` | +0.00 | +0.00 | +0.00 |
| `Q005_cupredoxin_function_transfer` | +0.00 | +0.00 | +0.00 |
| `Q006_ubiquitin_like_function_divergence` | +0.00 | +0.00 | +0.00 |
| `Q007_chey_like_function_divergence` | +0.00 | +0.00 | +0.00 |
| `Q008_v1_atp_synthase_bulge_classification` | +0.00 | +0.00 | +0.00 |
| `Q010_rossmann_family_function_divergence` | +0.00 | +0.00 | +0.00 |
| `Q012_not_true_family_specific_pair` | +0.00 | +0.00 | +0.00 |
| `Q014_paper_example_reclassification_pa1492` | +0.00 | +0.00 | +0.00 |
| `Q015_artifact_tag_not_homology` | +0.00 | +0.00 | +0.00 |
| `Q017_prp8_evolutionary_mosaic` | +0.00 | +0.00 | +0.00 |
| `Q018_tal_effector_repeat_lengths` | +0.00 | +0.00 | +0.00 |
| `Q021_nad_binding_convergent_evolution` | +0.00 | +0.00 | +0.00 |
| `Q023_homeodomain_heterogeneous_fold` | +0.00 | +0.00 | +0.00 |
| `Q026_protein_kinase_like_function_transfer` | +0.00 | +0.00 | +0.00 |

## MCP Effect Over API

| Question | Biological delta | Factual delta | Gated delta |
|---|---:|---:|---:|
| `Q016_pdb_1acc_domain_split` | -0.33 | -1.00 | -0.50 |
| `Q018_tal_effector_repeat_lengths` | +0.33 | +0.33 | +0.33 |
| `Q009_heme_bundle_fold_not_homology` | -0.33 | +0.00 | -0.25 |
| `Q014_paper_example_reclassification_pa1492` | +0.00 | +1.00 | +0.25 |
| `Q022_colg_crya_remote_homology` | +0.00 | +0.67 | +0.17 |
| `Q007_chey_like_function_divergence` | +0.00 | +0.33 | +0.08 |
| `Q021_nad_binding_convergent_evolution` | +0.00 | +0.33 | +0.08 |
| `Q026_protein_kinase_like_function_transfer` | +0.00 | +0.33 | +0.08 |
| `Q001_histone_related_proteins` | +0.00 | +0.00 | +0.00 |
| `Q002_mazg_hise_remote_homology` | +0.00 | +0.00 | +0.00 |
| `Q003_actin_like_atpase_divergence` | +0.00 | +0.00 | +0.00 |
| `Q004_ferritin_like_functional_divergence` | +0.00 | +0.00 | +0.00 |
| `Q005_cupredoxin_function_transfer` | +0.00 | +0.00 | +0.00 |
| `Q006_ubiquitin_like_function_divergence` | +0.00 | +0.00 | +0.00 |
| `Q008_v1_atp_synthase_bulge_classification` | +0.00 | +0.00 | +0.00 |
| `Q010_rossmann_family_function_divergence` | +0.00 | +0.00 | +0.00 |
| `Q012_not_true_family_specific_pair` | +0.00 | +0.00 | +0.00 |
| `Q015_artifact_tag_not_homology` | +0.00 | +0.00 | +0.00 |
| `Q017_prp8_evolutionary_mosaic` | +0.00 | +0.00 | +0.00 |
| `Q020_syntaxin_fragment_reclassification` | +0.00 | +0.00 | +0.00 |
| `Q023_homeodomain_heterogeneous_fold` | +0.00 | +0.00 | +0.00 |
| `Q024_fibronectin_fragment_selection` | +0.00 | +0.00 | +0.00 |
| `Q025_classification_as_evolutionary_hypothesis` | +0.00 | +0.00 | +0.00 |

## Error Tags By Condition

| Condition | Error tag | Count |
|---|---|---:|
| `pretrained_only` | `identifier_or_boundary_error` | 51 |
| `pretrained_only` | `incomplete_answer` | 31 |
| `pretrained_only` | `historical_release_omission` | 7 |
| `pretrained_only` | `wrong_classification` | 5 |
| `pretrained_only` | `missing_annotation_caveat` | 4 |
| `pretrained_only` | `missing_quality_context` | 3 |
| `pretrained_only` | `api_evidence_error` | 1 |
| `pretrained_only` | `missing_required_external_evidence` | 1 |
| `pretrained_only` | `periodic_release_omission` | 1 |
| `pretrained_api` | `api_evidence_error` | 8 |
| `pretrained_api` | `missing_annotation_caveat` | 3 |
| `pretrained_api` | `missing_quality_context` | 3 |
| `pretrained_api` | `incomplete_answer` | 2 |
| `pretrained_api` | `historical_release_omission` | 1 |
| `pretrained_skill` | `periodic_release_omission` | 3 |
| `pretrained_skill` | `historical_release_omission` | 2 |
| `pretrained_skill` | `incomplete_answer` | 1 |
| `pretrained_mcp` | `historical_release_omission` | 3 |
| `pretrained_mcp` | `incomplete_answer` | 3 |
| `pretrained_mcp` | `missing_quality_context` | 3 |
| `pretrained_mcp` | `missing_annotation_caveat` | 2 |
| `pretrained_mcp` | `fabricated_release_fact` | 1 |
| `pretrained_mcp` | `periodic_release_omission` | 1 |
| `pretrained_mcp_skill` | `historical_release_omission` | 3 |
| `pretrained_mcp_skill` | `periodic_release_omission` | 3 |
| `pretrained_mcp_skill` | `api_evidence_error` | 2 |
| `pretrained_mcp_skill` | `fabricated_release_fact` | 1 |
| `pretrained_mcp_skill` | `incomplete_answer` | 1 |
| `pretrained_mcp_skill` | `unsupported_homology_claim` | 1 |
