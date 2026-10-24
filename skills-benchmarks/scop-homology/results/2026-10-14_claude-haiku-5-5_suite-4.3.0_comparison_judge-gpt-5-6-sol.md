# SCOPe Benchmark Comparison

- Suite: `4.3.0`
- Answer model: `claude-haiku-5-5`
- Evaluator: `openai/gpt-5.6-sol`
- Replicates per condition: 3
- Harness: claude-code-cli-isolated; one fresh session per question

## Aggregate Results

| Condition | Biological reasoning | Factual support | Secondary gated | Run-level gated SD |
|---|---:|---:|---:|---:|
| `pretrained_only` | 2.23 | 1.58 | 1.99 | 0.16 |
| `pretrained_api` | 3.36 | 2.62 | 3.03 | 0.23 |
| `pretrained_skill` | 3.68 | 3.70 | 3.68 | 0.06 |
| `pretrained_mcp` | 3.29 | 3.16 | 3.23 | 0.19 |
| `pretrained_mcp_skill` | 3.57 | 3.17 | 3.41 | 0.13 |

## Skill Effect Over API

| Question | Biological delta | Factual delta | Gated delta |
|---|---:|---:|---:|
| `Q018_tal_effector_repeat_lengths` | +2.00 | +2.00 | +2.00 |
| `Q010_rossmann_family_function_divergence` | +0.00 | +2.33 | +1.83 |
| `Q009_heme_bundle_fold_not_homology` | +1.33 | +2.00 | +1.67 |
| `Q012_not_true_family_specific_pair` | +1.33 | +1.67 | +1.42 |
| `Q015_artifact_tag_not_homology` | +0.00 | +2.33 | +1.42 |
| `Q024_fibronectin_fragment_selection` | +1.00 | +2.00 | +1.42 |
| `Q023_homeodomain_heterogeneous_fold` | +1.00 | +1.67 | +1.17 |
| `Q017_prp8_evolutionary_mosaic` | -1.33 | -0.33 | -1.08 |
| `Q025_classification_as_evolutionary_hypothesis` | +0.67 | +1.67 | +0.92 |
| `Q014_paper_example_reclassification_pa1492` | +0.00 | +1.00 | +0.83 |
| `Q021_nad_binding_convergent_evolution` | +0.67 | +1.00 | +0.75 |
| `Q026_protein_kinase_like_function_transfer` | +0.67 | +0.67 | +0.67 |
| `Q006_ubiquitin_like_function_divergence` | +0.33 | +0.67 | +0.42 |
| `Q022_colg_crya_remote_homology` | +0.00 | +0.67 | +0.33 |
| `Q016_pdb_1acc_domain_split` | +0.00 | +0.67 | +0.33 |
| `Q001_histone_related_proteins` | +0.00 | +1.00 | +0.25 |
| `Q005_cupredoxin_function_transfer` | +0.00 | +1.00 | +0.25 |
| `Q007_chey_like_function_divergence` | +0.00 | +1.00 | +0.25 |
| `Q002_mazg_hise_remote_homology` | +0.00 | +0.67 | +0.17 |
| `Q020_syntaxin_fragment_reclassification` | +0.00 | -0.67 | -0.17 |
| `Q003_actin_like_atpase_divergence` | +0.00 | +0.33 | +0.08 |
| `Q008_v1_atp_synthase_bulge_classification` | +0.00 | +0.33 | +0.08 |
| `Q004_ferritin_like_functional_divergence` | -0.33 | +1.00 | +0.00 |

## Skill Effect Over MCP

