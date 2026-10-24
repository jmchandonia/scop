# SCOPe Benchmark Comparison

- Suite: `4.3.0`
- Answer model: `claude-fable-5-1`
- Evaluator: `openai/gpt-5.6-sol`
- Replicates per condition: 3
- Harness: claude-code-cli-isolated; one fresh session per question

## Aggregate Results

| Condition | Biological reasoning | Factual support | Secondary gated | Run-level gated SD |
|---|---:|---:|---:|---:|
| `pretrained_only` | 2.46 | 1.14 | 1.84 | 0.04 |
| `pretrained_api` | 3.38 | 2.43 | 2.97 | 0.03 |
| `pretrained_skill` | 3.93 | 3.67 | 3.81 | 0.02 |
| `pretrained_mcp` | 3.67 | 3.04 | 3.37 | 0.09 |
| `pretrained_mcp_skill` | 3.88 | 2.99 | 3.60 | 0.06 |

## Skill Effect Over API

| Question | Biological delta | Factual delta | Gated delta |
|---|---:|---:|---:|
| `Q007_chey_like_function_divergence` | +1.00 | +3.00 | +2.00 |
| `Q012_not_true_family_specific_pair` | +2.00 | +2.00 | +2.00 |
| `Q018_tal_effector_repeat_lengths` | +2.00 | +2.00 | +2.00 |
| `Q021_nad_binding_convergent_evolution` | +2.00 | +2.00 | +2.00 |
| `Q015_artifact_tag_not_homology` | +0.67 | +2.33 | +1.50 |
| `Q024_fibronectin_fragment_selection` | +0.00 | +2.00 | +1.25 |
| `Q006_ubiquitin_like_function_divergence` | +1.00 | +1.33 | +1.08 |
| `Q009_heme_bundle_fold_not_homology` | +1.00 | +1.33 | +1.08 |
| `Q020_syntaxin_fragment_reclassification` | +1.00 | +0.67 | +1.08 |
| `Q001_histone_related_proteins` | +1.00 | +1.00 | +1.00 |
| `Q004_ferritin_like_functional_divergence` | +0.67 | +1.00 | +0.75 |
| `Q022_colg_crya_remote_homology` | -0.33 | +1.67 | +0.75 |
| `Q014_paper_example_reclassification_pa1492` | +0.00 | +1.33 | +0.50 |
| `Q016_pdb_1acc_domain_split` | +0.67 | +0.00 | +0.50 |
| `Q008_v1_atp_synthase_bulge_classification` | +0.33 | +0.67 | +0.42 |
| `Q002_mazg_hise_remote_homology` | +0.00 | +1.00 | +0.25 |
| `Q003_actin_like_atpase_divergence` | +0.00 | +1.00 | +0.25 |
| `Q010_rossmann_family_function_divergence` | +0.00 | +1.00 | +0.25 |
| `Q026_protein_kinase_like_function_transfer` | +0.00 | +1.00 | +0.25 |
| `Q017_prp8_evolutionary_mosaic` | +0.00 | +0.67 | +0.17 |
| `Q025_classification_as_evolutionary_hypothesis` | +0.00 | +0.33 | +0.08 |
| `Q005_cupredoxin_function_transfer` | -0.33 | +1.00 | +0.00 |
| `Q023_homeodomain_heterogeneous_fold` | +0.00 | +0.00 | +0.00 |

## Skill Effect Over MCP

