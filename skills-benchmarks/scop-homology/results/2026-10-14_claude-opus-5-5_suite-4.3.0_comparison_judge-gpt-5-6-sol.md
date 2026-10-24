# SCOPe Benchmark Comparison

- Suite: `4.3.0`
- Answer model: `claude-opus-5-5`
- Evaluator: `openai/gpt-5.6-sol`
- Replicates per condition: 3
- Harness: claude-code-cli-isolated; one fresh session per question

## Aggregate Results

| Condition | Biological reasoning | Factual support | Secondary gated | Run-level gated SD |
|---|---:|---:|---:|---:|
| `pretrained_only` | 3.14 | 2.23 | 2.77 | 0.13 |
| `pretrained_api` | 3.41 | 2.68 | 3.14 | 0.07 |
| `pretrained_skill` | 3.93 | 3.77 | 3.83 | 0.11 |
| `pretrained_mcp` | 3.75 | 3.65 | 3.71 | 0.05 |
| `pretrained_mcp_skill` | 3.90 | 3.71 | 3.83 | 0.12 |

## Skill Effect Over API

| Question | Biological delta | Factual delta | Gated delta |
|---|---:|---:|---:|
| `Q012_not_true_family_specific_pair` | +2.00 | +2.67 | +2.17 |
| `Q018_tal_effector_repeat_lengths` | +2.00 | +2.33 | +2.08 |
| `Q015_artifact_tag_not_homology` | +1.33 | +2.00 | +1.50 |
| `Q016_pdb_1acc_domain_split` | +1.33 | +1.00 | +1.25 |
| `Q023_homeodomain_heterogeneous_fold` | +1.33 | +1.00 | +1.25 |
| `Q020_syntaxin_fragment_reclassification` | +1.33 | +0.67 | +1.17 |
| `Q025_classification_as_evolutionary_hypothesis` | +1.00 | +1.00 | +1.00 |
| `Q001_histone_related_proteins` | +0.67 | +1.00 | +0.75 |
| `Q024_fibronectin_fragment_selection` | +0.00 | +1.67 | +0.75 |
| `Q006_ubiquitin_like_function_divergence` | +0.33 | +1.33 | +0.58 |
| `Q002_mazg_hise_remote_homology` | +0.00 | +1.33 | +0.50 |
| `Q008_v1_atp_synthase_bulge_classification` | +0.33 | +0.67 | +0.42 |
| `Q005_cupredoxin_function_transfer` | +0.00 | +1.00 | +0.42 |
| `Q021_nad_binding_convergent_evolution` | +0.33 | +0.33 | +0.33 |
| `Q003_actin_like_atpase_divergence` | +0.00 | +1.00 | +0.25 |
| `Q004_ferritin_like_functional_divergence` | +0.00 | +1.00 | +0.25 |
| `Q010_rossmann_family_function_divergence` | +0.00 | +1.00 | +0.25 |
| `Q014_paper_example_reclassification_pa1492` | +0.00 | +1.00 | +0.25 |
| `Q022_colg_crya_remote_homology` | +0.00 | +1.00 | +0.25 |
| `Q026_protein_kinase_like_function_transfer` | +0.00 | +1.00 | +0.25 |
| `Q009_heme_bundle_fold_not_homology` | +0.00 | +0.67 | +0.17 |
| `Q017_prp8_evolutionary_mosaic` | +0.00 | +0.33 | +0.08 |
| `Q007_chey_like_function_divergence` | +0.00 | +0.00 | +0.00 |

## Skill Effect Over MCP

