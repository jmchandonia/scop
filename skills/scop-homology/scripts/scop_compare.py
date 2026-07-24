#!/usr/bin/env python3
"""Compare SCOPe evidence without guessing when required records are missing."""

import argparse
import hashlib
import json
import re
import sys
import time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen


DEFAULT_BASE_URL = "https://scop.berkeley.edu/api/v1"
SCCS_RE = re.compile(r"^[a-z](?:\.[0-9]+){0,3}$")
PDB_RE = re.compile(r"^[0-9][A-Za-z0-9]{3}$")
HOMOLOGY_LEVELS = {"fa", "sf"}
RETRYABLE_STATUS = {429, 500, 502, 503, 504}


class JsonSource:
    """Read JSON from the live API or a fetch_fixtures.py-style manifest."""

    def __init__(self, base_url, timeout, retries, fixture_manifest=None):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self.retries = retries
        self.fixture_manifest = fixture_manifest
        self.fixture_manifest_sha256 = None
        self.fixture_dir = None
        self.fixtures = {}
        if fixture_manifest is not None:
            self._load_fixture_manifest(fixture_manifest)

    @property
    def description(self):
        if self.fixture_manifest is not None:
            return {
                "type": "fixture_manifest",
                "manifest": self.fixture_manifest.name,
                "manifest_sha256": self.fixture_manifest_sha256,
            }
        return {"type": "live_api", "base_url": self.base_url}

    def _load_fixture_manifest(self, path):
        try:
            payload = path.read_bytes()
            manifest = json.loads(payload)
        except (OSError, json.JSONDecodeError) as error:
            raise RuntimeError(f"cannot read fixture manifest {path}: {error}") from error
        self.fixture_manifest_sha256 = hashlib.sha256(payload).hexdigest()
        entries = manifest.get("entries")
        if not isinstance(entries, list):
            raise RuntimeError(f"fixture manifest {path} has no entries list")
        self.fixture_dir = path.resolve().parent
        for entry in entries:
            if not isinstance(entry, dict) or not isinstance(entry.get("path"), str):
                raise RuntimeError(f"fixture manifest {path} has an invalid entry")
            for request_path in self._fixture_path_aliases(entry["path"]):
                if request_path in self.fixtures:
                    raise RuntimeError(
                        f"fixture manifest {path} repeats request path {request_path}"
                    )
                self.fixtures[request_path] = entry

    @staticmethod
    def _fixture_path_aliases(path):
        aliases = {path}
        if path.startswith("/api/v1/"):
            aliases.add(path.removeprefix("/api/v1"))
        elif path == "/api/v1":
            aliases.add("/")
        return aliases

    def get(self, path):
        if not path.startswith("/"):
            raise ValueError(f"SCOPe request path must start with '/': {path!r}")
        if self.fixture_manifest is not None:
            return self._get_fixture(path)
        return self._get_live(path)

    def _get_fixture(self, path):
        entry = self.fixtures.get(path)
        if entry is None:
            raise RuntimeError(
                f"fixture manifest has no response for {path}; evidence unavailable"
            )
        status = entry.get("status")
        if status != 200:
            raise RuntimeError(f"fixture for {path} has HTTP status {status}")
        filename = entry.get("file")
        expected_hash = entry.get("sha256")
        if not isinstance(filename, str) or not isinstance(expected_hash, str):
            raise RuntimeError(f"fixture metadata is incomplete for {path}")
        fixture_path = (self.fixture_dir / filename).resolve()
        if self.fixture_dir not in fixture_path.parents:
            raise RuntimeError(f"fixture path escapes manifest directory: {filename}")
        try:
            payload = fixture_path.read_bytes()
        except OSError as error:
            raise RuntimeError(f"cannot read fixture for {path}: {error}") from error
        actual_hash = hashlib.sha256(payload).hexdigest()
        if actual_hash != expected_hash:
            raise RuntimeError(
                f"fixture hash mismatch for {path}: {actual_hash} != {expected_hash}"
            )
        return decode_json(payload, path)

    def _get_live(self, path):
        url = f"{self.base_url}{path}"
        request = Request(
            url,
            headers={
                "Accept": "application/json",
                "User-Agent": "scop-homology/scop_compare.py",
            },
        )
        for attempt in range(self.retries + 1):
            try:
                with urlopen(request, timeout=self.timeout) as response:
                    return decode_json(response.read(), path)
            except HTTPError as error:
                if error.code not in RETRYABLE_STATUS or attempt == self.retries:
                    raise RuntimeError(
                        f"SCOPe request failed for {path}: HTTP {error.code}"
                    ) from error
            except (URLError, TimeoutError, OSError) as error:
                if attempt == self.retries:
                    raise RuntimeError(
                        f"SCOPe request failed for {path}: {error}"
                    ) from error
            time.sleep(min(2**attempt, 4))
        raise AssertionError("unreachable")


