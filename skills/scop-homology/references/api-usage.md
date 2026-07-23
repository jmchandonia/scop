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
the question asks about a historical change.

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
GET /releases/{release}/astral/quality/pdb/{code}
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

## Answer Checklist

- Release returned by the API.
- Public identifier for every classified entity.
- Lineage for evolutionary claims.
- Annotation or homology warning when biologically material.
- Actual quality fields for rankings.
- Clear separation of verified facts, interpretation, and unsupported
  conclusions.
