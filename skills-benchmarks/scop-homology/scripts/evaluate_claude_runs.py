#!/usr/bin/env python3
"""Blindly evaluate replicated benchmark runs with a Claude judge.

This is the Claude Code counterpart of ``evaluate_codex_runs.py``: one fresh,
tool-less, non-persistent session per question, opaque randomized answer
labels, the private gold shown only to the judge, and structured output
validated against the harness evaluation schema. Scores, summaries and
comparison reports are written in the same formats so the two evaluators are
interchangeable in the result tree.
"""

import argparse
import concurrent.futures
import hashlib
import json
import os
import random
import subprocess
import time
from collections import defaultdict
from pathlib import Path

from evaluate_codex_runs import (
    ROOT,
    answer_id,
    evidence_bundle,
    gated_score,
    read_jsonl,
    utc_now,
    write_comparison,
    write_summary,
)


MAX_ATTEMPTS = 4


def judge_prompt(instructions: str, question: dict, rubric: str, answers: list[dict]) -> str:
    question_record = {
        field: question[field]
        for field in ("id", "track", "category", "prompt", "expected_answer", "scoring_focus")
    }
    answer_lines = "\n".join(
        json.dumps(
            {
                "answer_id": answer["answer_id"],
                "question_id": question["id"],
                "answer": answer["answer"],
                "evidence_bundle": answer["evidence_bundle"],
            },
            separators=(",", ":"),
        )
        for answer in answers
    )
    return (
        f"{instructions}\n\n"
        "The workspace files are reproduced below instead of being mounted.\n\n"
        f"## question.json\n\n```json\n{json.dumps(question_record, indent=2)}\n```\n\n"
        f"## rubric.md\n\n{rubric}\n\n"
        f"## answers.jsonl\n\n```jsonl\n{answer_lines}\n```\n\n"
        f"Return exactly {len(answers)} evaluations, one per answer_id, as structured output."
    )


def claude_environment() -> dict:
    return {
        key: value
        for key, value in os.environ.items()
        if not key.startswith("CLAUDE_CODE_") and key not in {"CLAUDECODE", "CLAUDE_PID"}
    }


