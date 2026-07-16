---
name: scop-homology
description: Use classic SCOP, ASTRAL, and SCOPe protein structural classification data through the public SCOPe REST API to answer questions about protein domains, hierarchy, homology, folds, families, superfamilies, stable releases, ASTRAL/SPACI/AEROSPACI data, and reproducible SCOPe citations. Use for SCOP/SCOPe/ASTRAL questions only; this skill does not cover SCOP2, CATH, ECOD, or other protein classification resources.
---

# SCOP Homology

Use this skill to answer questions with SCOP and SCOPe data from the REST API. Do not scrape SCOPe web pages: bot access to human web pages may be blocked, and the API is the supported machine interface.

Primary sources for interpretation:

- Current database content and current semantics: `references/api-usage.md` and `references/biological-interpretation.md`, distilled from the downloaded SCOPe online help.
- How to reason biologically with SCOP: reference 6, Brenner et al. 1996, "Understanding protein structure: Using SCOP for fold interpretation", distilled in `references/biological-interpretation.md`.

## API First

Use `https://scop.berkeley.edu/api/v1` as the default base URL. Check `https://scop.berkeley.edu/api/v1/openapi.yaml` when exact endpoint details matter.

Use only public identifiers in requests and answers:

- `sunid`: public numeric SCOP/SCOPe unique identifier for any hierarchy node.
- `sid`: stable domain identifier such as `d4as4b_`.
- `sccs`: public classification string such as `e.7.1.1`.
- PDB code: four-character PDB identifier such as `4as4`.
- Release: public release selector such as `2.08` or `1.75`.

Do not expose or invent internal database IDs such as `node_id` or `release_id`.

Prefer release-stable requests for reproducible answers. The API defaults to the current release, but answers should report the release returned by the API. For old data, use canonical release-scoped paths such as `/releases/2.08/sunids/{sunid}`.

## Common Workflows

1. Given a PDB code, fetch `/releases/{release}/pdb/{code}` and `/releases/{release}/pdb/{code}/domains`, then inspect each domain's parents, annotations, homology, and images as needed.
2. Given a `sid`, `sunid`, or `sccs`, call `/releases/{release}/resolve/{identifier}` if the identifier type is unclear; otherwise use `/releases/{release}/sids/{sid}`, `/releases/{release}/sunids/{sunid}`, or `/releases/{release}/sccs/{sccs}` directly.
3. For hierarchy context, call `/parents` for lineage and `/children?limit=...` for paginated descendants.
4. For biological interpretation, call `/annotations` and `/homology` before claiming homology, novelty, artifacts, repeats, or "not true fold/family" caveats.
5. For common-name or keyword lookup, use `/releases/{release}/search?q=...` with a small `limit`; exact identifiers are still preferred when available.
6. For structure quality and ASTRAL-derived quality information, use `/releases/{release}/pdb/{code}/quality` or `/releases/{release}/astral/quality/pdb/{code}`.
7. For visualization, use `/images` on the domain or node endpoint to retrieve thumbnail and larger static image URLs.
8. For parseable files, ASTRAL sequences/subsets, RAF maps, and PDB-style coordinate archives, use `/releases/{release}/downloads` or the narrower ASTRAL/download endpoints. Do not scrape download pages.
9. For worked examples based on the SCOPe help and papers, read `references/biological-interpretation.md`, especially the examples using `d4akea1`, `d1ux8a_`, `d1akea1`, and `d1shka_`.

## Code Samples

Minimal Python lookup:

```python
import requests

BASE = "https://scop.berkeley.edu/api/v1"

def get_json(path, **params):
    r = requests.get(f"{BASE}{path}", params=params, timeout=30)
    r.raise_for_status()
    return r.json()

node = get_json("/releases/2.08/sunids/194566")
parents = get_json("/releases/2.08/sunids/194566/parents")
annotations = get_json("/releases/2.08/sunids/194566/annotations")

print(node.get("release"), node.get("sunid"), node.get("sccs"), node.get("description"))
for parent in parents.get("parents", []):
    print(parent.get("level"), parent.get("sccs"), parent.get("description"))
```

PDB-to-domain workflow:

```python
import requests

BASE = "https://scop.berkeley.edu/api/v1"

def j(path, **params):
    response = requests.get(f"{BASE}{path}", params=params, timeout=30)
    response.raise_for_status()
    return response.json()

pdb_code = "4as4"
release = "2.08"

summary = j(f"/releases/{release}/pdb/{pdb_code}")
domains = j(f"/releases/{release}/pdb/{pdb_code}/domains")
quality = j(f"/releases/{release}/pdb/{pdb_code}/quality")

for domain in domains.get("domains", []):
    sid = domain["sid"]
    detail = j(f"/releases/{release}/sids/{sid}")
    lineage = j(f"/releases/{release}/sids/{sid}/parents")
    homology = j(f"/releases/{release}/sids/{sid}/homology")
    print(sid, detail.get("sccs"), detail.get("description"))
    print("release:", detail.get("release"))
    print("homology warnings:", homology.get("warnings", []))
    print("lineage levels:", [p.get("level") for p in lineage.get("parents", [])])
```

Paginated traversal:

```python
import requests

BASE = "https://scop.berkeley.edu/api/v1"

cursor = None
while True:
    params = {"limit": 100}
    if cursor:
        params["cursor"] = cursor
    data = requests.get(f"{BASE}/releases/2.08/domains", params=params, timeout=30).json()
    for domain in data.get("domains", []):
        process(domain)
    cursor = data.get("next_cursor")
    if not cursor:
        break
```

Shell check:

```bash
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/resolve/d4as4b_'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/pdb/4as4/domains'
curl -sS 'https://scop.berkeley.edu/api/v1/releases/2.08/sunids/194566/homology'
```

## Biological Interpretation Rules

Apply these rules before answering biological questions:

- Treat the domain as the fundamental classified unit, not necessarily the whole PDB chain or whole protein.
- Family and superfamily are the main homology-bearing levels. A family usually indicates closer evolutionary relationship; a superfamily indicates probable common ancestry based on structural, functional, and sequence evidence.
- Fold and class group structures by geometry and secondary-structure organization; they do not by themselves imply homology.
- Species and protein levels describe lower-level grouping within a family/superfamily and should not be confused with NCBI species or UniProt records unless the API response explicitly links them.
- Check `annotations` and `homology` for artifacts, cloning/expression tags, repeats, structural heterogeneity, fragments, "not true fold", or other caveats before making strong claims.
- SCOPe includes automated classification, but manual curation is still central for new folds, superfamilies, families, artifacts, and corrections. Avoid implying that every classification is a de novo manual decision.
- ASTRAL is now integrated into SCOPe. Refer to ASTRAL as SCOPe-derived sequence, subset, RAF, coordinate, and quality resources rather than as a separate current database.
- Stable releases are best for benchmarking and reproducibility. Periodic SCOPe updates add new PDB entries to the current release without changing older domains; they do not replace stable releases.

## Resource Loading

Read `references/api-usage.md` when endpoint choice, parameters, pagination, or code examples matter.

Read `references/biological-interpretation.md` when answering questions about homology, fold interpretation, hierarchy semantics, ASTRAL, structural heterogeneity, artifacts, releases, citations, or how examples from the papers map to API workflows.
