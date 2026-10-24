#!/usr/bin/env python3
"""Run isolated, replicated SCOPe benchmark conditions through Codex CLI."""

import argparse
import concurrent.futures
import hashlib
import json
import os
import shutil
import subprocess
import tempfile
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen

from live_api_capture import LiveApiCapture, capture_summary, write_capture


ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = ROOT.parents[1]
HARNESS_VERSION = "4.0.0"
MCP_SERVER_NAME = "scope"
MCP_TOOL_PREFIX = f"mcp__{MCP_SERVER_NAME}__"
DEFAULT_MCP_COMMAND = REPO_ROOT / "mcp" / ".venv" / "bin" / "scop-mcp"
CODEX_TIMEOUT_SECONDS = 1800
MAX_ATTEMPTS = 3
RATE_LIMIT_MARKERS = ("rate limit", "rate_limit", "429", "overloaded", "529")
CONDITIONS = (
    "pretrained_only",
    "pretrained_api",
    "pretrained_skill",
    "pretrained_mcp",
    "pretrained_mcp_skill",
)
# skill: the public scop-homology skill is copied into the workspace.
# api: the model may call the REST API itself through the capture gateway.
# mcp: the SCOPe MCP server is registered and is the only live SCOPe source.
CONDITION_FEATURES = {
    "pretrained_only": {"skill": False, "api": False, "mcp": False},
    "pretrained_api": {"skill": False, "api": True, "mcp": False},
    "pretrained_skill": {"skill": True, "api": True, "mcp": False},
    "pretrained_mcp": {"skill": False, "api": False, "mcp": True},
    "pretrained_mcp_skill": {"skill": True, "api": False, "mcp": True},
}
DEFAULT_API_BASE_URL = "https://scop.berkeley.edu/api/v1"
CONDITION_INSTRUCTIONS = {
    "pretrained_only": (
        "Use pretrained model knowledge only. The only benchmark file you may "
        "read is `questions_blind.jsonl`. Do not access the network. Record "
        "`pretrained model knowledge` in `evidence_used` when appropriate."
    ),
    "pretrained_api": (
        "Use pretrained model knowledge plus the live SCOPe REST API through "
        "`{{API_BASE_URL}}`. This loopback capture URL forwards every GET to the "
        "real SCOPe API. Do not contact another network endpoint. Discover the "
        "current release before making release-scoped requests. Record each API "
        "path and query actually used, beginning with `/api/v1/`, in "
        "`evidence_used`. Copy the exact path and query string; do not add "
        "defaulted parameters you did not send or drop parameters you did send. "
        "Record only API requests that returned a 2xx response."
    ),
    "pretrained_skill": (
        "Use pretrained model knowledge, the live SCOPe REST API through "
        "`{{API_BASE_URL}}`, and the public skill under `scop-homology/`. Begin "
        "with `scop-homology/SKILL.md`. The loopback capture URL overrides the "
        "skill's default API base and forwards every GET to the real SCOPe API; "
        "pass it to `scop_compare.py` with `--base-url` when using that helper. "
        "Do not contact another network endpoint. Record each API path and query "
        "actually used, beginning with `/api/v1/`, and each skill file actually "
        "used in `evidence_used`. Copy the exact path and query string; do not "
        "add defaulted parameters you did not send or drop parameters you did "
        "send. Record only API requests that returned a 2xx response."
    ),
    "pretrained_mcp": (
        "Use pretrained model knowledge plus the SCOPe MCP tools registered in "
        "this session. The MCP server forwards every request to the real SCOPe "
        "REST API. You may use the local shell only to read files in this "
        "isolated workspace. Do not use curl, Python, or any other shell "
        "command to query SCOPe or any other network endpoint; the MCP tools "
        "are the only permitted live SCOPe source. Record each MCP tool "
        "actually used, by name, and each REST source path returned by a tool "
        "and actually relied on, beginning with `/api/v1/`, in `evidence_used`."
    ),
    "pretrained_mcp_skill": (
        "Use pretrained model knowledge, the SCOPe MCP tools registered in "
        "this session, and the public skill under `scop-homology/`. Begin by "
        "reading `scop-homology/SKILL.md`. The MCP server forwards every "
        "request to the real SCOPe REST API. You may use the local shell only "
        "to read files in this isolated workspace; do not run the skill's "
        "command-line helper or use curl, Python, or any other shell command "
        "to query SCOPe or any other network endpoint. Apply the skill guidance "
        "through the MCP tools instead. Record each MCP tool actually used, by "
        "name, each REST source path returned by a tool and actually relied on, "
        "beginning with `/api/v1/`, and each skill file actually used in "
        "`evidence_used`."
    ),
}
FORBIDDEN_NONSKILL_LOG_MARKERS = (
    "/scratch/jmc/scop/skills/scop-homology",
    "/scratch/jmc/scop-server",
    "skill-sources/scop-homology",
    "/.codex/skills/scop-homology",
)


