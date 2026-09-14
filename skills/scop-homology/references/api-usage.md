# SCOPe API Usage

Base URL: `https://scop.berkeley.edu/api/v1`

OpenAPI: `https://scop.berkeley.edu/api/v1/openapi.yaml`

Use release-scoped endpoints for reproducible answers. Replace placeholders
such as `{release}`, `{sid}`, and `{code}` with public values returned or
provided during the task.

## Release Selection

```text
GET /releases/current
GET /releases
GET /releases/{release}/root
```

Use the current endpoint to discover current status. Use a stable public
release selector for reproducible analysis. Query each release separately when
the question asks about a historical change. The current-status response
reports release labels and dates; it does not by itself define what records a
periodic update added or whether a particular classification changed. Do not
invent those semantics from the label.

## Resolution And Search

```text
GET /releases/{release}/resolve/{identifier}
GET /releases/{release}/search?q={query}&limit=10
GET /releases/{release}/sids/{sid}
GET /releases/{release}/sunids/{sunid}
GET /releases/{release}/sccs/{sccs}
```

Prefer exact lookup after search. Search results are candidates, not sufficient
evidence for a hierarchy or homology claim.

## Hierarchy

```text
GET /levels
GET /releases/{release}/sids/{sid}/parents
GET /releases/{release}/sunids/{sunid}/parents
GET /releases/{release}/sccs/{sccs}/parents
GET /releases/{release}/sunids/{sunid}/children?limit=100
```

Treat `next_cursor` as opaque. Before comparing two domains, retrieve both
parent lineages in the same release. Compare hierarchy nodes by their public
level and SUNID, not by similar name strings.
The `/parents` response excludes the queried node. The comparison helper
retrieves both the node record and its parents, and includes the node itself
when comparing hierarchy identifiers or their release histories. Frozen helper
fixtures must contain both responses for each compared identifier.

## PDB Records

```text
GET /releases/{release}/pdb/{code}
GET /releases/{release}/pdb/{code}/chains
GET /releases/{release}/pdb/{code}/domains
GET /releases/{release}/pdb/{code}/heterogens
GET /releases/{release}/pdb/{code}/revisions
```

Do not assume one chain equals one domain. Retrieve the domain list before
making a chain-level biological interpretation.

## PDB-Chain Comparisons

With MCP, use `get_scop_chain` for chain metadata and existing classified
domains. Use `get_scop_chain_hits` for a bounded combined BLAST/FATCAT result;
set `evidence="sequence"` or `evidence="structure"` on `get_scop_chain_hits` when
the question is limited to one evidence stream.

REST fallbacks:

```text
GET /releases/{release}/chains/{chain}
GET /releases/{release}/chains/{chain}/hits?limit=100
GET /releases/{release}/chains/{chain}/sequence-hits?limit=100
GET /releases/{release}/chains/{chain}/structure-hits?limit=100
GET /sequences/sids/{sid}?release={release}&source=seqres&style=genetic&format=json
```

A chain identifier is a four-character PDB code plus one chain character, such
as `1ux8A`. BLAST ranges are one-based inclusive positions in the compared
sequences. The current chain FATCAT response instead retains PDB residue
numbering in its integer starts and computes legacy ends from residue counts.
Insertion-code information is not preserved by those integer fields. These
are not verified sequence intervals: numbering offsets, insertion codes, and
missing residues can invalidate interval arithmetic. Do not combine the streams
by numeric overlap without a verified residue mapping. The combined response
contains sequence and structure evidence from the same release; it does not
infer domains, homology, or a SCOPe classification. Results are bounded and are
not cursor-paginated, so a result count equal to the requested limit is not an
exhaustive search.

The chain FATCAT response's `rmsd` is the initial FATCAT RMSD (`ini_rmsd`). Its
two range lengths count residues on the respective sides, not paired residues.
The response does not expose residue correspondence, alignment gaps, optimized
RMSD, or a verified mapping to the BLAST sequence. A transformation matrix alone
does not supply those observations. See `chain-comparisons.md` for how to report
these evidence limits.

Use the domain-sequence endpoint only when an exact target sequence length is
needed to test terminal coverage. If several sequences are returned and the hit
does not identify which one was compared, report the terminal-coverage test as
unresolved rather than choosing one. For interpretation and reporting rules,
read `chain-comparisons.md`.

## Annotations And Homology

```text
GET /releases/{release}/sids/{sid}/annotations
GET /releases/{release}/sids/{sid}/homology
GET /releases/{release}/sunids/{sunid}/annotations
GET /releases/{release}/sunids/{sunid}/homology
```

Check both endpoints when artifacts, fragments, repeats, alternative domain
divisions, structural heterogeneity, or unusual fold/family semantics could
affect the answer. Absence of a warning is not independent proof of homology.

## ASTRAL Quality

```text
GET /releases/{release}/pdb/{code}/quality
GET /releases/{release}/astral/quality?limit=100
```

Only compare structures using fields retrieved for all candidates. Report the
actual AEROSPACI/SPACI values and relevant experimental fields. If a requested
measure is missing for any candidate, state that the comparison cannot be made
using that measure.

## Collections And Downloads

```text
GET /releases/{release}/domains?limit=100
GET /releases/{release}/pdb?limit=100
GET /releases/{release}/downloads
GET /releases/{release}/parseable-files
GET /releases/{release}/astral/files
GET /releases/{release}/astral/raf
GET /releases/{release}/astral/pdbstyle
GET /releases/{release}/astral/subsets
```

Follow pagination for collection claims. A partial first page is not evidence
for release-wide counts or absence.

The release-scoped PDB collection lists entries with classified domains using
the existing PDB and classification tables. Domain counts deduplicate domains
linked to multiple chains; `first_sid` and `first_sunid` identify the same
representative domain. No generated release manifest is required.

## Evidence Failures

- `404`: verify the identifier and release; do not silently switch releases.
- Missing field: state that the requested claim is not supported by that
  response.
- Timeout or unavailable API: name the required request and abstain from exact
  release-specific claims.
- Conflicting records: report the conflict and the releases involved instead
  of selecting the convenient value.
- Endpoint absent from OpenAPI: state that the current public API does not
  expose the requested information.
- Classification present but rationale absent: report the classification and
  say that the API response does not expose the supporting alignment,
  literature argument, or curator reasoning.

## Answer Checklist

- Release returned by the API.
- Public identifier for every classified entity.
- Lineage for evolutionary claims.
- Annotation or homology warning when biologically material.
- Actual quality fields for rankings.
- Clear separation of verified facts, interpretation, and unsupported
  conclusions.
