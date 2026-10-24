# SCOPe Benchmark Comparison

- Suite: `4.3.0`
- Answer model: `claude-opus-5-5`
- Evaluator: `anthropic/claude-fable-5-1`
- Replicates per condition: 3
- Harness: claude-code-cli-isolated; one fresh session per question

## Aggregate Results

| Condition | Biological reasoning | Factual support | Secondary gated | Run-level gated SD |
|---|---:|---:|---:|---:|
| `pretrained_only` | 3.25 | 2.13 | 2.88 | 0.10 |
| `pretrained_api` | 3.70 | 3.45 | 3.63 | 0.03 |
| `pretrained_skill` | 3.99 | 3.90 | 3.96 | 0.04 |
| `pretrained_mcp` | 3.94 | 3.86 | 3.92 | 0.02 |
| `pretrained_mcp_skill` | 3.99 | 3.93 | 3.97 | 0.03 |

## Skill Effect Over API

| Question | Biological delta | Factual delta | Gated delta |
|---|---:|---:|---:|
| `Q018_tal_effector_repeat_lengths` | +2.00 | +2.00 | +2.00 |
| `Q020_syntaxin_fragment_reclassification` | +1.33 | +1.33 | +1.33 |
| `Q012_not_true_family_specific_pair` | +1.00 | +2.00 | +1.25 |
| `Q015_artifact_tag_not_homology` | +0.67 | +0.33 | +0.58 |
| `Q001_histone_related_proteins` | +0.67 | +0.00 | +0.50 |
| `Q009_heme_bundle_fold_not_homology` | +0.33 | +0.67 | +0.42 |
| `Q003_actin_like_atpase_divergence` | +0.33 | +0.00 | +0.25 |
| `Q004_ferritin_like_functional_divergence` | +0.00 | +1.00 | +0.25 |
| `Q016_pdb_1acc_domain_split` | +0.33 | +0.00 | +0.25 |
| `Q024_fibronectin_fragment_selection` | +0.00 | +1.00 | +0.25 |
| `Q025_classification_as_evolutionary_hypothesis` | +0.00 | +1.00 | +0.25 |
| `Q021_nad_binding_convergent_evolution` | +0.33 | -0.33 | +0.17 |
| `Q006_ubiquitin_like_function_divergence` | +0.00 | +0.33 | +0.08 |
| `Q007_chey_like_function_divergence` | +0.00 | +0.33 | +0.08 |
| `Q022_colg_crya_remote_homology` | -0.33 | +0.67 | -0.08 |
| `Q002_mazg_hise_remote_homology` | +0.00 | +0.00 | +0.00 |
| `Q005_cupredoxin_function_transfer` | +0.00 | +0.00 | +0.00 |
| `Q008_v1_atp_synthase_bulge_classification` | +0.00 | +0.00 | +0.00 |
| `Q010_rossmann_family_function_divergence` | +0.00 | +0.00 | +0.00 |
| `Q014_paper_example_reclassification_pa1492` | +0.00 | +0.00 | +0.00 |
| `Q017_prp8_evolutionary_mosaic` | +0.00 | +0.00 | +0.00 |
| `Q023_homeodomain_heterogeneous_fold` | +0.00 | +0.00 | +0.00 |
| `Q026_protein_kinase_like_function_transfer` | +0.00 | +0.00 | +0.00 |

## Skill Effect Over MCP

| Question | Biological delta | Factual delta | Gated delta |
|---|---:|---:|---:|
| `Q012_not_true_family_specific_pair` | +0.67 | +1.33 | +0.83 |
| `Q001_histone_related_proteins` | +0.33 | +0.00 | +0.25 |
| `Q008_v1_atp_synthase_bulge_classification` | +0.00 | +0.33 | +0.08 |
| `Q017_prp8_evolutionary_mosaic` | +0.00 | +0.33 | +0.08 |
| `Q021_nad_binding_convergent_evolution` | +0.00 | -0.33 | -0.08 |
| `Q023_homeodomain_heterogeneous_fold` | +0.00 | -0.33 | -0.08 |
| `Q024_fibronectin_fragment_selection` | +0.00 | +0.33 | +0.08 |
| `Q002_mazg_hise_remote_homology` | +0.00 | +0.00 | +0.00 |
| `Q003_actin_like_atpase_divergence` | +0.00 | +0.00 | +0.00 |
| `Q004_ferritin_like_functional_divergence` | +0.00 | +0.00 | +0.00 |
| `Q005_cupredoxin_function_transfer` | +0.00 | +0.00 | +0.00 |
| `Q006_ubiquitin_like_function_divergence` | +0.00 | +0.00 | +0.00 |
| `Q007_chey_like_function_divergence` | +0.00 | +0.00 | +0.00 |
| `Q009_heme_bundle_fold_not_homology` | +0.00 | +0.00 | +0.00 |
| `Q010_rossmann_family_function_divergence` | +0.00 | +0.00 | +0.00 |
| `Q014_paper_example_reclassification_pa1492` | +0.00 | +0.00 | +0.00 |
| `Q015_artifact_tag_not_homology` | +0.00 | +0.00 | +0.00 |
| `Q016_pdb_1acc_domain_split` | +0.00 | +0.00 | +0.00 |
| `Q018_tal_effector_repeat_lengths` | +0.00 | +0.00 | +0.00 |
| `Q020_syntaxin_fragment_reclassification` | +0.00 | +0.00 | +0.00 |
| `Q022_colg_crya_remote_homology` | +0.00 | +0.00 | +0.00 |
| `Q025_classification_as_evolutionary_hypothesis` | +0.00 | +0.00 | +0.00 |
| `Q026_protein_kinase_like_function_transfer` | +0.00 | +0.00 | +0.00 |

