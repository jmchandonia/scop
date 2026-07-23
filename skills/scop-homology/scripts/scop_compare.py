#!/usr/bin/env python3
"""Compare SCOPe lineages or PDB quality records without guessing missing data."""

import argparse
import json
import re
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import urlopen


BASE = "https://scop.berkeley.edu/api/v1"
SCCS_RE = re.compile(r"^[a-z](?:\.[0-9]+){0,3}$")


def get_json(path):
    try:
        with urlopen(f"{BASE}{path}", timeout=30) as response:
            return json.load(response)
    except (HTTPError, URLError, TimeoutError) as error:
        raise RuntimeError(f"SCOPe request failed for {path}: {error}") from error


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


def compare_domains(args):
    records = []
    for identifier in (args.first, args.second):
        base = identifier_path(args.release, identifier)
        lineage = get_json(f"{base}/parents")
        annotations = get_json(f"{base}/annotations")
        homology = get_json(f"{base}/homology")
        parents = lineage.get("parents")
        if not isinstance(parents, list):
            raise RuntimeError(f"lineage missing for {identifier!r}")
        records.append(
            {
                "identifier": identifier,
                "release": lineage.get("release"),
                "parents": parents,
                "annotations": annotations,
                "homology": homology,
            }
        )
    if any(record["release"] != args.release for record in records):
        raise RuntimeError("a response did not match the requested release")

    second_nodes = {
        (node.get("level", {}).get("code"), node.get("sunid"))
        for node in records[1]["parents"]
    }
    shared = [
        node
        for node in records[0]["parents"]
        if (node.get("level", {}).get("code"), node.get("sunid")) in second_nodes
    ]
    homology_levels = {"fa", "sf"}
    deepest_homology = next(
        (
            node
            for node in reversed(shared)
            if node.get("level", {}).get("code") in homology_levels
        ),
        None,
    )
    return {
        "release": args.release,
        "domains": records,
        "deepest_shared_node": shared[-1] if shared else None,
        "deepest_shared_homology_node": deepest_homology,
    }


def compare_quality(args):
    records = []
    for code in (args.first, args.second):
        response = get_json(f"/releases/{args.release}/pdb/{code}/quality")
        quality = response.get("quality")
        if not isinstance(quality, dict):
            raise RuntimeError(f"quality record missing for {code!r}")
        if quality.get(args.measure) is None:
            raise RuntimeError(f"{args.measure} is missing for {code!r}; no ranking made")
        records.append(quality)
    first_value = records[0][args.measure]
    second_value = records[1][args.measure]
    preferred = None
    if first_value != second_value:
        preferred = records[0]["pdb_code"] if first_value > second_value else records[1]["pdb_code"]
    return {
        "release": args.release,
        "measure": args.measure,
        "records": records,
        "preferred_by_measure": preferred,
    }


def main():
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    domains = subparsers.add_parser("domains")
    domains.add_argument("release")
    domains.add_argument("first")
    domains.add_argument("second")
    domains.set_defaults(handler=compare_domains)

    quality = subparsers.add_parser("quality")
    quality.add_argument("release")
    quality.add_argument("first")
    quality.add_argument("second")
    quality.add_argument(
        "--measure", choices=("aerospaci", "spaci"), default="aerospaci"
    )
    quality.set_defaults(handler=compare_quality)

    args = parser.parse_args()
    try:
        result = args.handler(args)
    except (RuntimeError, ValueError) as error:
        print(error, file=sys.stderr)
        return 2
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
