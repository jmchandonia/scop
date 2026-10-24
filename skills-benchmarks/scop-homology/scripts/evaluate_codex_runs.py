#!/usr/bin/env python3
"""Blindly evaluate replicated benchmark runs one question at a time."""

import argparse
import concurrent.futures
import hashlib
import json
import os
import random
import shutil
import statistics
import subprocess
import tempfile
import time
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAX_ATTEMPTS = 12


def utc_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace(
        "+00:00", "Z"
    )


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
        "models_cache.json",
    ):
        metadata = source / name
        if metadata.is_file():
            shutil.copy2(metadata, destination / name)


def mean(records: list[dict], field: str) -> float:
    values = [record[field] for record in records if record[field] is not None]
    return statistics.mean(values)


def gated_score(biological_score: int, factual_score: int) -> float:
    factual_gate = (1, 2, 3, 4, 4)[factual_score]
    return min((3 * biological_score + factual_score) / 4, factual_gate)


def answer_id(run_id: str, question_id: str) -> str:
    digest = hashlib.sha256(f"{run_id}\0{question_id}".encode()).hexdigest()[:12]
    return f"A{digest}"


def evidence_bundle(run_dir: Path, answer: dict) -> list[dict]:
    bundle = []
    seen = set()
    for item in answer.get("evidence_used", []):
        if item.get("kind") != "api":
            continue
        reference = item.get("reference")
        digest = item.get("sha256")
        key = (reference, digest)
        if key in seen:
            continue
        seen.add(key)

        body_path = (
            run_dir
            / "api"
            / answer["question_id"]
            / "bodies"
            / f"{digest}.body"
        )
        try:
            body_text = body_path.read_text(encoding="utf-8")
        except OSError as error:
            raise RuntimeError(f"cannot read API evidence body: {body_path}") from error
        try:
            body = json.loads(body_text)
            body_format = "json"
        except json.JSONDecodeError:
            body = body_text
            body_format = "text"
        bundle.append(
            {
                "kind": "api",
                "reference": reference,
                "sha256": digest,
                "body_format": body_format,
                "body": body,
            }
        )
    return bundle


def error_tag_counts(records: list[dict]) -> Counter:
    return Counter(tag for record in records for tag in record.get("error_tags", []))


def append_error_tag_table(lines: list[str], counts: Counter) -> None:
    lines.extend(["", "## Error Tags", ""])
    if not counts:
        lines.append("No evaluator error tags.")
        return
    lines.extend(["| Error tag | Count |", "|---|---:|"])
    for tag, count in sorted(counts.items(), key=lambda item: (-item[1], item[0])):
        lines.append(f"| `{tag}` | {count} |")


def judge_question(
    question: dict,
    answers: list[dict],
    instructions: str,
    model: str,
    reasoning_effort: str,
) -> list[dict]:
    shuffled = list(answers)
    random.Random(f"{question['id']}:scop-homology-4").shuffle(shuffled)
    with tempfile.TemporaryDirectory(
        prefix=f"scop-evaluate-{question['id']}-"
    ) as tmp_name:
        tmp = Path(tmp_name)
        workspace = tmp / "workspace"
        workspace.mkdir()
        judge_question_record = {
            field: question[field]
            for field in (
                "id",
                "track",
                "category",
                "prompt",
                "expected_answer",
                "scoring_focus",
            )
        }
        (workspace / "question.json").write_text(
            json.dumps(judge_question_record, indent=2) + "\n",
            encoding="utf-8",
        )
        shutil.copy2(ROOT / "rubric.md", workspace / "rubric.md")
        with (workspace / "answers.jsonl").open("w", encoding="utf-8") as handle:
            for answer in shuffled:
                handle.write(
                    json.dumps(
                        {
                            "answer_id": answer["answer_id"],
                            "question_id": question["id"],
                            "answer": answer["answer"],
                            "evidence_bundle": answer["evidence_bundle"],
                        },
                        separators=(",", ":"),
                    )
                    + "\n"
                )

        codex_home = tmp / "codex-home"
        isolated_codex_home(codex_home)
        response_path = tmp / "evaluation.json"
        command = [
            "codex",
            "exec",
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
            "read-only",
            "--cd",
            str(workspace),
            "--skip-git-repo-check",
            "--output-schema",
            str(ROOT / "schemas" / "harness-evaluation.schema.json"),
            "--output-last-message",
            str(response_path),
            "-",
        ]
        environment = os.environ.copy()
        environment["CODEX_HOME"] = str(codex_home)
        result = None
        for attempt in range(1, MAX_ATTEMPTS + 1):
            result = subprocess.run(
                command,
                input=instructions,
                text=True,
                capture_output=True,
                env=environment,
            )
            if result.returncode == 0:
                break
            if attempt < MAX_ATTEMPTS:
                print(
                    f"retrying evaluation {question['id']} after "
                    f"exit {result.returncode}",
                    flush=True,
                )
                time.sleep(15 * attempt)
        if result is None or result.returncode:
            raise RuntimeError(
                f"evaluation of {question['id']} failed after {MAX_ATTEMPTS} "
                f"attempts "
                f"({result.returncode if result else 'no result'}):\n"
                f"{result.stdout if result else ''}\n"
                f"{result.stderr if result else ''}"
            )
        evaluations = json.loads(
            response_path.read_text(encoding="utf-8")
        )["evaluations"]

    expected = {answer["answer_id"] for answer in answers}
    returned = [record["answer_id"] for record in evaluations]
    if len(returned) != len(expected) or set(returned) != expected:
        raise RuntimeError(
            f"evaluator returned incomplete or duplicate labels for {question['id']}"
        )
    if any(record["question_id"] != question["id"] for record in evaluations):
        raise RuntimeError(f"evaluator changed question ID for {question['id']}")
    return evaluations


