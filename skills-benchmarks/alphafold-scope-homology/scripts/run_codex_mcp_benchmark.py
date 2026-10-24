#!/usr/bin/env python3
"""Run and evaluate the blind AlphaFold benchmark with a loopback MCP server."""

from __future__ import annotations

import argparse
from collections import Counter
import concurrent.futures
import hashlib
import html
import json
import sys
import os
import re
import shutil
import subprocess
import tempfile
import time
import urllib.request
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = ROOT.parents[1]
sys.path.insert(0, str(REPO_ROOT / "scripts"))
from scop_public_skills import public_skills_dir
CONDITION = "pretrained_mcp_skill"
DEFAULT_MCP_URL = "http://127.0.0.1:8000/mcp"
HARNESS_VERSION = "2.9.6"
DEFAULT_MAX_MCP_CALLS = 8
CLAIM_STATUS = "non_official_evidence_hypothesis"
CLAIM_DISCLOSURE = (
    "This is a non-official evidence assessment, not a definitive determination "
    "of homology or an official SCOPe classification."
)
MODEL_SCOPED_TOOLS = frozenset(
    {
        "get_alphafold_model",
        "get_alphafold_region_confidence",
        "get_alphafold_regions_confidence",
        "get_alphafold_decision_evidence",
        "get_alphafold_hits",
        "get_alphafold_region_hits",
        "get_alphafold_sequence_hits",
        "get_alphafold_structure_hits",
        "list_alphafold_uniprot_matching_domains",
    }
)
DEFINITIVE_CLAIM_PATTERNS = (
    re.compile(
        r"^decision\s*:\s*(?:this|the)\s+(?:AlphaFold\s+)?"
        r"(?:model|target|region)\s+(?:is|are|belongs|has)\b",
        re.IGNORECASE | re.MULTILINE,
    ),
    re.compile(
        r"^assign\s+(?:only\s+)?(?:the\s+)?"
        r"(?:(?:supported|high-confidence|local)\s+)*"
        r"(?:model|target|region|domains?|intervals?|residues?|AF-[A-Za-z0-9-]+)",
        re.IGNORECASE | re.MULTILINE,
    ),
    re.compile(r"\b(?:I|we)\s+(?:assign|classify)\b", re.IGNORECASE),
    re.compile(
        r"\b(?:is|are|was|were)\s+(?:not\s+)?(?:merely\s+|only\s+)?homologous\b",
        re.IGNORECASE,
    ),
    re.compile(
        r"\b(?:confirm(?:s|ed|ing)?|prov(?:e[sd]?|ing)|establish(?:es|ed|ing)?)"
        r"\s+(?:the\s+)?homology\b",
        re.IGNORECASE,
    ),
    re.compile(
        r"\bhomology\s+(?:is\s+)?(?:confirmed|proven|established|rejected)\b",
        re.IGNORECASE,
    ),
    re.compile(
        r"\b(?:correct|final|supported)\s+SCOPe\s+(?:assignment|classification)\b",
        re.IGNORECASE,
    ),
    re.compile(r"\bhomologous_to_SCOPe\s*=\s*true\b", re.IGNORECASE),
    re.compile(
        r"\bassign(?:ed|ing)?\s+(?:AF-[A-Za-z0-9-]+|the\s+(?:model|region)|residues?\b)",
        re.IGNORECASE,
    ),
    re.compile(
        r"\b(?:the\s+)?(?:AlphaFold\s+)?(?:model|target|region)\s+"
        r"(?:belongs\s+to|is\s+(?:assigned\s+to|classified\s+as)|"
        r"(?:has|does(?:\s+not)?\s+have)\s+homology\s+to)",
        re.IGNORECASE,
    ),
    re.compile(
        r"\bAF-[A-Za-z0-9-]+\s+(?:belongs\s+to|is\s+(?:assigned\s+to|"
        r"classified\s+as)|(?:has|does(?:\s+not)?\s+have)\s+homology\s+to)",
        re.IGNORECASE,
    ),
    re.compile(
        r"\b(?:the\s+)?(?:AlphaFold\s+)?(?:model|target|region|AF-[A-Za-z0-9-]+)"
        r"\s+(?:has|supports?|shows?|demonstrates?)\b[^.\n]{0,50}\bhomology\b",
        re.IGNORECASE,
    ),
    re.compile(
        r"\b(?:final|definitive)\s+"
        r"(?:assignment|classification)\b",
        re.IGNORECASE,
    ),
    re.compile(r"\b(?:homology|classification)\s+level\s*=", re.IGNORECASE),
    re.compile(
        r"\b(?:positive|negative)\s*,?\s+(?:region-specific\s+)?"
        r"(?:homology|classification)\s+(?:call|assignment)\b",
        re.IGNORECASE,
    ),
    re.compile(r"\banswer\s+(?:yes|no)\b[^.\n]{0,80}\bhomology\b", re.IGNORECASE),
    re.compile(
        r"\bsupported\s+SCOPe(?:\s+\S+)?\s+(?:assignment|classification)\b",
        re.IGNORECASE,
    ),
    re.compile(
        r"\b(?:the\s+)?(?:AlphaFold\s+)?(?:model|target|region|this|AF-[A-Za-z0-9-]+)"
        r"\s+(?:is|are)\s+(?!(?:a|an)\s+(?:candidate|possible|putative)\b)"
        r"(?:a|an|in\s+the|the)\b[^.\n,;]{0,80}?\b(?:family|superfamily|domain|fold)\b",
        re.IGNORECASE,
    ),
)
CLAIM_QUALIFIER_RE = re.compile(
    r"\b(?:does\s+not|do\s+not|did\s+not|cannot|can\s+not|"
    r"(?:doesn|don|didn|can|couldn|wouldn|isn|aren|wasn|weren)'t|"
    r"not|no|without|rather\s+than|instead\s+of|neither|nor)\s+"
    r"(?:(?:a|an|the|any|alone|independently|necessarily|reliably|"
    r"currently|yet|definitively|by\s+itself)\s+){0,3}$",
    re.IGNORECASE,
)
NON_TARGET_LEVEL_COMPARISON_RE = re.compile(
    r"\b(?:evidence|assessment|hypothesis|result)\s+"
    r"(?:is\s+|at\s+|as\s+)?(?:family|superfamily|fold)(?:-level)?\s+"
    r"rather\s+than\s+(?:family|superfamily|fold)(?:-level|\s+level)?\b",
    re.IGNORECASE,
)
NON_TARGET_THIS_META_RE = re.compile(
    r"^this\s+(?:is|are)\b[^.\n]{0,80}\b"
    r"(?:answer|assessment|basis|context|evidence|hypothesis|interpretation|"
    r"observation|rationale|reason|result|signal|support)\b",
    re.IGNORECASE,
)
QUALIFIED_LEVEL_HYPOTHESIS_SUFFIX_RE = re.compile(
    r"^(?:-level|\s+level)?\s+(?:evidence\s+)?"
    r"(?:candidate|hypothesis|interpretation|signal)\b",
    re.IGNORECASE,
)
NON_TARGET_FAMILY_EVIDENCE_SUFFIX_RE = re.compile(
    r"^-(?:candidate|discriminating)\s+evidence\b",
    re.IGNORECASE,
)
HIT_PAGE_BASE = (
    "https://brass.lbl.gov/~makelyan/scop-test/scop-newui-php-bootstrap/"
    "alphafold/af_x_domains/index.php"
)
BENCHMARK_PATH_RE = re.compile(
    r"^/releases/([^/]+)/sunids/(\d+)/fatcat-chain-benchmark"
    r"\?minimum_supporting_hits=(\d+)$"
)
BENCHMARK_URL_RE = re.compile(
    r"/releases/([^/]+)/sunids/(\d+)/fatcat-chain-benchmark"
    r"\?minimum_supporting_hits=(\d+)$"
)
HITS_PATH_RE = re.compile(r"^/releases/([^/]+)/alphafolds/([^/]+)/hits\?limit=(\d+)$")
SID_TOKEN_RE = re.compile(r"\bd[0-9][A-Za-z0-9]{3}[A-Za-z0-9_]{2}\b")
TOOL_NAME_RE = re.compile(r"\b(?:get|list|search|resolve|compare)_[a-z0-9_]+\b")
DOMAIN_EVIDENCE_TOOLS = frozenset(
    {
        "get_scop_domain",
        "get_scop_domains",
        "get_scop_domain_lineage",
        "get_scop_domain_annotations",
        "get_scop_domain_homology",
    }
)
DOMAIN_EVIDENCE_INCLUDES = frozenset({"parents", "annotations", "homology"})
BENCHMARK_EVIDENCE_TOOLS = frozenset(
    {"get_scop_fatcat_chain_benchmark", "get_scop_fatcat_chain_benchmarks"}
)
USAGE_KEYS = (
    "input_tokens",
    "cached_input_tokens",
    "cache_write_input_tokens",
    "output_tokens",
    "reasoning_output_tokens",
)

ANSWER_OUTPUT_SCHEMA = {
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "type": "object",
    "additionalProperties": False,
    "required": [
        "answer",
        "evidence_used",
        "representative_sids",
        "claim_status",
        "input_attestation",
    ],
    "properties": {
        "answer": {"type": "string", "minLength": 1},
        "evidence_used": {
            "type": "array",
            "items": {"type": "string", "minLength": 1},
        },
        "representative_sids": {
            "type": "array",
            "maxItems": 6,
            "items": {
                "type": "string",
                "pattern": "^d[0-9][A-Za-z0-9]{3}[A-Za-z0-9_]{2}$",
            },
        },
        "claim_status": {"type": "string", "const": CLAIM_STATUS},
        "input_attestation": {
            "type": "object",
            "additionalProperties": False,
            "required": [
                "question_id",
                "question_sha256",
                "skill_sha256",
                "model",
                "release",
            ],
            "properties": {
                "question_id": {"type": "string", "minLength": 1},
                "question_sha256": {
                    "type": "string",
                    "pattern": "^[a-f0-9]{64}$",
                },
                "skill_sha256": {
                    "type": "string",
                    "pattern": "^[a-f0-9]{64}$",
                },
                "model": {"type": "string", "minLength": 1},
                "release": {"type": "string", "minLength": 1},
            },
        },
    },
}

EVALUATION_OUTPUT_SCHEMA = {
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "type": "object",
    "additionalProperties": False,
    "required": ["score", "criterion_scores", "applied_penalties", "rubric_notes"],
    "properties": {
        "score": {"type": "integer", "minimum": 0, "maximum": 4},
        "criterion_scores": {
            "type": "object",
            "additionalProperties": False,
            "required": [
                "task_resolution",
                "scientific_correctness",
                "evidence_completeness",
                "calibration_and_caveats",
            ],
            "properties": {
                "task_resolution": {"type": "integer", "minimum": 0, "maximum": 4},
                "scientific_correctness": {"type": "integer", "minimum": 0, "maximum": 4},
                "evidence_completeness": {"type": "integer", "minimum": 0, "maximum": 4},
                "calibration_and_caveats": {"type": "integer", "minimum": 0, "maximum": 4},
            },
        },
        "applied_penalties": {
            "type": "array",
            "items": {"type": "string", "minLength": 1},
        },
        "rubric_notes": {"type": "string", "minLength": 1},
    },
}


