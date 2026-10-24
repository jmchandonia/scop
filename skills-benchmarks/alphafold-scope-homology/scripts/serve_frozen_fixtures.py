#!/usr/bin/env python3
"""Serve the frozen AlphaFold benchmark REST snapshot on loopback."""

from __future__ import annotations

import argparse
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
from urllib.parse import parse_qsl, urlsplit


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_FIXTURE_DIR = ROOT / "fixtures" / "api"
DEFAULT_BASE_PATH = "/api/v1/index.php"


def route_key(target: str) -> tuple[str, tuple[tuple[str, str], ...]]:
    """Match equivalent query strings without weakening path matching."""
    parsed = urlsplit(target)
    return parsed.path, tuple(sorted(parse_qsl(parsed.query, keep_blank_values=True)))


def load_fixture_routes(
    fixture_dir: Path,
) -> dict[tuple[str, tuple[tuple[str, str], ...]], tuple[int, str, bytes]]:
    """Load and verify every manifest-addressed response before binding."""
    fixture_dir = fixture_dir.resolve()
    manifest = json.loads((fixture_dir / "manifest.json").read_text(encoding="utf-8"))
    entries = manifest.get("entries")
    if not isinstance(entries, list) or not entries:
        raise RuntimeError("fixture manifest has no entries")

    routes = {}
    for entry in entries:
        filename = entry.get("file")
        path = entry.get("path")
        expected_sha256 = entry.get("sha256")
        if not all(isinstance(value, str) and value for value in (filename, path)):
            raise RuntimeError("fixture manifest entry has no file or path")
        fixture_path = (fixture_dir / filename).resolve()
        if fixture_path.parent != fixture_dir:
            raise RuntimeError(f"fixture path escapes its directory: {filename}")
        body = fixture_path.read_bytes()
        observed_sha256 = hashlib.sha256(body).hexdigest()
        if observed_sha256 != expected_sha256:
            raise RuntimeError(
                f"fixture digest mismatch for {filename}: "
                f"expected {expected_sha256}, observed {observed_sha256}"
            )
        key = route_key(path)
        if key in routes:
            raise RuntimeError(f"duplicate fixture route: {path}")
        routes[key] = (
            int(entry.get("status", 200)),
            str(entry.get("content_type", "application/json")),
            body,
        )
    return routes


def build_server(
    fixture_dir: Path,
    host: str,
    port: int,
    base_path: str = DEFAULT_BASE_PATH,
) -> ThreadingHTTPServer:
    routes = load_fixture_routes(fixture_dir)
    normalized_base = base_path.rstrip("/")

    class FixtureHandler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def do_GET(self) -> None:  # noqa: N802
            self._serve(include_body=True)

        def do_HEAD(self) -> None:  # noqa: N802
            self._serve(include_body=False)

        def _serve(self, *, include_body: bool) -> None:
            parsed = urlsplit(self.path)
            if parsed.path == normalized_base:
                fixture_path = "/"
            elif parsed.path.startswith(normalized_base + "/"):
                fixture_path = parsed.path[len(normalized_base) :]
            else:
                self._not_found(include_body)
                return
            key = route_key(fixture_path + (f"?{parsed.query}" if parsed.query else ""))
            response = routes.get(key)
            if response is None:
                self._not_found(include_body)
                return
            status, content_type, body = response
            self.send_response(status)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            if include_body:
                self.wfile.write(body)

        def _not_found(self, include_body: bool) -> None:
            body = b'{"status":404,"title":"Frozen fixture route not found"}\n'
            self.send_response(404)
            self.send_header("Content-Type", "application/problem+json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            if include_body:
                self.wfile.write(body)

        def log_message(self, format: str, *args: object) -> None:
            return

    server = ThreadingHTTPServer((host, port), FixtureHandler)
    server.daemon_threads = True
    return server


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--fixture-dir", type=Path, default=DEFAULT_FIXTURE_DIR)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8766)
    parser.add_argument("--base-path", default=DEFAULT_BASE_PATH)
    args = parser.parse_args()
    if not 0 <= args.port <= 65535:
        parser.error("port must be between 0 and 65535")
    if not args.base_path.startswith("/"):
        parser.error("base-path must be absolute")

    server = build_server(args.fixture_dir, args.host, args.port, args.base_path)
    address, port = server.server_address[:2]
    print(
        f"serving {len(load_fixture_routes(args.fixture_dir))} verified fixtures "
        f"at http://{address}:{port}{args.base_path}",
        flush=True,
    )
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