def decode_json(payload, path):
    try:
        result = json.loads(payload)
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise RuntimeError(f"response for {path} is not valid JSON: {error}") from error
    if not isinstance(result, dict):
        raise RuntimeError(f"response for {path} is not a JSON object")
    return result


def identifier_path(release, identifier):
    encoded = quote(identifier, safe="._-")
    if identifier.isdigit():
        return f"/releases/{release}/sunids/{encoded}"
    if identifier.startswith("d") and len(identifier) >= 6:
        return f"/releases/{release}/sids/{encoded}"
    if SCCS_RE.fullmatch(identifier):
        return f"/releases/{release}/sccs/{encoded}"
    raise ValueError(
        f"cannot infer identifier type for {identifier!r}; use a SID, SUNID, or SCCS"
    )


def node_key(node):
    level = node.get("level")
    code = level.get("code") if isinstance(level, dict) else None
    return code, node.get("sunid")


def node_level(node):
    level = node.get("level")
    return level.get("code") if isinstance(level, dict) else None


def require_lineage(response, release, identifier):
    if str(response.get("release")) != release:
        raise RuntimeError(
            f"lineage for {identifier!r} reports release "
            f"{response.get('release')!r}, expected {release!r}"
        )
    parents = response.get("parents")
    if not isinstance(parents, list) or not parents:
        raise RuntimeError(f"lineage missing for {identifier!r} in release {release}")
    if any(not isinstance(node, dict) for node in parents):
        raise RuntimeError(f"lineage contains an invalid node for {identifier!r}")
    return parents


def fetch_domain(source, release, identifier, include_caveats):
    base = identifier_path(release, identifier)
    lineage = source.get(f"{base}/parents")
    record = {
        "identifier": identifier,
        "release": release,
        "parents": require_lineage(lineage, release, identifier),
    }
    if include_caveats:
        record["annotations"] = source.get(f"{base}/annotations")
        record["homology"] = source.get(f"{base}/homology")
    return record


def shared_nodes(records):
    common = {node_key(node) for node in records[0]["parents"]}
    for record in records[1:]:
        common.intersection_update(node_key(node) for node in record["parents"])
    return [node for node in records[0]["parents"] if node_key(node) in common]


def relationship_summary(shared):
    deepest = shared[-1] if shared else None
    deepest_homology = next(
        (node for node in reversed(shared) if node_level(node) in HOMOLOGY_LEVELS),
        None,
    )
    homology_level = node_level(deepest_homology) if deepest_homology else None
    if homology_level == "fa":
        classification = "shared_family"
    elif homology_level == "sf":
        classification = "shared_superfamily"
    elif deepest is not None and node_level(deepest) == "cf":
        classification = "shared_fold_only"
    elif deepest is not None and node_level(deepest) == "cl":
        classification = "shared_class_only"
    else:
        classification = "no_shared_classification"
    return {
        "classification": classification,
        "deepest_shared_node": deepest,
        "deepest_shared_homology_node": deepest_homology,
    }


def compare_domains(args, source):
    if len(args.identifiers) < 2:
        raise ValueError("domains requires at least two identifiers")
    records = [
        fetch_domain(source, args.release, identifier, args.include_caveats)
        for identifier in args.identifiers
    ]
    return {
        "source": source.description,
        "release": args.release,
        "domains": records,
        "relationship": relationship_summary(shared_nodes(records)),
    }