| Question | Biological delta | Factual delta | Gated delta |
|---|---:|---:|---:|
| `Q012_not_true_family_specific_pair` | +1.33 | +1.33 | +1.33 |
| `Q004_ferritin_like_functional_divergence` | +1.00 | +0.67 | +0.92 |
| `Q001_histone_related_proteins` | +0.67 | +0.33 | +0.58 |
| `Q008_v1_atp_synthase_bulge_classification` | +0.00 | -0.67 | -0.58 |
| `Q023_homeodomain_heterogeneous_fold` | +0.00 | +0.67 | +0.58 |
| `Q016_pdb_1acc_domain_split` | +0.67 | +0.00 | +0.50 |
| `Q009_heme_bundle_fold_not_homology` | -0.33 | +0.00 | -0.25 |
| `Q015_artifact_tag_not_homology` | +0.00 | -0.67 | -0.17 |
| `Q006_ubiquitin_like_function_divergence` | +0.00 | -0.33 | -0.08 |
| `Q002_mazg_hise_remote_homology` | +0.00 | +0.00 | +0.00 |
| `Q003_actin_like_atpase_divergence` | +0.00 | +0.00 | +0.00 |
| `Q005_cupredoxin_function_transfer` | +0.00 | +0.00 | +0.00 |
| `Q007_chey_like_function_divergence` | +0.00 | +0.00 | +0.00 |
| `Q010_rossmann_family_function_divergence` | +0.00 | +0.00 | +0.00 |
| `Q014_paper_example_reclassification_pa1492` | +0.00 | +0.00 | +0.00 |
| `Q017_prp8_evolutionary_mosaic` | +0.00 | +0.00 | +0.00 |
| `Q018_tal_effector_repeat_lengths` | +0.00 | +0.00 | +0.00 |
| `Q020_syntaxin_fragment_reclassification` | +0.00 | +0.00 | +0.00 |
| `Q021_nad_binding_convergent_evolution` | +0.00 | +0.00 | +0.00 |
| `Q022_colg_crya_remote_homology` | +0.00 | +0.00 | +0.00 |
| `Q024_fibronectin_fragment_selection` | +0.00 | +0.00 | +0.00 |
| `Q025_classification_as_evolutionary_hypothesis` | +0.00 | +0.00 | +0.00 |
| `Q026_protein_kinase_like_function_transfer` | +0.00 | +0.00 | +0.00 |

## MCP Effect Over API

| Question | Biological delta | Factual delta | Gated delta |
|---|---:|---:|---:|
| `Q018_tal_effector_repeat_lengths` | +2.00 | +2.33 | +2.08 |
| `Q023_homeodomain_heterogeneous_fold` | +1.33 | +2.00 | +1.92 |
| `Q015_artifact_tag_not_homology` | +1.33 | +2.00 | +1.50 |
| `Q020_syntaxin_fragment_reclassification` | +1.33 | +0.67 | +1.17 |
| `Q008_v1_atp_synthase_bulge_classification` | +0.33 | +1.33 | +1.00 |
| `Q025_classification_as_evolutionary_hypothesis` | +1.00 | +1.00 | +1.00 |
| `Q024_fibronectin_fragment_selection` | +0.00 | +1.67 | +0.75 |
| `Q004_ferritin_like_functional_divergence` | -1.00 | +0.33 | -0.67 |
| `Q006_ubiquitin_like_function_divergence` | +0.33 | +1.33 | +0.58 |
| `Q017_prp8_evolutionary_mosaic` | +0.67 | +0.33 | +0.58 |
| `Q002_mazg_hise_remote_homology` | +0.00 | +1.33 | +0.50 |
| `Q016_pdb_1acc_domain_split` | +0.67 | +0.00 | +0.50 |
| `Q005_cupredoxin_function_transfer` | +0.00 | +1.00 | +0.42 |
| `Q021_nad_binding_convergent_evolution` | +0.33 | +0.33 | +0.33 |
| `Q003_actin_like_atpase_divergence` | +0.00 | +1.00 | +0.25 |
| `Q014_paper_example_reclassification_pa1492` | +0.00 | +1.00 | +0.25 |
| `Q022_colg_crya_remote_homology` | +0.00 | +1.00 | +0.25 |
| `Q026_protein_kinase_like_function_transfer` | +0.00 | +1.00 | +0.25 |
| `Q012_not_true_family_specific_pair` | +0.00 | +0.67 | +0.17 |
| `Q001_histone_related_proteins` | +0.00 | +0.67 | +0.17 |
| `Q010_rossmann_family_function_divergence` | +0.00 | +0.67 | +0.17 |
| `Q009_heme_bundle_fold_not_homology` | -0.33 | +0.67 | -0.08 |
| `Q007_chey_like_function_divergence` | +0.00 | +0.00 | +0.00 |