| Question | Biological delta | Factual delta | Gated delta |
|---|---:|---:|---:|
| `Q012_not_true_family_specific_pair` | +1.67 | +1.33 | +1.58 |
| `Q023_homeodomain_heterogeneous_fold` | +0.00 | +1.33 | +1.17 |
| `Q021_nad_binding_convergent_evolution` | +1.33 | +0.00 | +1.00 |
| `Q015_artifact_tag_not_homology` | +0.00 | +1.67 | +0.83 |
| `Q020_syntaxin_fragment_reclassification` | +0.67 | +0.67 | +0.83 |
| `Q024_fibronectin_fragment_selection` | +0.00 | +1.00 | +0.83 |
| `Q001_histone_related_proteins` | +1.00 | -0.67 | +0.58 |
| `Q022_colg_crya_remote_homology` | -0.33 | -0.67 | -0.58 |
| `Q005_cupredoxin_function_transfer` | -0.33 | -1.00 | -0.50 |
| `Q004_ferritin_like_functional_divergence` | +0.67 | -0.33 | +0.42 |
| `Q006_ubiquitin_like_function_divergence` | -0.33 | -0.67 | -0.42 |
| `Q026_protein_kinase_like_function_transfer` | +0.00 | -1.00 | -0.25 |
| `Q002_mazg_hise_remote_homology` | +0.00 | -0.67 | -0.17 |
| `Q008_v1_atp_synthase_bulge_classification` | +0.00 | +0.67 | +0.17 |
| `Q003_actin_like_atpase_divergence` | +0.00 | -0.33 | -0.08 |
| `Q007_chey_like_function_divergence` | +0.33 | -0.67 | +0.08 |
| `Q010_rossmann_family_function_divergence` | +0.00 | -0.33 | -0.08 |
| `Q017_prp8_evolutionary_mosaic` | +0.00 | -0.33 | -0.08 |
| `Q018_tal_effector_repeat_lengths` | +0.33 | -0.67 | +0.08 |
| `Q025_classification_as_evolutionary_hypothesis` | +0.00 | -0.33 | -0.08 |
| `Q009_heme_bundle_fold_not_homology` | +0.00 | -0.33 | -0.08 |
| `Q014_paper_example_reclassification_pa1492` | +0.00 | +0.00 | +0.00 |
| `Q016_pdb_1acc_domain_split` | +0.00 | +0.00 | +0.00 |

## MCP Effect Over API

| Question | Biological delta | Factual delta | Gated delta |
|---|---:|---:|---:|
| `Q007_chey_like_function_divergence` | +0.67 | +2.67 | +1.67 |
| `Q018_tal_effector_repeat_lengths` | +1.67 | +1.67 | +1.67 |
| `Q006_ubiquitin_like_function_divergence` | +1.00 | +1.00 | +1.00 |
| `Q009_heme_bundle_fold_not_homology` | +1.00 | +1.00 | +1.00 |
| `Q021_nad_binding_convergent_evolution` | +0.33 | +1.67 | +0.67 |
| `Q015_artifact_tag_not_homology` | +0.67 | +0.67 | +0.67 |
| `Q022_colg_crya_remote_homology` | +0.00 | +1.00 | +0.67 |
| `Q002_mazg_hise_remote_homology` | +0.00 | +1.00 | +0.25 |
| `Q005_cupredoxin_function_transfer` | +0.00 | +1.00 | +0.25 |
| `Q008_v1_atp_synthase_bulge_classification` | +0.33 | +0.00 | +0.25 |
| `Q014_paper_example_reclassification_pa1492` | +0.00 | +0.33 | +0.25 |
| `Q016_pdb_1acc_domain_split` | +0.67 | -1.00 | -0.25 |
| `Q020_syntaxin_fragment_reclassification` | +0.33 | +0.00 | +0.25 |
| `Q026_protein_kinase_like_function_transfer` | +0.00 | +1.00 | +0.25 |
| `Q001_histone_related_proteins` | +0.00 | +0.67 | +0.17 |
| `Q003_actin_like_atpase_divergence` | +0.00 | +0.33 | +0.08 |
| `Q004_ferritin_like_functional_divergence` | +0.00 | +0.33 | +0.08 |
| `Q010_rossmann_family_function_divergence` | +0.00 | +0.33 | +0.08 |
| `Q017_prp8_evolutionary_mosaic` | +0.00 | +0.33 | +0.08 |
| `Q012_not_true_family_specific_pair` | +0.00 | +0.00 | +0.00 |
| `Q023_homeodomain_heterogeneous_fold` | +0.00 | +0.00 | +0.00 |
| `Q024_fibronectin_fragment_selection` | +0.00 | +0.00 | +0.00 |
| `Q025_classification_as_evolutionary_hypothesis` | +0.00 | +0.00 | +0.00 |

