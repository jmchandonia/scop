#!/usr/bin/env python3
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

questions = [json.loads(line) for line in (ROOT / "questions.jsonl").read_text().splitlines() if line.strip()]
fields = ("id", "category", "difficulty", "model", "release", "prompt")
output = ROOT / "inputs" / "questions_blind.jsonl"
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text("".join(json.dumps({key: row[key] for key in fields}, separators=(",", ":")) + "\n" for row in questions))
print(f"wrote {len(questions)} blind questions to {output}")
