#!/usr/bin/env python3

import hashlib
import json
import sys
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen


SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPTS))

from evaluate_codex_runs import evidence_bundle
from live_api_capture import LiveApiCapture, capture_summary, write_capture
from run_codex_benchmark import (
    audit_event_log,
    canonical_api_reference,
    mcp_tool_calls,
    normalize_evidence,
    observe_current_release,
)


class FakeScopHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/api/v1/ok?limit=2":
            status = 200
            payload = {"ok": True, "path": self.path}
        elif self.path == "/api/v1/releases/current":
            status = 200
            payload = {
                "version": "9.99",
                "stable_label": "9.99-stable",
                "latest_periodic_update": {"version": "9.99-2099-01-01"},
            }
        elif self.path == "/api/v1/transient":
            status = 503
            payload = {"error": "temporary"}
        else:
            status = 404
            payload = {"error": "not found"}
        body = json.dumps(payload, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("X-Request-ID", f"fake-{status}")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, _format, *_args):
        return


class LiveApiCaptureTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.upstream = ThreadingHTTPServer(("127.0.0.1", 0), FakeScopHandler)
        cls.thread = threading.Thread(
            target=cls.upstream.serve_forever, daemon=True
        )
        cls.thread.start()
        host, port = cls.upstream.server_address
        cls.upstream_url = f"http://{host}:{port}/api/v1"

    @classmethod
    def tearDownClass(cls):
        cls.upstream.shutdown()
        cls.upstream.server_close()
        cls.thread.join(timeout=5)

    def test_forwards_and_captures_success_and_transient_response(self):
        with LiveApiCapture(self.upstream_url, "QTEST", timeout=2) as capture:
            request = Request(
                f"{capture.base_url}/ok?limit=2",
                headers={"Accept": "application/json", "User-Agent": "test-client"},
            )
            with urlopen(request, timeout=2) as response:
                success_body = response.read()
            with self.assertRaises(HTTPError) as caught:
                urlopen(f"{capture.base_url}/transient", timeout=2)
            self.assertEqual(caught.exception.code, 503)
            transient_body = caught.exception.read()
            snapshot = capture.snapshot()

        self.assertEqual(len(snapshot["records"]), 2)
        first, second = snapshot["records"]
        self.assertEqual(first["request"]["path_query"], "/api/v1/ok?limit=2")
        self.assertEqual(first["request"]["headers"]["user-agent"], "test-client")
        self.assertEqual(first["response"]["status"], 200)
        self.assertFalse(first["transient"])
        self.assertEqual(second["response"]["status"], 503)
        self.assertTrue(second["transient"])
        self.assertEqual(
            snapshot["bodies"][first["response"]["body_sha256"]], success_body
        )
        self.assertEqual(
            snapshot["bodies"][second["response"]["body_sha256"]], transient_body
        )
        self.assertEqual(
            capture_summary(snapshot),
            {
                "request_count": 2,
                "successful_response_count": 1,
                "transient_error_count": 1,
                "response_body_count": 2,
            },
        )

        with tempfile.TemporaryDirectory() as tmp_name:
            output = Path(tmp_name) / "capture"
            summary = write_capture(snapshot, output)
            self.assertEqual(summary["request_count"], 2)
            lines = (output / "trace.jsonl").read_text(
                encoding="utf-8"
            ).splitlines()
            self.assertEqual(len(lines), 2)
            for record in map(json.loads, lines):
                body_path = output / record["response"]["body_file"]
                body = body_path.read_bytes()
                self.assertEqual(
                    hashlib.sha256(body).hexdigest(),
                    record["response"]["body_sha256"],
                )

    def test_rejects_writes_without_contacting_upstream(self):
        with LiveApiCapture(self.upstream_url, "QWRITE", timeout=2) as capture:
            request = Request(
                f"{capture.base_url}/ok", data=b"{}", method="POST"
            )
            with self.assertRaises(HTTPError) as caught:
                urlopen(request, timeout=2)
            self.assertEqual(caught.exception.code, 405)
            caught.exception.read()
            snapshot = capture.snapshot()
        self.assertEqual(len(snapshot["records"]), 1)
        self.assertEqual(
            snapshot["records"][0]["error"]["category"], "gateway_method"
        )
        self.assertIsNone(
            snapshot["records"][0]["request"]["upstream_url"]
        )

    def test_release_observation_and_captured_evidence_normalization(self):
        observation, snapshot = observe_current_release(
            self.upstream_url, "release-test", timeout=2
        )
        self.assertEqual(observation["stable_release"], "9.99")
        self.assertEqual(
            observation["latest_periodic_update"], "9.99-2099-01-01"
        )
        record = snapshot["records"][0]
        evidence, rejected = normalize_evidence(
            "pretrained_api",
            ["/api/v1/releases/current"],
            Path("/not-used"),
            snapshot["records"],
            snapshot["bodies"],
        )
        self.assertEqual(rejected, [])
        self.assertEqual(
            evidence,
            [
                {
                    "kind": "api",
                    "reference": "/api/v1/releases/current",
                    "sha256": record["response"]["body_sha256"],
                }
            ],
        )
        self.assertEqual(
            normalize_evidence(
                "pretrained_api",
                ["/api/v1/releases/9.99/root"],
                Path("/not-used"),
                snapshot["records"],
                snapshot["bodies"],
            )[0],
            [],
        )

    def test_api_reference_and_direct_bypass_audit(self):
        self.assertEqual(
            canonical_api_reference(
                "http://127.0.0.1:1234/api/v1/releases/current"
            ),
            "/api/v1/releases/current",
        )
        event = json.dumps(
            {
                "item": {
                    "type": "command_execution",
                    "command": "curl https://scop.berkeley.edu/api/v1/releases/current",
                }
            }
        )
        with self.assertRaisesRegex(RuntimeError, "bypassed"):
            audit_event_log(
                "pretrained_api", event, "http://127.0.0.1:1234/api/v1"
            )

    def test_mcp_call_parsing_and_evidence_normalization(self):
        event_log = "\n".join(
            [
                json.dumps(
                    {
                        "type": "item.started",
                        "item": {
                            "id": "item_0",
                            "type": "mcp_tool_call",
                            "server": "scope",
                            "tool": "search_scop",
                            "status": "in_progress",
                        },
                    }
                ),
                json.dumps(
                    {
                        "type": "item.completed",
                        "item": {
                            "id": "item_0",
                            "type": "mcp_tool_call",
                            "server": "scope",
                            "tool": "search_scop",
                            "status": "completed",
                        },
                    }
                ),
            ]
        )
        self.assertEqual(mcp_tool_calls(event_log), ["mcp__scope__search_scop"])

        body = b'{"ok":true}'
        digest = hashlib.sha256(body).hexdigest()
        evidence, rejected = normalize_evidence(
            "pretrained_mcp",
            [
                "search_scop",
                "mcp__scope__search_scop",
                "/api/v1/ok",
                "unmatched free text",
            ],
            Path("/not-used"),
            [
                {
                    "request": {"path_query": "/api/v1/ok?limit=2"},
                    "response": {"status": 200, "body_sha256": digest},
                }
            ],
            {digest: body},
            {"mcp__scope__search_scop"},
        )
        self.assertEqual(
            evidence,
            [
                {"kind": "mcp", "reference": "mcp__scope__search_scop"},
                {
                    "kind": "api",
                    "reference": "/api/v1/ok?limit=2",
                    "sha256": digest,
                },
            ],
        )
        self.assertEqual(rejected, ["unmatched free text"])

    def test_mcp_audit_rejects_direct_network_shell_commands(self):
        event = json.dumps(
            {
                "item": {
                    "type": "command_execution",
                    "command": "curl http://127.0.0.1:1234/api/v1/releases/current",
                }
            }
        )
        with self.assertRaisesRegex(RuntimeError, "direct network access"):
            audit_event_log(
                "pretrained_mcp",
                event,
                "http://127.0.0.1:1234/api/v1",
                {"mcp__scope__get_scop_service_info"},
                True,
            )

    def test_evidence_bundle_inlines_cited_api_bodies(self):
        body = json.dumps({"release": "9.99", "ok": True}).encode("utf-8")
        digest = hashlib.sha256(body).hexdigest()
        with tempfile.TemporaryDirectory() as tmp_name:
            body_dir = Path(tmp_name) / "api" / "QTEST" / "bodies"
            body_dir.mkdir(parents=True)
            (body_dir / f"{digest}.body").write_bytes(body)
            answer = {
                "question_id": "QTEST",
                "evidence_used": [
                    {
                        "kind": "skill_reference",
                        "reference": "skills/scop-homology/SKILL.md",
                        "sha256": "0" * 64,
                    },
                    {
                        "kind": "api",
                        "reference": "/api/v1/releases/current",
                        "sha256": digest,
                    },
                    {
                        "kind": "api",
                        "reference": "/api/v1/releases/current",
                        "sha256": digest,
                    },
                ],
            }

            self.assertEqual(
                evidence_bundle(Path(tmp_name), answer),
                [
                    {
                        "kind": "api",
                        "reference": "/api/v1/releases/current",
                        "sha256": digest,
                        "body_format": "json",
                        "body": {"release": "9.99", "ok": True},
                    }
                ],
            )

    def test_evidence_bundle_inlines_cited_non_json_bodies_as_text(self):
        body = b"openapi: 3.1.0\ninfo:\n  title: SCOPe REST API\n"
        digest = hashlib.sha256(body).hexdigest()
        with tempfile.TemporaryDirectory() as tmp_name:
            body_dir = Path(tmp_name) / "api" / "QTEST" / "bodies"
            body_dir.mkdir(parents=True)
            (body_dir / f"{digest}.body").write_bytes(body)
            answer = {
                "question_id": "QTEST",
                "evidence_used": [
                    {
                        "kind": "api",
                        "reference": "/api/v1/openapi.yaml",
                        "sha256": digest,
                    },
                ],
            }

            self.assertEqual(
                evidence_bundle(Path(tmp_name), answer),
                [
                    {
                        "kind": "api",
                        "reference": "/api/v1/openapi.yaml",
                        "sha256": digest,
                        "body_format": "text",
                        "body": "openapi: 3.1.0\ninfo:\n  title: SCOPe REST API\n",
                    }
                ],
            )


if __name__ == "__main__":
    unittest.main()
