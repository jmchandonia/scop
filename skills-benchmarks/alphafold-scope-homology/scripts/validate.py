#!/usr/bin/env python3
import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker
from run_codex_mcp_benchmark import (
    CLAIM_DISCLOSURE,
    HARNESS_VERSION,
    definitive_claim_matches,
    evaluator_prompt,
    generator_prompt,
    text_sha256,
    tree_sha256,
)

ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = ROOT.parents[1]
sys.path.insert(0, str(REPO_ROOT / "scripts"))
from scop_public_skills import public_skills_dir
OPENAPI_CANDIDATES = (
    REPO_ROOT / "php" / "api" / "v1" / "openapi.yaml",
    REPO_ROOT / "api" / "v1" / "openapi.yaml",
)
OPENAPI_PATH = next(
    (path for path in OPENAPI_CANDIDATES if path.is_file()), OPENAPI_CANDIDATES[0]
)
GOLD_CLAIM_DISCLOSURE = (
    "Reference assessment only: this is a non-official evidence hypothesis, "
    "not a definitive homology determination or SCOPe classification."
)


def gold_layout_errors(answer_text):
    """Validate the reviewer reference layout, not generated-answer scoring."""
    errors = []
    markers = (
        "\n\nBenchmark question: ",
        "\n\n### 1. Conclusion\n\n",
        "\n\n### 2. What can be said at each level\n\n",
        "\n\n### 3. Evidence considered\n\n",
        "\n\n### 4. Caveats by evidence item\n\n",
    )
    positions = [answer_text.find(marker) for marker in markers]
    for marker, position in zip(markers, positions):
        if position < 0:
            errors.append(f"gold lacks section: {marker.strip()}")
    if errors:
        return errors
    if positions != sorted(positions):
        return ["gold sections are not in reading order"]

    assessments = answer_text[positions[2] : positions[3]]
    levels = re.findall(r"^\| (Function|Family|Superfamily|Fold) \|", assessments, re.M)
    expected_levels = ["Function", "Family", "Superfamily", "Fold"]
    if not levels or len(levels) % 4 or levels != expected_levels * (len(levels) // 4):
        errors.append("gold lacks ordered function/family/superfamily/fold assessments")

    evidence = re.findall(
        r"^- \*\*(E\d+)\*\* — (.+)$",
        answer_text[positions[3] : positions[4]],
        re.M,
    )
    evidence_ids = [identifier for identifier, _ in evidence]
    if not evidence_ids or evidence_ids != [f"E{i}" for i in range(1, len(evidence) + 1)]:
        errors.append("gold lacks a consecutively numbered evidence list")
    for identifier, observation in evidence:
        if not re.search(r"\[[^\]]+\]\(https?://[^\s)]+\)", observation):
            errors.append(f"gold evidence {identifier} lacks a clickable source")
    caveat_ids = re.findall(
        r"^- \*\*(E\d+)\*\* — .+$", answer_text[positions[4] :], re.M
    )
    if caveat_ids != evidence_ids:
        errors.append("gold caveats do not correspond to the evidence items")
    return errors


def load_json(path, errors):
    try:
        return json.loads(path.read_text())
    except Exception as error:
        errors.append(f"{path}: {error}")
        return None


def load_jsonl(path, errors):
    records = []
    try:
        lines = path.read_text().splitlines()
    except Exception as error:
        errors.append(f"{path}: {error}")
        return records
    for number, line in enumerate(lines, 1):
        if not line.strip():
            continue
        try:
            records.append(json.loads(line))
        except Exception as error:
            errors.append(f"{path}:{number}: {error}")
    return records


def validator(name, errors):
    schema = load_json(ROOT / "schemas" / name, errors)
    if schema is None:
        return None
    try:
        Draft202012Validator.check_schema(schema)
    except Exception as error:
        errors.append(f"{name}: invalid schema: {error}")
        return None
    return Draft202012Validator(schema, format_checker=FormatChecker())


def validate_records(records, schema_name, label, errors):
    check = validator(schema_name, errors)
    if check is None:
        return
    for index, record in enumerate(records, 1):
        for error in check.iter_errors(record):
            errors.append(f"{label}:{index}: {error.message}")


def fixture_paths(plan):
    paths = []
    release, limit = plan["release"], plan["hit_limit"]
    for model, sids in plan["models"].items():
        base = f"/releases/{release}/alphafolds/{model}"
        paths.extend((base, f"{base}/hits?limit={limit}"))
        for sid in sids:
            domain = f"/releases/{release}/sids/{sid}"
            paths.append(domain)
            paths.extend(f"{domain}/{part}" for part in plan["domain_subresources"])
        for benchmark in plan.get("benchmark_nodes", {}).get(model, []):
            paths.append(
                f"/releases/{release}/sunids/{benchmark['sunid']}"
                "/fatcat-chain-benchmark?minimum_supporting_hits="
                f"{benchmark['minimum_supporting_hits']}"
            )
    return sorted(set(paths))


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--minimum-completed-runs",
        type=int,
        default=0,
        help=(
            "fail unless at least this many current-suite runs validate; the "
            "development default is zero, use 1 for a release check"
        ),
    )
    args = parser.parse_args(argv)
    if args.minimum_completed_runs < 0:
        parser.error("--minimum-completed-runs must be non-negative")

    errors = []
    benchmark = load_json(ROOT / "benchmark.json", errors)
    plan = load_json(ROOT / "evidence-plan.json", errors)
    questions = load_jsonl(ROOT / "questions.jsonl", errors)
    blind = load_jsonl(ROOT / "inputs" / "questions_blind.jsonl", errors)
    gold = load_jsonl(ROOT / "gold" / "answers.jsonl", errors)
    validate_records(questions, "question.schema.json", "questions", errors)
    validate_records(blind, "blind-question.schema.json", "blind", errors)
    validate_records(gold, "gold-answer.schema.json", "gold", errors)
    if len(questions) != 12:
        errors.append(f"expected 12 questions, found {len(questions)}")
    ids = [row.get("id") for row in questions]
    if len(ids) != len(set(ids)):
        errors.append("question IDs are not unique")
    expected_opaque_ids = [f"AFQ{number:03d}" for number in range(1, 13)]
    if ids != expected_opaque_ids:
        errors.append(
            "current question IDs must be opaque and ordered AFQ001 through AFQ012"
        )
    expected_blind = [
        {
            key: row[key]
            for key in ("id", "category", "difficulty", "model", "release", "prompt")
        }
        for row in questions
    ]
    if blind != expected_blind:
        errors.append("blind questions are stale; run scripts/build_blind.py")
    if {row.get("question_id") for row in gold} != set(ids):
        errors.append("gold answers do not exactly cover the question IDs")
    questions_by_id = {row.get("id"): row for row in questions}
    for answer in gold:
        question = questions_by_id.get(answer.get("question_id"))
        if question and (answer.get("model"), answer.get("release")) != (
            question.get("model"),
            question.get("release"),
        ):
            errors.append(f"{answer.get('question_id')}: gold model/release mismatch")
        answer_text = answer.get("answer", "")
        if not answer_text.startswith(GOLD_CLAIM_DISCLOSURE):
            errors.append(
                f"{answer.get('question_id')}: gold lacks the reference claim boundary"
            )
        errors.extend(
            f"{answer.get('question_id')}: {error}"
            for error in gold_layout_errors(answer_text)
        )
        if "SCCS " not in answer_text:
            errors.append(
                f"{answer.get('question_id')}: gold lacks named SCCS terminology"
            )
        release_link_suffix = f"&ver={answer.get('release')})"
        if "https://scop.berkeley.edu/?sunid=" not in answer_text or (
            answer.get("release") and release_link_suffix not in answer_text
        ):
            errors.append(
                f"{answer.get('question_id')}: gold lacks a release-qualified SCOPe link"
            )
        matches = definitive_claim_matches(answer_text)
        if matches:
            errors.append(
                f"{answer.get('question_id')}: gold has definitive claim language: "
                f"{matches}"
            )
    if plan:
        paths = fixture_paths(plan)
        manifest = load_json(ROOT / "fixtures" / "api" / "manifest.json", errors)
        if manifest:
            if manifest.get("benchmark_id") != benchmark.get("benchmark_id"):
                errors.append("fixture manifest benchmark_id differs from benchmark")
            if manifest.get("suite_version") != benchmark.get("suite_version"):
                errors.append("fixture manifest suite_version differs from benchmark")
            entries = manifest.get("entries", [])
            if [entry.get("path") for entry in entries] != paths:
                errors.append("fixture paths differ from the evidence plan")
            if (
                manifest.get("evidence_plan_sha256")
                != hashlib.sha256(
                    (ROOT / "evidence-plan.json").read_bytes()
                ).hexdigest()
            ):
                errors.append("fixture manifest uses a different evidence plan")
            if (
                manifest.get("questions_sha256")
                != hashlib.sha256((ROOT / "questions.jsonl").read_bytes()).hexdigest()
            ):
                errors.append("fixture manifest uses different questions")
            if (
                manifest.get("openapi_sha256")
                != hashlib.sha256(OPENAPI_PATH.read_bytes()).hexdigest()
            ):
                errors.append("fixture manifest uses a different OpenAPI contract")
            if (
                manifest.get("skill_sha256")
                != hashlib.sha256(
                    (
                        public_skills_dir() / "alphafold-scope-homology" / "SKILL.md"
                    ).read_bytes()
                ).hexdigest()
            ):
                errors.append("fixture manifest uses a different skill")
            manifest_paths = {entry.get("path") for entry in entries}
            for answer in gold:
                missing_paths = set(answer.get("evidence_paths", [])) - manifest_paths
                if missing_paths:
                    errors.append(
                        f"{answer.get('question_id')}: gold evidence missing from fixtures: {sorted(missing_paths)}"
                    )
            expected_files = {"manifest.json"}
            for entry in entries:
                path = ROOT / "fixtures" / "api" / entry.get("file", "")
                expected_files.add(path.name)
                if not path.is_file():
                    errors.append(f"missing fixture {path.name}")
                elif hashlib.sha256(path.read_bytes()).hexdigest() != entry.get(
                    "sha256"
                ):
                    errors.append(f"fixture hash mismatch: {path.name}")
            actual_files = {
                path.name for path in (ROOT / "fixtures" / "api").glob("*.json")
            }
            if actual_files != expected_files:
                errors.append("fixture directory does not match its manifest")
    if not (ROOT / "gold" / "evidence-summary.json").is_file():
        errors.append("gold/evidence-summary.json is missing")
    runs = [path for path in (ROOT / "results" / "runs").iterdir() if path.is_dir()]
    current_run_count = 0
    for run in runs:
        manifest = load_json(run / "manifest.json", errors)
        answers = load_jsonl(run / "answers.jsonl", errors)
        evaluations = load_jsonl(run / "evaluation.jsonl", errors)
        validate_records(
            [manifest] if manifest else [], "run-manifest.schema.json", str(run), errors
        )
        validate_records(answers, "answer.schema.json", str(run), errors)
        validate_records(evaluations, "evaluation.schema.json", str(run), errors)
        answer_ids = [row.get("question_id") for row in answers]
        evaluation_ids = [row.get("question_id") for row in evaluations]
        if len(answer_ids) != len(set(answer_ids)):
            errors.append(f"{run}: answer question IDs must be unique")
        if len(evaluation_ids) != len(set(evaluation_ids)):
            errors.append(f"{run}: evaluation question IDs must be unique")
        if set(answer_ids) != set(evaluation_ids):
            errors.append(f"{run}: answers and evaluations cover different questions")
        if manifest:
            run_id = manifest.get("run_id")
            condition = manifest.get("condition")
            is_current_suite = manifest.get("suite_version") == benchmark.get(
                "suite_version"
            )
            if is_current_suite:
                current_run_count += 1
                if set(answer_ids) != set(ids):
                    errors.append(
                        f"{run}: current answers must cover every question exactly once"
                    )
                if set(evaluation_ids) != set(ids):
                    errors.append(
                        f"{run}: current evaluations must cover every question exactly once"
                    )
            if run.name != run_id:
                errors.append(f"{run}: directory name must equal manifest run_id")
            expected_questions_hash = hashlib.sha256(
                (ROOT / "questions.jsonl").read_bytes()
            ).hexdigest()
            expected_fixture_hash = hashlib.sha256(
                (ROOT / "fixtures" / "api" / "manifest.json").read_bytes()
            ).hexdigest()
            expected_blind_hash = hashlib.sha256(
                (ROOT / "inputs" / "questions_blind.jsonl").read_bytes()
            ).hexdigest()
            expected_gold_hash = hashlib.sha256(
                (ROOT / "gold" / "answers.jsonl").read_bytes()
            ).hexdigest()
            expected_rubric_hash = hashlib.sha256(
                (ROOT / "rubric.md").read_bytes()
            ).hexdigest()
            expected_runner_hash = hashlib.sha256(
                (ROOT / "scripts" / "run_codex_mcp_benchmark.py").read_bytes()
            ).hexdigest()
            expected_skill_hash = hashlib.sha256(
                (
                    public_skills_dir() / "alphafold-scope-homology" / "SKILL.md"
                ).read_bytes()
            ).hexdigest()
            expected_skill_tree_hash = tree_sha256(
                public_skills_dir() / "alphafold-scope-homology"
            )
            expected_mcp_source_tree_hash = tree_sha256(REPO_ROOT / "mcp" / "src")
            if is_current_suite:
                if manifest.get("questions_sha256") != expected_questions_hash:
                    errors.append(f"{run}: questions_sha256 does not match this suite")
                if manifest.get("fixture_manifest_sha256") != expected_fixture_hash:
                    errors.append(
                        f"{run}: fixture_manifest_sha256 does not match this suite"
                    )
                if manifest.get("skill_sha256") != expected_skill_hash:
                    errors.append(f"{run}: skill_sha256 does not match this suite")
                for field, expected in (
                    ("blind_questions_sha256", expected_blind_hash),
                    ("gold_answers_sha256", expected_gold_hash),
                    ("rubric_sha256", expected_rubric_hash),
                    ("runner_sha256", expected_runner_hash),
                    ("skill_tree_sha256", expected_skill_tree_hash),
                    ("mcp_source_tree_sha256", expected_mcp_source_tree_hash),
                ):
                    if manifest.get(field) != expected:
                        errors.append(f"{run}: {field} does not match this suite")
                for field, filename in (
                    ("answers_sha256", "answers.jsonl"),
                    ("evaluation_sha256", "evaluation.jsonl"),
                ):
                    expected = hashlib.sha256((run / filename).read_bytes()).hexdigest()
                    if manifest.get(field) != expected:
                        errors.append(f"{run}: {field} does not match {filename}")

                harness = manifest.get("model", {}).get("harness", {})
                if harness.get("version") != HARNESS_VERSION:
                    errors.append(
                        f"{run}: current suite requires harness version "
                        f"{HARNESS_VERSION}"
                    )
                if harness.get("workspace_input_attestation_enforced") is not True:
                    errors.append(
                        f"{run}: workspace input attestation was not enforced"
                    )
                if harness.get("non_definitive_claim_enforced") is not True:
                    errors.append(f"{run}: non-definitive claims were not enforced")
                if harness.get("evidence_citations_enforced") is not True:
                    errors.append(f"{run}: evidence citations were not enforced")
                semantic_audit = harness.get("semantic_audit", {})
                if set(semantic_audit) != set(ids):
                    errors.append(
                        f"{run}: semantic audit must cover every question exactly once"
                    )
                else:
                    for question_id, audit in semantic_audit.items():
                        if audit.get("issues"):
                            errors.append(
                                f"{run}: {question_id} has semantic audit issues"
                            )
                        citation_audit = audit.get("citation_audit", {})
                        if not citation_audit.get("items") or citation_audit.get("issues"):
                            errors.append(
                                f"{run}: {question_id} lacks a passing evidence-citation audit"
                            )
                max_mcp_calls = (
                    manifest.get("model", {})
                    .get("parameters", {})
                    .get("max_mcp_calls")
                )
                if isinstance(max_mcp_calls, int) and max_mcp_calls > 0:
                    expected_generator_prompts = {
                        question["id"]: text_sha256(
                            generator_prompt(
                                {
                                    key: question[key]
                                    for key in (
                                        "id",
                                        "category",
                                        "difficulty",
                                        "model",
                                        "release",
                                        "prompt",
                                    )
                                },
                                max_mcp_calls,
                            )
                        )
                        for question in questions
                    }
                    observed_generator_prompts = harness.get(
                        "generator_inputs", {}
                    ).get("prompt_sha256_by_question")
                    if observed_generator_prompts != expected_generator_prompts:
                        errors.append(f"{run}: generator prompt hashes do not match")
                else:
                    errors.append(f"{run}: invalid max_mcp_calls in manifest")

                answers_by_id = {row.get("question_id"): row for row in answers}
                gold_by_id = {row.get("question_id"): row for row in gold}
                rubric_text = (ROOT / "rubric.md").read_text()
                if set(semantic_audit) == set(ids) and set(answers_by_id) == set(ids):
                    expected_evaluator_prompts = {
                        question["id"]: text_sha256(
                            evaluator_prompt(
                                question,
                                gold_by_id[question["id"]],
                                answers_by_id[question["id"]],
                                rubric_text,
                                semantic_audit[question["id"]],
                            )
                        )
                        for question in questions
                    }
                    observed_evaluator_prompts = harness.get(
                        "evaluator_inputs", {}
                    ).get("prompt_sha256_by_question")
                    if observed_evaluator_prompts != expected_evaluator_prompts:
                        errors.append(f"{run}: evaluator prompt hashes do not match")

                for answer in answers:
                    question = questions_by_id.get(answer.get("question_id"))
                    if question is None:
                        continue
                    blind_record = {
                        key: question[key]
                        for key in (
                            "id",
                            "category",
                            "difficulty",
                            "model",
                            "release",
                            "prompt",
                        )
                    }
                    question_hash = hashlib.sha256(
                        (json.dumps(blind_record, separators=(",", ":")) + "\n").encode(
                            "utf-8"
                        )
                    ).hexdigest()
                    expected_attestation = {
                        "question_id": question["id"],
                        "question_sha256": question_hash,
                        "skill_sha256": expected_skill_hash,
                        "model": question["model"],
                        "release": question["release"],
                    }
                    if answer.get("claim_status") != (
                        "non_official_evidence_hypothesis"
                    ):
                        errors.append(
                            f"{run}: {question['id']} lacks non-definitive claim status"
                        )
                    if answer.get("input_attestation") != expected_attestation:
                        errors.append(
                            f"{run}: {question['id']} input attestation mismatch"
                        )
                    answer_text = answer.get("answer", "")
                    if not answer_text.startswith(CLAIM_DISCLOSURE):
                        errors.append(
                            f"{run}: {question['id']} lacks the claim-boundary disclosure"
                        )
                    matches = definitive_claim_matches(answer_text)
                    if matches:
                        errors.append(
                            f"{run}: {question['id']} has definitive claim language: "
                            f"{matches}"
                        )
                criterion_names = {
                    "task_resolution",
                    "scientific_correctness",
                    "evidence_completeness",
                    "calibration_and_caveats",
                }
                for evaluation in evaluations:
                    if set(evaluation.get("criterion_scores", {})) != criterion_names:
                        errors.append(
                            f"{run}: {evaluation.get('question_id')} lacks complete criterion scores"
                        )
                    if not isinstance(evaluation.get("applied_penalties"), list):
                        errors.append(
                            f"{run}: {evaluation.get('question_id')} lacks applied_penalties"
                        )
            if any(
                row.get("run_id") != run_id or row.get("condition") != condition
                for row in answers
            ):
                errors.append(f"{run}: answer run_id/condition differs from manifest")
            if any(row.get("run_id") != run_id for row in evaluations):
                errors.append(f"{run}: evaluation run_id differs from manifest")
        if not (run / "summary.md").is_file():
            errors.append(f"{run}: summary.md is missing")
    if current_run_count < args.minimum_completed_runs:
        errors.append(
            f"expected at least {args.minimum_completed_runs} completed current-suite "
            f"runs, found {current_run_count}"
        )
    if errors:
        print("\n".join(f"ERROR: {error}" for error in errors), file=sys.stderr)
        return 1
    fixture_count = len(fixture_paths(plan)) if plan else 0
    print(
        f"validated {len(questions)} questions, {fixture_count} fixtures, "
        f"{len(gold)} gold answers, {len(runs)} historical runs, and "
        f"{current_run_count} current-suite runs"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
