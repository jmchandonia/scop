# SCOPe Benchmark Comparison

- Suite: `4.3.0`
- Answer model: `claude-sonnet-5-5`
- Evaluator: `openai/gpt-5.6-sol`
- Replicates per condition: 3
- Harness: claude-code-cli-isolated; one fresh session per question

## Aggregate Results

| Condition | Biological reasoning | Factual support | Secondary gated | Run-level gated SD |
|---|---:|---:|---:|---:|
| `pretrained_only` | 3.26 | 1.84 | 2.58 | 0.06 |
| `pretrained_api` | 3.51 | 3.10 | 3.34 | 0.15 |
| `pretrained_skill` | 3.81 | 3.84 | 3.82 | 0.05 |
| `pretrained_mcp` | 3.54 | 3.46 | 3.49 | 0.04 |
| `pretrained_mcp_skill` | 3.64 | 3.67 | 3.64 | 0.06 |

## Skill Effect Over API

| Question | Biological delta | Factual delta | Gated delta |
|---|---:|---:|---:|
| `Q018_tal_effector_repeat_lengths` | +2.00 | +2.00 | +2.00 |
| `Q012_not_true_family_specific_pair` | +1.33 | +2.33 | +1.58 |
| `Q023_homeodomain_heterogeneous_fold` | +1.33 | +2.33 | +1.58 |
| `Q014_paper_example_reclassification_pa1492` | +0.00 | +2.00 | +1.00 |
| `Q015_artifact_tag_not_homology` | +0.00 | +1.67 | +1.00 |
| `Q017_prp8_evolutionary_mosaic` | +1.00 | +0.67 | +0.92 |
| `Q001_histone_related_proteins` | +1.00 | +0.33 | +0.83 |
| `Q010_rossmann_family_function_divergence` | +0.67 | +1.33 | +0.83 |
| `Q021_nad_binding_convergent_evolution` | +0.00 | +1.33 | +0.75 |
| `Q020_syntaxin_fragment_reclassification` | +0.67 | +0.67 | +0.67 |
| `Q022_colg_crya_remote_homology` | -0.67 | -0.67 | -0.67 |
| `Q025_classification_as_evolutionary_hypothesis` | +0.67 | +0.67 | +0.67 |
| `Q003_actin_like_atpase_divergence` | -0.33 | +0.00 | -0.25 |
| `Q004_ferritin_like_functional_divergence` | -0.33 | +0.00 | -0.25 |
| `Q009_heme_bundle_fold_not_homology` | -0.33 | +0.00 | -0.25 |
| `Q024_fibronectin_fragment_selection` | +0.00 | +1.00 | +0.25 |
| `Q002_mazg_hise_remote_homology` | +0.00 | +0.67 | +0.17 |
| `Q007_chey_like_function_divergence` | +0.00 | +0.67 | +0.17 |
| `Q005_cupredoxin_function_transfer` | +0.00 | +0.00 | +0.00 |
| `Q006_ubiquitin_like_function_divergence` | +0.00 | +0.00 | +0.00 |
| `Q008_v1_atp_synthase_bulge_classification` | +0.00 | +0.00 | +0.00 |
| `Q016_pdb_1acc_domain_split` | +0.00 | +0.00 | +0.00 |
| `Q026_protein_kinase_like_function_transfer` | +0.00 | +0.00 | +0.00 |

## Skill Effect Over MCP

