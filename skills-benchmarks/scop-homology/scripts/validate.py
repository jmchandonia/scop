#!/usr/bin/env python3
"""Validate benchmark cases, blind inputs, fixtures, and stored results."""

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker


ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = ROOT.parents[1]
EXPECTED_CONDITIONS = {
    "pretrained_only",
    "pretrained_api",
    "pretrained_skill",
    "pretrained_mcp",
    "pretrained_mcp_skill",
}
SKILL_CONDITIONS = {"pretrained_skill", "pretrained_mcp_skill"}
LIVE_CONDITIONS = {
    "pretrained_api",
    "pretrained_skill",
    "pretrained_mcp",
    "pretrained_mcp_skill",
}
FORBIDDEN_NONSKILL_LOG_MARKERS = (
    "/scratch/jmc/scop/skills/scop-homology",
    "/scratch/jmc/scop-server",
    "skill-sources/scop-homology",
    "/.codex/skills/scop-homology",
)
QUESTION_FIELDS = {
    "id",
    "track",
    "category",
    "prompt",
    "entities",
    "seed_identifiers",
    "api_evidence",
    "expected_answer",
    "scoring_focus",
}
ANSWER_FIELDS = {
    "run_id",
    "question_id",
    "condition",
    "model",
    "prompt",
    "answer",
    "evidence_used",
    "timestamp",
}
EVALUATION_FIELDS = {
    "run_id",
    "question_id",
    "condition",
    "model",
    "biological_reasoning_score",
    "factual_support_score",
    "score",
    "rubric_notes",
    "evaluator",
    "timestamp",
}


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def tree_sha256(root: Path) -> str:
    digest = hashlib.sha256()
    for path in sorted(item for item in root.rglob("*") if item.is_file()):
        digest.update(path.relative_to(root).as_posix().encode("utf-8"))
        digest.update(b"\0")
        digest.update(path.read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()


def read_json(path: Path, errors: list[str]):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        errors.append(f"{path}: {error}")
        return None


def read_jsonl(path: Path, errors: list[str]) -> list[dict]:
    records = []
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as error:
        errors.append(f"{path}: {error}")
        return records
    for line_number, line in enumerate(lines, 1):
        if not line.strip():
            continue
        try:
            value = json.loads(line)
        except json.JSONDecodeError as error:
            errors.append(f"{path}:{line_number}: {error}")
            continue
        if not isinstance(value, dict):
            errors.append(f"{path}:{line_number}: record must be an object")
            continue
        records.append(value)
    return records


def load_validator(name: str, errors: list[str]):
    path = ROOT / "schemas" / name
    schema = read_json(path, errors)
    if schema is None:
        return None
    try:
        Draft202012Validator.check_schema(schema)
    except Exception as error:
        errors.append(f"{path}: invalid JSON Schema: {error}")
        return None
    return Draft202012Validator(schema, format_checker=FormatChecker())


def validate_records(records: list[dict], validator, label: str, errors: list[str]) -> None:
    if validator is None:
        return
    for index, record in enumerate(records, 1):
        for error in sorted(validator.iter_errors(record), key=lambda item: list(item.path)):
            location = ".".join(str(part) for part in error.path)
            suffix = f".{location}" if location else ""
            errors.append(f"{label}:{index}{suffix}: {error.message}")


def blind_record(question: dict) -> dict:
    return {
        "id": question["id"],
        "track": question["track"],
        "category": question["category"],
        "prompt": question["prompt"],
    }


def validate_questions(errors: list[str]) -> tuple[list[dict], list[str]]:
    questions_path = ROOT / "questions.jsonl"
    questions = read_jsonl(questions_path, errors)
    validate_records(
        questions, load_validator("question.schema.json", errors), "questions.jsonl", errors
    )
    ids = [question.get("id") for question in questions]
    if len(ids) != len(set(ids)):
        errors.append("questions.jsonl: question IDs must be unique")

    tracks = set()
    evidence_paths = []
    for question in questions:
        missing = QUESTION_FIELDS - question.keys()
        if missing:
            errors.append(f"{question.get('id', '<unknown>')}: missing {sorted(missing)}")
            continue
        if question["track"] not in {"conformance", "held_out"}:
            errors.append(f"{question['id']}: invalid track {question['track']!r}")
        tracks.add(question["track"])
        for seed in question["seed_identifiers"]:
            if set(seed) - {"type", "value", "role"} or not {"type", "value"} <= set(seed):
                errors.append(f"{question['id']}: invalid seed identifier {seed!r}")
        for path in question["api_evidence"]:
            if not path.startswith("/api/v1/releases/"):
                errors.append(f"{question['id']}: noncanonical evidence path {path}")
            evidence_paths.append(path)
    if tracks != {"conformance", "held_out"}:
        errors.append("questions.jsonl must contain conformance and held_out tracks")

    blind_path = ROOT / "inputs" / "questions_blind.jsonl"
    blind = read_jsonl(blind_path, errors)
    validate_records(
        blind,
        load_validator("blind-question.schema.json", errors),
        "questions_blind.jsonl",
        errors,
    )
    expected_blind = [
        blind_record(question)
        for question in questions
        if QUESTION_FIELDS <= question.keys()
    ]
    if blind != expected_blind:
        errors.append("questions_blind.jsonl is stale; run scripts/build_blind.py")
    return questions, sorted(set(evidence_paths))


def validate_fixtures(evidence_paths: list[str], errors: list[str]) -> None:
    fixture_dir = ROOT / "fixtures" / "api"
    manifest = read_json(fixture_dir / "manifest.json", errors)
    if not manifest:
        return
    if manifest.get("questions_sha256") != sha256(ROOT / "questions.jsonl"):
        errors.append("fixture manifest was generated from different questions")
    openapi_path = REPO_ROOT / "php" / "api" / "v1" / "openapi.yaml"
    benchmark = read_json(ROOT / "benchmark.json", errors) or {}
    if (
        manifest.get("suite_version") == benchmark.get("suite_version")
        and manifest.get("openapi_sha256") != sha256(openapi_path)
    ):
        # The fixtures are mounted only by the suite they were captured for;
        # later live-API suites keep them as gold-development snapshots.
        errors.append("fixture manifest OpenAPI hash does not match the current contract")

    entries = manifest.get("entries", [])
    entry_paths = [entry.get("path") for entry in entries]
    if entry_paths != evidence_paths:
        errors.append("fixture manifest paths do not exactly match unique API evidence paths")
    expected_files = {"manifest.json"}
    for entry in entries:
        filename = entry.get("file", "")
        expected_files.add(filename)
        path = fixture_dir / filename
        if not 200 <= entry.get("status", 0) < 300:
            errors.append(f"fixture {filename}: non-success status {entry.get('status')}")
        if not path.is_file():
            errors.append(f"fixture {filename}: file is missing")
            continue
        if sha256(path) != entry.get("sha256"):
            errors.append(f"fixture {filename}: SHA-256 mismatch")
        read_json(path, errors)
    actual_files = {path.name for path in fixture_dir.glob("*.json")}
    if actual_files != expected_files:
        errors.append(
            f"fixture directory differs from manifest: extra={sorted(actual_files - expected_files)}, "
            f"missing={sorted(expected_files - actual_files)}"
        )


def validate_capture_directory(
    directory: Path,
    expected_label: str,
    trace_validator,
    errors: list[str],
) -> list[dict]:
    summary = read_json(directory / "summary.json", errors)
    records = read_jsonl(directory / "trace.jsonl", errors)
    validate_records(records, trace_validator, f"{directory}/trace.jsonl", errors)
    if not isinstance(summary, dict):
        return records
    if summary.get("label") != expected_label:
        errors.append(f"{directory}: capture label must be {expected_label!r}")
    if summary.get("format") != "scop-live-http-v1":
        errors.append(f"{directory}: unknown capture format")
    expected_sequences = list(range(1, len(records) + 1))
    if [record.get("sequence") for record in records] != expected_sequences:
        errors.append(f"{directory}: trace sequence must be contiguous from 1")

    successful = sum(
        1
        for record in records
        if isinstance(record.get("response"), dict)
        and 200 <= record["response"].get("status", 0) < 300
    )
    transient = sum(1 for record in records if record.get("transient") is True)
    body_hashes = {
        record.get("response", {}).get("body_sha256") for record in records
    }
    body_hashes.discard(None)
    expected_summary = {
        "request_count": len(records),
        "successful_response_count": successful,
        "transient_error_count": transient,
        "response_body_count": len(body_hashes),
    }
    for field, expected in expected_summary.items():
        if summary.get(field) != expected:
            errors.append(
                f"{directory}: {field} must be {expected}, got {summary.get(field)!r}"
            )

    bodies_dir = directory / "bodies"
    actual_bodies = (
        {path.name for path in bodies_dir.iterdir() if path.is_file()}
        if bodies_dir.is_dir()
        else set()
    )
    expected_bodies = {f"{digest}.body" for digest in body_hashes}
    if actual_bodies != expected_bodies:
        errors.append(
            f"{directory}: captured body set differs: "
            f"extra={sorted(actual_bodies - expected_bodies)}, "
            f"missing={sorted(expected_bodies - actual_bodies)}"
        )
    for record in records:
        response = record.get("response", {})
        digest = response.get("body_sha256")
        body_file = response.get("body_file")
        if not isinstance(digest, str) or body_file != f"bodies/{digest}.body":
            continue
        body_path = directory / body_file
        if not body_path.is_file():
            continue
        if sha256(body_path) != digest:
            errors.append(f"{body_path}: SHA-256 mismatch")
        if body_path.stat().st_size != response.get("body_bytes"):
            errors.append(f"{body_path}: byte count differs from trace")
    return records


def validate_live_api_capture(
    run_dir: Path,
    manifest: dict,
    question_ids: set[str],
    questions_requiring_api: set[str],
    errors: list[str],
) -> dict[str, dict[str, str]]:
    api_access = manifest.get("api_access")
    if not isinstance(api_access, dict):
        errors.append(f"{run_dir}: live API run has no api_access manifest")
        return {}
    api_dir = run_dir / "api"
    if not api_dir.is_dir():
        errors.append(f"{run_dir}: live API capture directory is missing")
        return {}
    if api_access.get("trace_tree_sha256") != tree_sha256(api_dir):
        errors.append(f"{run_dir}: live API capture tree SHA-256 mismatch")
    openapi_path = REPO_ROOT / "php" / "api" / "v1" / "openapi.yaml"
    if api_access.get("openapi_sha256") != sha256(openapi_path):
        errors.append(f"{run_dir}: live API OpenAPI SHA-256 mismatch")

    expected_directories = question_ids | {"release-start", "release-end"}
    actual_directories = {
        path.name for path in api_dir.iterdir() if path.is_dir()
    }
    if actual_directories != expected_directories:
        errors.append(
            f"{run_dir}: live API trace set differs: "
            f"extra={sorted(actual_directories - expected_directories)}, "
            f"missing={sorted(expected_directories - actual_directories)}"
        )

    trace_validator = load_validator("api-trace.schema.json", errors)
    all_records = []
    successful_by_question: dict[str, dict[str, str]] = {}
    response_body_count = 0
    for label in sorted(expected_directories):
        directory = api_dir / label
        if not directory.is_dir():
            continue
        records = validate_capture_directory(
            directory, label, trace_validator, errors
        )
        all_records.extend(records)
        summary = read_json(directory / "summary.json", errors)
        if isinstance(summary, dict):
            response_body_count += summary.get("response_body_count", 0)
            if summary.get("upstream_base_url") != api_access.get(
                "upstream_base_url"
            ):
                errors.append(f"{directory}: upstream base URL differs from manifest")
        if label in question_ids:
            successful_by_question[label] = {
                record["request"]["path_query"]: record["response"]["body_sha256"]
                for record in records
                if 200 <= record.get("response", {}).get("status", 0) < 300
            }
            if label in questions_requiring_api and not successful_by_question[label]:
                errors.append(f"{directory}: question has no successful API response")

    aggregates = {
        "question_trace_count": len(question_ids),
        "request_count": len(all_records),
        "successful_response_count": sum(
            1
            for record in all_records
            if 200 <= record.get("response", {}).get("status", 0) < 300
        ),
        "transient_error_count": sum(
            1 for record in all_records if record.get("transient") is True
        ),
        "response_body_count": response_body_count,
    }
    for field, expected in aggregates.items():
        if api_access.get(field) != expected:
            errors.append(
                f"{run_dir}: api_access.{field} must be {expected}, "
                f"got {api_access.get(field)!r}"
            )

    for observation_name, directory_name in (
        ("release_start", "release-start"),
        ("release_end", "release-end"),
    ):
        observation = api_access.get(observation_name, {})
        trace_path = api_dir / directory_name / "trace.jsonl"
        records = read_jsonl(trace_path, errors)
        successful = [
            record
            for record in records
            if record.get("request", {}).get("path_query")
            == "/api/v1/releases/current"
            and 200 <= record.get("response", {}).get("status", 0) < 300
        ]
        if len(successful) != 1:
            errors.append(
                f"{run_dir}: {observation_name} must contain one successful current-release request"
            )
        else:
            response = successful[0]["response"]
            if observation.get("response_sha256") != response["body_sha256"]:
                errors.append(
                    f"{run_dir}: {observation_name} response hash differs from trace"
                )
            payload = read_json(
                api_dir / directory_name / response["body_file"], errors
            )
            if isinstance(payload, dict):
                periodic = payload.get("latest_periodic_update")
                periodic_version = (
                    periodic.get("version") if isinstance(periodic, dict) else None
                )
                expected_observation = {
                    "stable_release": payload.get("version"),
                    "stable_label": payload.get("stable_label"),
                    "latest_periodic_update": periodic_version,
                    "response_sha256": response["body_sha256"],
                }
                if observation != expected_observation:
                    errors.append(
                        f"{run_dir}: {observation_name} metadata differs from "
                        "captured current-release response"
                    )
    start_release = api_access.get("release_start", {}).get("stable_release")
    end_release = api_access.get("release_end", {}).get("stable_release")
    if start_release != end_release:
        errors.append(f"{run_dir}: stable release changed during live API run")
    return successful_by_question


def normalized_text(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", " ", value.lower()).strip()


def validate_skill_overlap(
    skill_dir: Path | None, questions: list[dict], errors: list[str]
) -> None:
    if skill_dir is None:
        return
    if not skill_dir.is_dir():
        errors.append(f"skill directory does not exist: {skill_dir}")
        return
    text = normalized_text("\n".join(
        path.read_text(encoding="utf-8", errors="ignore")
        for path in skill_dir.rglob("*")
        if path.is_file()
    ))
    for question in questions:
        if question.get("track") != "held_out":
            continue
        candidates = [
            ("entity", value) for value in question.get("entities", [])
        ]
        for seed in question.get("seed_identifiers", []):
            if seed.get("type") in {"concept", "paper", "release"}:
                continue
            candidates.append(("identifier", str(seed.get("value", ""))))
        for kind, value in candidates:
            normalized = normalized_text(value)
            if len(normalized) >= 4 and normalized in text:
                errors.append(
                    f"{question['id']}: held-out {kind} {value!r} appears in {skill_dir}"
                )


def gated_score(biological_score: int, factual_score: int) -> float:
    factual_gate = (1, 2, 3, 4, 4)[factual_score]
    weighted = (3 * biological_score + factual_score) / 4
    return min(weighted, factual_gate)


def validate_reporting_groups(
    benchmark: dict, questions: list[dict], errors: list[str]
) -> set[str]:
    question_ids = {question["id"] for question in questions}
    reporting_groups = benchmark.get("reporting_groups", {})
    biological = reporting_groups.get("biological", {})
    api_conformance = reporting_groups.get("api_conformance", {})
    excluded_ids = set(biological.get("exclude_question_ids", []))
    api_ids = set(api_conformance.get("question_ids", []))

    unknown = (excluded_ids | api_ids) - question_ids
    if unknown:
        errors.append(f"reporting groups reference unknown questions: {sorted(unknown)}")
    if excluded_ids != api_ids:
        errors.append(
            "biological exclusions must exactly match api_conformance question IDs"
        )
    biological_ids = question_ids - excluded_ids
    if not biological_ids:
        errors.append("biological reporting group must not be empty")
    if not api_ids:
        errors.append("api_conformance reporting group must not be empty")
    return api_ids


def validate_results(
    benchmark: dict,
    questions: list[dict],
    api_conformance_ids: set[str],
    errors: list[str],
) -> int:
    question_ids = {question["id"] for question in questions}
    questions_requiring_api = {
        question["id"] for question in questions if question.get("api_evidence")
    }
    current_suite = benchmark["suite_version"]
    allowed_suites = {
        current_suite,
        *benchmark.get("compatible_result_suite_versions", []),
    }
    runs_dir = ROOT / "results" / "runs"
    manifest_validator = load_validator("run-manifest.schema.json", errors)
    answer_validator = load_validator("answer.schema.json", errors)
    evaluation_validator = load_validator("evaluation.schema.json", errors)
    checked_runs = 0
    for run_dir in sorted(path for path in runs_dir.iterdir() if path.is_dir()):
        manifest = read_json(run_dir / "manifest.json", errors)
        if not manifest:
            continue
        if manifest.get("suite_version") not in allowed_suites:
            continue
        checked_runs += 1
        required_files = {"manifest.json", "answers.jsonl", "evaluation.jsonl", "summary.md"}
        missing_files = [name for name in required_files if not (run_dir / name).is_file()]
        if missing_files:
            errors.append(f"{run_dir}: missing result files {missing_files}")
            continue
        validate_records([manifest], manifest_validator, f"{run_dir}/manifest.json", errors)
        required_manifest = {
            "run_id",
            "benchmark_id",
            "suite_version",
            "questions_sha256",
            "blind_questions_sha256",
            "condition",
            "model",
            "harness",
            "skill",
            "fixture_manifest_sha256",
            "isolation_audit",
            "evaluation",
            "started_at",
            "completed_at",
        }
        missing = required_manifest - manifest.keys()
        if missing:
            errors.append(f"{run_dir}/manifest.json: missing {sorted(missing)}")
        if manifest.get("run_id") != run_dir.name:
            errors.append(f"{run_dir}: manifest run_id must match directory name")
        if manifest.get("benchmark_id") != benchmark["benchmark_id"]:
            errors.append(f"{run_dir}: benchmark_id mismatch")
        if manifest.get("questions_sha256") != sha256(ROOT / "questions.jsonl"):
            errors.append(f"{run_dir}: questions SHA-256 mismatch")
        if manifest.get("blind_questions_sha256") != sha256(
            ROOT / "inputs" / "questions_blind.jsonl"
        ):
            errors.append(f"{run_dir}: blind questions SHA-256 mismatch")
        if manifest.get("condition") not in EXPECTED_CONDITIONS:
            errors.append(f"{run_dir}: unknown condition {manifest.get('condition')!r}")
        logs_dir = run_dir / "logs"
        if not logs_dir.is_dir():
            errors.append(f"{run_dir}: logs directory is missing")
        else:
            expected_logs = {
                f"{question_id}.{suffix}"
                for question_id in question_ids
                for suffix in ("events.jsonl", "stderr.log")
            }
            actual_logs = {
                path.name for path in logs_dir.iterdir() if path.is_file()
            }
            if actual_logs != expected_logs:
                errors.append(
                    f"{run_dir}: log set differs: "
                    f"extra={sorted(actual_logs - expected_logs)}, "
                    f"missing={sorted(expected_logs - actual_logs)}"
                )
            audit = manifest.get("isolation_audit", {})
            if audit.get("logs_sha256") != tree_sha256(logs_dir):
                errors.append(f"{run_dir}: isolation log SHA-256 mismatch")
            if manifest.get("condition") not in SKILL_CONDITIONS:
                event_text = "\n".join(
                    path.read_text(encoding="utf-8", errors="ignore")
                    for path in logs_dir.glob("*.events.jsonl")
                ).lower()
                for marker in FORBIDDEN_NONSKILL_LOG_MARKERS:
                    if marker.lower() in event_text:
                        errors.append(
                            f"{run_dir}: event logs contain forbidden marker {marker!r}"
                        )

        successful_api = {}
        is_current_live_run = (
            manifest.get("suite_version") == current_suite
            and manifest.get("condition") in LIVE_CONDITIONS
        )
        if is_current_live_run:
            successful_api = validate_live_api_capture(
                run_dir, manifest, question_ids, questions_requiring_api, errors
            )
        elif manifest.get("suite_version") == current_suite:
            if manifest.get("api_access") is not None:
                errors.append(f"{run_dir}: pretrained-only run must not have API access")
            if (run_dir / "api").exists():
                errors.append(f"{run_dir}: pretrained-only run has API traces")

        answers = read_jsonl(run_dir / "answers.jsonl", errors)
        evaluations = read_jsonl(run_dir / "evaluation.jsonl", errors)
        validate_records(answers, answer_validator, f"{run_dir}/answers.jsonl", errors)
        validate_records(
            evaluations, evaluation_validator, f"{run_dir}/evaluation.jsonl", errors
        )
        for record, fields, label in (
            *((answer, ANSWER_FIELDS, "answer") for answer in answers),
            *((evaluation, EVALUATION_FIELDS, "evaluation") for evaluation in evaluations),
        ):
            missing = fields - record.keys()
            if missing:
                errors.append(f"{run_dir}: {label} record missing {sorted(missing)}")
            if record.get("question_id") not in question_ids:
                errors.append(f"{run_dir}: unknown question ID {record.get('question_id')!r}")
            if record.get("condition") != manifest.get("condition"):
                errors.append(f"{run_dir}: {label} condition differs from manifest")
        for evaluation in evaluations:
            question_id = evaluation.get("question_id")
            biological_score = evaluation.get("biological_reasoning_score")
            factual_score = evaluation.get("factual_support_score")
            overall_score = evaluation.get("score")
            if question_id in api_conformance_ids:
                if biological_score is not None:
                    errors.append(
                        f"{run_dir}: {question_id} biological_reasoning_score must be null"
                    )
                if overall_score != factual_score:
                    errors.append(
                        f"{run_dir}: {question_id} score must equal factual_support_score"
                    )
            elif isinstance(biological_score, int) and isinstance(factual_score, int):
                expected_score = gated_score(biological_score, factual_score)
                if overall_score != expected_score:
                    errors.append(
                        f"{run_dir}: {question_id} score must be {expected_score} "
                        "from the weighted subscores and factual-support gate"
                    )
        if is_current_live_run:
            for answer in answers:
                question_id = answer.get("question_id")
                permitted = successful_api.get(question_id, {})
                api_evidence = [
                    evidence
                    for evidence in answer.get("evidence_used", [])
                    if evidence.get("kind") == "api"
                ]
                if question_id in questions_requiring_api and not api_evidence:
                    errors.append(
                        f"{run_dir}: {question_id} has no captured API evidence"
                    )
                for evidence in api_evidence:
                    reference = evidence.get("reference")
                    if reference not in permitted:
                        errors.append(
                            f"{run_dir}: {question_id} API evidence "
                            f"{reference!r} was not successfully captured"
                        )
                    elif evidence.get("sha256") != permitted[reference]:
                        errors.append(
                            f"{run_dir}: {question_id} API evidence hash mismatch "
                            f"for {reference!r}"
                        )
        answer_ids = [record.get("question_id") for record in answers]
        evaluation_ids = [record.get("question_id") for record in evaluations]
        if set(answer_ids) != question_ids or len(answer_ids) != len(question_ids):
            errors.append(f"{run_dir}: answers must contain each question exactly once")
        if set(evaluation_ids) != question_ids or len(evaluation_ids) != len(question_ids):
            errors.append(f"{run_dir}: evaluations must contain each question exactly once")
    return checked_runs


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--skill-dir", type=Path)
    args = parser.parse_args()
    errors: list[str] = []

    benchmark = read_json(ROOT / "benchmark.json", errors)
    if not benchmark:
        return 1
    condition_ids = {condition.get("id") for condition in benchmark.get("conditions", [])}
    if condition_ids != EXPECTED_CONDITIONS:
        errors.append(f"benchmark conditions must be {sorted(EXPECTED_CONDITIONS)}")
    conditions = {
        condition.get("id"): condition for condition in benchmark.get("conditions", [])
    }
    for condition_id in sorted(LIVE_CONDITIONS):
        if conditions.get(condition_id, {}).get("data_access") != "live_rest_capture":
            errors.append(f"{condition_id} must use captured live REST access")
    load_validator("harness-answer.schema.json", errors)
    load_validator("harness-evaluation.schema.json", errors)
    load_validator("api-trace.schema.json", errors)

    questions, evidence_paths = validate_questions(errors)
    api_conformance_ids = validate_reporting_groups(benchmark, questions, errors)
    validate_fixtures(evidence_paths, errors)
    validate_skill_overlap(args.skill_dir, questions, errors)
    checked_runs = validate_results(benchmark, questions, api_conformance_ids, errors)

    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        print(f"validation failed with {len(errors)} error(s)", file=sys.stderr)
        return 1
    print(
        f"validated {len(questions)} questions, {len(evidence_paths)} gold fixtures, "
        f"and {checked_runs} compatible runs"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