def compare_history(args, source):
    if len(args.releases) < 2:
        raise ValueError("history requires at least two releases")
    records = [
        fetch_domain(source, release, args.identifier, args.include_caveats)
        for release in args.releases
    ]
    levels = ("cl", "cf", "sf", "fa", "dm", "sp")
    classifications = []
    for record in records:
        by_level = {node_level(node): node for node in record["parents"]}
        classifications.append(
            {
                "release": record["release"],
                "levels": {level: by_level.get(level) for level in levels},
            }
        )
    changed_levels = []
    for level in levels:
        identities = {
            node_key(item["levels"][level])
            if item["levels"][level] is not None
            else None
            for item in classifications
        }
        if len(identities) > 1:
            changed_levels.append(level)
    result = {
        "source": source.description,
        "identifier": args.identifier,
        "releases": classifications,
        "changed_levels": changed_levels,
    }
    if args.include_caveats:
        result["records"] = records
    return result


def validate_pdb_code(code):
    if not PDB_RE.fullmatch(code):
        raise ValueError(f"invalid PDB code {code!r}")
    return code.lower()


def compare_quality(args, source):
    if len(args.pdb_codes) < 2:
        raise ValueError("quality requires at least two PDB codes")
    records = []
    for supplied_code in args.pdb_codes:
        code = validate_pdb_code(supplied_code)
        response = source.get(f"/releases/{args.release}/pdb/{code}/quality")
        if str(response.get("release")) != args.release:
            raise RuntimeError(
                f"quality response for {code} reports release "
                f"{response.get('release')!r}, expected {args.release!r}"
            )
        quality = response.get("quality")
        if not isinstance(quality, dict):
            raise RuntimeError(f"quality record missing for {code!r}")
        value = quality.get(args.measure)
        if not isinstance(value, (int, float)):
            raise RuntimeError(
                f"{args.measure} is missing or nonnumeric for {code!r}; no ranking made"
            )
        records.append(quality)
    ranked = sorted(
        records,
        key=lambda record: (-record[args.measure], record.get("pdb_code", "")),
    )
    best_value = ranked[0][args.measure]
    top = [
        record.get("pdb_code")
        for record in ranked
        if record[args.measure] == best_value
    ]
    return {
        "source": source.description,
        "release": args.release,
        "measure": args.measure,
        "records": records,
        "ranking": [record.get("pdb_code") for record in ranked],
        "top_by_measure": top,
        "preferred_by_measure": top[0] if len(top) == 1 else None,
    }


def build_parser():
    parser = argparse.ArgumentParser(
        description=(
            "Compare SCOPe lineages, release history, or structure quality. "
            "The command fails rather than filling in unavailable evidence."
        )
    )
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    parser.add_argument("--timeout", type=float, default=30.0)
    parser.add_argument("--retries", type=int, default=2)
    parser.add_argument(
        "--fixture-manifest",
        type=Path,
        help="read frozen JSON responses from this manifest instead of the network",
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    domains = subparsers.add_parser(
        "domains", help="compare two or more identifiers in one release"
    )
    domains.add_argument("release")
    domains.add_argument("identifiers", nargs="+")
    domains.add_argument(
        "--include-caveats",
        action="store_true",
        help="also require annotation and homology responses for every identifier",
    )
    domains.set_defaults(handler=compare_domains)

    history = subparsers.add_parser(
        "history", help="compare one identifier across two or more releases"
    )
    history.add_argument("identifier")
    history.add_argument("releases", nargs="+")
    history.add_argument(
        "--include-caveats",
        action="store_true",
        help="also require annotation and homology responses in every release",
    )
    history.set_defaults(handler=compare_history)

    quality = subparsers.add_parser(
        "quality", help="rank two or more PDB entries by a retrieved quality measure"
    )
    quality.add_argument("release")
    quality.add_argument("pdb_codes", nargs="+")
    quality.add_argument(
        "--measure", choices=("aerospaci", "spaci"), default="aerospaci"
    )
    quality.set_defaults(handler=compare_quality)
    return parser


def main():
    parser = build_parser()
    args = parser.parse_args()
    if args.timeout <= 0:
        parser.error("--timeout must be positive")
    if args.retries < 0:
        parser.error("--retries must be nonnegative")
    try:
        source = JsonSource(
            args.base_url,
            args.timeout,
            args.retries,
            args.fixture_manifest,
        )
        result = args.handler(args, source)
    except (RuntimeError, ValueError) as error:
        print(error, file=sys.stderr)
        return 2
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
