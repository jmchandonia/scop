import hashlib
import importlib.util
import json
from pathlib import Path

import pytest


ROOT = Path(__file__).resolve().parents[1]
SERVER_PATH = ROOT / "scripts" / "serve_frozen_fixtures.py"
SPEC = importlib.util.spec_from_file_location("serve_frozen_fixtures", SERVER_PATH)
SERVER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(SERVER)


def _fixture_tree(tmp_path: Path) -> Path:
    body = b'{"release":"2.08"}\n'
    fixture = tmp_path / "release.json"
    fixture.write_bytes(body)
    manifest = {
        "entries": [
            {
                "content_type": "application/json",
                "file": fixture.name,
                "path": "/releases/current?second=2&first=1",
                "sha256": hashlib.sha256(body).hexdigest(),
                "status": 200,
            }
        ]
    }
    (tmp_path / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
    return tmp_path


def test_fixture_routes_verify_content_and_normalize_query_order(tmp_path):
    fixture_dir = _fixture_tree(tmp_path)

    routes = SERVER.load_fixture_routes(fixture_dir)

    assert SERVER.route_key("/releases/current?first=1&second=2") in routes
    assert next(iter(routes.values())) == (
        200,
        "application/json",
        b'{"release":"2.08"}\n',
    )


def test_fixture_routes_reject_digest_drift(tmp_path):
    fixture_dir = _fixture_tree(tmp_path)
    (fixture_dir / "release.json").write_text("{}\n", encoding="utf-8")

    with pytest.raises(RuntimeError, match="fixture digest mismatch"):
        SERVER.load_fixture_routes(fixture_dir)
