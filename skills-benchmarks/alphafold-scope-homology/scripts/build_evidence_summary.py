#!/usr/bin/env python3
import json
import statistics
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def bands(values):
    total = len(values) or 1
    return {
        "median": round(statistics.median(values), 2) if values else None,
        "pct_ge_90": round(100 * sum(v >= 90 for v in values) / total, 2),
        "pct_70_to_90": round(100 * sum(70 <= v < 90 for v in values) / total, 2),
        "pct_50_to_70": round(100 * sum(50 <= v < 70 for v in values) / total, 2),
        "pct_lt_50": round(100 * sum(v < 50 for v in values) / total, 2),
    }


def high_clusters(values, offset=1, minimum=3):
    clusters, start = [], None
    for index, value in enumerate(values, offset):
        if value >= 90 and start is None:
            start = index
        if value < 90 and start is not None:
            if index - start >= minimum:
                clusters.append([start, index - 1])
            start = None
    if start is not None and offset + len(values) - start >= minimum:
        clusters.append([start, offset + len(values) - 1])
    return clusters


def compact_hit(hit, rank, model_length):
    query_range = hit.get("alphafold_range") or {}
    start, end = query_range.get("start"), query_range.get("end")
    inclusive_span = (
        end - start + 1
        if isinstance(start, int) and isinstance(end, int) and end >= start
        else None
    )
    result = {
        "rank": rank,
        "sid": hit["domain"]["sid"],
        "sccs": hit["domain"]["sccs"],
        "description": hit["domain"].get("description"),
        "range": query_range,
        "inclusive_query_span": inclusive_span,
        "model_coverage_fraction": (
            round(inclusive_span / model_length, 6) if inclusive_span else None
        ),
    }
    for key in (
        "log10_evalue",
        "percent_identity",
        "p_value",
        "identity",
        "similarity",
        "score",
        "rmsd",
    ):
        if key in hit:
            result[key] = hit[key]
    return result


def main():
    plan = json.loads((ROOT / "evidence-plan.json").read_text())
    manifest = json.loads((ROOT / "fixtures" / "api" / "manifest.json").read_text())
    by_path = {entry["path"]: entry["file"] for entry in manifest["entries"]}
    release, limit = plan["release"], plan["hit_limit"]
    summary = {"release": release, "models": {}}
    for model, sids in plan["models"].items():
        base = f"/releases/{release}/alphafolds/{model}"
        model_data = json.loads((ROOT / "fixtures" / "api" / by_path[base]).read_text())["alphafold"]
        hits = json.loads((ROOT / "fixtures" / "api" / by_path[f"{base}/hits?limit={limit}"]).read_text())
        plddt = model_data["residue_plddt"]
        regions = []
        for start, end in plan["candidate_regions"][model]:
            values = plddt[start - 1:end]
            regions.append({"range": [start, end], "confidence": bands(values), "high_plddt_clusters": high_clusters(values, start)})
        domains = {}
        for sid in sids:
            domain = f"/releases/{release}/sids/{sid}"
            domains[sid] = {
                "node": json.loads((ROOT / "fixtures" / "api" / by_path[domain]).read_text()).get("node"),
                "lineage": json.loads((ROOT / "fixtures" / "api" / by_path[f"{domain}/parents"]).read_text()),
                "annotations": json.loads((ROOT / "fixtures" / "api" / by_path[f"{domain}/annotations"]).read_text()),
                "homology": json.loads((ROOT / "fixtures" / "api" / by_path[f"{domain}/homology"]).read_text()),
            }
        benchmarks = {}
        for benchmark in plan.get("benchmark_nodes", {}).get(model, []):
            path = (
                f"/releases/{release}/sunids/{benchmark['sunid']}"
                "/fatcat-chain-benchmark?minimum_supporting_hits="
                f"{benchmark['minimum_supporting_hits']}"
            )
            benchmarks[
                f"{benchmark['sunid']}@{benchmark['minimum_supporting_hits']}"
            ] = json.loads(
                (ROOT / "fixtures" / "api" / by_path[path]).read_text()
            )
        summary["models"][model] = {
            "uniprot_accession": model_data["uniprot_accession"],
            "sequence_length": model_data["sequence_length"],
            "global_plddt": model_data.get("global_plddt"),
            "sequence_evidence": model_data.get("sequence_evidence"),
            "whole_model_confidence": bands(plddt),
            "candidate_regions": regions,
            "sequence_hit_count": len(hits["sequence_hits"]),
            "structure_hit_count": len(hits["structure_hits"]),
            "sequence_sccs_counts": dict(Counter(hit["domain"]["sccs"] for hit in hits["sequence_hits"])),
            "structure_sccs_counts": dict(Counter(hit["domain"]["sccs"] for hit in hits["structure_hits"])),
            "top_sequence_hits": [
                compact_hit(hit, rank, model_data["sequence_length"])
                for rank, hit in enumerate(hits["sequence_hits"][:20], 1)
            ],
            "top_structure_hits": [
                compact_hit(hit, rank, model_data["sequence_length"])
                for rank, hit in enumerate(hits["structure_hits"][:20], 1)
            ],
            "all_sequence_hits": [
                compact_hit(hit, rank, model_data["sequence_length"])
                for rank, hit in enumerate(hits["sequence_hits"], 1)
            ],
            "all_structure_hits": [
                compact_hit(hit, rank, model_data["sequence_length"])
                for rank, hit in enumerate(hits["structure_hits"], 1)
            ],
            "representative_domains": domains,
            "fatcat_chain_benchmarks": benchmarks,
        }
    output = ROOT / "gold" / "evidence-summary.json"
    output.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n")
    print(f"wrote evidence summary to {output}")


if __name__ == "__main__":
    main()