## Error Tags By Condition

| Condition | Error tag | Count |
|---|---|---:|
| `pretrained_only` | `identifier_or_boundary_error` | 24 |
| `pretrained_only` | `missing_required_external_evidence` | 17 |
| `pretrained_only` | `incomplete_answer` | 16 |
| `pretrained_only` | `api_evidence_error` | 15 |
| `pretrained_only` | `wrong_classification` | 14 |
| `pretrained_only` | `unsupported_nonhomology_claim` | 10 |
| `pretrained_only` | `historical_release_omission` | 8 |
| `pretrained_only` | `missing_annotation_caveat` | 6 |
| `pretrained_only` | `fabricated_release_fact` | 5 |
| `pretrained_only` | `periodic_release_omission` | 3 |
| `pretrained_only` | `quality_ranking_error` | 2 |
| `pretrained_only` | `fragment_similarity_omission` | 1 |
| `pretrained_only` | `unsafe_function_transfer` | 1 |
| `pretrained_only` | `unsupported_homology_claim` | 1 |
| `pretrained_api` | `api_evidence_error` | 49 |
| `pretrained_api` | `fabricated_release_fact` | 9 |
| `pretrained_api` | `unsupported_nonhomology_claim` | 8 |
| `pretrained_api` | `missing_annotation_caveat` | 6 |
| `pretrained_api` | `identifier_or_boundary_error` | 5 |
| `pretrained_api` | `missing_required_external_evidence` | 5 |
| `pretrained_api` | `wrong_classification` | 5 |
| `pretrained_api` | `fabricated_annotation` | 3 |
| `pretrained_api` | `fragment_similarity_omission` | 2 |
| `pretrained_api` | `unsafe_function_transfer` | 2 |
| `pretrained_api` | `periodic_release_omission` | 1 |
| `pretrained_api` | `quality_ranking_error` | 1 |
| `pretrained_skill` | `missing_required_external_evidence` | 4 |
| `pretrained_skill` | `api_evidence_error` | 3 |
| `pretrained_skill` | `fabricated_release_fact` | 3 |
| `pretrained_skill` | `periodic_release_omission` | 3 |
| `pretrained_skill` | `quality_ranking_error` | 2 |
| `pretrained_skill` | `wrong_classification` | 2 |
| `pretrained_mcp` | `api_evidence_error` | 8 |
| `pretrained_mcp` | `missing_required_external_evidence` | 4 |
| `pretrained_mcp` | `unsupported_nonhomology_claim` | 4 |
| `pretrained_mcp` | `missing_annotation_caveat` | 3 |
| `pretrained_mcp` | `wrong_classification` | 3 |
| `pretrained_mcp` | `unsafe_function_transfer` | 2 |
| `pretrained_mcp` | `fabricated_release_fact` | 1 |
| `pretrained_mcp` | `identifier_or_boundary_error` | 1 |
| `pretrained_mcp` | `periodic_release_omission` | 1 |
| `pretrained_mcp` | `quality_ranking_error` | 1 |
| `pretrained_mcp_skill` | `api_evidence_error` | 13 |
| `pretrained_mcp_skill` | `missing_required_external_evidence` | 5 |
| `pretrained_mcp_skill` | `periodic_release_omission` | 2 |
| `pretrained_mcp_skill` | `fabricated_release_fact` | 1 |
| `pretrained_mcp_skill` | `missing_annotation_caveat` | 1 |
