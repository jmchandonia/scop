# FATCAT Evidence And Caveats

Use this reference when interpreting FATCAT structure hits returned for an
AlphaFold model.

## What The Statistics Mean

FATCAT aligns structures from chains of aligned fragment pairs. Its statistical
model estimates the significance of an alignment score from an empirically fit
extreme-value distribution. The P-value describes the chance of obtaining the
same structural similarity from random structures; smaller is more
significant.

This is evidence of structural similarity. It is not:

- the probability that two proteins are homologous;
- the probability that a SCOPe family assignment is correct;
- proof of shared function;
- a substitute for alignment coverage or domain boundaries.

Primary references:

- Ye and Godzik, "Database searching by flexible protein structure alignment,"
  *Protein Science* 2004, PMID 15215527:
  https://pmc.ncbi.nlm.nih.gov/articles/PMC2279924/
- Ye and Godzik, "FATCAT: a web server for flexible structure comparison and
  structure similarity searching," *Nucleic Acids Research* 2004, PMID
  15215455: https://pmc.ncbi.nlm.nih.gov/articles/PMC441568/
- Li et al., "FATCAT 2.0: towards a better understanding of the structural
  diversity of proteins," *Nucleic Acids Research* 2020:
  https://pmc.ncbi.nlm.nih.gov/articles/PMC7319568/

## Interpretation Rules

- Consider P-value, score, RMSD, and aligned length together. A low RMSD over a
  short fragment does not establish a domain-wide relationship.
- Calculate query coverage from the returned AlphaFold range and full model
  length. Do not invent target coverage when the full target-domain length is
  unavailable.
- Treat sequence and structure rankings as separate evidence streams.
- Prefer convergence between sequence evidence, structural evidence, and
  SCOPe lineage over the top result from only one list.
- Examine competing classifications, not only the first hit. FATCAT 2.0 notes
  that the most biologically interesting similarity is not always captured by
  P-value rank alone.
- Treat repeated hits from the same family or near-identical structures as
  correlated evidence.
- Preserve SCOPe comments such as automated matches, fragments, repeats,
  heterogeneity, and "not a true fold/family" warnings.

## Mode And Output Limits

FATCAT supports rigid and flexible comparisons, but the current MCP hit schema
does not report the run mode, twist count, equivalent-position count, or
alignment blocks. Do not infer hinges, flexibility, or conformational changes
from the returned transform or score.

The current REST implementation may filter structure hits before returning
them. Therefore:

- an empty list does not establish that no structural relationship exists;
- a returned hit has passed server-side criteria but still needs biological
  interpretation;
- result-count limits can hide classification diversity below the first page.

If exact run mode, thresholds, or database composition matter, report that the
current response does not establish them and request provenance metadata.