| Question | Biological delta | Factual delta | Gated delta |
|---|---:|---:|---:|
| `Q016_pdb_1acc_domain_split` | +1.33 | +0.67 | +1.33 |
| `Q014_paper_example_reclassification_pa1492` | +0.67 | +1.00 | +1.08 |
| `Q017_prp8_evolutionary_mosaic` | +1.00 | +0.33 | +0.83 |
| `Q018_tal_effector_repeat_lengths` | +0.33 | +1.00 | +0.67 |
| `Q025_classification_as_evolutionary_hypothesis` | -0.67 | -0.67 | -0.67 |
| `Q020_syntaxin_fragment_reclassification` | +0.33 | +0.33 | +0.33 |
| `Q022_colg_crya_remote_homology` | -0.33 | -0.33 | -0.33 |
| `Q001_histone_related_proteins` | +0.33 | +0.00 | +0.25 |
| `Q012_not_true_family_specific_pair` | -0.33 | +0.00 | -0.25 |
| `Q015_artifact_tag_not_homology` | +0.00 | +1.00 | +0.25 |
| `Q007_chey_like_function_divergence` | +0.00 | +0.67 | +0.17 |
| `Q009_heme_bundle_fold_not_homology` | +0.00 | -0.67 | -0.17 |
| `Q004_ferritin_like_functional_divergence` | -0.33 | +0.67 | -0.08 |
| `Q006_ubiquitin_like_function_divergence` | +0.00 | +0.33 | +0.08 |
| `Q008_v1_atp_synthase_bulge_classification` | +0.00 | -0.33 | -0.08 |
| `Q010_rossmann_family_function_divergence` | +0.00 | +0.33 | +0.08 |
| `Q023_homeodomain_heterogeneous_fold` | +0.00 | +0.33 | +0.08 |
| `Q002_mazg_hise_remote_homology` | +0.00 | +0.00 | +0.00 |
| `Q003_actin_like_atpase_divergence` | +0.00 | +0.00 | +0.00 |
| `Q005_cupredoxin_function_transfer` | +0.00 | +0.00 | +0.00 |
| `Q021_nad_binding_convergent_evolution` | +0.00 | +0.00 | +0.00 |
| `Q024_fibronectin_fragment_selection` | +0.00 | +0.00 | +0.00 |
| `Q026_protein_kinase_like_function_transfer` | +0.00 | +0.00 | +0.00 |

## MCP Effect Over API

| Question | Biological delta | Factual delta | Gated delta |
|---|---:|---:|---:|
| `Q016_pdb_1acc_domain_split` | -1.33 | -1.33 | -1.50 |
| `Q023_homeodomain_heterogeneous_fold` | +1.33 | +2.00 | +1.50 |
| `Q018_tal_effector_repeat_lengths` | +1.67 | +1.00 | +1.33 |
| `Q010_rossmann_family_function_divergence` | +0.67 | +1.00 | +0.75 |
| `Q015_artifact_tag_not_homology` | +0.00 | +0.67 | +0.75 |
| `Q021_nad_binding_convergent_evolution` | +0.00 | +1.33 | +0.75 |
| `Q025_classification_as_evolutionary_hypothesis` | +0.67 | +0.67 | +0.67 |
| `Q009_heme_bundle_fold_not_homology` | -0.67 | +0.00 | -0.50 |
| `Q004_ferritin_like_functional_divergence` | -0.33 | -0.67 | -0.42 |
| `Q014_paper_example_reclassification_pa1492` | -0.67 | +0.00 | -0.33 |
| `Q020_syntaxin_fragment_reclassification` | +0.33 | +0.33 | +0.33 |
| `Q022_colg_crya_remote_homology` | -0.33 | -0.33 | -0.33 |
| `Q002_mazg_hise_remote_homology` | +0.00 | +1.00 | +0.25 |
| `Q012_not_true_family_specific_pair` | -0.67 | +1.00 | -0.25 |
| `Q024_fibronectin_fragment_selection` | +0.00 | +1.00 | +0.25 |
| `Q017_prp8_evolutionary_mosaic` | +0.00 | +0.67 | +0.17 |
| `Q001_histone_related_proteins` | +0.00 | +0.33 | +0.08 |
| `Q006_ubiquitin_like_function_divergence` | +0.00 | -0.33 | -0.08 |
| `Q003_actin_like_atpase_divergence` | +0.00 | +0.00 | +0.00 |
| `Q005_cupredoxin_function_transfer` | +0.00 | +0.00 | +0.00 |
| `Q007_chey_like_function_divergence` | +0.00 | +0.00 | +0.00 |
| `Q008_v1_atp_synthase_bulge_classification` | +0.00 | +0.00 | +0.00 |
| `Q026_protein_kinase_like_function_transfer` | +0.00 | +0.00 | +0.00 |

