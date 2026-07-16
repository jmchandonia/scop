# SCOPe API Usage

Base URL: `https://scop.berkeley.edu/api/v1`

OpenAPI: `https://scop.berkeley.edu/api/v1/openapi.yaml`

Use this reference for endpoint selection, stable identifiers, pagination, and reproducible API requests.

## Principles

- Use the API instead of scraping HTML pages.
- Use public identifiers only: `sunid`, `sid`, `sccs`, PDB code, and public release strings such as `2.08`.
- Do not ask for or expose internal SQL, `node_id`, `release_id`, table names, or direct database access.
- Default to current release only for exploratory questions. For reproducible answers, use `/releases/{release}/...` paths and include the returned release in the answer.
- Prefer exact lookup endpoints over broad search. Use `/releases/{release}/search?q=...` for common names, keywords, PDB codes, SIDs, SUNIDs, and SCCS strings when the exact identifier is not known.
- Prefer canonical release-first paths. Older query-parameter aliases should not be used in new agent workflows.
- Use pagination on collection endpoints. Treat cursors as opaque.

## Release Selection

Current release metadata:

```bash
curl -sS 'https://scop.berkeley.edu/api/v1/releases/current'
```

All public releases:

```bash
curl -sS 'https://scop.berkeley.edu/api/v1/releases'
```

The current release is queried by default. To query an old release, use release-first paths:

```bash
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sunids/194566'
```

Use stable public versions such as `2.08`; ordinary clients should not need internal release IDs or special stable labels. SCOPe releases starting with 2.02 may have periodic update labels; stable releases are still the right selector for most reproducible analyses and benchmarking.

## Identifier Resolution

Resolve when the identifier type is ambiguous:

```bash
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/resolve/d4as4b_'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/resolve/194566'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/resolve/e.7.1.1'
```

Direct lookups:

```bash
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sunids/194566'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sids/d4as4b_'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sccs/e.7.1.1'
```

Common-name or keyword search:

```bash
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/search?q=adenylate%20kinase&limit=10'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/search?q=histone&limit=10'
```

## Hierarchy

Get level definitions:

```bash
curl -sS 'https://scop.berkeley.edu/api/v1/levels'
```

Get root for a release:

```bash
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/root'
```

Get lineage:

```bash
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sunids/194566/parents'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sids/d4as4b_/parents'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sccs/e.7.1.1/parents'
```

Get children with seek pagination:

```bash
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sunids/46456/children?limit=100'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sccs/a.1/children?limit=100'
```

Continue with the returned `next_cursor` value if present:

```bash
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sunids/46456/children?limit=100&cursor=OPAQUE_CURSOR'
```

## PDB Entries

Compact PDB summary:

```bash
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/pdb/4as4'
```

Domains and chains:

```bash
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/pdb/4as4/domains'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/pdb/4as4/chains'
```

Heterogens and revisions:

```bash
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/pdb/4as4/heterogens'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/pdb/4as4/revisions'
```

## Annotations, Homology, Images

Annotations include comments, inconsistencies, repeats, and cross-references when available:

```bash
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sunids/194566/annotations'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sids/d4as4b_/annotations'
```

Homology endpoints summarize homology scope and warnings:

```bash
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sunids/194566/homology'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sids/d4as4b_/homology'
```

Images endpoints expose thumbnail and larger static image URLs for domain, chain, and structure contexts:

```bash
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sunids/194566/images'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sids/d4as4b_/images'
```

## ASTRAL Quality

SPACI/AEROSPACI quality for a PDB entry:

```bash
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/pdb/4as4/quality'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/astral/quality/pdb/4as4'
```

Paginated release-wide quality rows:

```bash
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/astral/quality?limit=100'
```

Use AEROSPACI/SPACI as quality guidance, not as a homology measure. AEROSPACI was designed to choose high-quality representatives in representative subsets by penalizing known aberrant entries.

## Collections

Domain index:

```bash
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/domains?limit=100'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/domains?class=a&limit=100'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/domains?sccs_prefix=a.39&limit=100'
```

PDB index:

```bash
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/pdb?limit=100'
```

Downloads and ASTRAL manifests:

```bash
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/downloads'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/parseable-files'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/astral/files'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/astral/raf'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/astral/pdbstyle'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/astral/subsets'
```

Always follow `next_cursor` rather than constructing offsets.

## Answer Shape

When answering from API data, include:

- Identifier used: `sid`, `sunid`, `sccs`, or PDB code.
- Release returned by the API.
- Lineage when biological interpretation depends on hierarchy.
- Annotation/homology warnings when present.
- A statement of uncertainty if an endpoint lacks a field needed for the requested conclusion.

Do not answer biological claims solely from a name string when lineage, annotations, or homology endpoints are available.

## Worked API Examples From Help/Papers

The SCOPe help's domain-visualization example uses `d4akea1`. To reproduce that lookup:

```bash
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sids/d4akea1'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sids/d4akea1/parents'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sids/d4akea1/images'
```

Expected interpretation: `d4akea1` is an E. coli adenylate kinase domain in SCOPe 2.08, family `c.37.1.1` "Nucleotide and nucleoside kinases", superfamily `c.37.1` "P-loop containing nucleoside triphosphate hydrolases". The images endpoint returns thumbnail and larger domain/chain/structure image URLs.

The parseable-file help examples include `d1ux8a_`:

```bash
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sids/d1ux8a_'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sids/d1ux8a_/parents'
```

Expected interpretation: `d1ux8a_` is a Bacillus subtilis truncated hemoglobin domain in family `a.1.1.1`, superfamily `a.1.1`, fold `a.1` "Globin-like", class `a` "All alpha proteins".

To answer a homology question like "Are adenylate kinase domain `d1akea1` and shikimate kinase domain `d1shka_` homologous in SCOPe?":

```bash
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sids/d1akea1/parents'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sids/d1shka_/parents'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sids/d1akea1/homology'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sids/d1shka_/homology'
```

Expected interpretation: the domains have different families, `c.37.1.1` and `c.37.1.2`, but share superfamily `c.37.1` "P-loop containing nucleoside triphosphate hydrolases". In SCOPe terms that supports probable common ancestry at the superfamily level, with different family-level groupings.
