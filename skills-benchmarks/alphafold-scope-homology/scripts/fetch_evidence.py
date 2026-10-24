#!/usr/bin/env python3
"""Fetch and hash every response file in the frozen evidence plan."""

import argparse
import concurrent.futures
import hashlib
import json
import sys
import re
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = ROOT.parents[1]
sys.path.insert(0, str(REPO_ROOT / "scripts"))
from scop_public_skills import public_skills_dir
DEFAULT_BASE = "http://127.0.0.1:8765/api/v1/index.php"


def digest(data):
    return hashlib.sha256(data).hexdigest()


def paths_from_plan(plan):
    release = plan["release"]
    limit = plan["hit_limit"]
    paths = []
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


def fixture_name(path):
    parsed = urlsplit(path)
    label = parsed.path.strip("/")
    if parsed.query:
        label += "_" + parsed.query
    label = re.sub(r"[^A-Za-z0-9._-]+", "_", label).strip("_")
    return f"{label}__{digest(path.encode())[:10]}.json"


def fetch(base, path, timeout):
    url = base.rstrip("/") + path
    request = urllib.request.Request(url, headers={"Accept": "application/json", "User-Agent": "alphafold-scope-homology-benchmark/1"})
    last = None
    for attempt in range(3):
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                raw = response.read()
                if response.status != 200:
                    raise RuntimeError(f"HTTP {response.status} {url}")
                parsed = json.loads(raw)
                body = (json.dumps(parsed, indent=2, sort_keys=True) + "\n").encode()
                return path, fixture_name(path), body, response.headers.get_content_type()
        except Exception as error:
            last = error
            time.sleep(attempt + 1)
    raise RuntimeError(f"failed {url}: {last}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default=DEFAULT_BASE)
    parser.add_argument("--timeout", type=float, default=60)
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument(
        "--refresh",
        action="store_true",
        help="refetch existing fixtures instead of only filling missing paths",
    )
    args = parser.parse_args()
    plan = json.loads((ROOT / "evidence-plan.json").read_text())
    paths = paths_from_plan(plan)
    expected_count = len(paths)
    output = ROOT / "fixtures" / "api"
    output.mkdir(parents=True, exist_ok=True)
    results = []
    pending = []
    for path in paths:
        filename = fixture_name(path)
        existing = output / filename
        if existing.is_file() and not args.refresh:
            json.loads(existing.read_text())
            body = existing.read_bytes()
            results.append((path, filename, body, "application/json"))
        else:
            pending.append(path)
    if results:
        print(f"resuming with {len(results)} existing fixtures")
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = [pool.submit(fetch, args.base_url, path, args.timeout) for path in pending]
        for index, future in enumerate(concurrent.futures.as_completed(futures), 1):
            result = future.result()
            results.append(result)
            (output / result[1]).write_bytes(result[2])
            print(f"[{len(results)}/{expected_count}] {result[0]}")
    entries = []
    for path, filename, body, content_type in sorted(results):
        (output / filename).write_bytes(body)
        entries.append({"path": path, "file": filename, "status": 200, "content_type": content_type, "sha256": digest(body)})
    expected = {entry["file"] for entry in entries} | {"manifest.json"}
    for old in output.glob("*.json"):
        if old.name not in expected:
            old.unlink()
    benchmark = json.loads((ROOT / "benchmark.json").read_text())
    manifest = {
        "benchmark_id": benchmark["benchmark_id"],
        "suite_version": benchmark["suite_version"],
        "base_url": args.base_url.rstrip("/"),
        "fetched_at": datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
        "evidence_plan_sha256": digest((ROOT / "evidence-plan.json").read_bytes()),
        "questions_sha256": digest((ROOT / "questions.jsonl").read_bytes()),
        "openapi_sha256": digest((REPO_ROOT / "php" / "api" / "v1" / "openapi.yaml").read_bytes()),
        "skill_sha256": digest((public_skills_dir() / "alphafold-scope-homology" / "SKILL.md").read_bytes()),
        "entries": entries,
    }
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n")
    print(f"wrote {expected_count} fixtures to {output}")


if __name__ == "__main__":
    main()