def utc_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace(
        "+00:00", "Z"
    )


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def toml_string(value: str | Path) -> str:
    return json.dumps(str(value))


def tree_sha256(root: Path) -> str:
    digest = hashlib.sha256()
    for path in sorted(item for item in root.rglob("*") if item.is_file()):
        digest.update(path.relative_to(root).as_posix().encode("utf-8"))
        digest.update(b"\0")
        digest.update(path.read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()


def read_jsonl(path: Path) -> list[dict]:
    return [
        json.loads(line)
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]


def git_value(repository: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", "-C", str(repository), *args],
        check=True,
        text=True,
        capture_output=True,
    )
    return result.stdout.strip()


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
        "models_cache.json",
    ):
        metadata = source / name
        if metadata.is_file():
            shutil.copy2(metadata, destination / name)


def normalize_evidence(
    condition: str,
    references: list[str],
    skill_dir: Path,
    api_records: list[dict],
    api_bodies: dict[str, bytes],
    mcp_tools_used: set[str] | None = None,
) -> tuple[list[dict], list[str]]:
    features = CONDITION_FEATURES[condition]
    mcp_tools_used = mcp_tools_used or set()
    captured_api = set()
    linked_api = set()
    successful_api = {}
    by_path = {}
    for trace in api_records:
        path_query = trace["request"]["path_query"]
        captured_api.add(path_query)
        status = trace["response"]["status"]
        if 200 <= status < 300:
            body_sha256 = trace["response"]["body_sha256"]
            successful_api[path_query] = body_sha256
            collect_linked_api_references(api_bodies.get(body_sha256), linked_api)
            by_path.setdefault(path_query.split("?", 1)[0], path_query)
    mcp_short_names = {name.removeprefix(MCP_TOOL_PREFIX) for name in mcp_tools_used}
    normalized = []
    rejected = []
    seen = set()
    for reference in references:
        value = reference.strip()
        if is_pretrained_reference(value) or value.lower().startswith("pretrained"):
            continue
        record = None
        if value.startswith("questions_blind.jsonl"):
            record = {
                "kind": "seed",
                "reference": "inputs/questions_blind.jsonl",
                "sha256": sha256(ROOT / "inputs" / "questions_blind.jsonl"),
            }
        elif features["skill"] and value.startswith("scop-homology/"):
            relative = value.split()[0].removeprefix("scop-homology/")
            source = skill_dir / relative
            if source.is_file():
                record = {
                    "kind": "skill_reference",
                    "reference": f"skills/scop-homology/{relative}",
                    "sha256": sha256(source),
                }
        elif features["mcp"] and (
            value in mcp_tools_used or value in mcp_short_names
        ):
            record = {
                "kind": "mcp",
                "reference": (
                    value if value.startswith(MCP_TOOL_PREFIX) else MCP_TOOL_PREFIX + value
                ),
            }
        elif features["api"] or features["mcp"]:
            try:
                api_reference = canonical_api_reference(value)
            except RuntimeError:
                try:
                    api_reference = canonical_api_reference(value.split()[0])
                except RuntimeError:
                    api_reference = None
            if api_reference in {"/api/v1", "/api/v1/"}:
                continue
            if api_reference in linked_api or (
                api_reference in captured_api and api_reference not in successful_api
            ):
                continue
            if api_reference not in successful_api and api_reference in by_path:
                api_reference = by_path[api_reference]
            if api_reference in successful_api:
                record = {
                    "kind": "api",
                    "reference": api_reference,
                    "sha256": successful_api[api_reference],
                }
        if record is None:
            rejected.append(value)
            continue
        key = (record["kind"], record["reference"])
        if key not in seen:
            seen.add(key)
            normalized.append(record)
    return normalized, rejected


