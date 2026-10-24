# SCOPe Benchmark Comparison

- Suite: `4.3.0`
- Answer model: `claude-fable-5-1`
- Evaluator: `anthropic/claude-fable-5-1`
- Replicates per condition: 3
- Harness: claude-code-cli-isolated; one fresh session per question

## Aggregate Results

| Condition | Biological reasoning | Factual support | Secondary gated | Run-level gated SD |
|---|---:|---:|---:|---:|
| `pretrained_only` | 2.48 | 1.51 | 2.15 | 0.03 |
| `pretrained_api` | 3.72 | 3.33 | 3.58 | 0.03 |
| `pretrained_skill` | 4.00 | 3.97 | 3.99 | 0.01 |
| `pretrained_mcp` | 3.86 | 3.61 | 3.79 | 0.02 |
| `pretrained_mcp_skill` | 4.00 | 3.83 | 3.96 | 0.01 |

## Skill Effect Over API

| Question | Biological delta | Factual delta | Gated delta |
|---|---:|---:|---:|
| `Q018_tal_effector_repeat_lengths` | +2.00 | +2.33 | +2.08 |
| `Q012_not_true_family_specific_pair` | +2.00 | +2.00 | +2.00 |
| `Q007_chey_like_function_divergence` | +0.00 | +2.33 | +1.42 |
| `Q020_syntaxin_fragment_reclassification` | +0.67 | +1.00 | +0.75 |
| `Q021_nad_binding_convergent_evolution` | +0.33 | +1.33 | +0.58 |
| `Q024_fibronectin_fragment_selection` | +0.00 | +1.33 | +0.50 |
| `Q006_ubiquitin_like_function_divergence` | +0.33 | +0.67 | +0.42 |
| `Q009_heme_bundle_fold_not_homology` | +0.33 | +0.67 | +0.42 |
| `Q023_homeodomain_heterogeneous_fold` | +0.33 | +0.33 | +0.33 |
| `Q004_ferritin_like_functional_divergence` | +0.33 | +0.00 | +0.25 |
| `Q022_colg_crya_remote_homology` | +0.00 | +1.00 | +0.25 |
| `Q005_cupredoxin_function_transfer` | +0.00 | +0.67 | +0.17 |
| `Q001_histone_related_proteins` | +0.00 | +0.33 | +0.08 |
| `Q015_artifact_tag_not_homology` | +0.00 | +0.33 | +0.08 |
| `Q026_protein_kinase_like_function_transfer` | +0.00 | +0.33 | +0.08 |
| `Q002_mazg_hise_remote_homology` | +0.00 | +0.00 | +0.00 |
| `Q003_actin_like_atpase_divergence` | +0.00 | +0.00 | +0.00 |
| `Q008_v1_atp_synthase_bulge_classification` | +0.00 | +0.00 | +0.00 |
| `Q010_rossmann_family_function_divergence` | +0.00 | +0.00 | +0.00 |
| `Q014_paper_example_reclassification_pa1492` | +0.00 | +0.00 | +0.00 |
| `Q016_pdb_1acc_domain_split` | +0.00 | +0.00 | +0.00 |
| `Q017_prp8_evolutionary_mosaic` | +0.00 | +0.00 | +0.00 |
| `Q025_classification_as_evolutionary_hypothesis` | +0.00 | +0.00 | +0.00 |

## Skill Effect Over MCP

| Question | Biological delta | Factual delta | Gated delta |
|---|---:|---:|---:|
| `Q012_not_true_family_specific_pair` | +2.00 | +2.00 | +2.00 |
| `Q022_colg_crya_remote_homology` | +0.67 | +0.67 | +0.67 |
| `Q021_nad_binding_convergent_evolution` | +0.33 | +0.00 | +0.25 |
| `Q023_homeodomain_heterogeneous_fold` | +0.33 | +0.00 | +0.25 |
| `Q003_actin_like_atpase_divergence` | +0.00 | -0.67 | -0.17 |
| `Q009_heme_bundle_fold_not_homology` | +0.00 | +0.67 | +0.17 |
| `Q014_paper_example_reclassification_pa1492` | +0.00 | +0.67 | +0.17 |
| `Q024_fibronectin_fragment_selection` | +0.00 | +0.67 | +0.17 |
| `Q025_classification_as_evolutionary_hypothesis` | +0.00 | -0.67 | -0.17 |
| `Q008_v1_atp_synthase_bulge_classification` | +0.00 | +0.33 | +0.08 |
| `Q017_prp8_evolutionary_mosaic` | +0.00 | +0.33 | +0.08 |
| `Q018_tal_effector_repeat_lengths` | +0.00 | +0.33 | +0.08 |
| `Q020_syntaxin_fragment_reclassification` | +0.00 | +0.33 | +0.08 |
| `Q004_ferritin_like_functional_divergence` | +0.00 | +0.33 | +0.08 |
| `Q001_histone_related_proteins` | +0.00 | +0.00 | +0.00 |
| `Q002_mazg_hise_remote_homology` | +0.00 | +0.00 | +0.00 |
| `Q005_cupredoxin_function_transfer` | +0.00 | +0.00 | +0.00 |
| `Q006_ubiquitin_like_function_divergence` | +0.00 | +0.00 | +0.00 |
| `Q007_chey_like_function_divergence` | +0.00 | +0.00 | +0.00 |
| `Q010_rossmann_family_function_divergence` | +0.00 | +0.00 | +0.00 |
| `Q015_artifact_tag_not_homology` | +0.00 | +0.00 | +0.00 |
| `Q016_pdb_1acc_domain_split` | +0.00 | +0.00 | +0.00 |
| `Q026_protein_kinase_like_function_transfer` | +0.00 | +0.00 | +0.00 |

