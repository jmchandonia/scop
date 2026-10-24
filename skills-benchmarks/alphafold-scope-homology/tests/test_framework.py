import hashlib
import json
import subprocess
import sys
from pathlib import Path

import httpx
import pytest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT.parents[1] / "mcp" / "src"))
sys.path.insert(0, str(ROOT / "scripts"))

from scop_mcp.app import (
    _compact_alphafold_hit_stream,
    _summarize_alphafold_hit_stream,
    fetch_alphafold_decision_evidence,
)
from scop_mcp.client import ScopRestClient

from fetch_evidence import paths_from_plan  # noqa: E402
from run_codex_mcp_benchmark import definitive_claim_matches  # noqa: E402
from validate import gold_layout_errors  # noqa: E402


GOLD_CLAIM_DISCLOSURE = (
    "Reference assessment only: this is a non-official evidence hypothesis, "
    "not a definitive homology determination or SCOPe classification."
)


def jsonl(path):
    return [json.loads(line) for line in path.read_text().splitlines() if line.strip()]


def frozen_fixture_transport():
    manifest = json.loads((ROOT / "fixtures" / "api" / "manifest.json").read_text())
    fixture_by_path = {
        entry["path"]: ROOT / "fixtures" / "api" / entry["file"]
        for entry in manifest["entries"]
    }

    async def handler(request: httpx.Request) -> httpx.Response:
        path = request.url.path.split("/index.php", 1)[-1]
        if request.url.query:
            path += "?" + request.url.query.decode()
        fixture = fixture_by_path.get(path)
        if fixture is None:
            return httpx.Response(404, json={"title": f"fixture absent: {path}"})
        return httpx.Response(200, content=fixture.read_bytes())

    return httpx.MockTransport(handler)


def compact_sids_by_model(plan, manifest):
    fixture_by_path = {entry["path"]: entry["file"] for entry in manifest["entries"]}
    result = {}
    for model in plan["models"]:
        hit_path = (
            f"/releases/{plan['release']}/alphafolds/{model}/hits"
            f"?limit={plan['hit_limit']}"
        )
        hits = json.loads((ROOT / "fixtures" / "api" / fixture_by_path[hit_path]).read_text())
        model_length = hits["subject"]["sequence_length"]
        compact_sids = set()
        for stream, kind in (
            (hits["sequence_hits"], "sequence"),
            (hits["structure_hits"], "structure"),
        ):
            summarized, groups = _summarize_alphafold_hit_stream(
                stream, kind, model_length
            )
            compact, _ = _compact_alphafold_hit_stream(summarized, groups)
            compact_sids.update(hit["domain"]["sid"] for hit in compact)
        result[model] = compact_sids
    return result


def current_suite_run_count():
    suite_version = json.loads((ROOT / "benchmark.json").read_text())["suite_version"]
    count = 0
    for path in (ROOT / "results" / "runs").iterdir():
        manifest = path / "manifest.json"
        if not path.is_dir() or not manifest.is_file():
            continue
        if json.loads(manifest.read_text()).get("suite_version") == suite_version:
            count += 1
    return count


def test_suite_has_twelve_questions_and_matching_gold_answers():
    questions = jsonl(ROOT / "questions.jsonl")
    gold = jsonl(ROOT / "gold" / "answers.jsonl")
    assert len(questions) == 12
    assert {row["id"] for row in questions} == {row["question_id"] for row in gold}
    assert [row["id"] for row in questions] == [
        f"AFQ{number:03d}" for number in range(1, 13)
    ]
    assert all(row["answer"].startswith(GOLD_CLAIM_DISCLOSURE) for row in gold)
    assert all("\n\nBenchmark question: " in row["answer"] for row in gold)
    for row in gold:
        assert not gold_layout_errors(row["answer"]), row["question_id"]
    assert all("SCCS " in row["answer"] for row in gold)
    assert all(
        "https://scop.berkeley.edu/?sunid=" in row["answer"]
        and f"&ver={row['release']})" in row["answer"]
        for row in gold
    )
    assert all(not definitive_claim_matches(row["answer"]) for row in gold)


def test_frozen_manifest_has_expected_valid_fixtures():
    plan = json.loads((ROOT / "evidence-plan.json").read_text())
    manifest = json.loads((ROOT / "fixtures" / "api" / "manifest.json").read_text())
    assert len(manifest["entries"]) == len(paths_from_plan(plan))
    for entry in manifest["entries"]:
        fixture = ROOT / "fixtures" / "api" / entry["file"]
        assert fixture.is_file()
        assert hashlib.sha256(fixture.read_bytes()).hexdigest() == entry["sha256"]


