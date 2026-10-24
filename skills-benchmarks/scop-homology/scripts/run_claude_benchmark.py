#!/usr/bin/env python3
"""Run isolated, replicated SCOPe benchmark conditions through Claude Code CLI.

This runner mirrors ``run_codex_benchmark.py``: one fresh, non-persistent
session per question, a loopback capture gateway in front of the live SCOPe
REST API, structured answers validated against the harness answer schema, and
an audit of every tool call recorded in the session event log. It adds two
MCP conditions in which the SCOPe MCP server (stdio transport) is the only
route to SCOPe data; the server itself is pointed at the capture gateway so
its REST traffic is retained as evidence.
"""

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

from live_api_capture import LiveApiCapture, capture_summary, write_capture
from run_codex_benchmark import (
    CONDITION_INSTRUCTIONS as CODEX_CONDITION_INSTRUCTIONS,
    DEFAULT_API_BASE_URL,
    FORBIDDEN_NONSKILL_LOG_MARKERS,
    canonical_api_reference,
    captured_api_evidence,
    collect_linked_api_references,
    git_value,
    is_pretrained_reference,
    observe_current_release,
    read_jsonl,
    sha256,
    tree_sha256,
    utc_now,
)


ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = ROOT.parents[1]
HARNESS_VERSION = "1.0.0"
MCP_SERVER_NAME = "scope"
MCP_TOOL_PREFIX = f"mcp__{MCP_SERVER_NAME}__"
DEFAULT_MCP_COMMAND = REPO_ROOT / "mcp" / ".venv" / "bin" / "scop-mcp"
CONDITIONS = (
    "pretrained_only",
    "pretrained_api",
    "pretrained_skill",
    "pretrained_mcp",
    "pretrained_mcp_skill",
)
# skill: the public scop-homology skill is copied into the workspace.
# api: the model may call the REST API itself (curl through the gateway).
# mcp: the SCOPe MCP server is registered; the model has no shell.
CONDITION_FEATURES = {
    "pretrained_only": {"skill": False, "api": False, "mcp": False},
    "pretrained_api": {"skill": False, "api": True, "mcp": False},
    "pretrained_skill": {"skill": True, "api": True, "mcp": False},
    "pretrained_mcp": {"skill": False, "api": False, "mcp": True},
    "pretrained_mcp_skill": {"skill": True, "api": False, "mcp": True},
}
CONDITION_INSTRUCTIONS = {
    **CODEX_CONDITION_INSTRUCTIONS,
    "pretrained_mcp": (
        "Use pretrained model knowledge plus the SCOPe MCP tools registered in "
        "this session (their names begin with `mcp__scope__`). The MCP server "
        "forwards every request to the real SCOPe REST API. You have no shell "
        "and no other network access; do not attempt to contact any other "
        "endpoint. Record each MCP tool actually used, by name, and each REST "
        "source path returned by a tool and actually relied on, beginning with "
        "`/api/v1/`, in `evidence_used`."
    ),
    "pretrained_mcp_skill": (
        "Use pretrained model knowledge, the SCOPe MCP tools registered in "
        "this session (their names begin with `mcp__scope__`), and the public "
        "skill under `scop-homology/`. Begin by reading `scop-homology/SKILL.md` "
        "with the Read tool. The MCP server forwards every request to the real "
        "SCOPe REST API. You have no shell, so the skill's command-line helper "
        "cannot be run; apply its guidance through the MCP tools instead. Do "
        "not attempt to contact any other endpoint. Record each MCP tool "
        "actually used, by name, each REST source path returned by a tool and "
        "actually relied on, beginning with `/api/v1/`, and each skill file "
        "actually used in `evidence_used`."
    ),
}
BUILTIN_TOOLS = {
    "pretrained_only": "",
    "pretrained_api": "Bash,Read,Glob,Grep",
    "pretrained_skill": "Bash,Read,Glob,Grep",
    "pretrained_mcp": "",
    "pretrained_mcp_skill": "Read,Glob,Grep",
}
FORBIDDEN_TOOLS = {"WebFetch", "WebSearch", "Agent", "Task"}
MAX_ATTEMPTS = 3


class QuestionAttemptError(RuntimeError):
    """A single session attempt failed and may be retried."""