## Error Tags By Condition

| Condition | Error tag | Count |
|---|---|---:|
| `pretrained_only` | `api_evidence_error` | 56 |
| `pretrained_only` | `wrong_classification` | 48 |
| `pretrained_only` | `fabricated_release_fact` | 34 |
| `pretrained_only` | `identifier_or_boundary_error` | 18 |
| `pretrained_only` | `unsupported_nonhomology_claim` | 14 |
| `pretrained_only` | `incomplete_answer` | 11 |
| `pretrained_only` | `missing_annotation_caveat` | 10 |
| `pretrained_only` | `unsafe_function_transfer` | 7 |
| `pretrained_only` | `historical_release_omission` | 6 |
| `pretrained_only` | `missing_required_external_evidence` | 6 |
| `pretrained_only` | `fabricated_annotation` | 4 |
| `pretrained_only` | `unsupported_homology_claim` | 4 |
| `pretrained_only` | `periodic_release_omission` | 3 |
| `pretrained_only` | `quality_ranking_error` | 3 |
| `pretrained_only` | `fragment_similarity_omission` | 1 |
| `pretrained_api` | `api_evidence_error` | 66 |
| `pretrained_api` | `missing_required_external_evidence` | 16 |
| `pretrained_api` | `unsupported_nonhomology_claim` | 11 |
| `pretrained_api` | `fabricated_release_fact` | 10 |
| `pretrained_api` | `wrong_classification` | 8 |
| `pretrained_api` | `missing_annotation_caveat` | 6 |
| `pretrained_api` | `unsafe_function_transfer` | 6 |
| `pretrained_api` | `quality_ranking_error` | 4 |
| `pretrained_api` | `incomplete_answer` | 2 |
| `pretrained_api` | `fragment_similarity_omission` | 1 |
| `pretrained_api` | `identifier_or_boundary_error` | 1 |
| `pretrained_skill` | `api_evidence_error` | 13 |
| `pretrained_skill` | `missing_required_external_evidence` | 6 |
| `pretrained_skill` | `quality_ranking_error` | 6 |
| `pretrained_skill` | `fabricated_release_fact` | 3 |
| `pretrained_skill` | `periodic_release_omission` | 1 |
| `pretrained_mcp` | `api_evidence_error` | 34 |
| `pretrained_mcp` | `missing_required_external_evidence` | 12 |
| `pretrained_mcp` | `unsupported_nonhomology_claim` | 11 |
| `pretrained_mcp` | `fabricated_release_fact` | 5 |
| `pretrained_mcp` | `quality_ranking_error` | 4 |
| `pretrained_mcp` | `historical_release_omission` | 3 |
| `pretrained_mcp` | `missing_annotation_caveat` | 3 |
| `pretrained_mcp` | `identifier_or_boundary_error` | 2 |
| `pretrained_mcp` | `wrong_classification` | 2 |
| `pretrained_mcp` | `fabricated_annotation` | 1 |
| `pretrained_mcp` | `missing_named_unsafe_transfer` | 1 |
| `pretrained_mcp` | `unsafe_function_transfer` | 1 |
| `pretrained_mcp_skill` | `api_evidence_error` | 55 |
| `pretrained_mcp_skill` | `missing_required_external_evidence` | 9 |
| `pretrained_mcp_skill` | `fabricated_release_fact` | 3 |
| `pretrained_mcp_skill` | `quality_ranking_error` | 3 |
| `pretrained_mcp_skill` | `identifier_or_boundary_error` | 2 |
| `pretrained_mcp_skill` | `historical_release_omission` | 1 |
| `pretrained_mcp_skill` | `unsupported_nonhomology_claim` | 1 |