| Question | Biological delta | Factual delta | Gated delta |
|---|---:|---:|---:|
| `Q025_classification_as_evolutionary_hypothesis` | +1.33 | +0.67 | +1.17 |
| `Q010_rossmann_family_function_divergence` | -0.33 | -1.33 | -1.00 |
| `Q007_chey_like_function_divergence` | +1.00 | +0.33 | +0.83 |
| `Q026_protein_kinase_like_function_transfer` | +1.00 | +0.33 | +0.83 |
| `Q024_fibronectin_fragment_selection` | +0.67 | +1.00 | +0.75 |
| `Q017_prp8_evolutionary_mosaic` | +1.00 | -0.33 | +0.67 |
| `Q016_pdb_1acc_domain_split` | +0.67 | +0.33 | +0.58 |
| `Q020_syntaxin_fragment_reclassification` | -0.67 | +0.00 | -0.50 |
| `Q002_mazg_hise_remote_homology` | +0.33 | +0.67 | +0.42 |
| `Q012_not_true_family_specific_pair` | -0.33 | -0.33 | -0.33 |
| `Q004_ferritin_like_functional_divergence` | +0.33 | +0.33 | +0.33 |
| `Q009_heme_bundle_fold_not_homology` | +0.33 | -0.33 | +0.17 |
| `Q003_actin_like_atpase_divergence` | +0.00 | -0.67 | -0.17 |
| `Q008_v1_atp_synthase_bulge_classification` | +0.33 | -0.33 | +0.17 |
| `Q021_nad_binding_convergent_evolution` | +0.33 | -1.00 | -0.17 |
| `Q022_colg_crya_remote_homology` | +0.00 | +0.67 | +0.17 |
| `Q006_ubiquitin_like_function_divergence` | +0.00 | +0.33 | +0.08 |
| `Q014_paper_example_reclassification_pa1492` | +0.00 | +0.33 | +0.08 |
| `Q001_histone_related_proteins` | +0.00 | +0.00 | +0.00 |
| `Q005_cupredoxin_function_transfer` | +0.00 | +0.00 | +0.00 |
| `Q015_artifact_tag_not_homology` | +0.00 | +0.00 | +0.00 |
| `Q018_tal_effector_repeat_lengths` | +0.33 | -0.33 | +0.00 |
| `Q023_homeodomain_heterogeneous_fold` | +0.00 | +0.00 | +0.00 |

## MCP Effect Over API

| Question | Biological delta | Factual delta | Gated delta |
|---|---:|---:|---:|
| `Q009_heme_bundle_fold_not_homology` | +1.33 | +1.67 | +1.58 |
| `Q010_rossmann_family_function_divergence` | -0.33 | +2.00 | +1.50 |
| `Q015_artifact_tag_not_homology` | +0.00 | +2.00 | +1.33 |
| `Q023_homeodomain_heterogeneous_fold` | +1.00 | +1.67 | +1.17 |
| `Q007_chey_like_function_divergence` | -1.00 | +0.00 | -0.75 |
| `Q014_paper_example_reclassification_pa1492` | +0.00 | +0.67 | +0.75 |
| `Q024_fibronectin_fragment_selection` | +0.33 | +1.33 | +0.75 |
| `Q025_classification_as_evolutionary_hypothesis` | -1.00 | +0.00 | -0.75 |
| `Q016_pdb_1acc_domain_split` | -0.67 | -0.67 | -0.67 |
| `Q018_tal_effector_repeat_lengths` | +1.33 | +0.33 | +0.67 |
| `Q017_prp8_evolutionary_mosaic` | -1.00 | +0.67 | -0.58 |
| `Q021_nad_binding_convergent_evolution` | +0.33 | +1.00 | +0.50 |
| `Q004_ferritin_like_functional_divergence` | -0.67 | +0.33 | -0.42 |
| `Q002_mazg_hise_remote_homology` | -0.33 | -0.33 | -0.33 |
| `Q008_v1_atp_synthase_bulge_classification` | -0.33 | -0.33 | -0.33 |
| `Q020_syntaxin_fragment_reclassification` | +0.33 | +0.00 | +0.25 |
| `Q022_colg_crya_remote_homology` | +0.00 | +0.00 | +0.17 |
| `Q026_protein_kinase_like_function_transfer` | -0.33 | +0.33 | -0.17 |
| `Q001_histone_related_proteins` | -0.33 | +0.33 | -0.17 |
| `Q003_actin_like_atpase_divergence` | +0.00 | +0.33 | +0.08 |
| `Q005_cupredoxin_function_transfer` | -0.33 | +0.67 | -0.08 |
| `Q012_not_true_family_specific_pair` | +0.00 | +0.33 | +0.08 |
| `Q006_ubiquitin_like_function_divergence` | +0.00 | +0.00 | +0.00 |

## Error Tags By Condition