def build_prompt(template: str, condition: str, question: dict, api_base_url: str) -> str:
    instructions = template.replace(
        "{{CONDITION_INSTRUCTIONS}}", CONDITION_INSTRUCTIONS[condition]
    ).replace("{{API_BASE_URL}}", api_base_url)
    return (
        f"{instructions}\n\n## Question\n\n"
        "The single question record from `questions_blind.jsonl` is:\n\n"
        f"```json\n{json.dumps(question, separators=(',', ':'))}\n```\n\n"
        "Answer this one question and return the structured output."
    )


def parse_events(event_log: str) -> dict:
    tool_uses = []
    results_by_id = {}
    result = None
    for line in event_log.splitlines():
        try:
            event = json.loads(line)
        except json.JSONDecodeError:
            continue
        if not isinstance(event, dict):
            continue
        if event.get("type") == "assistant":
            for block in event.get("message", {}).get("content", []):
                if isinstance(block, dict) and block.get("type") == "tool_use":
                    tool_uses.append(
                        {"id": block.get("id"), "name": block.get("name"), "input": block.get("input")}
                    )
        elif event.get("type") == "user":
            content = event.get("message", {}).get("content", [])
            for block in content if isinstance(content, list) else []:
                if isinstance(block, dict) and block.get("type") == "tool_result":
                    results_by_id[block.get("tool_use_id")] = bool(block.get("is_error"))
        elif event.get("type") == "result":
            result = event
    for use in tool_uses:
        use["is_error"] = results_by_id.get(use["id"])
    return {"tool_uses": tool_uses, "result": result}


def audit_events(
    condition: str,
    event_log: str,
    parsed: dict,
    capture_base_url: str | None,
    requires_api: bool = True,
) -> None:
    features = CONDITION_FEATURES[condition]
    if not features["skill"]:
        lowered = event_log.lower()
        hits = [
            marker
            for marker in FORBIDDEN_NONSKILL_LOG_MARKERS
            if marker.lower() in lowered
        ]
        if hits:
            raise QuestionAttemptError(
                f"{condition} event log contains forbidden skill/repository markers: "
                f"{hits}"
            )
    capture_host = None
    if capture_base_url is not None:
        capture_host = capture_base_url.removeprefix("http://").split("/", 1)[0]
    mcp_calls = 0
    for use in parsed["tool_uses"]:
        name = use["name"] or ""
        if name in FORBIDDEN_TOOLS:
            raise QuestionAttemptError(f"forbidden tool used: {name}")
        if name.startswith("mcp__"):
            if not features["mcp"] or not name.startswith(MCP_TOOL_PREFIX):
                raise QuestionAttemptError(f"unexpected MCP tool used: {name}")
            mcp_calls += 1
        if name == "Bash":
            if not features["api"]:
                # The tool is not registered in this condition. A request that
                # was refused (error result, no command ran) is harmless and
                # is kept; only a command that actually executed is a breach.
                if use.get("is_error") is False:
                    raise QuestionAttemptError(f"{condition} session executed Bash")
                continue
            command = str((use["input"] or {}).get("command", ""))
            lowered_command = command.lower()
            if "scop.berkeley.edu" in lowered_command:
                raise QuestionAttemptError(
                    "agent command bypassed the live API capture gateway: "
                    f"{command}"
                )
            uses_capture_variable = "scop_api_base_url" in lowered_command
            command_uses_url = (
                "http://" in lowered_command
                or "https://" in lowered_command
                or uses_capture_variable
            )
            if (
                "curl " in lowered_command
                and command_uses_url
                and capture_host is not None
                and capture_host not in command
                and not uses_capture_variable
            ):
                raise QuestionAttemptError(
                    "agent curl command did not use the live API capture gateway: "
                    f"{command}"
                )
    if features["mcp"] and mcp_calls == 0 and requires_api:
        raise QuestionAttemptError(f"{condition} session made no MCP tool call")