def write_summary(
    run_dir: Path,
    manifest: dict,
    questions: dict[str, dict],
    api_ids: set[str],
    evaluations: list[dict],
    evaluator_model: str,
) -> None:
    biological = [
        record for record in evaluations if record["question_id"] not in api_ids
    ]
    api_records = [
        record for record in evaluations if record["question_id"] in api_ids
    ]
    lines = [
        f"# {manifest['condition']} Benchmark Result",
        "",
        f"- Run: `{manifest['run_id']}`",
        f"- Suite: `{manifest['suite_version']}`",
        f"- Model: `{manifest['model']['name']}`",
        f"- Evaluator: `{evaluator_model}`",
        f"- Questions: {len(evaluations)}",
        "",
        "## Primary Biological Scores",
        "",
        "| Questions | Biological reasoning | Factual support | Secondary gated score |",
        "|---:|---:|---:|---:|",
        (
            f"| {len(biological)} | "
            f"{mean(biological, 'biological_reasoning_score'):.2f} | "
            f"{mean(biological, 'factual_support_score'):.2f} | "
            f"{mean(biological, 'score'):.2f} |"
        ),
        "",
        "Biological reasoning and factual support are the primary axes. The gated "
        "score is secondary and cannot hide weak factual support.",
        "",
        "## API Conformance",
        "",
        f"Factual-support mean: **{mean(api_records, 'factual_support_score'):.2f} / 4.00**.",
        "",
        "## Per-Question Scores",
        "",
        "| Question | Biological | Factual | Gated | Error tags |",
        "|---|---:|---:|---:|---|",
    ]
    for record in evaluations:
        biological_score = (
            "-"
            if record["biological_reasoning_score"] is None
            else str(record["biological_reasoning_score"])
        )
        tags = ", ".join(f"`{tag}`" for tag in record.get("error_tags", [])) or "-"
        lines.append(
            f"| `{record['question_id']}` | {biological_score} | "
            f"{record['factual_support_score']} | {record['score']} | {tags} |"
        )
    append_error_tag_table(lines, error_tag_counts(evaluations))
    (run_dir / "summary.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


CONDITION_ORDER = (
    "pretrained_only",
    "pretrained_api",
    "pretrained_skill",
    "pretrained_mcp",
    "pretrained_mcp_skill",
)


def write_comparison(
    run_data: list[tuple[Path, dict, list[dict]]],
    api_ids: set[str],
    model: str,
) -> list[Path]:
    """Write one comparison report per answer model covering its conditions."""
    by_model = defaultdict(list)
    for entry in run_data:
        by_model[entry[1]["model"]["name"]].append(entry)
    paths = []
    for answer_model, entries in sorted(by_model.items()):
        paths.append(write_model_comparison(entries, api_ids, model, answer_model))
    return paths


def write_model_comparison(
    run_data: list[tuple[Path, dict, list[dict]]],
    api_ids: set[str],
    model: str,
    answer_model: str,
) -> Path:
    by_condition = defaultdict(list)
    error_counts_by_condition = defaultdict(Counter)
    run_means = defaultdict(list)
    by_question = defaultdict(lambda: defaultdict(list))
    harnesses = set()
    for _run_dir, manifest, evaluations in run_data:
        condition = manifest["condition"]
        harnesses.add(manifest["harness"]["name"])
        error_counts_by_condition[condition].update(error_tag_counts(evaluations))
        biological = [
            record for record in evaluations if record["question_id"] not in api_ids
        ]
        by_condition[condition].extend(biological)
        run_means[condition].append(
            {
                field: mean(biological, field)
                for field in (
                    "biological_reasoning_score",
                    "factual_support_score",
                    "score",
                )
            }
        )
        for record in biological:
            by_question[record["question_id"]][condition].append(record)

    conditions = [
        condition for condition in CONDITION_ORDER if condition in by_condition
    ]
    replicate_counts = sorted({len(run_means[condition]) for condition in conditions})
    lines = [
        "# SCOPe Benchmark Comparison",
        "",
        f"- Suite: `{run_data[0][1]['suite_version']}`",
        f"- Answer model: `{answer_model}`",
        f"- Evaluator: `{model}`",
        f"- Replicates per condition: {', '.join(str(count) for count in replicate_counts)}",
        f"- Harness: {', '.join(sorted(harnesses))}; one fresh session per question",
        "",
        "## Aggregate Results",
        "",
        "| Condition | Biological reasoning | Factual support | Secondary gated | Run-level gated SD |",
        "|---|---:|---:|---:|---:|",
    ]
    for condition in conditions:
        gated_run_means = [record["score"] for record in run_means[condition]]
        sd = statistics.stdev(gated_run_means) if len(gated_run_means) > 1 else 0
        lines.append(
            f"| `{condition}` | "
            f"{mean(by_condition[condition], 'biological_reasoning_score'):.2f} | "
            f"{mean(by_condition[condition], 'factual_support_score'):.2f} | "
            f"{mean(by_condition[condition], 'score'):.2f} | {sd:.2f} |"
        )

    contrasts = (
        ("Skill Effect Over API", "pretrained_api", "pretrained_skill"),
        ("Skill Effect Over MCP", "pretrained_mcp", "pretrained_mcp_skill"),
        ("MCP Effect Over API", "pretrained_api", "pretrained_mcp"),
    )
    for heading, baseline, treatment in contrasts:
        if baseline not in by_condition or treatment not in by_condition:
            continue
        lines.extend(
            [
                "",
                f"## {heading}",
                "",
                "| Question | Biological delta | Factual delta | Gated delta |",
                "|---|---:|---:|---:|",
            ]
        )
        deltas = []
        for question_id, condition_records in by_question.items():
            if not all(
                condition in condition_records for condition in (baseline, treatment)
            ):
                continue
            deltas.append(
                {
                    "question_id": question_id,
                    "biological": (
                        mean(condition_records[treatment], "biological_reasoning_score")
                        - mean(condition_records[baseline], "biological_reasoning_score")
                    ),
                    "factual": (
                        mean(condition_records[treatment], "factual_support_score")
                        - mean(condition_records[baseline], "factual_support_score")
                    ),
                    "score": (
                        mean(condition_records[treatment], "score")
                        - mean(condition_records[baseline], "score")
                    ),
                }
            )
        for record in sorted(
            deltas, key=lambda item: (-abs(item["score"]), item["question_id"])
        ):
            lines.append(
                f"| `{record['question_id']}` | {record['biological']:+.2f} | "
                f"{record['factual']:+.2f} | {record['score']:+.2f} |"
            )

    lines.extend(["", "## Error Tags By Condition", ""])
    if any(error_counts_by_condition.values()):
        lines.extend(["| Condition | Error tag | Count |", "|---|---|---:|"])
        for condition in conditions:
            for tag, count in sorted(
                error_counts_by_condition[condition].items(),
                key=lambda item: (-item[1], item[0]),
            ):
                lines.append(f"| `{condition}` | `{tag}` | {count} |")
    else:
        lines.append("No evaluator error tags.")

    date = datetime.now(timezone.utc).date().isoformat()
    label = answer_model.replace("/", "-").replace(".", "-")
    suite = run_data[0][1]["suite_version"]
    path = ROOT / "results" / f"{date}_{label}_suite-{suite}_comparison.md"
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("run_dirs", nargs="+", type=Path)
    parser.add_argument("--model", default="gpt-5.6-sol")
    parser.add_argument(
        "--reasoning-effort", choices=("low", "medium", "high"), default="high"
    )
    parser.add_argument("--jobs", type=int, default=4)
    args = parser.parse_args()
    if args.jobs < 1:
        parser.error("--jobs must be positive")

    benchmark = json.loads((ROOT / "benchmark.json").read_text(encoding="utf-8"))
    api_ids = set(
        benchmark["reporting_groups"]["api_conformance"]["question_ids"]
    )
    questions_list = read_jsonl(ROOT / "questions.jsonl")
    questions = {question["id"]: question for question in questions_list}
    expected_ids = set(questions)
    manifests = {}
    answer_lookup = {}
    answers_by_question = defaultdict(list)
    for run_dir_arg in args.run_dirs:
        run_dir = run_dir_arg.resolve()
        manifest = json.loads(
            (run_dir / "manifest.json").read_text(encoding="utf-8")
        )
        if manifest["suite_version"] != benchmark["suite_version"]:
            raise RuntimeError(f"suite mismatch in {run_dir}")
        answers = read_jsonl(run_dir / "answers.jsonl")
        if {answer["question_id"] for answer in answers} != expected_ids:
            raise RuntimeError(f"question set mismatch in {run_dir}")
        manifests[run_dir] = manifest
        for answer in answers:
            opaque_id = answer_id(manifest["run_id"], answer["question_id"])
            entry = {
                "answer_id": opaque_id,
                "question_id": answer["question_id"],
                "answer": answer["answer"],
                "evidence_bundle": evidence_bundle(run_dir, answer),
            }
            answer_lookup[opaque_id] = (run_dir, answer)
            answers_by_question[answer["question_id"]].append(entry)

    instructions = (ROOT / "harness" / "evaluate-instructions.md").read_text(
        encoding="utf-8"
    )
    evaluated = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.jobs) as executor:
        futures = {
            executor.submit(
                judge_question,
                question,
                answers_by_question[question["id"]],
                instructions,
                args.model,
                args.reasoning_effort,
            ): question["id"]
            for question in questions_list
        }
        for future in concurrent.futures.as_completed(futures):
            question_id = futures[future]
            for record in future.result():
                evaluated[record["answer_id"]] = record
            print(f"evaluated {question_id}", flush=True)

    timestamp = utc_now()
    evaluator_name = f"openai/{args.model}"
    records_by_run = defaultdict(list)
    for opaque_id, (run_dir, answer) in answer_lookup.items():
        judged = evaluated[opaque_id]
        biological_score = judged["biological_reasoning_score"]
        factual_score = judged["factual_support_score"]
        score = (
            factual_score
            if biological_score is None
            else gated_score(biological_score, factual_score)
        )
        records_by_run[run_dir].append(
            {
                "run_id": manifests[run_dir]["run_id"],
                "question_id": answer["question_id"],
                "condition": manifests[run_dir]["condition"],
                "model": manifests[run_dir]["model"]["name"],
                "biological_reasoning_score": biological_score,
                "factual_support_score": factual_score,
                "score": score,
                "error_tags": sorted(judged["error_tags"]),
                "rubric_notes": judged["rubric_notes"],
                "evaluator": evaluator_name,
                "timestamp": timestamp,
            }
        )

    question_order = {
        question["id"]: index for index, question in enumerate(questions_list)
    }
    run_data = []
    for run_dir, manifest in manifests.items():
        evaluations = sorted(
            records_by_run[run_dir],
            key=lambda record: question_order[record["question_id"]],
        )
        with (run_dir / "evaluation.jsonl").open("w", encoding="utf-8") as handle:
            for record in evaluations:
                handle.write(json.dumps(record, separators=(",", ":")) + "\n")
        manifest["evaluation"] = {
            "provider": "openai",
            "model": args.model,
            "snapshot": args.model,
            "parameters": {
                "reasoning_effort": args.reasoning_effort,
                "blinding": "opaque_labels_randomized_per_question",
            },
            "instructions_sha256": hashlib.sha256(
                instructions.encode("utf-8")
            ).hexdigest(),
            "completed_at": timestamp,
        }
        (run_dir / "manifest.json").write_text(
            json.dumps(manifest, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        write_summary(
            run_dir,
            manifest,
            questions,
            api_ids,
            evaluations,
            evaluator_name,
        )
        run_data.append((run_dir, manifest, evaluations))
        print(f"wrote evaluation: {run_dir}", flush=True)

    for comparison in write_comparison(run_data, api_ids, evaluator_name):
        print(f"wrote comparison: {comparison}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
