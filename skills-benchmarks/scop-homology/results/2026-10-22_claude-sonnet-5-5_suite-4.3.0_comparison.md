# SCOPe Benchmark Comparison

- Suite: `4.3.0`
- Answer model: `claude-sonnet-5-5`
- Evaluator: `anthropic/claude-fable-5-1`
- Replicates per condition: 3
- Harness: claude-code-cli-isolated; one fresh session per question

## Aggregate Results

| Condition | Biological reasoning | Factual support | Secondary gated | Run-level gated SD |
|---|---:|---:|---:|---:|
| `pretrained_only` | 2.99 | 1.77 | 2.51 | 0.04 |
| `pretrained_api` | 3.57 | 3.29 | 3.50 | 0.09 |
| `pretrained_skill` | 3.93 | 3.87 | 3.91 | 0.03 |
| `pretrained_mcp` | 3.77 | 3.61 | 3.72 | 0.02 |
| `pretrained_mcp_skill` | 3.88 | 3.84 | 3.87 | 0.03 |

## Skill Effect Over API

| Question | Biological delta | Factual delta | Gated delta |
|---|---:|---:|---:|
| `Q020_syntaxin_fragment_reclassification` | +2.00 | +1.33 | +1.83 |
| `Q023_homeodomain_heterogeneous_fold` | +1.33 | +2.00 | +1.50 |
| `Q012_not_true_family_specific_pair` | +1.33 | +1.33 | +1.33 |
| `Q014_paper_example_reclassification_pa1492` | +1.00 | +2.00 | +1.25 |
| `Q018_tal_effector_repeat_lengths` | +1.00 | +2.00 | +1.25 |
| `Q024_fibronectin_fragment_selection` | +1.00 | +2.00 | +1.25 |
| `Q010_rossmann_family_function_divergence` | +0.67 | +1.33 | +0.83 |
| `Q003_actin_like_atpase_divergence` | -0.67 | +0.00 | -0.50 |
| `Q017_prp8_evolutionary_mosaic` | +0.33 | +0.33 | +0.33 |
| `Q004_ferritin_like_functional_divergence` | -0.33 | +0.00 | -0.25 |
| `Q007_chey_like_function_divergence` | +0.00 | +1.00 | +0.25 |
| `Q009_heme_bundle_fold_not_homology` | +0.33 | +0.00 | +0.25 |
| `Q025_classification_as_evolutionary_hypothesis` | +0.33 | +0.00 | +0.25 |
| `Q001_histone_related_proteins` | +0.00 | +0.33 | +0.08 |
| `Q005_cupredoxin_function_transfer` | +0.00 | +0.33 | +0.08 |
| `Q026_protein_kinase_like_function_transfer` | +0.00 | -0.33 | -0.08 |
| `Q021_nad_binding_convergent_evolution` | +0.00 | -0.33 | -0.08 |
| `Q002_mazg_hise_remote_homology` | +0.00 | +0.00 | +0.00 |
| `Q006_ubiquitin_like_function_divergence` | +0.00 | +0.00 | +0.00 |
| `Q008_v1_atp_synthase_bulge_classification` | +0.00 | +0.00 | +0.00 |
| `Q015_artifact_tag_not_homology` | +0.00 | +0.00 | +0.00 |
| `Q016_pdb_1acc_domain_split` | +0.00 | +0.00 | +0.00 |
| `Q022_colg_crya_remote_homology` | +0.00 | +0.00 | +0.00 |

## Skill Effect Over MCP

| Question | Biological delta | Factual delta | Gated delta |
|---|---:|---:|---:|
| `Q014_paper_example_reclassification_pa1492` | +1.33 | +1.67 | +1.42 |
| `Q016_pdb_1acc_domain_split` | +0.33 | +0.67 | +0.58 |
| `Q003_actin_like_atpase_divergence` | +0.67 | +0.00 | +0.50 |
| `Q017_prp8_evolutionary_mosaic` | +0.33 | +0.67 | +0.42 |
| `Q004_ferritin_like_functional_divergence` | -0.67 | +0.67 | -0.33 |
| `Q007_chey_like_function_divergence` | +0.00 | +1.00 | +0.25 |
| `Q020_syntaxin_fragment_reclassification` | +0.33 | +0.00 | +0.25 |
| `Q021_nad_binding_convergent_evolution` | +0.33 | +0.00 | +0.25 |
| `Q006_ubiquitin_like_function_divergence` | +0.00 | +0.33 | +0.08 |
| `Q008_v1_atp_synthase_bulge_classification` | +0.00 | -0.33 | -0.08 |
| `Q018_tal_effector_repeat_lengths` | +0.00 | +0.33 | +0.08 |
| `Q023_homeodomain_heterogeneous_fold` | +0.00 | +0.33 | +0.08 |
| `Q001_histone_related_proteins` | +0.00 | +0.00 | +0.00 |
| `Q002_mazg_hise_remote_homology` | +0.00 | +0.00 | +0.00 |
| `Q005_cupredoxin_function_transfer` | +0.00 | +0.00 | +0.00 |
| `Q009_heme_bundle_fold_not_homology` | +0.00 | +0.00 | +0.00 |
| `Q010_rossmann_family_function_divergence` | +0.00 | +0.00 | +0.00 |
| `Q012_not_true_family_specific_pair` | +0.00 | +0.00 | +0.00 |
| `Q015_artifact_tag_not_homology` | +0.00 | +0.00 | +0.00 |
| `Q022_colg_crya_remote_homology` | +0.00 | +0.00 | +0.00 |
| `Q024_fibronectin_fragment_selection` | +0.00 | +0.00 | +0.00 |
| `Q025_classification_as_evolutionary_hypothesis` | +0.00 | +0.00 | +0.00 |
| `Q026_protein_kinase_like_function_transfer` | +0.00 | +0.00 | +0.00 |

