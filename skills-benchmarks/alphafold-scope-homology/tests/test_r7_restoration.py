"""Check r7 evidence collection against its recorded tool responses.

These are offline compatibility tests, not a new model/scientific evaluation.
Only the collection timestamp is excluded from response equality. The public
instructions and runner audit intentionally differ in suite 1.17; fixture
payloads and the evidence-producing MCP implementation remain unchanged. The
fixture-manifest hash is checked after restoring its historical suite label and
skill fingerprint.
"""

import hashlib
import json
from pathlib import Path
import sys

import httpx
import pytest

ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = ROOT.parents[1]
sys.path.insert(0, str(REPO_ROOT / "mcp" / "src"))
sys.path.insert(0, str(ROOT / "scripts"))

from scop_mcp import app
from scop_mcp.client import ScopRestClient
from run_codex_mcp_benchmark import HARNESS_VERSION, tree_sha256, public_skills_dir


RUN = ROOT / "results/runs/2026-09-04_gpt-5-5_pretrained_mcp_skill_r7"
CHECKPOINT = json.loads((RUN / "checkpoint.json").read_text())
QUESTION_IDS = [f"AFQ{number:03d}" for number in range(1, 13)]
FETCHERS = {
    "get_alphafold_hits": app.fetch_alphafold_hits,
    "get_alphafold_decision_evidence": app.fetch_alphafold_decision_evidence,
    "get_scop_domains": app.fetch_scop_domains,
}


def without_collection_timestamps(value):
    if isinstance(value, dict):
        return {
            key: without_collection_timestamps(item)
            for key, item in value.items()
            if key != "fetched_at"
        }
    if isinstance(value, list):
        return [without_collection_timestamps(item) for item in value]
    return value


@pytest.fixture(scope="module")
def frozen_responses():
    manifest = json.loads((ROOT / "fixtures/api/manifest.json").read_text())
    responses = {}
    for entry in manifest["entries"]:
        payload = (ROOT / "fixtures/api" / entry["file"]).read_bytes()
        assert hashlib.sha256(payload).hexdigest() == entry["sha256"]
        responses[entry["path"]] = payload
    assert len(responses) == 355
    return responses


@pytest.mark.parametrize(
    ("key", "path", "is_tree"),
    [
        ("mcp_source_tree_sha256", REPO_ROOT / "mcp/src", True),
        ("fixture_manifest_sha256", ROOT / "fixtures/api/manifest.json", False),
    ],
)
def test_r7_evidence_implementation_matches_recorded_hashes(key, path, is_tree):
    if is_tree:
        actual = tree_sha256(path)
    else:
        manifest = json.loads(path.read_text())
        assert manifest["suite_version"] == json.loads(
            (ROOT / "benchmark.json").read_text()
        )["suite_version"]
        assert manifest["skill_sha256"] == hashlib.sha256(
            (public_skills_dir() / "alphafold-scope-homology/SKILL.md").read_bytes()
        ).hexdigest()
        manifest["suite_version"] = CHECKPOINT["suite_version"]
        manifest["skill_sha256"] = CHECKPOINT["skill_sha256"]
        manifest["openapi_sha256"] = (
            "59c0e33c5477e5ff96c2a62b71967fa33c757c9999ebaec82899dc8a29d9dc8f"
        )
        payload = (json.dumps(manifest, indent=2, sort_keys=True) + "\n").encode()
        actual = hashlib.sha256(payload).hexdigest()
    assert actual == CHECKPOINT[key]


@pytest.mark.asyncio
async def test_r7_default_catalog_has_no_experimental_validator():
    tools = {tool.name: tool for tool in await app.mcp.list_tools()}
    assert len(tools) == 38
    assert "validate_alphafold_decision_record" not in tools
    assert "evidence_packet_handle" not in tools["get_alphafold_hits"].inputSchema["properties"]
    assert "evidence_packet_handle" not in tools["get_alphafold_decision_evidence"].inputSchema["properties"]
    assert CHECKPOINT["harness_version"] == "2.5.1"
    assert CHECKPOINT["suite_version"] == "1.5.0"
    assert HARNESS_VERSION == "2.9.6"
    assert json.loads((ROOT / "benchmark.json").read_text())["suite_version"] == "1.18.4"


@pytest.mark.asyncio
@pytest.mark.parametrize("question_id", QUESTION_IDS)
async def test_r7_two_call_responses_replay_exactly(question_id, frozen_responses):
    events = [
        json.loads(line)
        for line in (RUN / "logs" / f"{question_id}.answer.a1.events.jsonl").read_text().splitlines()
    ]
    calls = [
        event["item"]
        for event in events
        if event.get("type") == "item.completed"
        and event.get("item", {}).get("type") == "mcp_tool_call"
    ]
    assert len(calls) == 2
    assert calls[0]["tool"] == "get_alphafold_hits"
    assert calls[1]["tool"] in {"get_alphafold_decision_evidence", "get_scop_domains"}

    for call in calls:
        assert call["server"] == "scop_local"
        assert call["status"] == "completed"
        assert call.get("error") is None
        expected = call["result"]["structured_content"]
        requested = []

        async def handler(request):
            path = request.url.path.split("/index.php", 1)[-1]
            if request.url.query:
                path += "?" + request.url.query.decode()
            assert path in frozen_responses, f"Unfrozen retrieval: {path}"
            requested.append(str(request.url))
            return httpx.Response(200, content=frozen_responses[path])

        # Stateless HTTP constructs a fresh REST client for each MCP request.
        async with ScopRestClient(
            CHECKPOINT["rest_base_url"], transport=httpx.MockTransport(handler)
        ) as client:
            actual = await FETCHERS[call["tool"]](client, **call["arguments"])

        assert without_collection_timestamps(actual) == without_collection_timestamps(expected)
        assert set(requested) == set(expected["api_urls"])
        assert len(requested) == len(set(requested)), "The restore introduced repeated retrievals"
        rendered_result = app._structured_tool_result(actual)
        assert rendered_result.content == []
        assert rendered_result.structuredContent == actual
        assert "claimability" not in actual
        assert "evidence_packet_handle" not in actual
