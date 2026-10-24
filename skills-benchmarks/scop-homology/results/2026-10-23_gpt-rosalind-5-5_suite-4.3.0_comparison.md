# SCOPe Benchmark Comparison

- Suite: `4.3.0`
- Answer model: `gpt-rosalind-5.5`
- Evaluator: `anthropic/claude-fable-5-1`
- Replicates per condition: 3
- Harness: codex-cli-isolated; one fresh session per question

## Aggregate Results

| Condition | Biological reasoning | Factual support | Secondary gated | Run-level gated SD |
|---|---:|---:|---:|---:|
| `pretrained_only` | 2.70 | 1.87 | 2.42 | 0.05 |
| `pretrained_api` | 3.72 | 3.80 | 3.74 | 0.25 |
| `pretrained_skill` | 3.96 | 4.00 | 3.97 | 0.06 |
| `pretrained_mcp` | 3.75 | 3.90 | 3.79 | 0.11 |
| `pretrained_mcp_skill` | 3.97 | 3.93 | 3.96 | 0.04 |

## Skill Effect Over API

| Question | Biological delta | Factual delta | Gated delta |
|---|---:|---:|---:|
| `Q009_heme_bundle_fold_not_homology` | +1.00 | +0.00 | +0.75 |
| `Q018_tal_effector_repeat_lengths` | +0.67 | +1.00 | +0.75 |
| `Q001_histone_related_proteins` | +0.67 | +0.67 | +0.67 |
| `Q012_not_true_family_specific_pair` | +0.67 | +0.67 | +0.67 |
| `Q020_syntaxin_fragment_reclassification` | +0.67 | +0.67 | +0.67 |
| `Q025_classification_as_evolutionary_hypothesis` | +0.67 | +0.00 | +0.50 |
| `Q005_cupredoxin_function_transfer` | +0.33 | +0.33 | +0.33 |
| `Q017_prp8_evolutionary_mosaic` | +0.33 | +0.33 | +0.33 |
| `Q024_fibronectin_fragment_selection` | +0.33 | +0.33 | +0.33 |
| `Q003_actin_like_atpase_divergence` | +0.33 | +0.00 | +0.25 |
| `Q006_ubiquitin_like_function_divergence` | -0.33 | +0.00 | -0.25 |
| `Q021_nad_binding_convergent_evolution` | +0.33 | +0.00 | +0.25 |
| `Q022_colg_crya_remote_homology` | -0.33 | +0.00 | -0.25 |
| `Q016_pdb_1acc_domain_split` | +0.00 | +0.67 | +0.17 |
| `Q002_mazg_hise_remote_homology` | +0.00 | +0.00 | +0.00 |
| `Q004_ferritin_like_functional_divergence` | +0.00 | +0.00 | +0.00 |
| `Q007_chey_like_function_divergence` | +0.00 | +0.00 | +0.00 |
| `Q008_v1_atp_synthase_bulge_classification` | +0.00 | +0.00 | +0.00 |
| `Q010_rossmann_family_function_divergence` | +0.00 | +0.00 | +0.00 |
| `Q014_paper_example_reclassification_pa1492` | +0.00 | +0.00 | +0.00 |
| `Q015_artifact_tag_not_homology` | +0.00 | +0.00 | +0.00 |
| `Q023_homeodomain_heterogeneous_fold` | +0.00 | +0.00 | +0.00 |
| `Q026_protein_kinase_like_function_transfer` | +0.00 | +0.00 | +0.00 |

## Skill Effect Over MCP

| Question | Biological delta | Factual delta | Gated delta |
|---|---:|---:|---:|
| `Q012_not_true_family_specific_pair` | +1.33 | +0.67 | +1.17 |
| `Q009_heme_bundle_fold_not_homology` | +1.00 | +0.33 | +0.83 |
| `Q005_cupredoxin_function_transfer` | +0.67 | +0.33 | +0.58 |
| `Q007_chey_like_function_divergence` | +0.67 | +0.00 | +0.50 |
| `Q022_colg_crya_remote_homology` | +0.67 | +0.00 | +0.50 |
| `Q020_syntaxin_fragment_reclassification` | +0.33 | +0.00 | +0.25 |
| `Q025_classification_as_evolutionary_hypothesis` | +0.33 | -0.33 | +0.17 |
| `Q016_pdb_1acc_domain_split` | +0.00 | +0.33 | +0.08 |
| `Q021_nad_binding_convergent_evolution` | +0.00 | -0.33 | -0.08 |
| `Q024_fibronectin_fragment_selection` | +0.00 | -0.33 | -0.08 |
| `Q001_histone_related_proteins` | +0.00 | +0.00 | +0.00 |
| `Q002_mazg_hise_remote_homology` | +0.00 | +0.00 | +0.00 |
| `Q003_actin_like_atpase_divergence` | +0.00 | +0.00 | +0.00 |
| `Q004_ferritin_like_functional_divergence` | +0.00 | +0.00 | +0.00 |
| `Q006_ubiquitin_like_function_divergence` | +0.00 | +0.00 | +0.00 |
| `Q008_v1_atp_synthase_bulge_classification` | +0.00 | +0.00 | +0.00 |
| `Q010_rossmann_family_function_divergence` | +0.00 | +0.00 | +0.00 |
| `Q014_paper_example_reclassification_pa1492` | +0.00 | +0.00 | +0.00 |
| `Q015_artifact_tag_not_homology` | +0.00 | +0.00 | +0.00 |
| `Q017_prp8_evolutionary_mosaic` | +0.00 | +0.00 | +0.00 |
| `Q018_tal_effector_repeat_lengths` | +0.00 | +0.00 | +0.00 |
| `Q023_homeodomain_heterogeneous_fold` | +0.00 | +0.00 | +0.00 |
| `Q026_protein_kinase_like_function_transfer` | +0.00 | +0.00 | +0.00 |