def utc_now() -> str:
    return (
        datetime.now(timezone.utc)
        .replace(microsecond=0)
        .isoformat()
        .replace("+00:00", "Z")
    )


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def text_sha256(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def tree_sha256(root: Path) -> str:
    digest = hashlib.sha256()
    for path in sorted(
        item
        for item in root.rglob("*")
        if item.is_file()
        and "__pycache__" not in item.parts
        and item.suffix not in {".pyc", ".pyo"}
    ):
        digest.update(path.relative_to(root).as_posix().encode("utf-8"))
        digest.update(b"\0")
        digest.update(path.read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()


def canonical_json_bytes(raw: bytes) -> bytes:
    """Normalize a JSON response exactly as the fixture freezer does."""
    parsed = json.loads(raw)
    return (json.dumps(parsed, indent=2, sort_keys=True) + "\n").encode("utf-8")


def fetch_canonical_json(base_url: str, path: str, timeout: float) -> bytes:
    url = base_url.rstrip("/") + path
    request = urllib.request.Request(
        url,
        headers={
            "Accept": "application/json",
            "User-Agent": "alphafold-scope-homology-benchmark-parity/1",
        },
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        if response.status != 200:
            raise RuntimeError(f"HTTP {response.status} {url}")
        return canonical_json_bytes(response.read())


def verify_fixture_parity(
    fixture_dir: Path,
    manifest: dict,
    base_url: str,
    *,
    timeout: float,
    workers: int,
    fetcher=None,
) -> dict:
    """Fail unless the live REST evidence equals every frozen fixture."""
    fetcher = fetcher or fetch_canonical_json
    entries = manifest.get("entries")
    if not isinstance(entries, list) or not entries:
        raise RuntimeError("fixture manifest has no evidence entries")

    failures = []

    def check(entry: dict) -> dict:
        path = entry["path"]
        fixture_path = fixture_dir / entry["file"]
        expected_manifest_sha = entry["sha256"]
        expected_file_sha = sha256(fixture_path)
        if expected_file_sha != expected_manifest_sha:
            return {
                "path": path,
                "error": "frozen fixture does not match its manifest",
                "expected": expected_manifest_sha,
                "observed": expected_file_sha,
            }
        try:
            frozen_body = canonical_json_bytes(fixture_path.read_bytes())
        except (json.JSONDecodeError, UnicodeDecodeError) as error:
            return {
                "path": path,
                "error": f"frozen fixture is not valid JSON: {error}",
            }
        try:
            live_body = fetcher(base_url, path, timeout)
        except Exception as error:
            return {"path": path, "error": f"live fetch failed: {error}"}
        frozen_sha = hashlib.sha256(frozen_body).hexdigest()
        live_sha = hashlib.sha256(live_body).hexdigest()
        if live_sha != frozen_sha:
            return {
                "path": path,
                "error": "live response differs from frozen fixture",
                "expected": frozen_sha,
                "observed": live_sha,
            }
        return {"path": path}

    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as executor:
        for result in executor.map(check, entries):
            if "error" in result:
                failures.append(result)
    if failures:
        sample = failures[:10]
        suffix = (
            ""
            if len(failures) <= len(sample)
            else f" (+{len(failures) - len(sample)} more)"
        )
        raise RuntimeError(
            "REST/fixture parity preflight failed; no benchmark answers were "
            f"generated: {sample}{suffix}"
        )
    return {
        "status": "passed",
        "checked_entries": len(entries),
        "rest_base_url": base_url.rstrip("/"),
    }


def read_jsonl(path: Path) -> list[dict]:
    return [
        json.loads(line)
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]


def isolated_codex_home(destination: Path) -> None:
    source = Path(os.environ.get("CODEX_HOME", Path.home() / ".codex"))
    auth = source / "auth.json"
    if not auth.is_file():
        raise RuntimeError(f"Codex authentication file is missing: {auth}")
    destination.mkdir(parents=True)
    shutil.copy2(auth, destination / "auth.json")
    for name in (
        "cloud-config-bundle-cache.json",
        "cloud-requirements-cache.json",
        "installation_id",
    ):
        metadata = source / name
        if metadata.is_file():
            shutil.copy2(metadata, destination / name)


def codex_command(
    workspace: Path,
    schema_path: Path,
    response_path: Path,
    model: str,
    reasoning_effort: str,
    mcp_url: str | None,
) -> list[str]:
    command = [
        "codex",
        "exec",
        "--json",
        "--ignore-user-config",
        "--ignore-rules",
        "--disable",
        "memories",
        "--ephemeral",
        "--model",
        model,
        "--config",
        f'model_reasoning_effort="{reasoning_effort}"',
        # The outer Bubblewrap process in run_isolated_codex is the sandbox.
        # A nested Codex sandbox cannot create its mount lock on that outer
        # read-only filesystem, which previously made the inputs unreadable.
        "--dangerously-bypass-approvals-and-sandbox",
        "--cd",
        str(workspace),
        "--skip-git-repo-check",
        "--output-schema",
        str(schema_path),
        "--output-last-message",
        str(response_path),
        "-",
    ]
    if mcp_url is not None:
        insert_at = command.index("--dangerously-bypass-approvals-and-sandbox")
        command[insert_at:insert_at] = [
            "--config",
            f'mcp_servers.scop_local.url="{mcp_url}"',
        ]
    return command


def outer_bwrap_command(
    session_root: Path, workspace: Path, command: list[str]
) -> list[str]:
    """Build the external sandbox that is the run's isolation boundary."""
    return [
        "bwrap",
        "--die-with-parent",
        "--ro-bind",
        "/",
        "/",
        "--dev-bind",
        "/dev",
        "/dev",
        "--proc",
        "/proc",
        # Hide the private repository, then expose only the temporary session.
        "--tmpfs",
        str(REPO_ROOT),
        "--bind",
        str(session_root),
        str(session_root),
        "--chdir",
        str(workspace),
        *command,
    ]


def isolated_environment(session_root: Path, codex_home: Path | None = None) -> dict:
    environment = os.environ.copy()
    runtime_tmp = session_root / "runtime-tmp"
    runtime_tmp.mkdir(exist_ok=True)
    environment["TMPDIR"] = str(runtime_tmp)
    environment["TMP"] = str(runtime_tmp)
    environment["TEMP"] = str(runtime_tmp)
    if codex_home is not None:
        environment["CODEX_HOME"] = str(codex_home)
    return environment


def verify_workspace_inputs(
    session_root: Path,
    workspace: Path,
    expected_hashes: dict[str, str],
) -> None:
    """Fail before model execution if the external sandbox cannot read inputs."""
    hash_binary = shutil.which("sha256sum")
    if hash_binary is None:
        raise RuntimeError("sha256sum is required for the isolated-input preflight")
    paths = list(expected_hashes)
    result = subprocess.run(
        outer_bwrap_command(session_root, workspace, [hash_binary, *paths]),
        text=True,
        capture_output=True,
        env=isolated_environment(session_root),
        cwd=workspace,
        timeout=30,
    )
    if result.returncode:
        raise RuntimeError(
            "isolated workspace input preflight failed:\n"
            f"{result.stdout}\n{result.stderr}"
        )
    observed = {}
    for line in result.stdout.splitlines():
        digest, separator, path = line.partition("  ")
        if separator:
            observed[path] = digest
    if observed != expected_hashes:
        raise RuntimeError(
            f"isolated workspace input hash mismatch: expected={expected_hashes}, "
            f"observed={observed}"
        )


def run_isolated_codex(
    *,
    session_root: Path,
    workspace: Path,
    prompt: str,
    schema: dict,
    model: str,
    reasoning_effort: str,
    mcp_url: str | None,
    timeout: float,
) -> tuple[dict, str, str]:
    schema_path = workspace / "output.schema.json"
    response_path = session_root / "response.json"
    schema_path.write_text(
        json.dumps(schema, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    codex_home = session_root / "codex-home"
    isolated_codex_home(codex_home)
    command = codex_command(
        workspace,
        schema_path,
        response_path,
        model,
        reasoning_effort,
        mcp_url,
    )
    wrapped = outer_bwrap_command(session_root, workspace, command)
    environment = isolated_environment(session_root, codex_home)
    try:
        result = subprocess.run(
            wrapped,
            input=prompt,
            text=True,
            capture_output=True,
            env=environment,
            cwd=workspace,
            timeout=timeout,
        )
    except subprocess.TimeoutExpired as error:
        raise RuntimeError(f"Codex session timed out after {timeout:g}s") from error
    if result.returncode:
        raise RuntimeError(
            f"Codex exited {result.returncode}:\n{result.stdout}\n{result.stderr}"
        )
    if not response_path.is_file():
        raise RuntimeError("Codex did not write its structured response")
    return (
        json.loads(response_path.read_text(encoding="utf-8")),
        result.stdout,
        result.stderr,
    )


def mcp_call_records(event_log: str) -> list[dict]:
    """Return terminal MCP calls with normalized names and success status."""
    calls = []
    for line in event_log.splitlines():
        try:
            event = json.loads(line)
        except json.JSONDecodeError:
            continue
        if event.get("type") != "item.completed":
            continue
        item = event.get("item")
        if not isinstance(item, dict):
            continue
        if item.get("type") != "mcp_tool_call":
            continue
        name = item.get("tool") or item.get("name")
        if not isinstance(name, str):
            continue
        if "__" in name:
            name = name.rsplit("__", 1)[-1]
        result = item.get("result")
        structured = {}
        if isinstance(result, dict):
            structured = result.get("structured_content") or result.get(
                "structuredContent"
            )
            if not isinstance(structured, dict):
                structured = {}
        source_urls = structured.get("api_urls", [])
        if not isinstance(source_urls, list):
            source_urls = []
        successful = (
            item.get("status") == "completed"
            and item.get("error") is None
            and not (isinstance(result, dict) and result.get("isError") is True)
        )
        arguments = item.get("arguments")
        arguments = arguments if isinstance(arguments, dict) else {}
        calls.append(
            {
                "server": item.get("server"),
                "tool": name,
                "arguments": arguments,
                "status": item.get("status"),
                "successful": successful,
                "source_urls": [url for url in source_urls if isinstance(url, str)],
                "citation_urls": citation_urls_from_result(structured, arguments),
            }
        )
    return calls


def mcp_tools_used(event_log: str) -> set[str]:
    return {
        call["tool"]
        for call in mcp_call_records(event_log)
        if call["successful"] and call["server"] == "scop_local"
    }


def event_types_used(event_log: str) -> set[str]:
    types = set()
    for line in event_log.splitlines():
        try:
            event = json.loads(line)
        except json.JSONDecodeError:
            continue
        if event.get("type") != "item.completed":
            continue
        item = event.get("item")
        if isinstance(item, dict) and isinstance(item.get("type"), str):
            types.add(item["type"])
    return types


def codex_usage(event_log: str) -> dict[str, int]:
    """Extract the terminal Codex usage record without counting partial events."""
    usage = {key: 0 for key in USAGE_KEYS}
    for line in event_log.splitlines():
        try:
            event = json.loads(line)
        except json.JSONDecodeError:
            continue
        if event.get("type") != "turn.completed" or not isinstance(
            event.get("usage"), dict
        ):
            continue
        for key in USAGE_KEYS:
            value = event["usage"].get(key, 0)
            if isinstance(value, int) and not isinstance(value, bool) and value >= 0:
                usage[key] += value
    return usage


def sum_usage(records: list[dict]) -> dict[str, int]:
    return {
        key: sum(record.get("usage", {}).get(key, 0) for record in records)
        for key in USAGE_KEYS
    }


def benchmark_requirements(gold_answer: dict) -> set[tuple[str, int, int]]:
    requirements = set()
    for path in gold_answer.get("evidence_paths", []):
        match = BENCHMARK_PATH_RE.fullmatch(path)
        if match:
            release, sunid, threshold = match.groups()
            requirements.add((release, int(sunid), int(threshold)))
    return requirements


def explicitly_named_benchmark_sunids(question: dict) -> set[int]:
    """Return only SUNIDs disclosed in the blind question itself."""
    prompt = question.get("prompt", "")
    if not isinstance(prompt, str):
        return set()
    return {
        int(value)
        for value in re.findall(r"\bSUNID\s*(?:[:=#]\s*)?(\d+)\b", prompt, re.I)
    }


def benchmark_call_tuples(
    calls: list[dict],
) -> set[tuple[str | None, int | str | None, int | None]]:
    """Normalize direct and lineage-resolved benchmark calls for auditing."""
    records = set()
    for call in calls:
        if call["tool"] == "get_scop_fatcat_chain_benchmark":
            requests = [call["arguments"]]
        elif call["tool"] == "get_scop_fatcat_chain_benchmarks":
            requests = call["arguments"].get("requests") or []
        elif call["tool"] == "get_alphafold_decision_evidence":
            resolved_records = set()
            for url in call.get("source_urls", []):
                match = BENCHMARK_URL_RE.search(url)
                if match:
                    release, sunid, threshold = match.groups()
                    resolved_records.add((release, int(sunid), int(threshold)))
            if resolved_records:
                records.update(resolved_records)
                continue
            requests = call["arguments"].get("calibration_requests") or []
            for request in requests:
                if not isinstance(request, dict):
                    continue
                records.add(
                    (
                        call["arguments"].get("release"),
                        f"{request.get('sid')}@{request.get('level')}",
                        request.get("minimum_supporting_hits"),
                    )
                )
            continue
        else:
            continue
        for request in requests:
            if not isinstance(request, dict):
                continue
            sunid = request.get("sunid")
            if isinstance(sunid, str) and sunid.isdigit():
                sunid = int(sunid)
            records.add(
                (
                    call["arguments"].get("release"),
                    sunid,
                    request.get("minimum_supporting_hits"),
                )
            )
    return records


def hit_requirements(gold_answer: dict) -> set[tuple[str, str, int]]:
    requirements = set()
    for path in gold_answer.get("evidence_paths", []):
        match = HITS_PATH_RE.fullmatch(path)
        if match:
            release, model, limit = match.groups()
            requirements.add((release, model, int(limit)))
    return requirements


def cited_representatives(response: dict) -> set[str]:
    representatives = response.get("representative_sids", [])
    if not isinstance(representatives, list):
        return set()
    return {
        sid
        for sid in representatives
        if isinstance(sid, str) and SID_TOKEN_RE.fullmatch(sid)
    }


def required_tools_for_question(private_question: dict) -> set[str]:
    required = set(private_question["required_tools"])
    # Combined hits already fetches the model for coverage and now embeds its
    # compact metadata, so a second model-tool call would repeat the same REST
    # endpoint and evidence.
    if "get_alphafold_hits" in required:
        required.discard("get_alphafold_model")
    if required.intersection(BENCHMARK_EVIDENCE_TOOLS):
        required.difference_update(BENCHMARK_EVIDENCE_TOOLS)
        required.add("get_scop_fatcat_chain_benchmarks")
    # One get_scop_domains call can retrieve every shortlisted base record and
    # all three evidence sections concurrently. Treat legacy domain-evidence
    # declarations as one batch MCP call while auditing its `includes` below.
    if required.intersection(DOMAIN_EVIDENCE_TOOLS):
        required.difference_update(DOMAIN_EVIDENCE_TOOLS)
        required.add("get_scop_domains")
    expected_text = " ".join(private_question.get("expected_behaviors", [])).lower()
    confidence_is_material = private_question.get("category") not in {
        None,
        "architecture_query",
        "pagination_sensitivity",
    }
    if (
        confidence_is_material
        or "plddt" in expected_text
        or "local confidence" in expected_text
    ):
        required.discard("get_alphafold_region_confidence")
        required.add("get_alphafold_regions_confidence")
    # For a regional assessment, the joined tool replaces separate confidence,
    # representative-domain, and prespecified-calibration calls. It resolves
    # calibration SUNIDs from the exact requested lineage level.
    if confidence_is_material and "get_scop_domains" in required:
        required.discard("get_scop_domains")
        required.discard("get_alphafold_regions_confidence")
        required.discard("get_scop_fatcat_chain_benchmarks")
        required.add("get_alphafold_decision_evidence")
    return required


def tool_capabilities(tools: set[str]) -> set[str]:
    """Expand strict supersets without rewriting the actual call ledger."""
    capabilities = set(tools)
    if "get_alphafold_decision_evidence" in tools:
        capabilities.update(
            {
                "get_alphafold_regions_confidence",
                "get_scop_domains",
            }
        )
    return capabilities


def definitive_claim_matches(answer: str) -> list[str]:
    # Negation applies to the immediately following claim phrase, not the whole
    # sentence. In particular, "the target is not homologous" is still a
    # definitive negative target claim: the "not" is inside the match.
    text = answer.replace("’", "'").replace("**", "")
    matches = []
    for pattern in DEFINITIVE_CLAIM_PATTERNS:
        for match in pattern.finditer(text):
            prefix = re.split(r"[.!?;,\n|]", text[: match.start()])[-1]
            matched_text = match.group(0)
            if not (
                CLAIM_QUALIFIER_RE.search(prefix)
                or NON_TARGET_LEVEL_COMPARISON_RE.search(matched_text)
                or NON_TARGET_THIS_META_RE.search(matched_text)
                or QUALIFIED_LEVEL_HYPOTHESIS_SUFFIX_RE.search(
                    text[match.end():match.end() + 40]
                )
                or NON_TARGET_FAMILY_EVIDENCE_SUFFIX_RE.search(
                    text[match.end():match.end() + 40]
                )
            ):
                matches.append(matched_text)
    return sorted(set(matches), key=str.lower)


def normalize_citation_url(url: str) -> str:
    """Compare destinations, including SCOPe's documented path-style node URLs."""
    try:
        parts = urlsplit(html.unescape(url))
    except ValueError:
        return "invalid-url:" + url
    scheme = parts.scheme.lower()
    host = parts.netloc.lower()
    path, query = parts.path, parts.query
    # https://scop.berkeley.edu/help/ documents /sunid=ID&ver=RELEASE;
    # repository REST records use /?sunid=ID&ver=RELEASE. They identify the
    # same node. Apply this equivalence only to pinned SCOPe node links.
    if host == "scop.berkeley.edu" and scheme in {"http", "https"}:
        node_query = query if path in {"", "/"} else ""
        if not query:
            node_query = path.removeprefix("/")
        parameters = parse_qsl(node_query, keep_blank_values=True)
        keys = {key for key, _ in parameters}
        if len(parameters) == 2 and any(
            keys == {identifier, "ver"} for identifier in ("sunid", "sid", "sccs")
        ):
            scheme, path, query = "https", "/", node_query
    return urlunsplit(
        (scheme, host, path,
         urlencode(sorted(parse_qsl(query, keep_blank_values=True))), parts.fragment)
    )


def citation_urls_from_result(result: dict, arguments: dict) -> list[str]:
    """Derive citations only from returned evidence, never the private gold.

    This does not change MCP payloads or perform additional retrieval. Hit links
    require a returned alignment with its stream; node links require a returned
    identifier and the result's release. Merely requesting a SID is insufficient.
    """
    sources = result.get("api_urls", [])
    urls = (
        {url for url in sources if isinstance(url, str)}
        if isinstance(sources, list) else set()
    )
    release = result.get("release")
    model = arguments.get("model") or result.get("model")
    rest_release_bases = set()
    if release:
        marker = f"/releases/{release}"
        for url in urls:
            try:
                parts = urlsplit(url)
            except ValueError:
                continue
            prefix, separator, suffix = parts.path.partition(marker)
            if separator and (not suffix or suffix.startswith("/")):
                rest_release_bases.add(
                    urlunsplit(
                        (parts.scheme, parts.netloc, prefix + marker, "", "")
                    )
                )

    def visit(value):
        if isinstance(value, list):
            for child in value:
                visit(child)
        elif isinstance(value, dict):
            if value.get("status") == "error":
                return
            if release:
                for key in ("sunid", "sid", "sccs"):
                    identifier = value.get(key)
                    if key == "sunid":
                        valid = isinstance(identifier, int) and not isinstance(identifier, bool)
                    else:
                        valid = isinstance(identifier, str) and bool(identifier)
                    if valid:
                        urls.add(
                            "https://scop.berkeley.edu/?"
                            + urlencode({key: identifier, "ver": release})
                        )
                        if key == "sunid":
                            urls.update(
                                f"{base}/sunids/{identifier}"
                                for base in rest_release_bases
                            )
            domain = value.get("domain")
            sid = domain.get("sid") if isinstance(domain, dict) else None
            mode = {"sequence": "seq", "structure": "struct"}.get(value.get("kind"))
            if model and sid and mode and "alphafold_range" in value:
                urls.add(
                    HIT_PAGE_BASE + "?"
                    + urlencode({"af": model, "mode": mode, "hit": sid})
                )
            for child in value.values():
                visit(child)

    visit(result)
    return sorted(urls)


def audit_answer_citations(answer: str, calls: list[dict]) -> dict:
    """Check cited evidence items and source provenance, not scientific entailment."""
    allowed_urls = {
        normalize_citation_url(url)
        for call in calls
        if call["successful"] and call["server"] == "scop_local"
        for url in call.get("citation_urls", call["source_urls"])
    }
    # URLs in code are examples/literals, not clickable source citations.
    prose = re.sub(r"(?ms)^\s*(`{3,}|~{3,}).*?^\s*\1\s*$", "", answer)
    prose = re.sub(r"`+[^`\n]*`+", "", prose)
    references = {
        key.strip().lower(): url
        for key, url in re.findall(
            r"(?m)^\s*\[([^\]]+)\]:\s*<?(https?://[^\s>]+)>?", prose
        )
    }
    start = re.search(
        r"(?im)^[ \t#*]*(?:3[.)]\s*)?Evidence considered\b[^\n]*\n", prose
    )
    issues = []
    if not start:
        return {
            "items": [],
            "issues": ["citation audit: missing Evidence considered section"],
        }
    evidence = prose[start.end():]
    end = re.search(
        r"(?im)^[ \t#*]*(?:4[.)]\s*)?Caveats\b", evidence
    )
    if end:
        evidence = evidence[:end.start()]
    # Definitions may be shared, but an item must actually reference one.
    evidence = re.sub(r"(?m)^\s*\[[^\]]+\]:[^\n]*", "", evidence)
    markers = list(re.finditer(
        r"(?m)^[ \t]*(?:[-+*]\s+)?(?:\*\*)?(E[1-9]\d*)"
        r"(?:\*\*)?(?=[\s.:—–-])", evidence
    ))
    if not markers:
        issues.append("citation audit: evidence must be listed with E1, E2, etc.")
    items = []
    for index, marker in enumerate(markers):
        identifier = marker.group(1)
        limit = markers[index + 1].start() if index + 1 < len(markers) else len(evidence)
        body = evidence[marker.end():limit]
        urls = re.findall(r"https?://[^\s<>\[\]`\"')]+", body)
        for label, reference in re.findall(r"\[([^\]]+)\]\[([^\]]*)\]", body):
            url = references.get((reference or label).strip().lower())
            if url:
                urls.append(url)
        for reference in re.findall(r"\[([^\]]+)\](?![\[(])", body):
            url = references.get(reference.strip().lower())
            if url:
                urls.append(url)
        urls = sorted({url.rstrip(".,;:!") for url in urls})
        unknown = [url for url in urls if normalize_citation_url(url) not in allowed_urls]
        if not urls:
            issues.append(f"citation audit: {identifier} lacks a clickable source URL")
        elif unknown:
            issues.append(
                f"citation audit: {identifier} cites unreturned sources: {unknown}"
            )
        items.append({"id": identifier, "urls": urls, "unreturned_urls": unknown})
    if len({item["id"] for item in items}) != len(items):
        issues.append("citation audit: evidence IDs must be unique")
    return {"items": items, "issues": issues}


def audit_answer_attempt(
    *,
    question: dict,
    private_question: dict,
    gold_answer: dict,
    response: dict,
    event_log: str,
    question_sha256: str,
    skill_sha256: str,
    rest_base_url: str,
    max_mcp_calls: int = DEFAULT_MAX_MCP_CALLS,
    allowed_source_urls: set[str] | None = None,
) -> dict:
    """Apply fail-closed input, claim, and MCP semantic checks."""
    issues = []
    calls = mcp_call_records(event_log)
    foreign_mcp_calls = [
        {
            "server": call["server"],
            "tool": call["tool"],
            "status": call["status"],
        }
        for call in calls
        if call["server"] != "scop_local"
    ]
    if foreign_mcp_calls:
        issues.append(f"non-scop_local MCP calls detected: {foreign_mcp_calls}")
    successful_calls = [
        call for call in calls if call["successful"] and call["server"] == "scop_local"
    ]
    failed_calls = [call for call in calls if not call["successful"]]
    successful_tools = {call["tool"] for call in successful_calls}
    successful_capabilities = tool_capabilities(successful_tools)
    if len(calls) > max_mcp_calls:
        issues.append(
            f"MCP call budget exceeded: observed {len(calls)}, allowed {max_mcp_calls}"
        )

    expected_source_prefix = rest_base_url.rstrip("/") + "/"
    missing_source_urls = [
        {"tool": call["tool"], "arguments": call["arguments"]}
        for call in successful_calls
        if not call["source_urls"]
    ]
    wrong_source_urls = sorted(
        {
            url
            for call in successful_calls
            for url in call["source_urls"]
            if not url.startswith(expected_source_prefix)
        }
    )
    unfrozen_source_urls = sorted(
        {
            url
            for call in successful_calls
            for url in call["source_urls"]
            if allowed_source_urls is not None and url not in allowed_source_urls
        }
    )
    if missing_source_urls:
        issues.append(
            "successful scop_local calls did not attest REST source URLs: "
            f"{missing_source_urls}"
        )
    if wrong_source_urls:
        issues.append(
            "scop_local calls used a REST source outside the parity-checked base: "
            f"{wrong_source_urls}"
        )
    if unfrozen_source_urls:
        issues.append(
            "scop_local calls used REST evidence absent from the parity-checked "
            f"fixture manifest: {unfrozen_source_urls}"
        )

    required_tools = required_tools_for_question(private_question)
    missing_tools = sorted(required_tools - successful_capabilities)
    if missing_tools:
        issues.append(f"missing successful required MCP calls: {missing_tools}")

    evidence_items = response.get("evidence_used", [])
    evidence_text = "\n".join(item for item in evidence_items if isinstance(item, str))
    reported_tools = set(TOOL_NAME_RE.findall(evidence_text))
    reported_capabilities = tool_capabilities(reported_tools)
    unreported_required_tools = sorted(required_tools - reported_capabilities)
    uncalled_reported_tools = sorted(reported_tools - successful_tools)
    if unreported_required_tools:
        issues.append(
            "required successful calls missing from evidence_used: "
            f"{unreported_required_tools}"
        )
    if uncalled_reported_tools:
        issues.append(
            f"evidence_used reports calls that did not succeed: {uncalled_reported_tools}"
        )

    representatives = response.get("representative_sids", [])
    if isinstance(representatives, list) and len(representatives) != len(
        set(representatives)
    ):
        issues.append("representative_sids must not contain duplicates")

    expected_attestation = {
        "question_id": question["id"],
        "question_sha256": question_sha256,
        "skill_sha256": skill_sha256,
        "model": question["model"],
        "release": question["release"],
    }
    observed_attestation = response.get("input_attestation")
    if observed_attestation != expected_attestation:
        issues.append(
            "input attestation mismatch: "
            f"expected={expected_attestation}, observed={observed_attestation}"
        )

    if response.get("claim_status") != CLAIM_STATUS:
        issues.append(f"claim_status must be {CLAIM_STATUS!r}")
    answer = response.get("answer", "")
    if not answer.startswith(CLAIM_DISCLOSURE):
        issues.append("answer is missing the mandatory non-definitive disclosure")
    definitive_matches = definitive_claim_matches(answer)
    if definitive_matches:
        issues.append(f"definitive claim language detected: {definitive_matches}")
    citation_audit = audit_answer_citations(answer, calls)
    issues.extend(citation_audit["issues"])

    forbidden_events = sorted(event_types_used(event_log).intersection({"web_search"}))
    if forbidden_events:
        issues.append(f"forbidden event types used: {forbidden_events}")

    off_target_calls = []
    wrong_release_calls = []
    for call in calls:
        arguments = call["arguments"]
        if (
            call["tool"] in MODEL_SCOPED_TOOLS
            and arguments.get("model") != question["model"]
        ):
            off_target_calls.append(
                {
                    "tool": call["tool"],
                    "model": arguments.get("model"),
                    "status": call["status"],
                }
            )
        if "release" in arguments and arguments["release"] != question["release"]:
            wrong_release_calls.append(
                {
                    "tool": call["tool"],
                    "release": arguments["release"],
                    "status": call["status"],
                }
            )
    if off_target_calls:
        issues.append(f"off-target AlphaFold calls detected: {off_target_calls}")
    if wrong_release_calls:
        issues.append(f"wrong-release MCP calls detected: {wrong_release_calls}")

    target_required_tools = required_tools.intersection(MODEL_SCOPED_TOOLS)
    successful_target_tools = {
        call["tool"]
        for call in successful_calls
        if call["tool"] in MODEL_SCOPED_TOOLS
        and call["arguments"].get("model") == question["model"]
        and call["arguments"].get("release") == question["release"]
    }
    missing_target_tools = sorted(target_required_tools - successful_target_tools)
    if missing_target_tools:
        issues.append(
            "required model-scoped calls did not successfully use the exact target: "
            f"{missing_target_tools}"
        )

    required_hits = hit_requirements(gold_answer)
    observed_hits = {
        (
            call["arguments"].get("release"),
            call["arguments"].get("model"),
            call["arguments"].get("limit"),
        )
        for call in successful_calls
        if call["tool"] == "get_alphafold_hits"
    }
    missing_hits = sorted(required_hits - observed_hits)
    if missing_hits:
        issues.append(f"missing planned combined-hit calls: {missing_hits}")

    required_representatives = {
        (question["release"], sid) for sid in cited_representatives(response)
    }
    observed_representative_calls = {
        (call["arguments"].get("release"), call["arguments"].get("sid"))
        for call in successful_calls
        if call["tool"] == "get_scop_domain"
        and set(call["arguments"].get("includes") or []).issuperset(
            DOMAIN_EVIDENCE_INCLUDES
        )
    }
    observed_representative_calls.update(
        (call["arguments"].get("release"), sid)
        for call in successful_calls
        if call["tool"] == "get_scop_domains"
        and set(call["arguments"].get("includes") or []).issuperset(
            DOMAIN_EVIDENCE_INCLUDES
        )
        for sid in (call["arguments"].get("sids") or [])
        if isinstance(sid, str)
    )
    observed_representative_calls.update(
        (call["arguments"].get("release"), sid)
        for call in successful_calls
        if call["tool"] == "get_alphafold_decision_evidence"
        for sid in (call["arguments"].get("sids") or [])
        if isinstance(sid, str)
    )
    missing_representative_calls = sorted(
        (release, sid)
        for release, sid in required_representatives
        if (release, sid) not in observed_representative_calls
    )
    if missing_representative_calls:
        issues.append(
            "missing compound representative calls with includes="
            f"{sorted(DOMAIN_EVIDENCE_INCLUDES)}: {missing_representative_calls}"
        )

    required_benchmarks = benchmark_requirements(gold_answer)
    attempted_benchmarks = benchmark_call_tuples(calls)
    observed_benchmarks = benchmark_call_tuples(successful_calls)
    required_benchmark_counts = Counter(
        (release, threshold) for release, _, threshold in required_benchmarks
    )
    observed_benchmark_counts = Counter(
        (release, threshold) for release, _, threshold in observed_benchmarks
    )
    missing_benchmark_counts = sorted(
        (
            release,
            threshold,
            required_count - observed_benchmark_counts[(release, threshold)],
        )
        for (release, threshold), required_count in required_benchmark_counts.items()
        if observed_benchmark_counts[(release, threshold)] < required_count
    )
    if missing_benchmark_counts:
        issues.append(
            "missing planned benchmark strata by release, threshold, and count: "
            f"{missing_benchmark_counts}"
        )

    named_sunids = explicitly_named_benchmark_sunids(question)
    explicitly_required_benchmarks = {
        requirement
        for requirement in required_benchmarks
        if requirement[1] in named_sunids
    }
    missing_benchmarks = sorted(
        explicitly_required_benchmarks - observed_benchmarks
    )
    if missing_benchmarks:
        issues.append(
            f"missing explicitly named benchmark calls: {missing_benchmarks}"
        )

    allowed_release_thresholds = set(required_benchmark_counts)
    wrong_planned_benchmarks = sorted(
        attempted
        for attempted in attempted_benchmarks
        if (attempted[0], attempted[2]) not in allowed_release_thresholds
    )
    if wrong_planned_benchmarks:
        if required_benchmark_counts:
            issues.append(
                "wrong threshold used for planned benchmark calls: "
                f"{wrong_planned_benchmarks}"
            )
        else:
            issues.append(
                "benchmark calls made without a prespecified calibration stratum: "
                f"{wrong_planned_benchmarks}"
            )

    return {
        "issues": issues,
        "used_tools": sorted(successful_tools),
        "missing_tools": missing_tools,
        "reported_tools": sorted(reported_tools),
        "unreported_required_tools": unreported_required_tools,
        "uncalled_reported_tools": uncalled_reported_tools,
        "successful_call_count": len(successful_calls),
        "failed_call_count": len(failed_calls),
        "total_call_count": len(calls),
        "max_mcp_calls": max_mcp_calls,
        "off_target_calls": off_target_calls,
        "wrong_release_calls": wrong_release_calls,
        "foreign_mcp_calls": foreign_mcp_calls,
        "expected_rest_base_url": rest_base_url.rstrip("/"),
        "missing_source_urls": missing_source_urls,
        "wrong_source_urls": wrong_source_urls,
        "unfrozen_source_urls": unfrozen_source_urls,
        "required_hits": sorted(required_hits),
        "observed_hits": sorted(observed_hits),
        "missing_hits": missing_hits,
        "required_representatives": sorted(required_representatives),
        "missing_representative_calls": missing_representative_calls,
        "required_benchmarks": sorted(required_benchmarks),
        "required_benchmark_counts": sorted(
            (release, threshold, count)
            for (release, threshold), count in required_benchmark_counts.items()
        ),
        "observed_benchmarks": sorted(observed_benchmarks),
        "observed_benchmark_counts": sorted(
            (release, threshold, count)
            for (release, threshold), count in observed_benchmark_counts.items()
        ),
        "missing_benchmarks": missing_benchmarks,
        "missing_benchmark_counts": missing_benchmark_counts,
        "forbidden_events": forbidden_events,
        "definitive_claim_matches": definitive_matches,
        "citation_audit": citation_audit,
        "input_attestation": observed_attestation,
    }


def generator_prompt(
    question: dict, max_mcp_calls: int = DEFAULT_MAX_MCP_CALLS
) -> str:
    return f"""You are generating one blind AlphaFold-SCOPe benchmark answer.

The workspace contains exactly one record in questions_blind.jsonl and the
public alphafold-scope-homology skill. Read
alphafold-scope-homology/SKILL.md once. Do not preload every reference: read
only a reference that the skill says is material to this exact decision.
The same blind question is embedded below so the target cannot be lost if a
file tool fails. It is the only authorized AlphaFold target for this session;
never infer a target from a directory name or temporary-path suffix.
Use only the registered `scop_local` MCP server. Do not use web
search, direct HTTP, SQL, repository paths, model memory for release-specific
facts, MCP discovery, or any non-MCP data source. Keep release
{question['release']} throughout.

Before making MCP calls, form a short task contract: exact target, exact
question being tested, every explicitly named representative or region,
requested contrasts, metrics, provenance facts, calibration thresholds,
required caveats, and requested output. Reconcile every item before writing the
answer: report and cite it, or explicitly state that it is unavailable and how
that limits the conclusion. Do not substitute another representative for one
named by the question. Compute SHA-256 for `questions_blind.jsonl` and
`alphafold-scope-homology/SKILL.md` from the workspace. Return those hashes,
the question's exact ID and model, and its release in `input_attestation`. If
either file cannot be read, do not infer or improvise an answer; report the
access failure, which will intentionally fail this attempt.

Follow the skill's question-specific evidence requirements, minimal evidence
workflow, decision rules, and response structure.
Use no more than {max_mcp_calls} MCP calls. Issue independent MCP calls together
in the same turn and do not send progress
messages. Start with one
`get_alphafold_hits` call using `limit=100`, `compact=true`, and
`include_transforms=false`; its `alphafold` object already contains compact
model metadata. Do not repeat it, call `get_alphafold_model`, or call the
separate sequence/structure endpoints for the same evidence except for the
focused completeness case below. Read hit rank,
`model_coverage`, raw/grouped counts, and overlap-SCCS grouping fields rather
than manually deriving or silently counting them.

The compact response must not hide evidence required to answer the question.
Repeat `get_alphafold_hits` once with the same model, release, and limit 100 but
`compact=false` only when the question asks about a rank window not enumerated
by compact representatives, or when summary frequencies show a
question-relevant classification but no compact record gives its representative
and metrics. Inspect only the affected stream from that raw response. Do not use
this exception just to collect more corroborating hits.
For a missing classification contrast, interpret classification at the exact
level named by the question. For a class-level contrast, shortlist only the
single highest-ranked raw representative across that class, not one per fold,
superfamily, or family below it. Do not put more than one raw-only SID in the
decision bundle unless the question explicitly requires several alternatives.
For a rank-window question, use raw
records to account for the requested ranks but do not add raw-only SIDs to the
decision bundle unless one changes the regional relationship and its lineage is
necessary to answer the question.

For a regional assessment where local confidence affects the answer, shortlist
at most one representative per materially distinct evidence stream, region,
competing lineage, or warning, up to six SIDs total. Preserve up to six material
ranges, including the full hit union/envelope when it differs from a tighter
representative core. Retrieve them together with one
`get_alphafold_decision_evidence(model=..., regions=[...], sids=[...],
calibration_requests=[...])` call. Do not follow it with standalone confidence,
domain, lineage, annotation, homology, or benchmark calls. Each optional
calibration request must contain the representative `sid`, the exact
relationship `level` you will report (`family`, `superfamily`, or `fold`), and
the question's literal `minimum_supporting_hits`; the tool resolves the SUNID
from that SID's retrieved lineage. If the blind question gives no threshold, or
FATCAT does not materially affect that hypothesis, omit the calibration request
and do not silently default to 2.

For a narrow pagination, provenance, or architecture question where confidence
cannot change the answer, use one `get_scop_domains` compound call instead when
lineage or warnings are needed.
In `evidence_used`, list the actual MCP calls used, including concise
identifying arguments.

Put the exact shortlisted domain identifiers in `representative_sids`. A SID
mentioned only because it appeared inside a retrieved annotation, as an
excluded or down-weighted hit, or as hit-list context is not shortlisted and
must not be added to that field. Every SID in `representative_sids` must be
present in the successful decision-evidence or `get_scop_domains` call in this
session.

Before returning, verify that the answer directly resolves the tested
proposition and every requested contrast. Preserve the union or envelope of
material hit ranges, identify overlaps, and keep boundaries approximate.
Report explicit coverage arithmetic for material hits. When confidence matters,
use the regional median, all four pLDDT bands, and material sustained
low-confidence stretches. Use `confidence_pattern` to report fragmented
high-pLDDT clusters when material, but do not use those clusters as a fold or
boundary criterion. Report raw versus conceptually nonredundant evidence when
it changes the decision. A representative's family label, an automated match,
or redundant structure-only hits are not by themselves family-discriminating
evidence.

An automated or placeholder structural family label supports only its
non-automated higher lineage; it does not independently confirm family-level
sequence evidence. Family-level convergence requires non-automated,
family-discriminating sequence and structure representatives, unless a
near-exact, near-full-length sequence alignment independently discriminates the
family and structure supports at least the same superfamily. Do not downgrade a
genuinely supported family hypothesis merely to sound non-definitive; express
the supported specificity as a hypothesis or strong chance and retain every
material caveat.

For each interpreted chain-benchmark fraction, inspect the current hit's
`chain_benchmark_applicability`. If it is
`outside_benchmark_filters` or `uncertain_missing_filter_inputs`, say so and
do not describe a high fraction as reassuring or as "no warning." Otherwise
give the exact prespecified threshold, status, fraction, denominator,
`requested_level`, and `resolved_node`. If no benchmark is applicable, do not
invent one.
Request calibration for the single strongest relationship level retained in
each regional conclusion. A narrower family named only as reference context or
a qualified candidate does not make family the calibration level.

No evidence set permits a final conclusion about target homology, official
classification, exact boundaries, or function. State only the strongest
defensible evidence hypothesis and all material residual uncertainty. When the
support is strong at the reported relationship level, you may say "the evidence
supports a strong chance of a [node/level] relationship for approximately
residues [range]"; this is qualitative, not a probability or assignment.

Use the skill's response structure and citation requirements. In the Evidence
considered section, write each item as `- **E1** — observation and interpretation.
Source: [source label](URL)` (then E2, etc.). Every item must contain a clickable
source URL; an MCP call number alone fails the citation audit. Use the skill's
hit-page template with the returned model, SID, and seq/struct stream for hit
evidence. For lineage, use a returned SUNID and this release in a SCOPe node link;
for confidence, annotations, provenance, and calibration, copy the relevant URL
from the successful MCP result's `api_urls`. Reference-style Markdown links are
also valid. Public SCOPe node links must be release-qualified exactly as
`https://scop.berkeley.edu/sunid={{SUNID}}&ver={{RELEASE}}`; never emit an
unversioned `sunid` link. Do not fetch a page just to cite it, cite an unreturned source, put
URLs only in `evidence_used`, or put them in code. Check every E item for its link
before returning. Preserve only
material annotations and warnings. Every release-specific fact and conclusion
basis in the conclusion or level tables must point to the relevant E item. Before
finalizing each controlling regional conclusion, check that it
contains: the material envelope and overlaps; representative core ranges and
coverage; rank, significance, identity, and aligned extent for selected hits;
FATCAT score and RMSD when returned; lineage through the claimed node and
higher-level agreement via `lineage_by_level`; material competing branches;
decision-changing confidence patterns, redundancy, annotations, and partner or
repeat caveats; and exact level-resolved calibration. Check all applicable caveat
categories required by the question or capable of changing the assessment,
including incomplete searches, pagination, partial coverage, boundary overlap,
correlation, competing lineages, automated/fragment/repeat warnings, confidence
and missing PAE/pTM, sequence/template dependence, partner context, FATCAT
limitations, benchmark applicability and sampling, and non-transfer of function.
Omit an item only when it cannot affect this question. Never omit a named
representative, required contrast, material alternative, negative result,
provenance fact, or required caveat. Omit unrelated evidence and repeated caveats. Copy
hit metrics from MCP results rather than recomputing or substituting a nearby
hit.

Apply the following final checks when relevant. For an empty sequence result,
report exact UniProt and query-sequence availability, stored-BLAST status and
row count, and qualifying/returned-hit count without conflating stored raw rows
with filtered hits. For a rank-window question, account for every requested
rank. For global-versus-local confidence, report numeric global pLDDT and the
whole-model distribution, then local median, all four bands, and material
clusters or low-confidence stretches for each distinct region separately. For
a classification contrast, report the strongest evidence on both sides rather
than only the winning interpretation.

Set `claim_status` to `{CLAIM_STATUS}`. Begin `answer` with this exact sentence:
"{CLAIM_DISCLOSURE}"
Never say that the AlphaFold model or a region is homologous, not homologous,
assigned, or classified. State only what the current evidence is consistent
with, favors, does not support, or leaves inconclusive.

BLIND QUESTION
{json.dumps(question, indent=2, sort_keys=True)}

Return only the schema-conforming answer object.
"""


def run_answer_question(
    question: dict,
    private_question: dict,
    gold_answer: dict,
    run_id: str,
    model: str,
    reasoning_effort: str,
    mcp_url: str,
    rest_base_url: str,
    timeout: float,
    max_attempts: int,
    max_mcp_calls: int,
    allowed_source_urls: set[str],
    logs_dir: Path,
) -> dict:
    last_error = None
    attempts = []
    question_started = time.monotonic()
    existing_attempts = len(
        list(logs_dir.glob(f"{question['id']}.answer.a*.audit.json"))
    )
    for local_attempt in range(1, max_attempts + 1):
        attempt = existing_attempts + local_attempt
        attempt_started = time.monotonic()
        events = ""
        stderr = ""
        with tempfile.TemporaryDirectory(prefix="af-scope-answer-") as session_name:
            session_root = Path(session_name)
            workspace = session_root / "workspace"
            workspace.mkdir()
            question_path = workspace / "questions_blind.jsonl"
            question_path.write_text(
                json.dumps(question, separators=(",", ":")) + "\n",
                encoding="utf-8",
            )
            shutil.copytree(
                public_skills_dir() / "alphafold-scope-homology",
                workspace / "alphafold-scope-homology",
            )
            skill_path = workspace / "alphafold-scope-homology" / "SKILL.md"
            expected_input_hashes = {
                "questions_blind.jsonl": sha256(question_path),
                **{
                    path.relative_to(workspace).as_posix(): sha256(path)
                    for path in sorted(
                        (workspace / "alphafold-scope-homology").rglob("*")
                    )
                    if path.is_file()
                    and "__pycache__" not in path.parts
                    and path.suffix not in {".pyc", ".pyo"}
                },
            }
            try:
                verify_workspace_inputs(session_root, workspace, expected_input_hashes)
                response, events, stderr = run_isolated_codex(
                    session_root=session_root,
                    workspace=workspace,
                    prompt=generator_prompt(question, max_mcp_calls),
                    schema=ANSWER_OUTPUT_SCHEMA,
                    model=model,
                    reasoning_effort=reasoning_effort,
                    mcp_url=mcp_url,
                    timeout=timeout,
                )
                audit = audit_answer_attempt(
                    question=question,
                    private_question=private_question,
                    gold_answer=gold_answer,
                    response=response,
                    event_log=events,
                    question_sha256=expected_input_hashes["questions_blind.jsonl"],
                    skill_sha256=expected_input_hashes[
                        "alphafold-scope-homology/SKILL.md"
                    ],
                    rest_base_url=rest_base_url,
                    max_mcp_calls=max_mcp_calls,
                    allowed_source_urls=allowed_source_urls,
                )
                attempts.append(
                    {
                        "attempt": attempt,
                        "events": events,
                        "stderr": stderr,
                        "duration_seconds": round(
                            time.monotonic() - attempt_started, 3
                        ),
                        "usage": codex_usage(events),
                        **audit,
                    }
                )
                write_attempt_logs(logs_dir, question["id"], [attempts[-1]])
                if audit["issues"]:
                    last_error = RuntimeError("; ".join(audit["issues"]))
                    continue
                timestamp = utc_now()
                return {
                    "record": {
                        "run_id": run_id,
                        "question_id": question["id"],
                        "condition": CONDITION,
                        "model": model,
                        "answer": response["answer"],
                        "evidence_used": response["evidence_used"],
                        "claim_status": response["claim_status"],
                        "input_attestation": response["input_attestation"],
                        "timestamp": timestamp,
                    },
                    "attempts": attempts,
                    "used_tools": audit["used_tools"],
                    "audit": audit,
                    "performance": {
                        "attempt_count": len(attempts),
                        "duration_seconds": round(
                            time.monotonic() - question_started, 3
                        ),
                        "usage": sum_usage(attempts),
                        "mcp_call_count": sum(
                            item.get("total_call_count", 0) for item in attempts
                        ),
                        "rejected_attempts": [
                            {
                                "attempt": item["attempt"],
                                "issues": item.get("issues")
                                or ([item["error"]] if item.get("error") else []),
                            }
                            for item in attempts[:-1]
                        ],
                    },
                }
            except (KeyError, ValueError, json.JSONDecodeError, RuntimeError) as error:
                last_error = error
                used_tools = sorted(mcp_tools_used(events))
                attempts.append(
                    {
                        "attempt": attempt,
                        "events": events,
                        "stderr": stderr,
                        "duration_seconds": round(
                            time.monotonic() - attempt_started, 3
                        ),
                        "usage": codex_usage(events),
                        "used_tools": used_tools,
                        "total_call_count": len(mcp_call_records(events)),
                        "missing_tools": sorted(
                            required_tools_for_question(private_question)
                            - set(used_tools)
                        ),
                        "error": str(error),
                    }
                )
                write_attempt_logs(logs_dir, question["id"], [attempts[-1]])
    raise RuntimeError(
        f"{question['id']} failed after {max_attempts} new attempts: {last_error}"
    )


def evaluator_prompt(
    question: dict,
    gold_answer: dict,
    answer: dict,
    rubric: str,
    semantic_audit: dict,
) -> str:
    private_case = {
        key: question[key]
        for key in (
            "id",
            "prompt",
            "expected_behaviors",
            "failure_modes",
        )
    }
    candidate = {
        "answer": answer["answer"],
        "evidence_used": answer["evidence_used"],
        "claim_status": answer.get("claim_status"),
    }
    deterministic_contract = {
        "required_tools_satisfied": not semantic_audit.get("missing_tools"),
        "required_combined_hit_calls": semantic_audit.get("required_hits", []),
        "required_benchmark_calls": semantic_audit.get("required_benchmarks", []),
        "representative_compound_calls_satisfied": not semantic_audit.get(
            "missing_representative_calls"
        ),
        "hard_audit_issues": semantic_audit.get("issues", []),
    }
    return f"""You are an independent benchmark evaluator. Score one candidate
answer from 0 to 4. Apply the rubric exactly, including every cap and mandatory
penalty. Check the case's expected behaviors and failure modes against the gold
answer. Do not infer credit from the benchmark condition or model identity;
those fields are intentionally omitted. Treat the gold answer as a reference
for evidence content, not as an exact-match key or authorization for definitive
wording. The draft gold is non-exhaustive: give full credit to scientifically
equivalent representative SIDs, evidence-supported interval envelopes, and
harmless rounding. Penalize a difference only when it materially contradicts
the supplied case or evidence, not merely because it differs from the gold's
example. Treat the deterministic contract below as authoritative: do not claim
that a planned benchmark, tool, target, or attestation is missing when its
corresponding requirement is absent or marked satisfied. The candidate must
independently satisfy the rubric's non-definitive claim boundary. Explain
concrete strengths, omissions, errors, and penalties in rubric_notes. Score
task resolution, scientific correctness, evidence completeness, and
calibration/caveats separately before selecting the rubric's final integer
score. List every actual penalty in applied_penalties; use an empty list when
none applies.

RUBRIC
{rubric}

PRIVATE CASE
{json.dumps(private_case, indent=2, sort_keys=True)}

GOLD ANSWER
{json.dumps(gold_answer, indent=2, sort_keys=True)}

CANDIDATE ANSWER
{json.dumps(candidate, indent=2, sort_keys=True)}

DETERMINISTIC CASE CONTRACT
{json.dumps(deterministic_contract, indent=2, sort_keys=True)}

Return only the schema-conforming evaluation object.
"""


def run_evaluation(
    question: dict,
    gold_answer: dict,
    answer: dict,
    semantic_audit: dict,
    rubric: str,
    run_id: str,
    evaluator_model: str,
    reasoning_effort: str,
    timeout: float,
) -> dict:
    started = time.monotonic()
    with tempfile.TemporaryDirectory(prefix="af-scope-evaluate-") as session_name:
        session_root = Path(session_name)
        workspace = session_root / "workspace"
        workspace.mkdir()
        response, events, stderr = run_isolated_codex(
            session_root=session_root,
            workspace=workspace,
            prompt=evaluator_prompt(
                question, gold_answer, answer, rubric, semantic_audit
            ),
            schema=EVALUATION_OUTPUT_SCHEMA,
            model=evaluator_model,
            reasoning_effort=reasoning_effort,
            mcp_url=None,
            timeout=timeout,
        )
    return {
        "record": {
            "run_id": run_id,
            "question_id": question["id"],
            "score": response["score"],
            "criterion_scores": response["criterion_scores"],
            "applied_penalties": response["applied_penalties"],
            "rubric_notes": response["rubric_notes"],
            "evaluator": evaluator_model,
            "timestamp": utc_now(),
        },
        "events": events,
        "stderr": stderr,
        "performance": {
            "duration_seconds": round(time.monotonic() - started, 3),
            "usage": codex_usage(events),
        },
    }


def write_attempt_logs(logs_dir: Path, question_id: str, attempts: list[dict]) -> None:
    for attempt in attempts:
        prefix = logs_dir / f"{question_id}.answer.a{attempt['attempt']}"
        Path(f"{prefix}.events.jsonl").write_text(
            attempt.get("events", ""), encoding="utf-8"
        )
        Path(f"{prefix}.stderr.txt").write_text(
            attempt.get("stderr", ""), encoding="utf-8"
        )
        Path(f"{prefix}.audit.json").write_text(
            json.dumps(
                {
                    key: value
                    for key, value in attempt.items()
                    if key not in {"events", "stderr"}
                },
                indent=2,
                sort_keys=True,
            )
            + "\n",
            encoding="utf-8",
        )


def reconcile_answer_performance(
    result: dict, logs_dir: Path, question_id: str
) -> dict:
    """Rebuild attempt totals from durable logs, including prior resumes."""
    attempts = []
    for path in logs_dir.glob(f"{question_id}.answer.a*.audit.json"):
        try:
            attempt = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            continue
        if isinstance(attempt, dict) and isinstance(attempt.get("attempt"), int):
            attempts.append(attempt)
    attempts.sort(key=lambda item: item["attempt"])
    if not attempts:
        return result

    rejected_attempts = []
    for attempt in attempts:
        issues = attempt.get("issues")
        if not isinstance(issues, list):
            issues = []
        if not issues and attempt.get("error"):
            issues = [str(attempt["error"])]
        if issues:
            rejected_attempts.append(
                {"attempt": attempt["attempt"], "issues": issues}
            )

    result["performance"] = {
        "attempt_count": len(attempts),
        "duration_seconds": round(
            sum(
                value
                for attempt in attempts
                if isinstance(
                    value := attempt.get("duration_seconds"), (int, float)
                )
            ),
            3,
        ),
        "usage": sum_usage(attempts),
        "mcp_call_count": sum(
            attempt.get("total_call_count", 0)
            for attempt in attempts
            if isinstance(attempt.get("total_call_count", 0), int)
        ),
        "rejected_attempts": rejected_attempts,
    }
    return result


def markdown_cell(value: object) -> str:
    return str(value).replace("|", "\\|").replace("\n", " ").strip()


def render_summary(
    evaluations: list[dict],
    answer_results: dict[str, dict],
    evaluation_results: dict[str, dict] | None = None,
) -> str:
    """Render an actionable score report rather than only an aggregate."""
    scores = [record["score"] for record in evaluations]
    total = sum(scores)
    distribution = {score: scores.count(score) for score in range(5)}
    criterion_names = (
        "task_resolution",
        "scientific_correctness",
        "evidence_completeness",
        "calibration_and_caveats",
    )
    criterion_means = {
        name: sum(record["criterion_scores"][name] for record in evaluations)
        / len(evaluations)
        for name in criterion_names
        if all(name in record.get("criterion_scores", {}) for record in evaluations)
    }
    audits = [result["audit"] for result in answer_results.values()]
    successful_calls = sum(audit["successful_call_count"] for audit in audits)
    failed_calls = sum(audit["failed_call_count"] for audit in audits)
    answer_performance = [
        result.get("performance", {}) for result in answer_results.values()
    ]
    evaluation_performance = [
        result.get("performance", {})
        for result in (evaluation_results or {}).values()
    ]
    usage = sum_usage(answer_performance + evaluation_performance)
    attempts = sum(item.get("attempt_count", 1) for item in answer_performance)
    rejected_attempts = max(0, attempts - len(answer_results))
    pass_at_1 = sum(
        item.get("attempt_count", 1) == 1 for item in answer_performance
    )
    max_attempts_observed = max(
        (item.get("attempt_count", 1) for item in answer_performance),
        default=1,
    )
    model_seconds = sum(
        item.get("duration_seconds", 0)
        for item in answer_performance + evaluation_performance
    )
    attempted_mcp_calls = sum(
        item.get("mcp_call_count", 0) for item in answer_performance
    )
    if not attempted_mcp_calls:
        attempted_mcp_calls = successful_calls + failed_calls
    lines = [
        "# Benchmark Summary",
        "",
        "## Outcome",
        "",
        f"- Mean score: {total / len(scores):.2f}/4 across {len(scores)} questions.",
        f"- Total points: {total}/{4 * len(scores)}.",
        "- Score distribution: "
        + ", ".join(f"{score}: {distribution[score]}" for score in range(4, -1, -1))
        + ".",
        *(
            [
                "- Criterion means: "
                + ", ".join(
                    f"{name.replace('_', ' ')} {value:.2f}/4"
                    for name, value in criterion_means.items()
                )
                + "."
            ]
            if criterion_means
            else []
        ),
        "",
        "## Harness validity",
        "",
        f"- Input attestation and semantic audit passed: {len(audits)}/{len(audits)}.",
        f"- Successful `scop_local` MCP calls in accepted attempts: {successful_calls}.",
        f"- Failed MCP calls in accepted attempts: {failed_calls}.",
        f"- Pass@1: {pass_at_1}/{len(answer_results)}; "
        f"pass@{max_attempts_observed}: {len(answer_results)}/{len(answer_results)}.",
        f"- Accepted answer attempts: {len(answer_results)}; rejected/retried: {rejected_attempts}.",
        "- Every accepted answer used the non-official evidence-hypothesis claim status.",
        "",
        "## Efficiency",
        "",
        f"- Input tokens: {usage['input_tokens']:,} ({usage['cached_input_tokens']:,} cached).",
        f"- Output tokens: {usage['output_tokens']:,} ({usage['reasoning_output_tokens']:,} reasoning).",
        f"- Cumulative model-session time: {model_seconds:.1f} seconds.",
        f"- MCP calls in accepted attempts: {successful_calls + failed_calls}; across all attempts: {attempted_mcp_calls}.",
        "",
        "## Per-question assessment",
        "",
        "| Question | Score | Attempts | MCP calls | Input tokens | What went well / what did not |",
        "|---|---:|---:|---:|---:|---|",
    ]
    for record in evaluations:
        result = answer_results.get(record["question_id"], {})
        performance = result.get("performance", {})
        audit = result.get("audit", {})
        mcp_calls = performance.get(
            "mcp_call_count",
            audit.get("successful_call_count", 0) + audit.get("failed_call_count", 0),
        )
        lines.append(
            f"| {markdown_cell(record['question_id'])} | {record['score']}/4 | "
            f"{performance.get('attempt_count', 1)} | "
            f"{mcp_calls} | "
            f"{performance.get('usage', {}).get('input_tokens', 0):,} | "
            f"{markdown_cell(record['rubric_notes'])} |"
        )
    retry_rows = [
        (question_id, retry)
        for question_id, result in answer_results.items()
        for retry in result.get("performance", {}).get("rejected_attempts", [])
    ]
    if retry_rows:
        lines.extend(
            [
                "",
                "## Invalid attempts retried",
                "",
                "| Question | Attempt | Deterministic rejection reason |",
                "|---|---:|---|",
            ]
        )
        for question_id, retry in retry_rows:
            lines.append(
                f"| {markdown_cell(question_id)} | {retry['attempt']} | "
                f"{markdown_cell('; '.join(retry.get('issues', [])))} |"
            )
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default="gpt-5.5")
    parser.add_argument("--evaluator-model", default="gpt-5.6-sol")
    parser.add_argument(
        "--reasoning-effort", choices=("low", "medium", "high"), default="medium"
    )
    parser.add_argument(
        "--evaluation-reasoning-effort",
        choices=("low", "medium", "high"),
        default="low",
    )
    parser.add_argument("--jobs", type=int, default=4)
    parser.add_argument("--mcp-url", default=DEFAULT_MCP_URL)
    parser.add_argument(
        "--rest-base-url",
        help=(
            "REST base used by the MCP server; defaults to the frozen fixture "
            "manifest base and is checked for exact fixture parity"
        ),
    )
    parser.add_argument("--preflight-timeout", type=float, default=60)
    parser.add_argument(
        "--preflight-jobs",
        type=int,
        default=16,
        help="Parallel REST requests used only for the frozen-fixture parity check.",
    )
    parser.add_argument("--answer-timeout", type=float, default=1800)
    parser.add_argument("--evaluation-timeout", type=float, default=900)
    parser.add_argument(
        "--max-attempts",
        type=int,
        default=2,
        help="Maximum new attempts per unfinished answer on this invocation.",
    )
    parser.add_argument(
        "--max-mcp-calls",
        type=int,
        default=DEFAULT_MAX_MCP_CALLS,
        help="Fail an answer attempt that exceeds this total MCP-call budget.",
    )
    parser.add_argument("--results-dir", type=Path, default=ROOT / "results" / "runs")
    parser.add_argument("--run-id")
    args = parser.parse_args()
    if (
        min(
            args.jobs,
            args.answer_timeout,
            args.evaluation_timeout,
            args.max_attempts,
            args.max_mcp_calls,
            args.preflight_timeout,
            args.preflight_jobs,
        )
        <= 0
    ):
        parser.error("jobs, timeouts, max-attempts, and max-mcp-calls must be positive")

    benchmark = json.loads((ROOT / "benchmark.json").read_text(encoding="utf-8"))
    blind_questions = read_jsonl(ROOT / "inputs" / "questions_blind.jsonl")
    private_questions = {
        item["id"]: item for item in read_jsonl(ROOT / "questions.jsonl")
    }
    gold_answers = {
        item["question_id"]: item
        for item in read_jsonl(ROOT / "gold" / "answers.jsonl")
    }
    fixture_dir = ROOT / "fixtures" / "api"
    fixture_manifest_path = fixture_dir / "manifest.json"
    fixture_manifest = json.loads(fixture_manifest_path.read_text(encoding="utf-8"))
    rest_base_url = args.rest_base_url or fixture_manifest["base_url"]
    allowed_source_urls = {
        rest_base_url.rstrip("/") + entry["path"]
        for entry in fixture_manifest["entries"]
    }
    model_label = args.model.replace("/", "-").replace(".", "-")
    run_id = args.run_id or (
        f"{datetime.now(timezone.utc).date().isoformat()}_"
        f"{model_label}_{CONDITION}_r2"
    )
    results_dir = args.results_dir.resolve()
    run_dir = results_dir / run_id
    if run_dir.exists():
        raise RuntimeError(f"run directory already exists: {run_dir}")
    fixture_parity = verify_fixture_parity(
        fixture_dir,
        fixture_manifest,
        rest_base_url,
        timeout=args.preflight_timeout,
        workers=args.preflight_jobs,
    )
    print(
        "fixture parity passed: "
        f"{fixture_parity['checked_entries']} responses at {rest_base_url}",
        flush=True,
    )
    results_dir.mkdir(parents=True, exist_ok=True)
    work_dir = ROOT / "results" / "work" / run_id
    logs_dir = work_dir / "logs"
    answer_checkpoints = work_dir / "checkpoints" / "answers"
    evaluation_checkpoints = work_dir / "checkpoints" / "evaluations"
    checkpoint_path = work_dir / "checkpoint.json"
    logs_dir.mkdir(parents=True, exist_ok=True)
    answer_checkpoints.mkdir(parents=True, exist_ok=True)
    evaluation_checkpoints.mkdir(parents=True, exist_ok=True)
    checkpoint_contract = {
        "run_id": run_id,
        "harness_version": HARNESS_VERSION,
        "suite_version": benchmark["suite_version"],
        "condition": CONDITION,
        "model": args.model,
        "evaluator_model": args.evaluator_model,
        "reasoning_effort": args.reasoning_effort,
        "evaluation_reasoning_effort": args.evaluation_reasoning_effort,
        "max_mcp_calls": args.max_mcp_calls,
        "preflight_jobs": args.preflight_jobs,
        "mcp_url": args.mcp_url,
        "rest_base_url": rest_base_url,
        "fixture_parity": fixture_parity,
        "questions_sha256": sha256(ROOT / "questions.jsonl"),
        "blind_questions_sha256": sha256(ROOT / "inputs" / "questions_blind.jsonl"),
        "fixture_manifest_sha256": sha256(ROOT / "fixtures" / "api" / "manifest.json"),
        "gold_answers_sha256": sha256(ROOT / "gold" / "answers.jsonl"),
        "rubric_sha256": sha256(ROOT / "rubric.md"),
        "skill_sha256": sha256(
            public_skills_dir() / "alphafold-scope-homology" / "SKILL.md"
        ),
        "skill_tree_sha256": tree_sha256(
            public_skills_dir() / "alphafold-scope-homology"
        ),
        "mcp_source_tree_sha256": tree_sha256(REPO_ROOT / "mcp" / "src"),
        "runner_sha256": sha256(Path(__file__)),
    }
    if checkpoint_path.is_file():
        checkpoint = json.loads(checkpoint_path.read_text(encoding="utf-8"))
        mismatches = {
            key: (checkpoint.get(key), value)
            for key, value in checkpoint_contract.items()
            if checkpoint.get(key) != value
        }
        if mismatches:
            raise RuntimeError(f"resume checkpoint does not match: {mismatches}")
        started_at = checkpoint["started_at"]
        print(f"resuming checkpoint {work_dir}", flush=True)
    else:
        started_at = utc_now()
        checkpoint = {**checkpoint_contract, "started_at": started_at}
        checkpoint_path.write_text(
            json.dumps(checkpoint, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

    answer_results = {}
    for question in blind_questions:
        path = answer_checkpoints / f"{question['id']}.json"
        if path.is_file():
            loaded_result = json.loads(
                path.read_text(encoding="utf-8")
            )
            answer_results[question["id"]] = reconcile_answer_performance(
                loaded_result, logs_dir, question["id"]
            )
            path.write_text(
                json.dumps(
                    answer_results[question["id"]], indent=2, sort_keys=True
                )
                + "\n",
                encoding="utf-8",
            )
    pending_answers = [
        question for question in blind_questions if question["id"] not in answer_results
    ]
    if answer_results:
        print(
            f"loaded {len(answer_results)} answer checkpoints; "
            f"{len(pending_answers)} remain",
            flush=True,
        )
    answer_failures = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.jobs) as executor:
        futures = {
            executor.submit(
                run_answer_question,
                question,
                private_questions[question["id"]],
                gold_answers[question["id"]],
                run_id,
                args.model,
                args.reasoning_effort,
                args.mcp_url,
                rest_base_url,
                args.answer_timeout,
                args.max_attempts,
                args.max_mcp_calls,
                allowed_source_urls,
                logs_dir,
            ): question["id"]
            for question in pending_answers
        }
        for future in concurrent.futures.as_completed(futures):
            question_id = futures[future]
            try:
                result = future.result()
            except Exception as error:
                answer_failures[question_id] = str(error)
                print(f"failed answer {question_id}: {error}", flush=True)
                continue
            answer_results[question_id] = {
                "record": result["record"],
                "used_tools": result["used_tools"],
                "audit": result["audit"],
                "performance": result["performance"],
            }
            reconcile_answer_performance(
                answer_results[question_id], logs_dir, question_id
            )
            (answer_checkpoints / f"{question_id}.json").write_text(
                json.dumps(answer_results[question_id], indent=2, sort_keys=True)
                + "\n",
                encoding="utf-8",
            )
            print(f"checkpointed answer {question_id}", flush=True)
    if answer_failures:
        (work_dir / "answer-failures.json").write_text(
            json.dumps(answer_failures, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        raise RuntimeError(
            f"{len(answer_failures)} answers remain unfinished; resume {work_dir}"
        )

    ordered_answers = [
        answer_results[question["id"]]["record"] for question in blind_questions
    ]
    with (work_dir / "answers.jsonl").open("w", encoding="utf-8") as handle:
        for record in ordered_answers:
            handle.write(json.dumps(record, separators=(",", ":")) + "\n")
    print(f"answer checkpoint complete: {len(ordered_answers)} records", flush=True)

    rubric = (ROOT / "rubric.md").read_text(encoding="utf-8")
    evaluation_results = {}
    for question in blind_questions:
        path = evaluation_checkpoints / f"{question['id']}.json"
        if path.is_file():
            evaluation_results[question["id"]] = json.loads(
                path.read_text(encoding="utf-8")
            )
    pending_evaluations = [
        answer
        for answer in ordered_answers
        if answer["question_id"] not in evaluation_results
    ]
    if evaluation_results:
        print(
            f"loaded {len(evaluation_results)} evaluation checkpoints; "
            f"{len(pending_evaluations)} remain",
            flush=True,
        )
    evaluation_failures = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.jobs) as executor:
        futures = {
            executor.submit(
                run_evaluation,
                private_questions[answer["question_id"]],
                gold_answers[answer["question_id"]],
                answer,
                answer_results[answer["question_id"]]["audit"],
                rubric,
                run_id,
                args.evaluator_model,
                args.evaluation_reasoning_effort,
                args.evaluation_timeout,
            ): answer["question_id"]
            for answer in pending_evaluations
        }
        for future in concurrent.futures.as_completed(futures):
            question_id = futures[future]
            try:
                result = future.result()
            except Exception as error:
                evaluation_failures[question_id] = str(error)
                print(f"failed evaluation {question_id}: {error}", flush=True)
                continue
            evaluation_results[question_id] = {
                "record": result["record"],
                "performance": result["performance"],
            }
            (logs_dir / f"{question_id}.evaluation.events.jsonl").write_text(
                result["events"], encoding="utf-8"
            )
            (logs_dir / f"{question_id}.evaluation.stderr.txt").write_text(
                result["stderr"], encoding="utf-8"
            )
            (evaluation_checkpoints / f"{question_id}.json").write_text(
                json.dumps(evaluation_results[question_id], indent=2, sort_keys=True)
                + "\n",
                encoding="utf-8",
            )
            print(f"checkpointed evaluation {question_id}", flush=True)
    if evaluation_failures:
        (work_dir / "evaluation-failures.json").write_text(
            json.dumps(evaluation_failures, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        raise RuntimeError(
            f"{len(evaluation_failures)} evaluations remain unfinished; "
            f"resume {work_dir}"
        )

    ordered_evaluations = [
        evaluation_results[question["id"]]["record"] for question in blind_questions
    ]
    with (work_dir / "evaluation.jsonl").open("w", encoding="utf-8") as handle:
        for record in ordered_evaluations:
            handle.write(json.dumps(record, separators=(",", ":")) + "\n")

    (work_dir / "summary.md").write_text(
        render_summary(ordered_evaluations, answer_results, evaluation_results),
        encoding="utf-8",
    )
    completed_at = utc_now()
    skill_dir = public_skills_dir() / "alphafold-scope-homology"
    manifest = {
        "run_id": run_id,
        "benchmark_id": benchmark["benchmark_id"],
        "suite_version": benchmark["suite_version"],
        "condition": CONDITION,
        "model": {
            "provider": "openai",
            "name": args.model,
            "snapshot": args.model,
            "parameters": {
                "reasoning_effort": args.reasoning_effort,
                "question_batching": "one_question_per_session",
                "jobs": args.jobs,
                "max_attempts": args.max_attempts,
                "max_mcp_calls": args.max_mcp_calls,
                "preflight_jobs": args.preflight_jobs,
            },
            "evaluator": {
                "provider": "openai",
                "name": args.evaluator_model,
                "snapshot": args.evaluator_model,
                "question_batching": "one_question_per_session",
                "reasoning_effort": args.evaluation_reasoning_effort,
            },
            "harness": {
                "name": "codex-cli-bwrap-local-mcp",
                "version": HARNESS_VERSION,
                "codex_version": subprocess.run(
                    ["codex", "--version"],
                    check=True,
                    text=True,
                    capture_output=True,
                ).stdout.strip(),
                "private_repository_hidden": True,
                "fresh_auth_only_codex_home": True,
                "outer_sandbox": "bubblewrap-read-only-root",
                "nested_codex_sandbox": False,
                "workspace_input_attestation_enforced": True,
                "non_definitive_claim_enforced": True,
                "evidence_citations_enforced": True,
                "mcp_transport": "loopback_streamable_http",
                "mcp_url": args.mcp_url,
                "rest_base_url": rest_base_url,
                "fixture_parity": fixture_parity,
                "mcp_rest_source_attestation_enforced": True,
                "mcp_source_commit": subprocess.run(
                    ["git", "-C", str(REPO_ROOT), "rev-parse", "HEAD"],
                    check=True,
                    text=True,
                    capture_output=True,
                ).stdout.strip(),
                "runner_sha256": sha256(Path(__file__)),
                "skill_tree_sha256": tree_sha256(skill_dir),
                "logs_sha256": tree_sha256(logs_dir),
                "required_tool_audit": {
                    question["id"]: answer_results[question["id"]]["used_tools"]
                    for question in blind_questions
                },
                "semantic_audit": {
                    question["id"]: answer_results[question["id"]]["audit"]
                    for question in blind_questions
                },
                "generator_inputs": {
                    "blind_questions_sha256": sha256(
                        ROOT / "inputs" / "questions_blind.jsonl"
                    ),
                    "fixture_manifest_sha256": sha256(
                        ROOT / "fixtures" / "api" / "manifest.json"
                    ),
                    "prompt_sha256_by_question": {
                        question["id"]: text_sha256(
                            generator_prompt(question, args.max_mcp_calls)
                        )
                        for question in blind_questions
                    },
                },
                "evaluator_inputs": {
                    "gold_answers_sha256": sha256(ROOT / "gold" / "answers.jsonl"),
                    "rubric_sha256": sha256(ROOT / "rubric.md"),
                    "answers_sha256": sha256(work_dir / "answers.jsonl"),
                    "prompt_sha256_by_question": {
                        answer["question_id"]: text_sha256(
                            evaluator_prompt(
                                private_questions[answer["question_id"]],
                                gold_answers[answer["question_id"]],
                                answer,
                                rubric,
                                answer_results[answer["question_id"]]["audit"],
                            )
                        )
                        for answer in ordered_answers
                    },
                },
                "performance": {
                    "answers": {
                        question_id: result.get("performance", {})
                        for question_id, result in answer_results.items()
                    },
                    "evaluations": {
                        question_id: result.get("performance", {})
                        for question_id, result in evaluation_results.items()
                    },
                },
            },
        },
        "questions_sha256": sha256(ROOT / "questions.jsonl"),
        "blind_questions_sha256": sha256(
            ROOT / "inputs" / "questions_blind.jsonl"
        ),
        "fixture_manifest_sha256": sha256(ROOT / "fixtures" / "api" / "manifest.json"),
        "gold_answers_sha256": sha256(ROOT / "gold" / "answers.jsonl"),
        "rubric_sha256": sha256(ROOT / "rubric.md"),
        "skill_sha256": sha256(skill_dir / "SKILL.md"),
        "skill_tree_sha256": tree_sha256(skill_dir),
        "mcp_source_tree_sha256": tree_sha256(REPO_ROOT / "mcp" / "src"),
        "runner_sha256": sha256(Path(__file__)),
        "answers_sha256": sha256(work_dir / "answers.jsonl"),
        "evaluation_sha256": sha256(work_dir / "evaluation.jsonl"),
        "started_at": started_at,
        "completed_at": completed_at,
    }
    (work_dir / "manifest.json").write_text(
        json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    shutil.move(str(work_dir), str(run_dir))
    print(f"completed run {run_dir}", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