| Condition | Error tag | Count |
|---|---|---:|
| `pretrained_only` | `incomplete_answer` | 37 |
| `pretrained_only` | `identifier_or_boundary_error` | 20 |
| `pretrained_only` | `wrong_classification` | 20 |
| `pretrained_only` | `missing_required_external_evidence` | 16 |
| `pretrained_only` | `api_evidence_error` | 12 |
| `pretrained_only` | `missing_annotation_caveat` | 12 |
| `pretrained_only` | `historical_release_omission` | 9 |
| `pretrained_only` | `unsupported_nonhomology_claim` | 8 |
| `pretrained_only` | `unsafe_function_transfer` | 6 |
| `pretrained_only` | `unsupported_homology_claim` | 6 |
| `pretrained_only` | `missing_named_unsafe_transfer` | 3 |
| `pretrained_only` | `periodic_release_omission` | 3 |
| `pretrained_only` | `missing_quality_context` | 2 |
| `pretrained_only` | `fabricated_release_fact` | 1 |
| `pretrained_api` | `api_evidence_error` | 48 |
| `pretrained_api` | `missing_annotation_caveat` | 9 |
| `pretrained_api` | `missing_required_external_evidence` | 9 |
| `pretrained_api` | `incomplete_answer` | 6 |
| `pretrained_api` | `fabricated_release_fact` | 5 |
| `pretrained_api` | `unsupported_nonhomology_claim` | 5 |
| `pretrained_api` | `periodic_release_omission` | 4 |
| `pretrained_api` | `wrong_classification` | 4 |
| `pretrained_api` | `identifier_or_boundary_error` | 3 |
| `pretrained_api` | `missing_named_unsafe_transfer` | 3 |
| `pretrained_api` | `historical_release_omission` | 2 |
| `pretrained_api` | `missing_quality_context` | 1 |
| `pretrained_api` | `unsafe_function_transfer` | 1 |
| `pretrained_skill` | `api_evidence_error` | 7 |
| `pretrained_skill` | `missing_required_external_evidence` | 7 |
| `pretrained_skill` | `incomplete_answer` | 4 |
| `pretrained_skill` | `missing_named_unsafe_transfer` | 4 |
| `pretrained_skill` | `periodic_release_omission` | 3 |
| `pretrained_skill` | `identifier_or_boundary_error` | 2 |
| `pretrained_skill` | `fabricated_release_fact` | 1 |
| `pretrained_skill` | `missing_annotation_caveat` | 1 |
| `pretrained_skill` | `missing_quality_context` | 1 |
| `pretrained_skill` | `wrong_classification` | 1 |
| `pretrained_mcp` | `api_evidence_error` | 26 |
| `pretrained_mcp` | `missing_required_external_evidence` | 13 |
| `pretrained_mcp` | `unsupported_nonhomology_claim` | 7 |
| `pretrained_mcp` | `incomplete_answer` | 6 |
| `pretrained_mcp` | `identifier_or_boundary_error` | 4 |
| `pretrained_mcp` | `missing_named_unsafe_transfer` | 4 |
| `pretrained_mcp` | `historical_release_omission` | 3 |
| `pretrained_mcp` | `missing_annotation_caveat` | 3 |
| `pretrained_mcp` | `missing_quality_context` | 3 |
| `pretrained_mcp` | `periodic_release_omission` | 3 |
| `pretrained_mcp` | `wrong_classification` | 3 |
| `pretrained_mcp` | `fabricated_annotation` | 1 |
| `pretrained_mcp` | `unsafe_function_transfer` | 1 |
| `pretrained_mcp` | `unsupported_homology_claim` | 1 |
| `pretrained_mcp_skill` | `api_evidence_error` | 30 |
| `pretrained_mcp_skill` | `missing_required_external_evidence` | 7 |
| `pretrained_mcp_skill` | `missing_named_unsafe_transfer` | 5 |
| `pretrained_mcp_skill` | `incomplete_answer` | 4 |
| `pretrained_mcp_skill` | `historical_release_omission` | 3 |
| `pretrained_mcp_skill` | `missing_annotation_caveat` | 3 |
| `pretrained_mcp_skill` | `periodic_release_omission` | 3 |
| `pretrained_mcp_skill` | `fabricated_release_fact` | 2 |
| `pretrained_mcp_skill` | `identifier_or_boundary_error` | 2 |
| `pretrained_mcp_skill` | `fabricated_annotation` | 1 |
| `pretrained_mcp_skill` | `missing_quality_context` | 1 |
| `pretrained_mcp_skill` | `unsafe_function_transfer` | 1 |
| `pretrained_mcp_skill` | `wrong_classification` | 1 |
