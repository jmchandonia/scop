# AlphaFold Evidence And Caveats

Use these constraints when AlphaFold coordinates or pLDDT affect the
assessment.

Primary reference:

- Jumper et al., "Highly accurate protein structure prediction with
  AlphaFold," *Nature* 2021:
  https://pmc.ncbi.nlm.nih.gov/articles/PMC8371605/

## Confidence Scope

- pLDDT predicts local C-alpha lDDT accuracy. It is calibrated evidence about
  local geometry, not certainty that each coordinate is correct.
- The paper reports a population-level correlation between pLDDT and observed
  lDDT-C-alpha, not a guarantee for an individual region.
- High side-chain accuracy is conditional on an accurate backbone. Do not use
  pLDDT alone to claim exact rotamers, active-site geometry, ligand contacts,
  or functional chemistry.
- Local pLDDT does not establish relative domain orientation. Whole-chain
  comparisons are sensitive to domain-packing errors. If pTM or predicted
  aligned error is unavailable, report that limitation.
- Interpret the full local distribution: median, all four pLDDT bands, and
  sustained stretches below 70 or 50. A `pLDDT >= 90` cluster is descriptive
  only. A hard threshold can fragment a credible region and does not establish
  fold coherence or identify an exact SCOPe domain boundary.

## Correlated Inputs

- AlphaFold uses homologous sequences in an MSA and can use PDB templates.
  Therefore, sequence evidence, predicted structure, and a structural match to
  a PDB-derived SCOPe domain are not automatically statistically independent.
- Call sequence and FATCAT results separate evidence streams or corroborating
  readouts. Claim independence only when model provenance supports it.
- The paper reports substantially lower accuracy when median effective MSA
  depth falls below roughly 30 sequences. If MSA depth is unavailable, label it
  unknown; do not infer depth from pLDDT or the number of SCOPe hits.
- Template or training-set overlap can make a structural match less novel. It
  does not make the match false, but it limits claims of independent
  validation.

## Missing Biological Context

- AlphaFold is weaker for regions whose shape depends mostly on contacts with
  other heteromeric chains, including bridging domains in complexes. Qualify a
  single-chain structural conclusion when partner-dependent folding is
  plausible.
- The model can implicitly adopt geometry associated with a predictable
  ligand, ion, haem, or stoichiometry without explicitly representing that
  component. Do not infer that the component is present or that its interaction
  is correct.
- Treat the coordinates as one PDB-like structural hypothesis. Do not infer a
  conformational ensemble, dynamics, state populations, or a unique biological
  state from one prediction.

## Effect On SCOPe Assessment

- Let broadly high local pLDDT, without controlling sustained low-confidence
  stretches, increase confidence in the geometry used by a well-covered FATCAT
  match.
- Do not let pLDDT promote a classification beyond the sequence, FATCAT,
  lineage, annotation, and homology evidence.
- Downgrade or qualify claims that depend on domain packing, an interface, a
  ligand-bound shape, or low-confidence coordinates.
