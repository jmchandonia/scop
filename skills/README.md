# SCOPe research skills

These skills help research agents retrieve and interpret evidence from the
public SCOPe service. They contain instructions, supporting references, and an
optional comparison helper. They do not require access to the SCOPe database
or server filesystem.

| Skill | Use it for |
| --- | --- |
| [scop-homology](scop-homology/SKILL.md) | SCOPe domain classifications, evolutionary relationships, PDB-chain comparisons, and structural quality. |
| [alphafold-scope-homology](alphafold-scope-homology/SKILL.md) | Evidence-based candidate relationships between AlphaFold model regions and SCOPe domains, with confidence and biological caveats. |

## Install and connect

1. Install either or both complete skill directories using your agent's skill
   installer, or copy them into the skill directory supported by your agent.
   Preserve the `references`, `agents`, and any `scripts` subdirectories.
   The public sources are
   [scop-homology](https://github.com/SCOPe-knowledgebase/scop/tree/main/skills/scop-homology)
   and
   [alphafold-scope-homology](https://github.com/SCOPe-knowledgebase/scop/tree/main/skills/alphafold-scope-homology).
2. In an MCP-capable client, add `https://scop.berkeley.edu/mcp` as a remote
   server using Streamable HTTP. No API key is required. Refresh the server's
   tool list after connecting.
3. Ask your agent to use the appropriate skill and specify the domain, PDB
   chain, or AlphaFold model of interest. Pin a SCOPe release, such as `2.08`,
   when reproducibility matters.

If MCP is unavailable, the skills document equivalent calls to the
[public REST API](https://scop.berkeley.edu/api/v1/), described by its
[OpenAPI specification](https://scop.berkeley.edu/api/v1/openapi.yaml).
The agent needs network access to retrieve current evidence. The optional
`scop-homology/scripts/scop_compare.py` helper uses Python 3.9 or newer and
the standard library; run it with `--help` for usage.

Example requests:

- Use scop-homology to compare two SCOPe domains in release 2.08, showing their
  lineages and any interpretation caveats.
- Use alphafold-scope-homology to assess the evidence for candidate structural
  relationships of AF-Q9BSH3-F1, distinguishing similarity from functional claims.

AlphaFold and unclassified-chain matches are evidence for investigation, not
official SCOPe classifications. Each skill explains the supporting evidence
and limitations that should accompany a result.

## Versions

Each skill carries its version in the `metadata.version` field of its `SKILL.md`. Version 1.0.0 is the unlabelled version benchmarked for the 2026 *Nucleic Acids Research* article (the `skills/` directory at commit bf929356); 1.0.1 adds only the version field. Repository releases are dated (for example `2026-10-27`) and list the skill and benchmark-suite versions they contain.