## MCP Effect Over API

| Question | Biological delta | Factual delta | Gated delta |
|---|---:|---:|---:|
| `Q020_syntaxin_fragment_reclassification` | +1.67 | +1.67 | +1.67 |
| `Q023_homeodomain_heterogeneous_fold` | +1.33 | +1.67 | +1.42 |
| `Q024_fibronectin_fragment_selection` | +1.00 | +2.00 | +1.25 |
| `Q018_tal_effector_repeat_lengths` | +1.00 | +1.67 | +1.17 |
| `Q010_rossmann_family_function_divergence` | +0.67 | +1.33 | +0.83 |
| `Q016_pdb_1acc_domain_split` | -0.33 | -1.33 | -0.75 |
| `Q003_actin_like_atpase_divergence` | -0.67 | +0.00 | -0.50 |
| `Q009_heme_bundle_fold_not_homology` | +0.33 | +0.00 | +0.25 |
| `Q014_paper_example_reclassification_pa1492` | -0.33 | +0.00 | -0.25 |
| `Q025_classification_as_evolutionary_hypothesis` | +0.33 | +0.00 | +0.25 |
| `Q004_ferritin_like_functional_divergence` | +0.00 | -0.67 | -0.17 |
| `Q021_nad_binding_convergent_evolution` | -0.33 | +0.33 | -0.17 |
| `Q001_histone_related_proteins` | +0.00 | +0.33 | +0.08 |
| `Q005_cupredoxin_function_transfer` | +0.00 | +0.33 | +0.08 |
| `Q006_ubiquitin_like_function_divergence` | +0.00 | -0.33 | -0.08 |
| `Q017_prp8_evolutionary_mosaic` | +0.00 | +0.33 | +0.08 |
| `Q002_mazg_hise_remote_homology` | +0.00 | +0.00 | +0.00 |
| `Q007_chey_like_function_divergence` | +0.00 | +0.00 | +0.00 |
| `Q008_v1_atp_synthase_bulge_classification` | +0.00 | +0.00 | +0.00 |
| `Q012_not_true_family_specific_pair` | +0.00 | +0.00 | +0.00 |
| `Q015_artifact_tag_not_homology` | +0.00 | +0.00 | +0.00 |
| `Q022_colg_crya_remote_homology` | +0.00 | +0.00 | +0.00 |
| `Q026_protein_kinase_like_function_transfer` | +0.00 | +0.00 | +0.00 |

## Error Tags By Condition

| Condition | Error tag | Count |
|---|---|---:|
| `pretrained_only` | `identifier_or_boundary_error` | 51 |
| `pretrained_only` | `incomplete_answer` | 28 |
| `pretrained_only` | `wrong_classification` | 13 |
| `pretrained_only` | `missing_annotation_caveat` | 12 |
| `pretrained_only` | `historical_release_omission` | 6 |
| `pretrained_only` | `unsupported_nonhomology_claim` | 4 |
| `pretrained_only` | `periodic_release_omission` | 3 |
| `pretrained_only` | `unsupported_homology_claim` | 1 |
| `pretrained_api` | `api_evidence_error` | 11 |
| `pretrained_api` | `missing_annotation_caveat` | 11 |
| `pretrained_api` | `wrong_classification` | 7 |
| `pretrained_api` | `historical_release_omission` | 3 |
| `pretrained_api` | `unsupported_nonhomology_claim` | 3 |
| `pretrained_api` | `fabricated_release_fact` | 2 |
| `pretrained_api` | `incomplete_answer` | 2 |
| `pretrained_skill` | `incomplete_answer` | 3 |
| `pretrained_skill` | `missing_named_unsafe_transfer` | 2 |
| `pretrained_skill` | `wrong_classification` | 2 |
| `pretrained_skill` | `api_evidence_error` | 1 |
| `pretrained_skill` | `identifier_or_boundary_error` | 1 |
| `pretrained_skill` | `missing_annotation_caveat` | 1 |
| `pretrained_skill` | `missing_required_external_evidence` | 1 |
| `pretrained_mcp` | `historical_release_omission` | 5 |
| `pretrained_mcp` | `wrong_classification` | 5 |
| `pretrained_mcp` | `api_evidence_error` | 4 |
| `pretrained_mcp` | `missing_annotation_caveat` | 3 |
| `pretrained_mcp` | `incomplete_answer` | 2 |
| `pretrained_mcp` | `missing_named_unsafe_transfer` | 2 |
| `pretrained_mcp` | `periodic_release_omission` | 2 |
| `pretrained_mcp` | `unsupported_nonhomology_claim` | 2 |
| `pretrained_mcp_skill` | `incomplete_answer` | 3 |
| `pretrained_mcp_skill` | `missing_annotation_caveat` | 3 |
| `pretrained_mcp_skill` | `missing_required_external_evidence` | 2 |
| `pretrained_mcp_skill` | `api_evidence_error` | 1 |
| `pretrained_mcp_skill` | `identifier_or_boundary_error` | 1 |