## MCP Effect Over API

| Question | Biological delta | Factual delta | Gated delta |
|---|---:|---:|---:|
| `Q018_tal_effector_repeat_lengths` | +2.00 | +2.00 | +2.00 |
| `Q007_chey_like_function_divergence` | +0.00 | +2.33 | +1.42 |
| `Q020_syntaxin_fragment_reclassification` | +0.67 | +0.67 | +0.67 |
| `Q022_colg_crya_remote_homology` | -0.67 | +0.00 | -0.50 |
| `Q006_ubiquitin_like_function_divergence` | +0.33 | +0.67 | +0.42 |
| `Q009_heme_bundle_fold_not_homology` | +0.33 | +0.00 | +0.25 |
| `Q021_nad_binding_convergent_evolution` | +0.00 | +1.00 | +0.25 |
| `Q024_fibronectin_fragment_selection` | +0.00 | +0.33 | +0.25 |
| `Q005_cupredoxin_function_transfer` | +0.00 | +0.67 | +0.17 |
| `Q014_paper_example_reclassification_pa1492` | +0.00 | -0.67 | -0.17 |
| `Q016_pdb_1acc_domain_split` | +0.00 | -0.67 | -0.17 |
| `Q001_histone_related_proteins` | +0.00 | +0.33 | +0.08 |
| `Q004_ferritin_like_functional_divergence` | +0.33 | -0.67 | +0.08 |
| `Q008_v1_atp_synthase_bulge_classification` | +0.00 | -0.33 | -0.08 |
| `Q015_artifact_tag_not_homology` | +0.00 | +0.33 | +0.08 |
| `Q017_prp8_evolutionary_mosaic` | +0.00 | -0.33 | -0.08 |
| `Q026_protein_kinase_like_function_transfer` | +0.00 | +0.33 | +0.08 |
| `Q023_homeodomain_heterogeneous_fold` | +0.00 | +0.33 | +0.08 |
| `Q002_mazg_hise_remote_homology` | +0.00 | +0.00 | +0.00 |
| `Q003_actin_like_atpase_divergence` | +0.00 | +0.00 | +0.00 |
| `Q010_rossmann_family_function_divergence` | +0.00 | +0.00 | +0.00 |
| `Q012_not_true_family_specific_pair` | +0.00 | +0.00 | +0.00 |
| `Q025_classification_as_evolutionary_hypothesis` | +0.00 | +0.00 | +0.00 |

## Error Tags By Condition

| Condition | Error tag | Count |
|---|---|---:|
| `pretrained_only` | `wrong_classification` | 46 |
| `pretrained_only` | `identifier_or_boundary_error` | 35 |
| `pretrained_only` | `fabricated_release_fact` | 28 |
| `pretrained_only` | `api_evidence_error` | 17 |
| `pretrained_only` | `incomplete_answer` | 13 |
| `pretrained_only` | `historical_release_omission` | 10 |
| `pretrained_only` | `missing_annotation_caveat` | 9 |
| `pretrained_only` | `unsupported_nonhomology_claim` | 5 |
| `pretrained_only` | `missing_quality_context` | 3 |
| `pretrained_only` | `fabricated_annotation` | 2 |
| `pretrained_only` | `fragment_similarity_omission` | 1 |
| `pretrained_api` | `api_evidence_error` | 11 |
| `pretrained_api` | `missing_annotation_caveat` | 7 |
| `pretrained_api` | `wrong_classification` | 7 |
| `pretrained_api` | `identifier_or_boundary_error` | 3 |
| `pretrained_api` | `fabricated_release_fact` | 2 |
| `pretrained_api` | `unsupported_nonhomology_claim` | 2 |
| `pretrained_api` | `fabricated_annotation` | 1 |
| `pretrained_api` | `fragment_similarity_omission` | 1 |
| `pretrained_api` | `unsafe_function_transfer` | 1 |
| `pretrained_skill` | `api_evidence_error` | 2 |
| `pretrained_mcp` | `api_evidence_error` | 9 |
| `pretrained_mcp` | `historical_release_omission` | 4 |
| `pretrained_mcp` | `missing_annotation_caveat` | 3 |
| `pretrained_mcp` | `wrong_classification` | 3 |
| `pretrained_mcp` | `identifier_or_boundary_error` | 2 |
| `pretrained_mcp` | `incomplete_answer` | 1 |
| `pretrained_mcp` | `unsupported_nonhomology_claim` | 1 |
| `pretrained_mcp_skill` | `api_evidence_error` | 7 |
| `pretrained_mcp_skill` | `identifier_or_boundary_error` | 2 |
| `pretrained_mcp_skill` | `historical_release_omission` | 1 |
| `pretrained_mcp_skill` | `wrong_classification` | 1 |