def is_pretrained_reference(reference: str) -> bool:
    lowered = reference.lower()
    return lowered.startswith("pretrained ") and "knowledge" in lowered[:64]


def collect_linked_api_references(body: bytes | None, references: set[str]) -> None:
    if body is None:
        return
    try:
        value = json.loads(body)
    except (UnicodeDecodeError, json.JSONDecodeError):
        return
    collect_api_strings(value, references)


def collect_api_strings(value, references: set[str]) -> None:
    if isinstance(value, dict):
        for child in value.values():
            collect_api_strings(child, references)
    elif isinstance(value, list):
        for child in value:
            collect_api_strings(child, references)
    elif isinstance(value, str) and value.startswith("/api/v1/"):
        references.add(value)


def captured_api_evidence(api_records: list[dict]) -> list[dict]:
    evidence = []
    seen = set()
    for trace in api_records:
        status = trace["response"]["status"]
        reference = trace["request"]["path_query"]
        if not 200 <= status < 300 or reference in seen:
            continue
        seen.add(reference)
        evidence.append(
            {
                "kind": "api",
                "reference": reference,
                "sha256": trace["response"]["body_sha256"],
            }
        )
    return evidence


def canonical_api_reference(reference: str) -> str:
    value = reference.strip()
    if value.startswith("GET "):
        value = value.removeprefix("GET ").strip()
    if value.startswith(("http://", "https://")):
        parsed = urlsplit(value)
        value = parsed.path
        if parsed.query:
            value = f"{value}?{parsed.query}"
    if value.startswith("/releases/"):
        value = f"/api/v1{value}"
    if not (value == "/api/v1" or value.startswith("/api/v1/")):
        raise RuntimeError(f"invalid live API evidence reference: {reference}")
    return value


def command_executions(event_log: str) -> list[str]:
    commands = []
    for line in event_log.splitlines():
        try:
            event = json.loads(line)
        except json.JSONDecodeError:
            continue
        item = event.get("item")
        if (
            isinstance(item, dict)
            and item.get("type") == "command_execution"
            and isinstance(item.get("command"), str)
        ):
            commands.append(item["command"])
    return commands


def prefixed_mcp_tool_name(server: str | None, tool: str) -> str:
    if tool.startswith("mcp__"):
        return tool
    return f"mcp__{server or MCP_SERVER_NAME}__{tool}"


def first_nested_string(value, keys: set[str]) -> str | None:
    if isinstance(value, dict):
        for key, child in value.items():
            if key in keys and isinstance(child, str) and child:
                return child
        for child in value.values():
            found = first_nested_string(child, keys)
            if found is not None:
                return found
    elif isinstance(value, list):
        for child in value:
            found = first_nested_string(child, keys)
            if found is not None:
                return found
    return None


def mcp_tool_calls(event_log: str) -> list[str]:
    calls = []
    seen = set()
    for line in event_log.splitlines():
        try:
            event = json.loads(line)
        except json.JSONDecodeError:
            continue
        if not isinstance(event, dict):
            continue
        item = event.get("item")
        candidates = [event]
        if isinstance(item, dict):
            candidates.append(item)
        for candidate in candidates:
            event_type = str(candidate.get("type") or "")
            name = str(candidate.get("name") or "")
            if not (
                "mcp_tool_call" in event_type
                or event_type == "mcp_tool"
                or name.startswith("mcp__")
            ):
                continue
            tool = first_nested_string(
                candidate, {"tool_name", "tool", "name", "callable_name"}
            )
            if not tool:
                continue
            server = first_nested_string(
                candidate, {"server", "server_name", "mcp_server"}
            )
            call_id = first_nested_string(
                candidate, {"call_id", "mcp_call_id", "tool_call_id", "id"}
            )
            full_name = prefixed_mcp_tool_name(server, tool)
            key = call_id or f"{len(calls)}:{full_name}"
            if key in seen:
                continue
            seen.add(key)
            calls.append(full_name)
    return calls