## MCP Effect Over API

| Question | Biological delta | Factual delta | Gated delta |
|---|---:|---:|---:|
| `Q007_chey_like_function_divergence` | -1.00 | +0.00 | -0.75 |
| `Q018_tal_effector_repeat_lengths` | +0.67 | +1.00 | +0.75 |
| `Q001_histone_related_proteins` | +0.67 | +0.67 | +0.67 |
| `Q012_not_true_family_specific_pair` | -0.67 | +0.00 | -0.50 |
| `Q022_colg_crya_remote_homology` | -0.67 | +0.00 | -0.50 |
| `Q025_classification_as_evolutionary_hypothesis` | +0.67 | +0.00 | +0.50 |
| `Q020_syntaxin_fragment_reclassification` | +0.33 | +0.67 | +0.42 |
| `Q017_prp8_evolutionary_mosaic` | +0.33 | +0.33 | +0.33 |
| `Q024_fibronectin_fragment_selection` | +0.33 | +0.33 | +0.33 |
| `Q003_actin_like_atpase_divergence` | +0.33 | +0.00 | +0.25 |
| `Q005_cupredoxin_function_transfer` | -0.33 | +0.00 | -0.25 |
| `Q006_ubiquitin_like_function_divergence` | -0.33 | +0.00 | -0.25 |
| `Q021_nad_binding_convergent_evolution` | +0.33 | +0.00 | +0.25 |
| `Q009_heme_bundle_fold_not_homology` | +0.00 | -0.33 | -0.08 |
| `Q016_pdb_1acc_domain_split` | +0.00 | -0.33 | -0.08 |
| `Q002_mazg_hise_remote_homology` | +0.00 | +0.00 | +0.00 |
| `Q004_ferritin_like_functional_divergence` | +0.00 | +0.00 | +0.00 |
| `Q008_v1_atp_synthase_bulge_classification` | +0.00 | +0.00 | +0.00 |
| `Q010_rossmann_family_function_divergence` | +0.00 | +0.00 | +0.00 |
| `Q014_paper_example_reclassification_pa1492` | +0.00 | +0.00 | +0.00 |
| `Q015_artifact_tag_not_homology` | +0.00 | +0.00 | +0.00 |
| `Q023_homeodomain_heterogeneous_fold` | +0.00 | +0.00 | +0.00 |
| `Q026_protein_kinase_like_function_transfer` | +0.00 | +0.00 | +0.00 |

## Error Tags By Condition

| Condition | Error tag | Count |
|---|---|---:|
| `pretrained_only` | `identifier_or_boundary_error` | 44 |
| `pretrained_only` | `incomplete_answer` | 33 |
| `pretrained_only` | `wrong_classification` | 16 |
| `pretrained_only` | `missing_annotation_caveat` | 12 |
| `pretrained_only` | `historical_release_omission` | 6 |
| `pretrained_only` | `missing_required_external_evidence` | 6 |
| `pretrained_only` | `unsupported_nonhomology_claim` | 5 |
| `pretrained_only` | `api_evidence_error` | 4 |
| `pretrained_only` | `missing_named_unsafe_transfer` | 3 |
| `pretrained_only` | `fabricated_release_fact` | 2 |
| `pretrained_only` | `missing_quality_context` | 1 |
| `pretrained_only` | `unsafe_function_transfer` | 1 |
| `pretrained_api` | `incomplete_answer` | 6 |
| `pretrained_api` | `missing_required_external_evidence` | 6 |
| `pretrained_api` | `missing_annotation_caveat` | 5 |
| `pretrained_api` | `api_evidence_error` | 4 |
| `pretrained_api` | `periodic_release_omission` | 3 |
| `pretrained_api` | `fabricated_annotation` | 1 |
| `pretrained_api` | `missing_named_unsafe_transfer` | 1 |
| `pretrained_api` | `missing_quality_context` | 1 |
| `pretrained_api` | `unsupported_nonhomology_claim` | 1 |
| `pretrained_api` | `wrong_classification` | 1 |
| `pretrained_skill` | `incomplete_answer` | 3 |
| `pretrained_skill` | `periodic_release_omission` | 3 |
| `pretrained_skill` | `missing_named_unsafe_transfer` | 1 |
| `pretrained_skill` | `missing_required_external_evidence` | 1 |
| `pretrained_mcp` | `missing_required_external_evidence` | 7 |
| `pretrained_mcp` | `incomplete_answer` | 4 |
| `pretrained_mcp` | `missing_named_unsafe_transfer` | 4 |
| `pretrained_mcp` | `historical_release_omission` | 3 |
| `pretrained_mcp` | `api_evidence_error` | 2 |
| `pretrained_mcp` | `periodic_release_omission` | 2 |
| `pretrained_mcp` | `missing_annotation_caveat` | 1 |
| `pretrained_mcp` | `unsafe_function_transfer` | 1 |
| `pretrained_mcp` | `unsupported_homology_claim` | 1 |
| `pretrained_mcp_skill` | `incomplete_answer` | 3 |
| `pretrained_mcp_skill` | `api_evidence_error` | 2 |
| `pretrained_mcp_skill` | `historical_release_omission` | 2 |
| `pretrained_mcp_skill` | `missing_named_unsafe_transfer` | 2 |
| `pretrained_mcp_skill` | `periodic_release_omission` | 1 |