def normalize_evidence(
    condition: str,
    references: list[str],
    skill_dir: Path,
    api_records: list[dict],
    api_bodies: dict[str, bytes],
    mcp_tools_used: set[str],
) -> tuple[list[dict], list[str]]:
    """Return accepted evidence records and the references that were rejected.

    Follows the suite 4.3 rules of the Codex runner: a cited API path must
    match a successfully captured request; paths that merely appear as links
    inside captured response bodies, requests that were captured but failed,
    and the API root are ignored rather than counted as rejections.
    """
    features = CONDITION_FEATURES[condition]
    successful_api = {}
    captured_api = set()
    linked_api = set()
    by_path = {}
    for trace in api_records:
        path_query = trace["request"]["path_query"]
        captured_api.add(path_query)
        status = trace["response"]["status"]
        if 200 <= status < 300:
            body_sha256 = trace["response"]["body_sha256"]
            successful_api[path_query] = body_sha256
            collect_linked_api_references(api_bodies.get(body_sha256), linked_api)
            # A citation that omits the query string still identifies the
            # captured request when exactly one path was requested that way.
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
            if api_reference in {"/api/v1", "/api/v1/"} or api_reference in linked_api or (
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


def claude_environment() -> dict:
    environment = {
        key: value
        for key, value in os.environ.items()
        if not key.startswith("CLAUDE_CODE_") and key not in {"CLAUDECODE", "CLAUDE_PID"}
    }
    return environment


def run_attempt(
    condition: str,
    question: dict,
    prompt_template: str,
    model: str,
    effort: str,
    skill_dir: Path,
    api_base_url: str,
    api_timeout: float,
    mcp_command: Path,
    max_budget_usd: float,
    requires_api: bool = True,
) -> dict:
    with tempfile.TemporaryDirectory(
        prefix=f"scop-claude-{condition}-{question['id']}-"
    ) as tmp_name:
        tmp = Path(tmp_name)
        workspace = tmp / "workspace"
        workspace.mkdir()
        (workspace / "questions_blind.jsonl").write_text(
            json.dumps(question, separators=(",", ":")) + "\n",
            encoding="utf-8",
        )
        features = CONDITION_FEATURES[condition]
        if features["skill"]:
            shutil.copytree(skill_dir, workspace / "scop-homology")
        # Claude Code validates the structured-output schema itself and rejects
        # the draft-2020-12 "$schema" declaration, so pass the schema body only.
        schema = json.loads(
            (ROOT / "schemas" / "harness-answer.schema.json").read_text(encoding="utf-8")
        )
        schema.pop("$schema", None)
        schema_text = json.dumps(schema, separators=(",", ":"))
        environment = claude_environment()
        # Session files, caches and any auto-memory land under a private HOME
        # so nothing from the operator's configuration reaches the session.
        home = tmp / "home"
        home.mkdir()
        environment["XDG_CONFIG_HOME"] = str(home / ".config")
        needs_capture = features["api"] or features["mcp"]

        def execute(capture_base_url: str | None) -> dict:
            prompt = build_prompt(
                prompt_template, condition, question, capture_base_url or ""
            )
            command = [
                "claude",
                "-p",
                prompt,
                "--model",
                model,
                "--effort",
                effort,
                "--output-format",
                "stream-json",
                "--verbose",
                "--no-session-persistence",
                "--setting-sources",
                "",
                "--strict-mcp-config",
                "--disable-slash-commands",
                "--dangerously-skip-permissions",
                "--max-budget-usd",
                str(max_budget_usd),
                "--json-schema",
                schema_text,
                "--tools",
                BUILTIN_TOOLS[condition],
            ]
            if features["mcp"]:
                mcp_config = {
                    "mcpServers": {
                        MCP_SERVER_NAME: {
                            "type": "stdio",
                            "command": str(mcp_command),
                            "args": [],
                            "env": {
                                "SCOP_REST_BASE_URL": capture_base_url,
                                "SCOP_MCP_TRANSPORT": "stdio",
                            },
                        }
                    }
                }
                command.extend(["--mcp-config", json.dumps(mcp_config)])
            if features["api"]:
                environment["SCOP_API_BASE_URL"] = capture_base_url
            started = time.monotonic()
            result = subprocess.run(
                command,
                stdin=subprocess.DEVNULL,
                text=True,
                capture_output=True,
                env=environment,
                cwd=workspace,
            )
            return {
                "returncode": result.returncode,
                "stdout": result.stdout,
                "stderr": result.stderr,
                "prompt": prompt,
                "elapsed_seconds": round(time.monotonic() - started, 1),
            }

        capture_snapshot = {
            "format": None,
            "label": question["id"],
            "upstream_base_url": api_base_url,
            "records": [],
            "bodies": {},
        }
        capture_base_url = None
        if needs_capture:
            with LiveApiCapture(api_base_url, question["id"], api_timeout) as capture:
                capture_base_url = capture.base_url
                outcome = execute(capture_base_url)
                capture_snapshot = capture.snapshot()
        else:
            outcome = execute(None)

        if outcome["returncode"]:
            raise QuestionAttemptError(
                f"{condition} {question['id']} exited {outcome['returncode']}:\n"
                f"{outcome['stdout'][-2000:]}\n{outcome['stderr'][-2000:]}"
            )
        parsed = parse_events(outcome["stdout"])
        result = parsed["result"]
        if result is None or result.get("is_error"):
            raise QuestionAttemptError(
                f"{condition} {question['id']} produced no successful result: "
                f"{json.dumps(result)[:1000] if result else outcome['stderr'][-1000:]}"
            )
        structured = result.get("structured_output")
        answers = structured.get("answers") if isinstance(structured, dict) else None
        if not answers or len(answers) != 1:
            raise QuestionAttemptError(
                f"{condition} {question['id']} returned no single structured answer"
            )
        audit_events(condition, outcome["stdout"], parsed, capture_base_url, requires_api)
        if needs_capture and requires_api:
            summary = capture_summary(capture_snapshot)
            if summary["successful_response_count"] == 0:
                failure_kind = (
                    "transient API failure"
                    if summary["transient_error_count"]
                    else "no successful live API request"
                )
                raise QuestionAttemptError(
                    f"{condition} {question['id']}: {failure_kind}"
                )
        answer = dict(answers[0])
        # The isolated workspace contains exactly one known question. Use that
        # identity rather than trusting a model-repeated bookkeeping label.
        answer["question_id"] = question["id"]
        mcp_tools_used = {
            use["name"] for use in parsed["tool_uses"] if use["name"].startswith("mcp__")
        }
        model_usage = result.get("modelUsage") or {}
        return {
            "answer": answer,
            "events": outcome["stdout"],
            "stderr": outcome["stderr"],
            "api_capture": capture_snapshot,
            "mcp_tools_used": sorted(mcp_tools_used),
            "mcp_call_count": sum(
                1 for use in parsed["tool_uses"] if use["name"].startswith("mcp__")
            ),
            "tool_call_count": len(parsed["tool_uses"]),
            "canonical_models": sorted(
                str(usage.get("canonicalModel") or name)
                for name, usage in model_usage.items()
            ),
            "cost_usd": float(result.get("total_cost_usd") or 0.0),
            "usage": result.get("usage") or {},
            "num_turns": result.get("num_turns"),
            "elapsed_seconds": outcome["elapsed_seconds"],
        }


RATE_LIMIT_MARKERS = ("rate limit", "rate_limit", "429", "overloaded", "529")


def run_question(*args) -> dict:
    last_error = None
    attempt = 0
    while attempt < MAX_ATTEMPTS:
        attempt += 1
        try:
            return run_attempt(*args)
        except QuestionAttemptError as error:
            last_error = error
            message = str(error)
            lowered = message.lower()
            if any(marker in lowered for marker in RATE_LIMIT_MARKERS):
                # Capacity errors do not count against the attempt budget, but
                # back off substantially so the run waits out the limit window.
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
    effort: str,
    skill_dir: Path,
    results_dir: Path,
    jobs: int,
    api_base_url: str,
    api_timeout: float,
    mcp_command: Path,
    max_budget_usd: float,
    question_ids: set[str] | None = None,
) -> Path:
    benchmark = json.loads((ROOT / "benchmark.json").read_text(encoding="utf-8"))
    questions = read_jsonl(ROOT / "inputs" / "questions_blind.jsonl")
    if question_ids:
        questions = [question for question in questions if question["id"] in question_ids]
        if not questions:
            raise RuntimeError(f"no blind questions match {sorted(question_ids)}")
    features = CONDITION_FEATURES[condition]
    date = datetime.now(timezone.utc).date().isoformat()
    model_label = model.replace("/", "-").replace(".", "-")
    run_id = f"{date}_{model_label}_{condition}_r{replicate}"
    if question_ids:
        run_id += "_partial"
    run_dir = results_dir / run_id
    if (run_dir / "manifest.json").is_file():
        print(f"skipping completed run: {run_dir}", flush=True)
        return run_dir
    if run_dir.exists():
        raise RuntimeError(f"incomplete run directory already exists: {run_dir}")
    if features["mcp"] and not mcp_command.is_file():
        raise RuntimeError(f"MCP server command is missing: {mcp_command}")

    template = (ROOT / "harness" / "answer-instructions.md").read_text(
        encoding="utf-8"
    )
    prompt_template = template.replace(
        "{{CONDITION_INSTRUCTIONS}}", CONDITION_INSTRUCTIONS[condition]
    )
    gold_by_id = {
        question["id"]: question for question in read_jsonl(ROOT / "questions.jsonl")
    }
    needs_capture = features["api"] or features["mcp"]
    started_at = utc_now()
    release_start = release_start_capture = None
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
                template,
                model,
                effort,
                skill_dir,
                api_base_url,
                api_timeout,
                mcp_command,
                max_budget_usd,
                bool(gold_by_id[question["id"]].get("api_evidence")),
            ): question["id"]
            for question in questions
        }
        for future in concurrent.futures.as_completed(futures):
            question_id = futures[future]
            results_by_id[question_id] = future.result()
            print(f"completed {condition} r{replicate} {question_id}", flush=True)

    release_end = release_end_capture = None
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
        evidence, rejected = normalize_evidence(
            condition,
            answer["evidence_used"],
            skill_dir,
            question_result["api_capture"]["records"],
            question_result["api_capture"]["bodies"],
            set(question_result["mcp_tools_used"]),
        )
        requires_api = bool(gold_by_id[question["id"]].get("api_evidence"))
        if needs_capture and requires_api and not any(item["kind"] == "api" for item in evidence):
            # The answer cited no usable API path; fall back to every
            # successfully captured request so the judge still sees the data.
            evidence.extend(captured_api_evidence(question_result["api_capture"]["records"]))
            if not any(item["kind"] == "api" for item in evidence):
                raise RuntimeError(f"{condition} {question['id']} had no successful API capture")
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
    canonical_models = sorted(
        {name for result in results_by_id.values() for name in result["canonical_models"]}
    )
    usage_totals = {"input_tokens": 0, "output_tokens": 0, "cache_read_input_tokens": 0,
                    "cache_creation_input_tokens": 0}
    for result in results_by_id.values():
        for key in usage_totals:
            usage_totals[key] += int(result["usage"].get(key) or 0)
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
            "provider": "anthropic",
            "name": model,
            "snapshot": ",".join(canonical_models) if canonical_models else model,
            "parameters": {
                "effort": effort,
                "question_batching": "one_question_per_session",
                "replicate": replicate,
                "builtin_tools": BUILTIN_TOOLS[condition],
            },
        },
        "harness": {
            "name": "claude-code-cli-isolated",
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
            "codex_home": "not_applicable",
            "question_workspace": "one_question_per_session",
            "event_log_format": "claude_stream_json",
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
        "usage": {
            "total_cost_usd": round(
                sum(result["cost_usd"] for result in results_by_id.values()), 4
            ),
            "session_seconds": round(
                sum(result["elapsed_seconds"] for result in results_by_id.values()), 1
            ),
            "tool_calls": sum(
                result["tool_call_count"] for result in results_by_id.values()
            ),
            **usage_totals,
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
    parser.add_argument("--model", default="claude-opus-5-5")
    parser.add_argument(
        "--effort", choices=("low", "medium", "high", "xhigh", "max"), default="high"
    )
    parser.add_argument("--replicates", type=int, default=3)
    parser.add_argument(
        "--replicate",
        action="append",
        type=int,
        help="Run only this replicate number; repeat as needed.",
    )
    parser.add_argument("--jobs", type=int, default=4)
    parser.add_argument("--api-base-url", default=DEFAULT_API_BASE_URL)
    parser.add_argument("--api-timeout", type=float, default=30.0)
    parser.add_argument("--max-budget-usd", type=float, default=5.0)
    parser.add_argument(
        "--skill-dir",
        type=Path,
        default=Path("/scratch/jmc/scop/skills/scop-homology"),
    )
    parser.add_argument("--mcp-command", type=Path, default=DEFAULT_MCP_COMMAND)
    parser.add_argument(
        "--results-dir", type=Path, default=ROOT / "results" / "runs"
    )
    parser.add_argument(
        "--question",
        action="append",
        help="Run only this blind question ID (repeatable); marks the run partial.",
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
                args.effort,
                args.skill_dir.resolve(),
                args.results_dir.resolve(),
                args.jobs,
                args.api_base_url,
                args.api_timeout,
                args.mcp_command.resolve(),
                args.max_budget_usd,
                set(args.question) if args.question else None,
            )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