def audit_event_log(
    condition: str,
    event_log: str,
    capture_base_url: str | None,
    mcp_tools_used: set[str] | None = None,
    requires_api: bool = False,
) -> None:
    features = CONDITION_FEATURES[condition]
    mcp_tools_used = mcp_tools_used or set()
    if features["skill"]:
        hits = []
    else:
        lowered = event_log.lower()
        hits = [
            marker
            for marker in FORBIDDEN_NONSKILL_LOG_MARKERS
            if marker.lower() in lowered
        ]
        if hits:
            raise RuntimeError(
                f"{condition} event log contains forbidden skill/repository markers: "
                f"{hits}"
            )
    if features["mcp"] and requires_api and not mcp_tools_used:
        raise RuntimeError(f"{condition} session made no MCP tool call")
    if capture_base_url is None:
        return
    capture_origin = None
    parsed_capture = urlsplit(capture_base_url)
    if parsed_capture.scheme and parsed_capture.netloc:
        capture_origin = f"{parsed_capture.scheme}://{parsed_capture.netloc}"
    for command in command_executions(event_log):
        lowered_command = command.lower()
        if "scop.berkeley.edu" in lowered_command:
            raise RuntimeError(
                "agent command bypassed the live API capture gateway: "
                f"{command}"
            )
        if features["mcp"] and (
            "curl " in lowered_command
            or "wget " in lowered_command
        ):
            raise RuntimeError(
                "agent shell command attempted direct network access under MCP: "
                f"{command}"
            )
        if not features["api"]:
            continue
        uses_capture_variable = "scop_api_base_url" in lowered_command
        uses_capture_origin = (
            capture_origin is not None and capture_origin in command
        )
        command_uses_url = (
            "http://" in lowered_command
            or "https://" in lowered_command
            or uses_capture_variable
        )
        if (
            "curl " in lowered_command
            and command_uses_url
            and capture_base_url not in command
            and not uses_capture_variable
            and not uses_capture_origin
        ):
            raise RuntimeError(
                "agent curl command did not use the live API capture gateway: "
                f"{command}"
            )


def observe_current_release(
    api_base_url: str, label: str, timeout: float
) -> tuple[dict, dict]:
    with LiveApiCapture(api_base_url, label, timeout) as capture:
        request = Request(
            f"{capture.base_url}/releases/current",
            headers={
                "Accept": "application/json",
                "User-Agent": "scop-benchmark-release-observation",
            },
        )
        try:
            with urlopen(request, timeout=timeout + 5) as response:
                payload = response.read()
        except (HTTPError, URLError, TimeoutError, OSError) as error:
            snapshot = capture.snapshot()
            raise RuntimeError(
                f"could not observe current SCOPe release: {error}; "
                f"trace={json.dumps(snapshot['records'], separators=(',', ':'))}"
            ) from error
        snapshot = capture.snapshot()
    try:
        release = json.loads(payload)
    except json.JSONDecodeError as error:
        raise RuntimeError("current-release response is not valid JSON") from error
    if not isinstance(release, dict) or not isinstance(release.get("version"), str):
        raise RuntimeError("current-release response has no stable version")
    periodic = release.get("latest_periodic_update")
    periodic_version = (
        periodic.get("version") if isinstance(periodic, dict) else None
    )
    return (
        {
            "stable_release": release["version"],
            "stable_label": release.get("stable_label"),
            "latest_periodic_update": periodic_version,
            "response_sha256": hashlib.sha256(payload).hexdigest(),
        },
        snapshot,
    )