def test_frozen_domain_evidence_covers_every_compact_hit_representative():
    plan = json.loads((ROOT / "evidence-plan.json").read_text())
    manifest = json.loads((ROOT / "fixtures" / "api" / "manifest.json").read_text())
    missing_by_model = {}

    for model, compact_sids in compact_sids_by_model(plan, manifest).items():
        planned_sids = plan["models"][model]
        missing = sorted(compact_sids - set(planned_sids))
        if missing:
            missing_by_model[model] = missing

    assert missing_by_model == {}


def test_frozen_benchmarks_cover_compact_representative_lineage_nodes():
    plan = json.loads((ROOT / "evidence-plan.json").read_text())
    manifest = json.loads((ROOT / "fixtures" / "api" / "manifest.json").read_text())
    fixture_by_path = {entry["path"]: entry["file"] for entry in manifest["entries"]}
    compact_by_model = compact_sids_by_model(plan, manifest)
    missing_by_model = {}

    for model, planned_benchmarks in plan["benchmark_nodes"].items():
        thresholds = {
            benchmark["minimum_supporting_hits"] for benchmark in planned_benchmarks
        }
        planned = {
            (benchmark["sunid"], benchmark["minimum_supporting_hits"])
            for benchmark in planned_benchmarks
        }
        lineage_sunids = set()
        for sid in compact_by_model[model]:
            parent_path = f"/releases/{plan['release']}/sids/{sid}/parents"
            parents = json.loads(
                (ROOT / "fixtures" / "api" / fixture_by_path[parent_path]).read_text()
            )["parents"]
            lineage_sunids.update(
                parent["sunid"]
                for parent in parents
                if parent.get("level", {}).get("code") in {"cf", "sf", "fa"}
            )
        missing = sorted(
            (sunid, threshold)
            for sunid in lineage_sunids
            for threshold in thresholds
            if (sunid, threshold) not in planned
        )
        if missing:
            missing_by_model[model] = missing

    assert missing_by_model == {}


def test_threshold_sensitivity_questions_freeze_every_supported_stratum():
    plan = json.loads((ROOT / "evidence-plan.json").read_text())
    questions = {row["id"]: row for row in jsonl(ROOT / "questions.jsonl")}

    for question_id, policy in plan["benchmark_threshold_sensitivity"].items():
        assert questions[question_id]["model"] == policy["model"]
        assert questions[question_id]["release"] == plan["release"]
        planned_thresholds = {
            benchmark["minimum_supporting_hits"]
            for benchmark in plan["benchmark_nodes"][policy["model"]]
        }
        assert planned_thresholds == set(policy["minimum_supporting_hits"])


@pytest.mark.asyncio
async def test_afq011_decision_bundle_preserves_envelope_and_resolves_superfamily():
    async with ScopRestClient(
        "https://fixture.test/api/v1/index.php",
        transport=frozen_fixture_transport(),
    ) as client:
        result = await fetch_alphafold_decision_evidence(
            client,
            "AF-P35637-F1",
            [{"start": 262, "end": 382}],
            ["d6snja_", "d2lcwa_", "d1hl6a_", "d1jmta_", "d2cpea1"],
            [
                {
                    "sid": "d2lcwa_",
                    "level": "superfamily",
                    "minimum_supporting_hits": 5,
                }
            ],
            release="2.08",
        )

    region = result["regions"][0]
    assert region["region"]["range"] == {"start": 262, "end": 382, "length": 121}
    assert region["region"]["model_coverage"] == {
        "residues": 121,
        "model_length": 526,
        "fraction": 0.230038,
        "percent": 23.0,
    }
    assert region["confidence_pattern"]["multiple_high_plddt_clusters"] is True
    assert region["confidence_pattern"]["high_plddt_cluster_count"] == 6
    calibration = result["calibrations"][0]
    assert calibration["source_sid"] == "d2lcwa_"
    assert calibration["requested_level"] == "superfamily"
    assert calibration["resolved_node"]["sunid"] == 54928
    assert calibration["resolved_node"]["sccs"] == "d.58.7"
    assert calibration["derived_counts"]["fraction_as_count"] == "46/46"


@pytest.mark.asyncio
async def test_afq004_decision_bundle_resolves_every_reported_superfamily():
    async with ScopRestClient(
        "https://fixture.test/api/v1/index.php",
        transport=frozen_fixture_transport(),
    ) as client:
        result = await fetch_alphafold_decision_evidence(
            client,
            "AF-P00533-F1",
            [
                {"start": 27, "end": 186},
                {"start": 182, "end": 338},
                {"start": 340, "end": 505},
                {"start": 505, "end": 638},
                {"start": 667, "end": 1035},
            ],
            ["d3u7ua3", "d1s78a3", "d2jiub_"],
            [
                {
                    "sid": "d3u7ua3",
                    "level": "superfamily",
                    "minimum_supporting_hits": 2,
                },
                {
                    "sid": "d1s78a3",
                    "level": "superfamily",
                    "minimum_supporting_hits": 2,
                },
                {
                    "sid": "d2jiub_",
                    "level": "superfamily",
                    "minimum_supporting_hits": 5,
                },
            ],
            release="2.08",
        )

    assert [
        (row["resolved_node"]["sunid"], row["resolved_node"]["sccs"])
        for row in result["calibrations"]
    ] == [(52058, "c.10.2"), (57184, "g.3.9"), (56112, "d.144.1")]
    assert result["calibrations"][1]["derived_counts"]["fraction_as_count"] == (
        "1/1"
    )
    assert result["calibrations"][2]["derived_counts"]["fraction_as_count"] == (
        "458/458"
    )