## MCP Effect Over API

| Question | Biological delta | Factual delta | Gated delta |
|---|---:|---:|---:|
| `Q018_tal_effector_repeat_lengths` | +2.00 | +2.00 | +2.00 |
| `Q020_syntaxin_fragment_reclassification` | +1.33 | +1.33 | +1.33 |
| `Q015_artifact_tag_not_homology` | +0.67 | +0.67 | +0.67 |
| `Q009_heme_bundle_fold_not_homology` | +0.33 | +0.67 | +0.42 |
| `Q001_histone_related_proteins` | +0.33 | +0.00 | +0.25 |
| `Q003_actin_like_atpase_divergence` | +0.33 | +0.00 | +0.25 |
| `Q004_ferritin_like_functional_divergence` | +0.00 | +1.00 | +0.25 |
| `Q016_pdb_1acc_domain_split` | +0.33 | +0.00 | +0.25 |
| `Q021_nad_binding_convergent_evolution` | +0.33 | +0.00 | +0.25 |
| `Q022_colg_crya_remote_homology` | +0.00 | +1.00 | +0.25 |
| `Q025_classification_as_evolutionary_hypothesis` | +0.00 | +1.00 | +0.25 |
| `Q006_ubiquitin_like_function_divergence` | +0.00 | +0.33 | +0.08 |
| `Q007_chey_like_function_divergence` | +0.00 | +0.33 | +0.08 |
| `Q024_fibronectin_fragment_selection` | +0.00 | +0.33 | +0.08 |
| `Q008_v1_atp_synthase_bulge_classification` | +0.00 | +0.33 | +0.08 |
| `Q017_prp8_evolutionary_mosaic` | +0.00 | +0.33 | +0.08 |
| `Q002_mazg_hise_remote_homology` | +0.00 | +0.00 | +0.00 |
| `Q005_cupredoxin_function_transfer` | +0.00 | +0.00 | +0.00 |
| `Q010_rossmann_family_function_divergence` | +0.00 | +0.00 | +0.00 |
| `Q012_not_true_family_specific_pair` | +0.00 | +0.00 | +0.00 |
| `Q014_paper_example_reclassification_pa1492` | +0.00 | +0.00 | +0.00 |
| `Q023_homeodomain_heterogeneous_fold` | +0.00 | +0.00 | +0.00 |
| `Q026_protein_kinase_like_function_transfer` | +0.00 | +0.00 | +0.00 |

## Error Tags By Condition

| Condition | Error tag | Count |
|---|---|---:|
| `pretrained_only` | `identifier_or_boundary_error` | 28 |
| `pretrained_only` | `incomplete_answer` | 23 |
| `pretrained_only` | `historical_release_omission` | 9 |
| `pretrained_only` | `missing_annotation_caveat` | 9 |
| `pretrained_only` | `wrong_classification` | 9 |
| `pretrained_only` | `unsupported_nonhomology_claim` | 5 |
| `pretrained_only` | `fabricated_release_fact` | 4 |
| `pretrained_only` | `api_evidence_error` | 3 |
| `pretrained_only` | `missing_quality_context` | 3 |
| `pretrained_only` | `fabricated_annotation` | 2 |
| `pretrained_only` | `fragment_similarity_omission` | 1 |
| `pretrained_api` | `api_evidence_error` | 18 |
| `pretrained_api` | `missing_annotation_caveat` | 8 |
| `pretrained_api` | `unsupported_nonhomology_claim` | 5 |
| `pretrained_api` | `wrong_classification` | 4 |
| `pretrained_api` | `fragment_similarity_omission` | 2 |
| `pretrained_api` | `identifier_or_boundary_error` | 1 |
| `pretrained_api` | `missing_quality_context` | 1 |
| `pretrained_skill` | `wrong_classification` | 3 |
| `pretrained_skill` | `api_evidence_error` | 1 |
| `pretrained_skill` | `fabricated_annotation` | 1 |
| `pretrained_mcp` | `missing_annotation_caveat` | 3 |
| `pretrained_mcp` | `missing_quality_context` | 2 |
| `pretrained_mcp` | `identifier_or_boundary_error` | 1 |
| `pretrained_mcp` | `unsupported_nonhomology_claim` | 1 |
| `pretrained_mcp` | `wrong_classification` | 1 |
| `pretrained_mcp_skill` | `incomplete_answer` | 2 |
| `pretrained_mcp_skill` | `api_evidence_error` | 1 |
| `pretrained_mcp_skill` | `missing_annotation_caveat` | 1 |
| `pretrained_mcp_skill` | `missing_quality_context` | 1 |