def run_attempt(
    condition: str,
    question: dict,
    requires_api: bool,
    prompt_template: str,
    model: str,
    reasoning_effort: str,
    skill_dir: Path,
    api_base_url: str,
    api_timeout: float,
    mcp_command: Path,
) -> dict:
    with tempfile.TemporaryDirectory(
        prefix=f"scop-{condition}-{question['id']}-"
    ) as tmp_name:
        tmp = Path(tmp_name)
        workspace = tmp / "workspace"
        workspace.mkdir()
        (workspace / "questions_blind.jsonl").write_text(
            json.dumps(question, separators=(",", ":")) + "\n",
            encoding="utf-8",
        )
        shutil.copy2(
            ROOT / "schemas" / "harness-answer.schema.json",
            workspace / "harness-answer.schema.json",
        )
        features = CONDITION_FEATURES[condition]
        if features["skill"]:
            shutil.copytree(skill_dir, workspace / "scop-homology")

        codex_home = tmp / "codex-home"
        isolated_codex_home(codex_home)
        response_path = tmp / "response.json"
        environment = os.environ.copy()
        environment["CODEX_HOME"] = str(codex_home)
        capture_snapshot = {
            "format": None,
            "label": question["id"],
            "upstream_base_url": api_base_url,
            "records": [],
            "bodies": {},
        }
        capture_base_url = None

        def execute(
            capture_base_url: str | None,
        ) -> tuple[subprocess.CompletedProcess, float]:
            command = [
                "codex",
                "--ask-for-approval",
                "never",
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
                "--sandbox",
                "workspace-write" if features["api"] else "read-only",
                "--cd",
                str(workspace),
                "--skip-git-repo-check",
                "--output-schema",
                str(workspace / "harness-answer.schema.json"),
                "--output-last-message",
                str(response_path),
                "-",
            ]
            if features["api"]:
                command[command.index("--cd"):command.index("--cd")] = [
                    "--config",
                    "sandbox_workspace_write.network_access=true",
                ]
            if features["mcp"]:
                command[command.index("--cd"):command.index("--cd")] = [
                    "--config",
                    (
                        f"mcp_servers.{MCP_SERVER_NAME}.command="
                        f"{toml_string(mcp_command)}"
                    ),
                    "--config",
                    f"mcp_servers.{MCP_SERVER_NAME}.args=[]",
                    "--config",
                    (
                        f"mcp_servers.{MCP_SERVER_NAME}.env="
                        "{"
                        f"SCOP_REST_BASE_URL={toml_string(capture_base_url or '')},"
                        'SCOP_MCP_TRANSPORT="stdio"'
                        "}"
                    ),
                ]
            prompt = prompt_template.replace("{{API_BASE_URL}}", capture_base_url or "")
            started = time.monotonic()
            try:
                result = subprocess.run(
                    command,
                    input=prompt,
                    text=True,
                    capture_output=True,
                    env=environment,
                    timeout=CODEX_TIMEOUT_SECONDS,
                )
            except subprocess.TimeoutExpired as error:
                stdout = error.stdout or ""
                stderr = error.stderr or ""
                raise RuntimeError(
                    f"{condition} {question['id']} timed out after "
                    f"{CODEX_TIMEOUT_SECONDS}s:\n{stdout}\n{stderr}"
                ) from error
            return result, round(time.monotonic() - started, 1)

        needs_capture = features["api"] or features["mcp"]
        if needs_capture:
            with LiveApiCapture(
                api_base_url, question["id"], api_timeout
            ) as capture:
                capture_base_url = capture.base_url
                if features["api"]:
                    environment["SCOP_API_BASE_URL"] = capture_base_url
                result, elapsed_seconds = execute(capture_base_url)
                capture_snapshot = capture.snapshot()
        else:
            result, elapsed_seconds = execute(None)
        if result.returncode:
            raise RuntimeError(
                f"{condition} {question['id']} failed ({result.returncode}):\n"
                f"{result.stdout}\n{result.stderr}\n"
                f"api_trace={json.dumps(capture_snapshot['records'], separators=(',', ':'))}"
            )
        used_mcp_tools = mcp_tool_calls(result.stdout)
        audit_event_log(
            condition,
            result.stdout,
            capture_base_url,
            set(used_mcp_tools),
            requires_api,
        )
        if needs_capture and requires_api:
            summary = capture_summary(capture_snapshot)
            if summary["successful_response_count"] == 0:
                failure_kind = (
                    "transient API failure"
                    if summary["transient_error_count"]
                    else "no successful live API request"
                )
                raise RuntimeError(
                    f"{condition} {question['id']}: {failure_kind}; "
                    f"trace={json.dumps(capture_snapshot['records'], separators=(',', ':'))}"
                )
        response = json.loads(response_path.read_text(encoding="utf-8"))
        answers = response["answers"]
        matching_answers = [
            answer for answer in answers if answer.get("question_id") == question["id"]
        ]
        if not answers or (len(answers) != 1 and not matching_answers):
            raise RuntimeError(
                f"{condition} {question['id']} returned unexpected IDs: "
                f"{[answer['question_id'] for answer in answers]}"
            )
        # The isolated workspace contains exactly one known question. Use that
        # identity rather than trusting a model-repeated bookkeeping label.
        answer = matching_answers[-1] if matching_answers else answers[0]
        answer["question_id"] = question["id"]
        return {
            "answer": answer,
            "events": result.stdout,
            "stderr": result.stderr,
            "api_capture": capture_snapshot,
            "mcp_tools_used": sorted(set(used_mcp_tools)),
            "mcp_call_count": len(used_mcp_tools),
            "elapsed_seconds": elapsed_seconds,
        }


