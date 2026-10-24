#!/usr/bin/env python3
"""Fetch a complete, hashed fixture set for the benchmark evidence paths."""

import argparse
import hashlib
import json
import re
import tempfile
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlsplit


ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = ROOT.parents[1]
DEFAULT_BASE_URL = "https://scop.berkeley.edu"


def sha256_bytes(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def read_questions(path: Path) -> list[dict]:
    questions = []
    with path.open(encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, 1):
            if not line.strip():
                continue
            try:
                questions.append(json.loads(line))
            except json.JSONDecodeError as error:
                raise ValueError(f"{path}:{line_number}: {error}") from error
    return questions


def fixture_name(path: str) -> str:
    parsed = urlsplit(path)
    label = parsed.path.removeprefix("/api/v1/").strip("/")
    if parsed.query:
        label += "_" + parsed.query
    label = re.sub(r"[^A-Za-z0-9._-]+", "_", label).strip("_")
    digest = hashlib.sha256(path.encode("utf-8")).hexdigest()[:10]
    return f"{label}__{digest}.json"


def fetch_json(url: str, timeout: float) -> tuple[bytes, dict]:
    request = urllib.request.Request(
        url,
        headers={"Accept": "application/json", "User-Agent": "scop-homology-benchmark/2"},
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            status = response.status
            headers = response.headers
            raw = response.read()
    except urllib.error.HTTPError as error:
        status = error.code
        headers = error.headers
        raw = error.read()

    try:
        parsed = json.loads(raw)
    except json.JSONDecodeError as error:
        raise RuntimeError(f"non-JSON response from {url}: HTTP {status}") from error

    normalized = (json.dumps(parsed, indent=2, sort_keys=True) + "\n").encode("utf-8")
    metadata = {
        "status": status,
        "content_type": headers.get_content_type(),
    }
    if headers.get("ETag"):
        metadata["etag"] = headers["ETag"]
    if headers.get("Last-Modified"):
        metadata["last_modified"] = headers["Last-Modified"]
    return normalized, metadata


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    parser.add_argument("--timeout", type=float, default=30.0)
    parser.add_argument("--questions", type=Path, default=ROOT / "questions.jsonl")
    parser.add_argument("--output", type=Path, default=ROOT / "fixtures" / "api")
    args = parser.parse_args()

    benchmark = json.loads((ROOT / "benchmark.json").read_text(encoding="utf-8"))
    question_bytes = args.questions.read_bytes()
    questions = read_questions(args.questions)
    paths = sorted({path for question in questions for path in question["api_evidence"]})
    if not paths:
        raise RuntimeError("no API evidence paths found")
    invalid = [path for path in paths if not path.startswith("/api/v1/releases/")]
    if invalid:
        raise RuntimeError(f"noncanonical API evidence paths: {invalid}")

    openapi_path = REPO_ROOT / "php" / "api" / "v1" / "openapi.yaml"
    fetched_at = datetime.now(timezone.utc).replace(microsecond=0).isoformat()
    entries = []

    args.output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="scop-fixtures-", dir=args.output) as tmp:
        stage = Path(tmp)
        for index, path in enumerate(paths, 1):
            url = args.base_url.rstrip("/") + path
            body, response = fetch_json(url, args.timeout)
            if not 200 <= response["status"] < 300:
                raise RuntimeError(f"fixture fetch failed: HTTP {response['status']} {url}")
            filename = fixture_name(path)
            (stage / filename).write_bytes(body)
            entries.append(
                {
                    "path": path,
                    "file": filename,
                    "status": response["status"],
                    "content_type": response["content_type"],
                    "sha256": sha256_bytes(body),
                    **({"etag": response["etag"]} if "etag" in response else {}),
                    **(
                        {"last_modified": response["last_modified"]}
                        if "last_modified" in response
                        else {}
                    ),
                }
            )
            print(f"[{index}/{len(paths)}] {path} -> {filename}")

        manifest = {
            "benchmark_id": benchmark["benchmark_id"],
            "suite_version": benchmark["suite_version"],
            "base_url": args.base_url.rstrip("/"),
            "fetched_at": fetched_at,
            "questions_sha256": sha256_bytes(question_bytes),
            "openapi_path": "../../php/api/v1/openapi.yaml",
            "openapi_sha256": sha256_bytes(openapi_path.read_bytes()),
            "entries": entries,
        }
        (stage / "manifest.json").write_text(
            json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )

        for old_file in args.output.glob("*.json"):
            old_file.unlink()
        for staged_file in stage.iterdir():
            staged_file.replace(args.output / staged_file.name)

    print(f"wrote {len(entries)} fixtures and manifest to {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