def test_blind_questions_hide_gold_fields():
    blind = jsonl(ROOT / "inputs" / "questions_blind.jsonl")
    assert len(blind) == 12
    forbidden = {"expected_behaviors", "failure_modes", "required_tools", "answer"}
    assert all(not forbidden.intersection(row) for row in blind)


def test_evidence_summary_contains_local_confidence_and_both_hit_types():
    summary = json.loads((ROOT / "gold" / "evidence-summary.json").read_text())
    assert len(summary["models"]) == 8
    for model in summary["models"].values():
        assert model["candidate_regions"]
        assert "sequence_evidence" in model
        assert "sequence_hit_count" in model
        assert "structure_hit_count" in model
        assert "all_sequence_hits" in model
        assert "all_structure_hits" in model
        assert all(
            hit["rank"] == rank
            for rank, hit in enumerate(model["all_sequence_hits"], 1)
        )
        assert all(
            hit["rank"] == rank
            for rank, hit in enumerate(model["all_structure_hits"], 1)
        )
        assert all("confidence" in region for region in model["candidate_regions"])

    q96 = summary["models"]["AF-Q96LI5-F1"]
    assert q96["sequence_evidence"]["status"] == "stored_blast_results_available"
    assert q96["sequence_evidence"]["stored_blast_result_count"] == 31
    assert q96["sequence_hit_count"] == 0
    assert {"52058@3", "56219@3"} <= set(q96["fatcat_chain_benchmarks"])

    brca = summary["models"]["AF-P38398-F1"]
    ring = next(hit for hit in brca["all_sequence_hits"] if hit["sid"] == "d1jm7a_")
    assert ring["rank"] == 24
    assert ring["range"] == {"start": 1, "end": 103, "length": 103}
    assert round(ring["log10_evalue"], 6) == -54.890214
    assert brca["representative_domains"]["d1jm7a_"]["node"]["sccs"] == "g.44.1.1"
    assert any(
        parent.get("sccs") == "g.44.1.1"
        for parent in brca["representative_domains"]["d1jm7a_"]["lineage"][
            "parents"
        ]
    )
    assert [region["range"] for region in brca["candidate_regions"]] == [
        [1, 103],
        [1646, 1757],
        [1755, 1863],
    ]

    assert [
        region["range"]
        for region in summary["models"]["AF-P04637-F1"]["candidate_regions"]
    ] == [[106, 276]]
    assert [
        region["range"]
        for region in summary["models"]["AF-P00533-F1"]["candidate_regions"]
    ] == [
        [27, 186],
        [182, 337],
        [340, 505],
        [505, 638],
        [667, 1035],
    ]


def test_structural_validator_passes_without_a_completed_run():
    result = subprocess.run(
        [
            "python3",
            str(ROOT / "scripts" / "validate.py"),
            "--minimum-completed-runs=0",
        ],
        check=False,
        capture_output=True,
        text=True,
    )
    assert result.returncode == 0, result.stderr


def test_development_validator_allows_no_completed_run_by_default():
    result = subprocess.run(
        ["python3", str(ROOT / "scripts" / "validate.py")],
        check=False,
        capture_output=True,
        text=True,
    )
    assert result.returncode == 0, result.stderr


def test_release_validator_can_require_a_completed_run():
    completed = current_suite_run_count()
    result = subprocess.run(
        [
            "python3",
            str(ROOT / "scripts" / "validate.py"),
            "--minimum-completed-runs=1",
        ],
        check=False,
        capture_output=True,
        text=True,
    )
    if completed:
        assert result.returncode == 0, result.stderr
    else:
        assert result.returncode == 1
        assert "expected at least 1 completed current-suite runs, found 0" in result.stderr


def test_validator_can_enforce_a_minimum_completed_run_count():
    completed = current_suite_run_count()
    result = subprocess.run(
        [
            "python3",
            str(ROOT / "scripts" / "validate.py"),
            f"--minimum-completed-runs={completed + 1}",
        ],
        check=False,
        capture_output=True,
        text=True,
    )
    assert result.returncode == 1
    assert "expected at least" in result.stderr