def run_question(*args) -> dict:
    last_error = None
    attempt = 0
    while attempt < MAX_ATTEMPTS:
        attempt += 1
        try:
            return run_attempt(*args)
        except RuntimeError as error:
            last_error = error
            message = str(error)
            lowered = message.lower()
            if any(marker in lowered for marker in RATE_LIMIT_MARKERS):
                attempt -= 1
                delay = min(600, 60 * (1 + len(message) % 3))
                print(f"capacity error; waiting {delay}s: {message[:200]}", flush=True)
            else:
                delay = 10 * attempt
                print(f"attempt {attempt} failed: {message[:300]}", flush=True)
            time.sleep(delay)
    raise RuntimeError(f"question failed after {MAX_ATTEMPTS} attempts: {last_error}")


def run_condition(
    condition: str,
    replicate: int,
    model: str,
    reasoning_effort: str,
    skill_dir: Path,
    results_dir: Path,
    jobs: int,
    api_base_url: str,
    api_timeout: float,
    mcp_command: Path,
) -> Path:
    benchmark = json.loads((ROOT / "benchmark.json").read_text(encoding="utf-8"))
    questions = read_jsonl(ROOT / "inputs" / "questions_blind.jsonl")
    gold_by_id = {
        question["id"]: question for question in read_jsonl(ROOT / "questions.jsonl")
    }
    date = datetime.now(timezone.utc).date().isoformat()
    model_label = model.replace("/", "-").replace(".", "-")
    run_id = f"{date}_{model_label}_{condition}_r{replicate}"
    run_dir = results_dir / run_id
    if run_dir.exists():
        raise RuntimeError(f"run directory already exists: {run_dir}")
    features = CONDITION_FEATURES[condition]
    if features["mcp"] and not mcp_command.is_file():
        raise RuntimeError(f"MCP server command is missing: {mcp_command}")

    template = (ROOT / "harness" / "answer-instructions.md").read_text(
        encoding="utf-8"
    )
    prompt_template = template.replace(
        "{{CONDITION_INSTRUCTIONS}}", CONDITION_INSTRUCTIONS[condition]
    )
    started_at = utc_now()
    release_start = None
    release_start_capture = None
    needs_capture = features["api"] or features["mcp"]
    if needs_capture:
        release_start, release_start_capture = observe_current_release(
            api_base_url, "release-start", api_timeout
        )
    results_by_id = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=jobs) as executor:
        futures = {
            executor.submit(
                run_question,
                condition,
                question,
                bool(gold_by_id[question["id"]]["api_evidence"]),
                prompt_template,
                model,
                reasoning_effort,
                skill_dir,
                api_base_url,
                api_timeout,
                mcp_command,
            ): question["id"]
            for question in questions
        }
        for future in concurrent.futures.as_completed(futures):
            question_id = futures[future]
            results_by_id[question_id] = future.result()
            print(f"completed {condition} r{replicate} {question_id}", flush=True)

    release_end = None
    release_end_capture = None
    if needs_capture:
        release_end, release_end_capture = observe_current_release(
            api_base_url, "release-end", api_timeout
        )
        if release_start["stable_release"] != release_end["stable_release"]:
            raise RuntimeError(
                "SCOPe stable release changed during the run: "
                f"{release_start['stable_release']} -> "
                f"{release_end['stable_release']}"
            )
    completed_at = utc_now()
    answer_records = []
    rejected_references = []
    for question in questions:
        question_result = results_by_id[question["id"]]
        answer = question_result["answer"]
        requires_api = bool(gold_by_id[question["id"]]["api_evidence"])
        evidence, rejected = normalize_evidence(
            condition,
            answer["evidence_used"],
            skill_dir,
            question_result["api_capture"]["records"],
            question_result["api_capture"]["bodies"],
            set(question_result["mcp_tools_used"]),
        )
        if (
            needs_capture
            and requires_api
            and not any(item["kind"] == "api" for item in evidence)
        ):
            evidence.extend(
                captured_api_evidence(question_result["api_capture"]["records"])
            )
        if (
            needs_capture
            and requires_api
            and not any(item["kind"] == "api" for item in evidence)
        ):
            raise RuntimeError(
                f"{condition} {question['id']} had no successful API capture"
            )
        rejected_references.extend(
            {"question_id": question["id"], "reference": reference}
            for reference in rejected
        )
        answer_records.append(
            {
                "run_id": run_id,
                "question_id": question["id"],
                "condition": condition,
                "model": model,
                "prompt": question["prompt"],
                "answer": answer["answer"],
                "evidence_used": evidence,
                "timestamp": completed_at,
            }
        )

    run_dir.mkdir(parents=True)
    logs_dir = run_dir / "logs"
    logs_dir.mkdir()
    api_dir = None
    api_summaries = []
    if needs_capture:
        api_dir = run_dir / "api"
        api_dir.mkdir()
        api_summaries.append(
            write_capture(release_start_capture, api_dir / "release-start")
        )
        for question in questions:
            api_summaries.append(
                write_capture(
                    results_by_id[question["id"]]["api_capture"],
                    api_dir / question["id"],
                )
            )
        api_summaries.append(
            write_capture(release_end_capture, api_dir / "release-end")
        )
    for question in questions:
        question_result = results_by_id[question["id"]]
        (logs_dir / f"{question['id']}.events.jsonl").write_text(
            question_result["events"], encoding="utf-8"
        )
        (logs_dir / f"{question['id']}.stderr.log").write_text(
            question_result["stderr"], encoding="utf-8"
        )
    with (run_dir / "answers.jsonl").open("w", encoding="utf-8") as handle:
        for record in answer_records:
            handle.write(json.dumps(record, separators=(",", ":")) + "\n")

    skill = None
    if features["skill"]:
        repository = skill_dir.parents[1]
        skill = {
            "repository": git_value(repository, "remote", "get-url", "origin"),
            "path": skill_dir.relative_to(repository).as_posix(),
            "commit": git_value(repository, "rev-parse", "HEAD"),
            "content_sha256": tree_sha256(skill_dir),
        }
    manifest = {
        "run_id": run_id,
        "benchmark_id": benchmark["benchmark_id"],
        "suite_version": benchmark["suite_version"],
        "questions_sha256": sha256(ROOT / "questions.jsonl"),
        "blind_questions_sha256": sha256(
            ROOT / "inputs" / "questions_blind.jsonl"
        ),
        "condition": condition,
        "model": {
            "provider": "openai",
            "name": model,
            "snapshot": model,
            "parameters": {
                "reasoning_effort": reasoning_effort,
                "question_batching": "one_question_per_session",
                "replicate": replicate,
            },
        },
        "harness": {
            "name": "codex-cli-isolated",
            "version": HARNESS_VERSION,
            "instructions_sha256": hashlib.sha256(
                prompt_template.encode("utf-8")
            ).hexdigest(),
        },
        "skill": skill,
        "fixture_manifest_sha256": None,
        "api_access": (
            {
                "mode": "live_rest_capture",
                "upstream_base_url": api_base_url,
                "openapi_sha256": sha256(
                    REPO_ROOT / "php" / "api" / "v1" / "openapi.yaml"
                ),
                "release_start": release_start,
                "release_end": release_end,
                "stable_release_consistent": True,
                "trace_format": "scop-live-http-v1",
                "trace_tree_sha256": tree_sha256(api_dir),
                "question_trace_count": len(questions),
                "request_count": sum(
                    summary["request_count"] for summary in api_summaries
                ),
                "successful_response_count": sum(
                    summary["successful_response_count"]
                    for summary in api_summaries
                ),
                "transient_error_count": sum(
                    summary["transient_error_count"]
                    for summary in api_summaries
                ),
                "response_body_count": sum(
                    summary["response_body_count"] for summary in api_summaries
                ),
            }
            if api_dir is not None
            else None
        ),
        "mcp_access": (
            {
                "server": "scop-mcp",
                "transport": "stdio",
                "command": str(mcp_command),
                "rest_base_url": "live_rest_capture_gateway",
                "tool_call_count": sum(
                    result["mcp_call_count"] for result in results_by_id.values()
                ),
                "tools_used": sorted(
                    {
                        name
                        for result in results_by_id.values()
                        for name in result["mcp_tools_used"]
                    }
                ),
            }
            if features["mcp"]
            else None
        ),
        "isolation_audit": {
            "status": "passed",
            "codex_home": "fresh_auth_only_per_question",
            "question_workspace": "one_question_per_session",
            "event_log_format": "codex_exec_jsonl",
            "forbidden_markers_checked": (
                list(FORBIDDEN_NONSKILL_LOG_MARKERS)
                if not features["skill"]
                else []
            ),
            "logs_sha256": tree_sha256(logs_dir),
        },
        "evidence_audit": {
            "rejected_reference_count": len(rejected_references),
            "rejected_references": rejected_references,
        },
        "evaluation": None,
        "started_at": started_at,
        "completed_at": completed_at,
    }
    (run_dir / "manifest.json").write_text(
        json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    print(f"completed {condition} r{replicate}: {run_dir}", flush=True)
    return run_dir


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--condition",
        action="append",
        choices=CONDITIONS,
        help="Condition to run; repeat as needed. Defaults to all five.",
    )
    parser.add_argument("--model", default="gpt-5.5")
    parser.add_argument(
        "--reasoning-effort", choices=("low", "medium", "high"), default="high"
    )
    parser.add_argument("--replicates", type=int, default=3)
    parser.add_argument(
        "--replicate",
        action="append",
        type=int,
        help="Run only this replicate number; repeat as needed.",
    )
    parser.add_argument("--jobs", type=int, default=4)
    parser.add_argument(
        "--api-base-url",
        default=DEFAULT_API_BASE_URL,
        help="Live SCOPe REST base URL; requests are forwarded and captured.",
    )
    parser.add_argument("--api-timeout", type=float, default=30.0)
    parser.add_argument(
        "--skill-dir",
        type=Path,
        default=Path("/scratch/jmc/scop/skills/scop-homology"),
    )
    parser.add_argument("--mcp-command", type=Path, default=DEFAULT_MCP_COMMAND)
    parser.add_argument(
        "--results-dir", type=Path, default=ROOT / "results" / "runs"
    )
    args = parser.parse_args()
    if args.replicates < 1 or args.jobs < 1 or args.api_timeout <= 0:
        parser.error("--replicates, --jobs, and --api-timeout must be positive")
    if args.replicate and any(value < 1 for value in args.replicate):
        parser.error("--replicate values must be positive")

    conditions = args.condition or list(CONDITIONS)
    replicate_numbers = args.replicate or range(1, args.replicates + 1)
    for replicate in replicate_numbers:
        for condition in conditions:
            run_condition(
                condition,
                replicate,
                args.model,
                args.reasoning_effort,
                args.skill_dir.resolve(),
                args.results_dir.resolve(),
                args.jobs,
                args.api_base_url,
                args.api_timeout,
                args.mcp_command.resolve(),
            )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
