#!/usr/bin/env python3
"""Check predeclared quantitative gates; artifact integrity uses validate.py."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import statistics


ROOT = Path(__file__).resolve().parents[1]
BASELINE_RUN = ROOT / "results" / "runs" / "2026-09-04_gpt-5-5_pretrained_mcp_skill_r5"


def assess_development_gates(
    answers: list[dict],
    evaluations: list[dict],
    expected_ids: set[str],
    baseline_median_input: float,
) -> dict:
    """Report quantitative gates without interpreting scientific prose."""
    answer_ids = [item["record"]["question_id"] for item in answers]
    evaluation_ids = [item["question_id"] for item in evaluations]
    complete = (
        len(answer_ids) == len(evaluation_ids) == len(expected_ids)
        and set(answer_ids) == set(evaluation_ids) == expected_ids
    )
    if not complete or not answers or baseline_median_input <= 0:
        return {
            "quantitative_development_gates_passed": False,
            "failed_gates": ["complete_run_and_baseline_required"],
        }
    scores = [item["score"] for item in evaluations]
    calls = [item["performance"]["mcp_call_count"] for item in answers]
    inputs = [item["performance"]["usage"]["input_tokens"] for item in answers]
    first_attempts = sum(item["performance"]["attempt_count"] == 1 for item in answers)
    metrics = {
        "question_count": len(expected_ids),
        "first_attempt_passes": first_attempts,
        "mean_scientific_score": statistics.mean(scores),
        "minimum_scientific_score": min(scores),
        "median_mcp_calls": statistics.median(calls),
        "maximum_mcp_calls": max(calls),
        "median_input_tokens": statistics.median(inputs),
        "baseline_median_input_tokens": baseline_median_input,
        "median_input_reduction_fraction": 1
        - statistics.median(inputs) / baseline_median_input,
    }
    gates = {
        "deterministic_audits_pass": all(
            item["audit"].get("issues") == [] for item in answers
        ),
        "first_attempt_passes_at_least_10_of_12": first_attempts >= 10
        and len(expected_ids) == 12,
        "mean_scientific_score_at_least_3": metrics["mean_scientific_score"] >= 3,
        # A low score can be waived only after separate scientific review; this
        # automatic check never decides that a low-scoring error is harmless.
        "no_score_below_2": metrics["minimum_scientific_score"] >= 2,
        "median_calls_at_most_5": metrics["median_mcp_calls"] <= 5,
        "maximum_calls_at_most_8": metrics["maximum_mcp_calls"] <= 8,
        "median_input_reduction_at_least_30_percent": metrics[
            "median_input_reduction_fraction"
        ]
        >= 0.30,
    }
    failed = [name for name, passed in gates.items() if not passed]
    return {
        "quantitative_development_gates_passed": not failed,
        "metrics": metrics,
        "gates": gates,
        "failed_gates": failed,
        "remaining_review": [
            "artifact and frozen-fixture integrity (validate.py)",
            "scientific interpretation and missing-fixture retry review",
            "independently reviewed one-shot holdout",
        ],
    }


def load_answers(run_dir: Path) -> list[dict]:
    return [
        json.loads(path.read_text())
        for path in sorted((run_dir / "checkpoints" / "answers").glob("*.json"))
    ]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run_dir", type=Path)
    parser.add_argument("--baseline-run", type=Path, default=BASELINE_RUN)
    args = parser.parse_args()
    answers = load_answers(args.run_dir)
    baseline = load_answers(args.baseline_run)
    if not baseline:
        parser.error("baseline has no accepted answer checkpoints")
    expected = {
        json.loads(line)["id"]
        for line in (ROOT / "inputs" / "questions_blind.jsonl").read_text().splitlines()
        if line.strip()
    }
    evaluations = [
        json.loads(line)
        for line in (args.run_dir / "evaluation.jsonl").read_text().splitlines()
        if line.strip()
    ]
    result = assess_development_gates(
        answers,
        evaluations,
        expected,
        statistics.median(
            item["performance"]["usage"]["input_tokens"] for item in baseline
        ),
    )
    result["run_id"] = args.run_dir.name
    result["baseline_run_id"] = args.baseline_run.name
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result["quantitative_development_gates_passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