def judge_question(
    question: dict,
    answers: list[dict],
    instructions: str,
    rubric: str,
    schema_text: str,
    model: str,
    effort: str,
    checkpoint_dir: Path | None = None,
) -> list[dict]:
    expected = {answer["answer_id"] for answer in answers}
    checkpoint = checkpoint_dir / f"{question['id']}.json" if checkpoint_dir else None
    if checkpoint and checkpoint.exists():
        evaluations = json.loads(checkpoint.read_text(encoding="utf-8"))
        if {record["answer_id"] for record in evaluations} == expected:
            print(f"reusing checkpoint for {question['id']}", flush=True)
            return evaluations
    shuffled = list(answers)
    random.Random(f"{question['id']}:scop-homology-4").shuffle(shuffled)
    prompt = judge_prompt(instructions, question, rubric, shuffled)
    # The prompt goes through stdin: with fifteen answers and their evidence
    # bundles it can exceed Linux's 128 KiB single-argument limit.
    command = [
        "claude",
        "-p",
        "--model",
        model,
        "--effort",
        effort,
        "--output-format",
        "json",
        "--no-session-persistence",
        "--setting-sources",
        "",
        "--strict-mcp-config",
        "--disable-slash-commands",
        "--tools",
        "",
        "--json-schema",
        schema_text,
    ]
    last_error = None
    for attempt in range(1, MAX_ATTEMPTS + 1):
        result = subprocess.run(
            command,
            input=prompt,
            text=True,
            capture_output=True,
            env=claude_environment(),
            cwd=str(ROOT),
        )
        evaluations = None
        if result.returncode == 0:
            try:
                payload = json.loads(result.stdout)
                evaluations = (payload.get("structured_output") or {}).get("evaluations")
            except json.JSONDecodeError:
                evaluations = None
        if evaluations:
            returned = [record["answer_id"] for record in evaluations]
            if len(returned) == len(expected) and set(returned) == expected and all(
                record["question_id"] == question["id"] for record in evaluations
            ):
                if checkpoint:
                    checkpoint.write_text(json.dumps(evaluations), encoding="utf-8")
                return evaluations
            last_error = f"incomplete or mislabelled evaluations: {returned}"
        else:
            last_error = (result.stderr or result.stdout)[-800:]
        print(f"retrying evaluation {question['id']} after attempt {attempt}: {last_error[:200]}", flush=True)
        time.sleep(15 * attempt)
    raise RuntimeError(f"evaluation of {question['id']} failed after {MAX_ATTEMPTS} attempts: {last_error}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("run_dirs", nargs="+", type=Path)
    parser.add_argument("--model", default="claude-opus-5-5")
    parser.add_argument("--effort", choices=("low", "medium", "high", "xhigh", "max"), default="high")
    parser.add_argument("--jobs", type=int, default=4)
    parser.add_argument(
        "--checkpoint-dir",
        type=Path,
        help="keep each judged question here so an interrupted run can resume without re-judging",
    )
    parser.add_argument(
        "--allow-compatible-suites",
        action="store_true",
        help="also accept runs recorded under a suite listed in compatible_result_suite_versions",
    )
    args = parser.parse_args()
    if args.jobs < 1:
        parser.error("--jobs must be positive")
    if args.checkpoint_dir:
        args.checkpoint_dir.mkdir(parents=True, exist_ok=True)

    benchmark = json.loads((ROOT / "benchmark.json").read_text(encoding="utf-8"))
    allowed_suites = {benchmark["suite_version"]}
    if args.allow_compatible_suites:
        allowed_suites.update(benchmark.get("compatible_result_suite_versions", []))
    api_ids = set(benchmark["reporting_groups"]["api_conformance"]["question_ids"])
    questions_list = read_jsonl(ROOT / "questions.jsonl")
    questions = {question["id"]: question for question in questions_list}
    expected_ids = set(questions)
    manifests = {}
    answer_lookup = {}
    answers_by_question = defaultdict(list)
    for run_dir_arg in args.run_dirs:
        run_dir = run_dir_arg.resolve()
        manifest = json.loads((run_dir / "manifest.json").read_text(encoding="utf-8"))
        if manifest["suite_version"] not in allowed_suites:
            raise RuntimeError(f"suite mismatch in {run_dir}")
        answers = read_jsonl(run_dir / "answers.jsonl")
        if {answer["question_id"] for answer in answers} != expected_ids:
            raise RuntimeError(f"question set mismatch in {run_dir}")
        manifests[run_dir] = manifest
        for answer in answers:
            opaque_id = answer_id(manifest["run_id"], answer["question_id"])
            answer_lookup[opaque_id] = (run_dir, answer)
            answers_by_question[answer["question_id"]].append(
                {
                    "answer_id": opaque_id,
                    "question_id": answer["question_id"],
                    "answer": answer["answer"],
                    "evidence_bundle": evidence_bundle(run_dir, answer),
                }
            )

    instructions = (ROOT / "harness" / "evaluate-instructions.md").read_text(encoding="utf-8")
    rubric = (ROOT / "rubric.md").read_text(encoding="utf-8")
    schema = json.loads((ROOT / "schemas" / "harness-evaluation.schema.json").read_text(encoding="utf-8"))
    schema.pop("$schema", None)
    schema_text = json.dumps(schema, separators=(",", ":"))
    evaluated = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.jobs) as executor:
        futures = {
            executor.submit(
                judge_question,
                question,
                answers_by_question[question["id"]],
                instructions,
                rubric,
                schema_text,
                args.model,
                args.effort,
                args.checkpoint_dir,
            ): question["id"]
            for question in questions_list
        }
        for future in concurrent.futures.as_completed(futures):
            question_id = futures[future]
            for record in future.result():
                evaluated[record["answer_id"]] = record
            print(f"evaluated {question_id}", flush=True)

    timestamp = utc_now()
    evaluator_name = f"anthropic/{args.model}"
    records_by_run = defaultdict(list)
    for opaque_id, (run_dir, answer) in answer_lookup.items():
        judged = evaluated[opaque_id]
        biological_score = judged["biological_reasoning_score"]
        factual_score = judged["factual_support_score"]
        score = factual_score if biological_score is None else gated_score(biological_score, factual_score)
        records_by_run[run_dir].append(
            {
                "run_id": manifests[run_dir]["run_id"],
                "question_id": answer["question_id"],
                "condition": manifests[run_dir]["condition"],
                "model": manifests[run_dir]["model"]["name"],
                "biological_reasoning_score": biological_score,
                "factual_support_score": factual_score,
                "score": score,
                "error_tags": sorted(set(judged.get("error_tags", []))),
                "rubric_notes": judged["rubric_notes"],
                "evaluator": evaluator_name,
                "timestamp": timestamp,
            }
        )

    question_order = {question["id"]: index for index, question in enumerate(questions_list)}
    run_data = []
    for run_dir, manifest in manifests.items():
        evaluations = sorted(records_by_run[run_dir], key=lambda record: question_order[record["question_id"]])
        with (run_dir / "evaluation.jsonl").open("w", encoding="utf-8") as handle:
            for record in evaluations:
                handle.write(json.dumps(record, separators=(",", ":")) + "\n")
        manifest["evaluation"] = {
            "provider": "anthropic",
            "model": args.model,
            "snapshot": args.model,
            "parameters": {
                "effort": args.effort,
                "blinding": "opaque_labels_randomized_per_question",
                "harness": "claude-code-cli-no-tools",
            },
            "instructions_sha256": hashlib.sha256(instructions.encode("utf-8")).hexdigest(),
            "completed_at": timestamp,
        }
        (run_dir / "manifest.json").write_text(
            json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
        write_summary(run_dir, manifest, questions, api_ids, evaluations, evaluator_name)
        run_data.append((run_dir, manifest, evaluations))
        print(f"wrote evaluation: {run_dir}", flush=True)

    for comparison in write_comparison(run_data, api_ids, evaluator_name):
        print(f"wrote comparison: {comparison}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
