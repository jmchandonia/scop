#!/usr/bin/env python3
"""Generate neutral benchmark inputs from the private gold questions."""

import argparse
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read_jsonl(path: Path):
    with path.open(encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, 1):
            if line.strip():
                try:
                    yield json.loads(line)
                except json.JSONDecodeError as error:
                    raise ValueError(f"{path}:{line_number}: {error}") from error


def blind_record(question: dict) -> dict:
    return {
        "id": question["id"],
        "track": question["track"],
        "category": question["category"],
        "prompt": question["prompt"],
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--questions", type=Path, default=ROOT / "questions.jsonl")
    parser.add_argument(
        "--output", type=Path, default=ROOT / "inputs" / "questions_blind.jsonl"
    )
    args = parser.parse_args()

    records = [blind_record(question) for question in read_jsonl(args.questions)]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8") as handle:
        for record in records:
            handle.write(json.dumps(record, separators=(",", ":"), sort_keys=False))
            handle.write("\n")
    print(f"wrote {len(records)} blind questions to {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
