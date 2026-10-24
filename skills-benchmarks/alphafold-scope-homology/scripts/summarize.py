#!/usr/bin/env python3
"""Regenerate a run summary from pinned records and harness metadata."""

from __future__ import annotations

import json
import sys
from pathlib import Path

from run_codex_mcp_benchmark import render_summary


def read_jsonl(path: Path) -> list[dict]:
    return [
        json.loads(line)
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]


def main() -> int:
    if len(sys.argv) != 2:
        raise SystemExit("usage: summarize.py RUN_DIRECTORY")
    run = Path(sys.argv[1])
    manifest = json.loads((run / "manifest.json").read_text(encoding="utf-8"))
    evaluations = read_jsonl(run / "evaluation.jsonl")
    harness = manifest.get("model", {}).get("harness", {})
    audits = harness.get("semantic_audit", {})
    performance = harness.get("performance", {})
    answer_performance = performance.get("answers", {})
    evaluation_performance = performance.get("evaluations", {})

    answer_results = {}
    for evaluation in evaluations:
        question_id = evaluation["question_id"]
        audit = audits.get(question_id, {})
        answer_results[question_id] = {
            "audit": {
                "successful_call_count": audit.get("successful_call_count", 0),
                "failed_call_count": audit.get("failed_call_count", 0),
            },
            "performance": answer_performance.get(question_id, {}),
        }
    evaluation_results = {
        question_id: {"performance": metrics}
        for question_id, metrics in evaluation_performance.items()
    }
    summary_path = run / "summary.md"
    summary_path.write_text(
        render_summary(evaluations, answer_results, evaluation_results),
        encoding="utf-8",
    )
    print(summary_path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
