#!/usr/bin/env python3
"""Forward live SCOPe API requests while retaining complete HTTP evidence."""

from __future__ import annotations

import hashlib
import json
import threading
import time
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen


API_PREFIX = "/api/v1"
TRANSIENT_STATUS = {429, 500, 502, 503, 504}
CAPTURE_FORMAT = "scop-live-http-v1"
REQUEST_HEADERS = ("Accept", "User-Agent")
RESPONSE_HEADERS = (
    "Content-Type",
    "ETag",
    "Last-Modified",
    "Retry-After",
    "X-Request-ID",
)


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def sha256_bytes(payload: bytes) -> str:
    return hashlib.sha256(payload).hexdigest()


def selected_headers(headers, names: tuple[str, ...]) -> dict[str, str]:
    result = {}
    for name in names:
        value = headers.get(name)
        if value is not None:
            result[name.lower()] = value
    return result


class LiveApiCapture:
    """A loopback-only gateway that forwards requests to a live API."""

    def __init__(self, upstream_base_url: str, label: str, timeout: float = 30.0):
        parsed = urlsplit(upstream_base_url)
        if parsed.scheme not in {"http", "https"} or not parsed.netloc:
            raise ValueError(f"invalid upstream API URL: {upstream_base_url!r}")
        if parsed.query or parsed.fragment:
            raise ValueError("upstream API URL must not contain a query or fragment")
        if not parsed.path.rstrip("/").endswith(API_PREFIX):
            raise ValueError(f"upstream API URL must end with {API_PREFIX}")
        if timeout <= 0:
            raise ValueError("timeout must be positive")
        self.upstream_base_url = upstream_base_url.rstrip("/")
        self.label = label
        self.timeout = timeout
        self._lock = threading.Lock()
        self._records: list[dict] = []
        self._bodies: dict[str, bytes] = {}
        self._sequence = 0
        self._server = None
        self._thread = None

    @property
    def base_url(self) -> str:
        if self._server is None:
            raise RuntimeError("capture gateway is not running")
        host, port = self._server.server_address
        return f"http://{host}:{port}{API_PREFIX}"

    def __enter__(self) -> LiveApiCapture:
        capture = self

        class Handler(BaseHTTPRequestHandler):
            protocol_version = "HTTP/1.1"

            def do_GET(self):
                capture._handle(self, "GET")

            def do_POST(self):
                capture._reject_method(self, "POST")

            def do_PUT(self):
                capture._reject_method(self, "PUT")

            def do_DELETE(self):
                capture._reject_method(self, "DELETE")

            def do_PATCH(self):
                capture._reject_method(self, "PATCH")

            def log_message(self, _format, *_args):
                return

        self._server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self._server.daemon_threads = True
        self._thread = threading.Thread(
            target=self._server.serve_forever,
            name=f"scop-api-capture-{self.label}",
            daemon=True,
        )
        self._thread.start()
        return self

    def __exit__(self, _exc_type, _exc_value, _traceback):
        if self._server is not None:
            self._server.shutdown()
            self._server.server_close()
        if self._thread is not None:
            self._thread.join(timeout=5)
        self._server = None
        self._thread = None

    def snapshot(self) -> dict:
        with self._lock:
            return {
                "format": CAPTURE_FORMAT,
                "label": self.label,
                "upstream_base_url": self.upstream_base_url,
                "records": [
                    dict(record)
                    for record in sorted(
                        self._records, key=lambda item: item["sequence"]
                    )
                ],
                "bodies": dict(self._bodies),
            }

    def _next_sequence(self) -> int:
        with self._lock:
            self._sequence += 1
            return self._sequence

    def _store(self, record: dict, body: bytes) -> None:
        digest = record["response"]["body_sha256"]
        with self._lock:
            self._records.append(record)
            self._bodies.setdefault(digest, body)

    def _upstream_url(self, path_query: str) -> str:
        parsed = urlsplit(path_query)
        relative_path = parsed.path.removeprefix(API_PREFIX)
        url = f"{self.upstream_base_url}{relative_path}"
        if parsed.query:
            url = f"{url}?{parsed.query}"
        return url

    def _handle(self, handler: BaseHTTPRequestHandler, method: str) -> None:
        started_at = utc_now()
        started = time.monotonic()
        sequence = self._next_sequence()
        request_headers = selected_headers(handler.headers, REQUEST_HEADERS)
        parsed = urlsplit(handler.path)
        error = None
        upstream_url = None
        response_headers = {}

        if not (
            parsed.path == API_PREFIX or parsed.path.startswith(f"{API_PREFIX}/")
        ):
            status = 404
            body = json.dumps(
                {"error": "capture gateway only exposes /api/v1"}
            ).encode("utf-8")
            response_headers = {"content-type": "application/json"}
            error = {
                "category": "gateway_path",
                "message": "request path is outside /api/v1",
            }
        else:
            upstream_url = self._upstream_url(handler.path)
            request = Request(
                upstream_url,
                method=method,
                headers={
                    "Accept": handler.headers.get("Accept", "application/json"),
                    "User-Agent": handler.headers.get(
                        "User-Agent", "scop-benchmark-live-capture"
                    ),
                },
            )
            try:
                with urlopen(request, timeout=self.timeout) as response:
                    status = response.status
                    body = response.read()
                    response_headers = selected_headers(
                        response.headers, RESPONSE_HEADERS
                    )
            except HTTPError as caught:
                status = caught.code
                body = caught.read()
                response_headers = selected_headers(caught.headers, RESPONSE_HEADERS)
            except (URLError, TimeoutError, OSError) as caught:
                status = 502
                error = {
                    "category": "upstream_transport",
                    "message": str(caught),
                }
                body = json.dumps(
                    {
                        "error": "upstream_transport",
                        "message": str(caught),
                    }
                ).encode("utf-8")
                response_headers = {"content-type": "application/json"}

        digest = sha256_bytes(body)
        completed_at = utc_now()
        record = {
            "format": CAPTURE_FORMAT,
            "sequence": sequence,
            "label": self.label,
            "started_at": started_at,
            "completed_at": completed_at,
            "duration_ms": round((time.monotonic() - started) * 1000, 3),
            "method": method,
            "request": {
                "path_query": handler.path,
                "upstream_url": upstream_url,
                "headers": request_headers,
            },
            "response": {
                "status": status,
                "headers": response_headers,
                "body_sha256": digest,
                "body_file": f"bodies/{digest}.body",
                "body_bytes": len(body),
            },
            "transient": status in TRANSIENT_STATUS or error is not None,
            "error": error,
        }
        self._store(record, body)
        handler.send_response(status)
        for name, value in response_headers.items():
            handler.send_header(name, value)
        handler.send_header("Content-Length", str(len(body)))
        handler.send_header("Connection", "close")
        handler.end_headers()
        try:
            handler.wfile.write(body)
        except BrokenPipeError:
            pass

    def _reject_method(self, handler: BaseHTTPRequestHandler, method: str) -> None:
        started_at = utc_now()
        started = time.monotonic()
        body = json.dumps(
            {"error": "SCOPe benchmark API access is read-only"}
        ).encode("utf-8")
        digest = sha256_bytes(body)
        record = {
            "format": CAPTURE_FORMAT,
            "sequence": self._next_sequence(),
            "label": self.label,
            "started_at": started_at,
            "completed_at": utc_now(),
            "duration_ms": round((time.monotonic() - started) * 1000, 3),
            "method": method,
            "request": {
                "path_query": handler.path,
                "upstream_url": None,
                "headers": selected_headers(handler.headers, REQUEST_HEADERS),
            },
            "response": {
                "status": 405,
                "headers": {"content-type": "application/json"},
                "body_sha256": digest,
                "body_file": f"bodies/{digest}.body",
                "body_bytes": len(body),
            },
            "transient": False,
            "error": {
                "category": "gateway_method",
                "message": "only GET requests are permitted",
            },
        }
        self._store(record, body)
        handler.send_response(405)
        handler.send_header("Content-Type", "application/json")
        handler.send_header("Content-Length", str(len(body)))
        handler.send_header("Connection", "close")
        handler.end_headers()
        try:
            handler.wfile.write(body)
        except BrokenPipeError:
            pass


def capture_summary(snapshot: dict) -> dict:
    records = snapshot["records"]
    return {
        "request_count": len(records),
        "successful_response_count": sum(
            1 for record in records if 200 <= record["response"]["status"] < 300
        ),
        "transient_error_count": sum(
            1 for record in records if record["transient"]
        ),
        "response_body_count": len(snapshot["bodies"]),
    }


def write_capture(snapshot: dict, destination: Path) -> dict:
    destination.mkdir(parents=True, exist_ok=False)
    bodies_dir = destination / "bodies"
    bodies_dir.mkdir()
    for digest, body in sorted(snapshot["bodies"].items()):
        if sha256_bytes(body) != digest:
            raise RuntimeError(f"captured body hash mismatch: {digest}")
        (bodies_dir / f"{digest}.body").write_bytes(body)
    with (destination / "trace.jsonl").open("w", encoding="utf-8") as handle:
        for record in snapshot["records"]:
            handle.write(json.dumps(record, separators=(",", ":")) + "\n")
    summary = {
        "format": snapshot["format"],
        "label": snapshot["label"],
        "upstream_base_url": snapshot["upstream_base_url"],
        **capture_summary(snapshot),
    }
    (destination / "summary.json").write_text(
        json.dumps(summary, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    return summary