## Error Tags By Condition

| Condition | Error tag | Count |
|---|---|---:|
| `pretrained_only` | `identifier_or_boundary_error` | 33 |
| `pretrained_only` | `incomplete_answer` | 23 |
| `pretrained_only` | `wrong_classification` | 21 |
| `pretrained_only` | `api_evidence_error` | 15 |
| `pretrained_only` | `missing_annotation_caveat` | 9 |
| `pretrained_only` | `unsupported_nonhomology_claim` | 8 |
| `pretrained_only` | `historical_release_omission` | 7 |
| `pretrained_only` | `missing_required_external_evidence` | 7 |
| `pretrained_only` | `fabricated_annotation` | 3 |
| `pretrained_only` | `periodic_release_omission` | 3 |
| `pretrained_only` | `fabricated_release_fact` | 1 |
| `pretrained_api` | `api_evidence_error` | 24 |
| `pretrained_api` | `missing_annotation_caveat` | 8 |
| `pretrained_api` | `wrong_classification` | 8 |
| `pretrained_api` | `unsupported_nonhomology_claim` | 7 |
| `pretrained_api` | `historical_release_omission` | 4 |
| `pretrained_api` | `identifier_or_boundary_error` | 4 |
| `pretrained_api` | `fabricated_release_fact` | 3 |
| `pretrained_api` | `incomplete_answer` | 3 |
| `pretrained_api` | `periodic_release_omission` | 3 |
| `pretrained_api` | `missing_named_unsafe_transfer` | 2 |
| `pretrained_api` | `missing_required_external_evidence` | 2 |
| `pretrained_api` | `fabricated_annotation` | 1 |
| `pretrained_api` | `quality_ranking_error` | 1 |
| `pretrained_skill` | `missing_required_external_evidence` | 6 |
| `pretrained_skill` | `missing_named_unsafe_transfer` | 3 |
| `pretrained_skill` | `periodic_release_omission` | 3 |
| `pretrained_skill` | `wrong_classification` | 3 |
| `pretrained_skill` | `incomplete_answer` | 2 |
| `pretrained_skill` | `api_evidence_error` | 1 |
| `pretrained_skill` | `historical_release_omission` | 1 |
| `pretrained_skill` | `missing_annotation_caveat` | 1 |
| `pretrained_mcp` | `api_evidence_error` | 9 |
| `pretrained_mcp` | `unsupported_nonhomology_claim` | 8 |
| `pretrained_mcp` | `wrong_classification` | 7 |
| `pretrained_mcp` | `historical_release_omission` | 6 |
| `pretrained_mcp` | `missing_required_external_evidence` | 6 |
| `pretrained_mcp` | `identifier_or_boundary_error` | 4 |
| `pretrained_mcp` | `missing_annotation_caveat` | 3 |
| `pretrained_mcp` | `periodic_release_omission` | 3 |
| `pretrained_mcp` | `incomplete_answer` | 2 |
| `pretrained_mcp` | `missing_named_unsafe_transfer` | 2 |
| `pretrained_mcp` | `fabricated_annotation` | 1 |
| `pretrained_mcp_skill` | `api_evidence_error` | 10 |
| `pretrained_mcp_skill` | `missing_required_external_evidence` | 8 |
| `pretrained_mcp_skill` | `historical_release_omission` | 3 |
| `pretrained_mcp_skill` | `incomplete_answer` | 3 |
| `pretrained_mcp_skill` | `missing_annotation_caveat` | 3 |
| `pretrained_mcp_skill` | `periodic_release_omission` | 3 |
| `pretrained_mcp_skill` | `unsupported_nonhomology_claim` | 3 |
| `pretrained_mcp_skill` | `missing_named_unsafe_transfer` | 2 |
| `pretrained_mcp_skill` | `identifier_or_boundary_error` | 1 |
