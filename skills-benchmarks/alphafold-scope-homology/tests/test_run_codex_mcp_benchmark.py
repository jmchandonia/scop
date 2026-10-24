import importlib.util
import hashlib
import json
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
REST_BASE = "https://fixture.test/api/v1/index.php"
RUNNER_PATH = ROOT / "scripts" / "run_codex_mcp_benchmark.py"
SPEC = importlib.util.spec_from_file_location("run_codex_mcp_benchmark", RUNNER_PATH)
RUNNER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(RUNNER)


def mcp_event(
    tool,
    arguments,
    status="completed",
    server="scop_local",
    source_urls=None,
    payload=None,
):
    source_urls = source_urls or [REST_BASE + "/test-evidence"]
    return json.dumps(
        {
            "type": "item.completed",
            "item": {
                "type": "mcp_tool_call",
                "server": server,
                "tool": tool,
                "arguments": arguments,
                "status": status,
                "error": None,
                "result": {
                    "structured_content": {
                        **(payload or {}),
                        "api_urls": source_urls
                    }
                },
            },
        }
    )


def valid_attempt_inputs():
    question = {"id": "AFQ000_test", "model": "AF-TEST-F1", "release": "2.08"}
    private_question = {
        "required_tools": [
            "get_alphafold_model",
            "get_alphafold_hits",
            "get_scop_fatcat_chain_benchmark",
        ]
    }
    gold_answer = {
        "evidence_paths": [
            "/releases/2.08/sunids/123/fatcat-chain-benchmark"
            "?minimum_supporting_hits=3"
        ]
    }
    response = {
        "answer": (
            RUNNER.CLAIM_DISCLOSURE
            + " The current evidence is consistent with a family-level hypothesis."
            + "\n\n### 3. Evidence considered\n\n"
            + f"- **E1** — Retrieved evidence supports the hypothesis. [source]({REST_BASE}/test-evidence)"
            + "\n\n### 4. Caveats by evidence item\n\n"
            + "- **E1** — This does not establish homology."
        ),
        "evidence_used": [
            "get_alphafold_model(model=AF-TEST-F1, release=2.08)",
            "get_alphafold_hits(model=AF-TEST-F1, release=2.08)",
            "get_scop_fatcat_chain_benchmarks(sunid=123, release=2.08)",
        ],
        "representative_sids": [],
        "claim_status": RUNNER.CLAIM_STATUS,
        "input_attestation": {
            "question_id": question["id"],
            "question_sha256": "a" * 64,
            "skill_sha256": "b" * 64,
            "model": question["model"],
            "release": question["release"],
        },
    }
    events = "\n".join(
        [
            mcp_event(
                "get_alphafold_model",
                {"model": question["model"], "release": question["release"]},
            ),
            mcp_event(
                "get_alphafold_hits",
                {"model": question["model"], "release": question["release"]},
            ),
            mcp_event(
                "get_scop_fatcat_chain_benchmarks",
                {
                    "requests": [
                        {"sunid": 123, "minimum_supporting_hits": 3}
                    ],
                    "release": question["release"],
                },
            ),
        ]
    )
    return question, private_question, gold_answer, response, events


def audit(question, private_question, gold_answer, response, events):
    return RUNNER.audit_answer_attempt(
        question=question,
        private_question=private_question,
        gold_answer=gold_answer,
        response=response,
        event_log=events,
        question_sha256="a" * 64,
        skill_sha256="b" * 64,
        rest_base_url=REST_BASE,
    )


def test_semantic_audit_accepts_attested_non_definitive_targeted_answer():
    inputs = valid_attempt_inputs()
    result = audit(*inputs)
    assert result["issues"] == []
    assert result["successful_call_count"] == 3
    assert result["failed_call_count"] == 0


def test_generator_schema_defers_sid_uniqueness_to_semantic_audit():
    assert "uniqueItems" not in RUNNER.ANSWER_OUTPUT_SCHEMA[
        "properties"
    ]["representative_sids"]

    question, private_question, gold_answer, response, events = valid_attempt_inputs()
    response["representative_sids"] = ["d1tesa_", "d1tesa_"]
    result = audit(question, private_question, gold_answer, response, events)
    assert "representative_sids must not contain duplicates" in result["issues"]


def test_failed_tool_call_does_not_satisfy_required_tool_audit():
    question, private_question, gold_answer, response, events = valid_attempt_inputs()
    marker = '"tool": "get_alphafold_hits"'
    before, after = events.split(marker, 1)
    after = after.replace('"status": "completed"', '"status": "failed"', 1)
    events = before + marker + after
    result = audit(question, private_question, gold_answer, response, events)
    assert "get_alphafold_hits" in result["missing_tools"]
    assert result["failed_call_count"] == 1


def test_semantic_audit_rejects_off_target_calls_and_wrong_benchmark_threshold():
    question, private_question, gold_answer, response, events = valid_attempt_inputs()
    events += "\n" + mcp_event(
        "get_alphafold_hits", {"model": "AF-WRONG-F1", "release": "2.08"}
    )
    events += "\n" + mcp_event(
        "get_scop_fatcat_chain_benchmark",
        {"sunid": 123, "minimum_supporting_hits": 2, "release": "2.08"},
    )
    result = audit(question, private_question, gold_answer, response, events)
    assert result["off_target_calls"]
    assert any("wrong threshold" in issue for issue in result["issues"])


def test_semantic_audit_accepts_reported_node_at_publicly_prescribed_threshold():
    question, private_question, gold_answer, response, events = valid_attempt_inputs()
    question["prompt"] = "Use minimum_supporting_hits=3 for the resulting node."
    events = events.replace('"sunid": 123', '"sunid": 999')

    result = audit(question, private_question, gold_answer, response, events)

    assert result["issues"] == []
    assert result["required_benchmark_counts"] == [("2.08", 3, 1)]
    assert result["observed_benchmarks"] == [("2.08", 999, 3)]


def test_semantic_audit_enforces_sunid_only_when_blind_question_names_it():
    question, private_question, gold_answer, response, events = valid_attempt_inputs()
    question["prompt"] = "Use SUNID 123 at minimum_supporting_hits=3."
    events = events.replace('"sunid": 123', '"sunid": 999')

    result = audit(question, private_question, gold_answer, response, events)

    assert result["missing_benchmarks"] == [("2.08", 123, 3)]
    assert any("explicitly named benchmark" in issue for issue in result["issues"])


def test_semantic_audit_rejects_benchmark_call_without_prescribed_threshold():
    question, private_question, gold_answer, response, events = valid_attempt_inputs()
    question["prompt"] = "Assess the conflicting FATCAT evidence."
    private_question["required_tools"].remove(
        "get_scop_fatcat_chain_benchmark"
    )
    gold_answer["evidence_paths"] = []

    result = audit(question, private_question, gold_answer, response, events)

    assert any(
        "without a prespecified calibration stratum" in issue
        for issue in result["issues"]
    )


def test_semantic_audit_rejects_definitive_target_claims_and_web_search():
    question, private_question, gold_answer, response, events = valid_attempt_inputs()
    response["answer"] = RUNNER.CLAIM_DISCLOSURE + " The model is homologous."
    events += "\n" + json.dumps(
        {"type": "item.completed", "item": {"type": "web_search"}}
    )
    result = audit(question, private_question, gold_answer, response, events)
    assert result["definitive_claim_matches"] == ["is homologous"]
    assert result["forbidden_events"] == ["web_search"]


def test_definitive_claim_linter_catches_additional_verdict_forms():
    forbidden = [
        "Decision: this target is globin-like.",
        "The target supports SCOPe superfamily homology.",
        "Assign only the supported intervals to SCOPe families.",
        "Final call: this is the Globins family.",
        "SCOPe homology level = superfamily.",
        "Answer yes for a positive, region-specific homology call.",
        "The region is in the Globins family.",
    ]
    for answer in forbidden:
        assert RUNNER.definitive_claim_matches(answer), answer

    assert not RUNNER.definitive_claim_matches(
        "Decision: the retrieved evidence supports the tested proposition."
    )


@pytest.mark.parametrize("answer", [
    "Conclusion:",
    "Conclusion: The retrieved evidence favors a Globins-family hypothesis.",
    "Final answer: The relationship remains unresolved.",
    "Final call: The retrieved evidence favors a candidate relationship.",
    "pLDDT supports local geometry but does not establish homology or exact boundaries.",
    "pLDDT doesn't independently establish homology.",
    "pLDDT doesn’t establish homology.",
    "High pLDDT cannot prove homology.",
    "The result is a qualified readout rather than a definitive classification.",
    "This is not a definitive classification.",
    "This is **not** a **definitive classification**.",
    "The family is a provisional hypothesis, not a final classification.",
    "These results do not independently establish homology.",
    "This is the controlling reason the result is superfamily-level rather than family.",
    "This is the controlling reason for reporting the evidence at superfamily rather than family level.",
    "This is a material competing family signal inside the same superfamily.",
    "This is the controlling reason the answer favors superfamily rather than family or only fold.",
    "The kinase region is the strongest family-level hypothesis because sequence and structure converge.",
    "The evidence supports a strong chance of a kinase-superfamily relationship for approximately residues 667-1035.",
    "This is the main family-discriminating evidence.",
    "This is the strongest N-terminal family-candidate evidence.",
])
def test_claim_checker_accepts_headings_and_local_qualifications(answer):
    assert RUNNER.definitive_claim_matches(answer) == []


@pytest.mark.parametrize("answer", [
    "The target is homologous to this family.",
    "The target is not homologous to this family.",
    "The target does not have homology to this family.",
    "pLDDT establishes homology.",
    "pLDDT not only establishes homology but proves it.",
    "This is a definitive classification.",
    "The target belongs to the Globins family.",
    "The model is assigned to the Globins family.",
    "This is the Globins family.",
    "The target is not merely homologous: its function is identical.",
    "pLDDT does not establish homology; the target is homologous.",
    "This is not a definitive classification. The target belongs to the Globins family.",
    "This is not a definitive classification, but the target is homologous.",
    "No claim is made here. Homology is established by pLDDT.",
    "The target is in the RRM superfamily rather than the Canonical RBD family.",
    "This is the RRM superfamily rather than the Canonical RBD family.",
    "The kinase region is the Protein kinases family because sequence and structure converge.",
    "The target is in the Globins family, while an alternative is a fold-level hypothesis.",
    "The target is in the Globins family and an alternative is a fold-level hypothesis.",
])
def test_claim_checker_still_rejects_actual_verdicts_and_does_not_leak_negation(answer):
    assert RUNNER.definitive_claim_matches(answer)


def test_recorded_layout_attempts_have_no_claim_checker_false_positives():
    logs = ROOT / "results/runs/2026-09-07_gpt-5-5_pretrained_mcp_skill_r7_layout/logs"
    attempts = 0
    rejected = 0
    for path in sorted(logs.glob("*.answer.a*.events.jsonl")):
        audit_path = path.with_name(path.name.replace(".events.jsonl", ".audit.json"))
        audit_record = json.loads(audit_path.read_text())
        for line in path.read_text().splitlines():
            event = json.loads(line)
            item = event.get("item", {})
            if event.get("type") == "item.completed" and item.get("type") == "agent_message":
                response = json.loads(item["text"])
                assert not RUNNER.definitive_claim_matches(response["answer"]), path.name
                attempts += 1
                rejected += bool(audit_record.get("definitive_claim_matches"))
    assert attempts == 17
    assert rejected == 5


def citation_calls(status="completed", server="scop_local"):
    event = mcp_event(
        "get_alphafold_hits",
        {"model": "AF-P69905-F1", "release": "2.08"},
        status=status,
        server=server,
        source_urls=[
            REST_BASE + "/test-evidence",
            REST_BASE + "/releases/2.08/alphafolds/AF-P69905-F1/hits?limit=100",
        ],
        payload={
            "release": "2.08",
            "sequence_hits": [{
                "kind": "sequence",
                "domain": {"sid": "d1bz1a_", "sunid": 15241, "sccs": "a.1.1.2"},
                "alphafold_range": {"start": 1, "end": 142},
            }],
            # An annotation-only SID must not license a hit-page citation.
            "comment": "automated match to d2dc3a_",
        },
    )
    return RUNNER.mcp_call_records(event)


def evidence_answer(items, definitions=""):
    return (
        "### 3. Evidence considered\n\n" + items
        + "\n\n### 4. Caveats by evidence item\n\n"
        + "E1. Local evidence does not describe the entire model.\n\n"
        + definitions
    )


def test_citation_audit_accepts_retrieved_hit_node_and_shared_api_references():
    hit_url = RUNNER.HIT_PAGE_BASE + "?hit=d1bz1a_&amp;mode=seq&af=AF-P69905-F1"
    answer = evidence_answer(
        f"- **E1** — Full-length sequence support. [hit]({hit_url})\n"
        "- **E2** — Retrieved lineage. <https://scop.berkeley.edu/?ver=2.08&sunid=15241>\n"
        "- **E3** — Model observation. [source][DATA]\n"
        "- **E4** — Another observation. [data][]\n"
        "- **E5** — Coverage context. [data]",
        f"[data]: {REST_BASE}/test-evidence",
    )
    result = RUNNER.audit_answer_citations(answer, citation_calls())
    assert result["issues"] == []
    assert len(result["items"]) == 5


@pytest.mark.parametrize("url", [
    "https://scop.berkeley.edu/sunid=15241&ver=2.08",
    "http://scop.berkeley.edu/sunid=15241&ver=2.08",
    "https://scop.berkeley.edu/sunid=15241&amp;ver=2.08",
    "https://scop.berkeley.edu?ver=2.08&sunid=15241",
    "https://scop.berkeley.edu/sid=d1bz1a_&ver=2.08",
    "https://scop.berkeley.edu/sccs=a.1.1.2&ver=2.08",
])
def test_citation_audit_accepts_documented_scope_node_url_formats(url):
    result = RUNNER.audit_answer_citations(
        evidence_answer(f"E1. Retrieved classification. [node]({url})"), citation_calls()
    )
    assert result["issues"] == []


def test_citation_audit_accepts_release_scoped_rest_url_for_returned_sunid():
    result = RUNNER.audit_answer_citations(
        evidence_answer(
            "E1. Retrieved classification. "
            f"[node]({REST_BASE}/releases/2.08/sunids/15241)"
        ),
        citation_calls(),
    )
    assert result["issues"] == []


@pytest.mark.parametrize("item", [
    "E1. Local confidence is high [MCP2].",
    f"E1. A URL in code is not a citation: `{REST_BASE}/test-evidence`.",
    f"E1. No inline source.\n```text\n{REST_BASE}/test-evidence\n```",
    "E1. No source. [label](URL)",
    "E1. Undefined reference [source][missing].",
    "E1. Not an HTTP source. [source](scop://release/2.08/sid/d1bz1a_)",
])
def test_citation_audit_rejects_missing_nonclickable_or_unresolved_links(item):
    result = RUNNER.audit_answer_citations(evidence_answer(item), citation_calls())
    assert "citation audit: E1 lacks a clickable source URL" in result["issues"]


def test_citation_audit_checks_each_item_not_just_a_global_source_list():
    answer = evidence_answer(
        f"E1. Sequence evidence. [source]({REST_BASE}/test-evidence)\n"
        "E2. Confidence evidence [MCP2].",
        f"[unused]: {REST_BASE}/test-evidence",
    )
    result = RUNNER.audit_answer_citations(answer, citation_calls())
    assert result["issues"] == ["citation audit: E2 lacks a clickable source URL"]


@pytest.mark.parametrize("url", [
    "https://unretrieved.test/paper",
    "https://scop.berkeley.edu/?sunid=999999&ver=2.08",
    "https://scop.berkeley.edu/?sunid=15241&ver=2.07",
    "https://scop.berkeley.edu/sunid=15241&ver=2.07",
    "https://scop.berkeley.edu/sunid=15241",
    "https://scop.berkeley.edu/sunid=15241&ver=2.08&ver=2.07",
    "https://scop.berkeley.edu/sid=d2dc3a_&ver=2.08",
    "https://scop.berkeley.edu/sccs=b.1.1.1&ver=2.08",
    "https://unretrieved.test/sunid=15241&ver=2.08",
    RUNNER.HIT_PAGE_BASE + "?af=AF-P69905-F1&mode=struct&hit=d1bz1a_",
    RUNNER.HIT_PAGE_BASE + "?af=AF-WRONG-F1&mode=seq&hit=d1bz1a_",
    RUNNER.HIT_PAGE_BASE + "?af=AF-P69905-F1&mode=seq&hit=d2dc3a_",
    REST_BASE + "/unreturned-evidence",
    REST_BASE + "/releases/2.08/sunids/999999",
    REST_BASE + "/releases/2.07/sunids/15241",
    "https://broken:host/source",
])
def test_citation_audit_rejects_sources_not_established_by_returned_evidence(url):
    result = RUNNER.audit_answer_citations(
        evidence_answer(f"E1. Observation. [source]({url})"), citation_calls()
    )
    assert any("E1 cites unreturned sources" in issue for issue in result["issues"])


@pytest.mark.parametrize("status,server", [("failed", "scop_local"), ("completed", "foreign")])
def test_failed_or_foreign_calls_do_not_authorize_citations(status, server):
    result = RUNNER.audit_answer_citations(
        evidence_answer(f"E1. Observation. [source]({REST_BASE}/test-evidence)"),
        citation_calls(status, server),
    )
    assert any("unreturned sources" in issue for issue in result["issues"])


def test_missing_citations_fail_the_full_answer_audit():
    question, private_question, gold_answer, response, events = valid_attempt_inputs()
    response["answer"] = response["answer"].replace(
        f"[source]({REST_BASE}/test-evidence)", "[MCP1]"
    )
    result = audit(question, private_question, gold_answer, response, events)
    assert result["citation_audit"]["issues"]
    assert "citation audit: E1 lacks a clickable source URL" in result["issues"]


def test_recorded_layout_answers_expose_missing_citations_without_changing_history():
    run = ROOT / "results/runs/2026-09-07_gpt-5-5_pretrained_mcp_skill_r7_layout"
    answers = [json.loads(line) for line in (run / "answers.jsonl").read_text().splitlines()]
    for answer in answers:
        question_id = answer["question_id"]
        attempt = json.loads((run / f"checkpoints/answers/{question_id}.json").read_text())[
            "performance"
        ]["attempt_count"]
        events = (run / f"logs/{question_id}.answer.a{attempt}.events.jsonl").read_text()
        result = RUNNER.audit_answer_citations(answer["answer"], RUNNER.mcp_call_records(events))
        assert result["items"], question_id
        assert any("lacks a clickable source URL" in issue for issue in result["issues"]), question_id


@pytest.mark.parametrize("question_id", ["AFQ001", "AFQ005", "AFQ009"])
def test_fresh_generation_sources_pass_after_scope_url_normalization(question_id):
    path = ROOT / "results/diagnostics/2026-09-08_checker-citations/logs"
    events = (path / f"{question_id}.answer.a1.events.jsonl").read_text()
    responses = [
        json.loads(event["item"]["text"])
        for line in events.splitlines()
        if (event := json.loads(line)).get("type") == "item.completed"
        and event.get("item", {}).get("type") == "agent_message"
    ]
    assert len(responses) == 1
    answer = responses[0]["answer"]
    assert not RUNNER.definitive_claim_matches(answer)
    result = RUNNER.audit_answer_citations(answer, RUNNER.mcp_call_records(events))
    assert result["items"]
    assert result["issues"] == []


def test_semantic_audit_rejects_foreign_mcp_server():
    question, private_question, gold_answer, response, events = valid_attempt_inputs()
    events += "\n" + mcp_event("list_mcp_resources", {}, server="codex")
    result = audit(question, private_question, gold_answer, response, events)
    assert result["foreign_mcp_calls"] == [
        {"server": "codex", "tool": "list_mcp_resources", "status": "completed"}
    ]


def test_semantic_audit_rejects_mcp_evidence_from_another_rest_base():
    question, private_question, gold_answer, response, events = valid_attempt_inputs()
    events = events.replace(
        REST_BASE + "/test-evidence",
        "https://different.test/api/v1/index.php/test-evidence",
        1,
    )
    result = audit(question, private_question, gold_answer, response, events)
    assert result["wrong_source_urls"] == [
        "https://different.test/api/v1/index.php/test-evidence"
    ]


def test_semantic_audit_rejects_unfrozen_same_base_evidence():
    question, private_question, gold_answer, response, events = valid_attempt_inputs()
    result = RUNNER.audit_answer_attempt(
        question=question,
        private_question=private_question,
        gold_answer=gold_answer,
        response=response,
        event_log=events,
        question_sha256="a" * 64,
        skill_sha256="b" * 64,
        rest_base_url=REST_BASE,
        allowed_source_urls={REST_BASE + "/different-frozen-path"},
    )
    assert result["unfrozen_source_urls"] == [REST_BASE + "/test-evidence"]


def test_semantic_audit_rejects_missing_mcp_source_attestation():
    question, private_question, gold_answer, response, events = valid_attempt_inputs()
    events = events.replace(
        json.dumps({"api_urls": [REST_BASE + "/test-evidence"]})[1:-1],
        "",
        1,
    )
    result = audit(question, private_question, gold_answer, response, events)
    assert result["missing_source_urls"]


def test_semantic_audit_requires_planned_hit_limit_and_representative_calls():
    question, private_question, gold_answer, response, events = valid_attempt_inputs()
    gold_answer["evidence_paths"].append(
        "/releases/2.08/alphafolds/AF-TEST-F1/hits?limit=100"
    )
    response["answer"] += " Representative d1tesa_ was inspected."
    response["representative_sids"] = ["d1tesa_"]
    result = audit(question, private_question, gold_answer, response, events)
    assert result["missing_hits"] == [("2.08", "AF-TEST-F1", 100)]
    assert result["missing_representative_calls"] == [("2.08", "d1tesa_")]

    events += "\n" + mcp_event(
        "get_scop_domains",
        {
            "sids": ["d1tesa_"],
            "includes": ["parents", "annotations", "homology"],
            "release": "2.08",
            "compact": True,
        },
    )
    response["evidence_used"].append(
        "get_scop_domains(sids=d1tesa_, includes=parents,annotations,homology, release=2.08)"
    )
    result = audit(question, private_question, gold_answer, response, events)
    assert result["missing_representative_calls"] == []


def test_semantic_audit_accepts_joined_decision_evidence_contract():
    question = {
        "id": "AFQ000_test",
        "category": "classification_level",
        "model": "AF-TEST-F1",
        "release": "2.08",
        "prompt": "Use minimum_supporting_hits=5 for the resulting node.",
    }
    private_question = {
        "category": "classification_level",
        "required_tools": [
            "get_alphafold_model",
            "get_alphafold_hits",
            "get_scop_domain",
            "get_scop_fatcat_chain_benchmark",
        ],
        "expected_behaviors": ["Reports local pLDDT by region."],
    }
    gold_answer = {
        "evidence_paths": [
            "/releases/2.08/alphafolds/AF-TEST-F1/hits?limit=100",
            "/releases/2.08/sunids/123/fatcat-chain-benchmark"
            "?minimum_supporting_hits=5",
        ]
    }
    response = {
        "answer": RUNNER.CLAIM_DISCLOSURE + " The evidence favors a hypothesis.\n\n"
        + evidence_answer(
            "E1. Retrieved regional evidence. "
            f"[model source]({REST_BASE}/releases/2.08/alphafolds/AF-TEST-F1)"
        ),
        "evidence_used": [
            "get_alphafold_hits(model=AF-TEST-F1, limit=100, release=2.08)",
            "get_alphafold_decision_evidence(model=AF-TEST-F1, "
            "regions=1-100, sids=d1tesa_, level=superfamily, threshold=5)",
        ],
        "representative_sids": ["d1tesa_"],
        "claim_status": RUNNER.CLAIM_STATUS,
        "input_attestation": {
            "question_id": question["id"],
            "question_sha256": "a" * 64,
            "skill_sha256": "b" * 64,
            "model": question["model"],
            "release": question["release"],
        },
    }
    events = "\n".join(
        [
            mcp_event(
                "get_alphafold_hits",
                {
                    "model": "AF-TEST-F1",
                    "limit": 100,
                    "release": "2.08",
                    "compact": True,
                    "include_transforms": False,
                },
                source_urls=[
                    REST_BASE
                    + "/releases/2.08/alphafolds/AF-TEST-F1/hits?limit=100"
                ],
            ),
            mcp_event(
                "get_alphafold_decision_evidence",
                {
                    "model": "AF-TEST-F1",
                    "regions": [{"start": 1, "end": 100}],
                    "sids": ["d1tesa_"],
                    "calibration_requests": [
                        {
                            "sid": "d1tesa_",
                            "level": "superfamily",
                            "minimum_supporting_hits": 5,
                        }
                    ],
                    "release": "2.08",
                },
                source_urls=[
                    REST_BASE + "/releases/2.08/alphafolds/AF-TEST-F1",
                    REST_BASE + "/releases/2.08/sids/d1tesa_/parents",
                    REST_BASE
                    + "/releases/2.08/sunids/123/fatcat-chain-benchmark"
                    "?minimum_supporting_hits=5",
                ],
            ),
        ]
    )

    result = audit(question, private_question, gold_answer, response, events)

    assert result["issues"] == []
    assert result["used_tools"] == [
        "get_alphafold_decision_evidence",
        "get_alphafold_hits",
    ]
    assert result["observed_benchmarks"] == [("2.08", 123, 5)]
    assert result["missing_representative_calls"] == []


def test_decision_bundle_satisfies_domain_batch_capability():
    question, _, _, response, _ = valid_attempt_inputs()
    question["category"] = "architecture_query"
    private_question = {
        "category": "architecture_query",
        "required_tools": [
            "get_alphafold_model",
            "get_alphafold_hits",
            "get_scop_domain",
        ],
    }
    gold_answer = {"evidence_paths": []}
    response["evidence_used"] = [
        "get_alphafold_hits(model=AF-TEST-F1, release=2.08)",
        "get_alphafold_decision_evidence(model=AF-TEST-F1, release=2.08)",
    ]
    events = "\n".join(
        [
            mcp_event(
                "get_alphafold_hits",
                {"model": "AF-TEST-F1", "release": "2.08"},
            ),
            mcp_event(
                "get_alphafold_decision_evidence",
                {
                    "model": "AF-TEST-F1",
                    "regions": [{"start": 1, "end": 100}],
                    "sids": ["d1tesa_"],
                    "calibration_requests": [],
                    "release": "2.08",
                },
            ),
        ]
    )

    result = audit(question, private_question, gold_answer, response, events)

    assert result["issues"] == []
    assert result["used_tools"] == [
        "get_alphafold_decision_evidence",
        "get_alphafold_hits",
    ]


def test_local_confidence_behavior_requires_deterministic_region_tool():
    question, private_question, gold_answer, response, events = valid_attempt_inputs()
    private_question["expected_behaviors"] = [
        "Reports local pLDDT by region without treating it as homology evidence."
    ]
    result = audit(question, private_question, gold_answer, response, events)
    assert "get_alphafold_regions_confidence" in result["missing_tools"]


def test_evidence_used_must_match_successful_required_calls():
    question, private_question, gold_answer, response, events = valid_attempt_inputs()
    response["evidence_used"].append("get_scop_domain(sid=d1tesa_, release=2.08)")
    result = audit(question, private_question, gold_answer, response, events)
    assert result["uncalled_reported_tools"] == ["get_scop_domain"]


def test_generator_prompt_embeds_the_authoritative_target_and_claim_boundary():
    question = {"model": "AF-TEST-F1", "release": "2.08", "prompt": "Assess it."}
    prompt = RUNNER.generator_prompt(question)
    normalized = " ".join(prompt.split())
    assert '"model": "AF-TEST-F1"' in prompt
    assert RUNNER.CLAIM_DISCLOSURE in prompt
    assert "never infer a target from a directory name" in prompt
    assert "include_transforms=false" in prompt
    assert "compact=true" in prompt
    assert "get_scop_domains" in prompt
    assert "get_alphafold_decision_evidence" in prompt
    assert "the exact relationship `level` you will report" in normalized
    assert "do not silently default to 2" in prompt
    assert "chain_benchmark_applicability" in prompt
    assert "lineage_by_level" in prompt
    assert "FATCAT score and RMSD" in prompt
    assert "skill's response structure and citation requirements" in prompt
    assert "every explicitly named representative or region" in prompt
    assert "Do not substitute another representative" in prompt
    assert "strong chance" in prompt
    assert "Every release-specific fact and conclusion basis" in normalized
    assert "never emit an unversioned `sunid` link" in normalized
    assert "Check all applicable caveat categories" in normalized
    assert "compact response must not hide evidence required" in normalized
    assert "Repeat `get_alphafold_hits` once" in normalized
    assert "rank window not enumerated" in normalized
    assert "single highest-ranked raw representative across that class" in normalized
    assert "more than one raw-only SID" in normalized
    assert "do not add raw-only SIDs" in normalized
    assert "automated or placeholder structural family label" in normalized
    assert "single strongest relationship level retained" in normalized
    assert "stored raw rows with filtered hits" in normalized
    assert "strongest evidence on both sides" in normalized
    assert "more than 8 MCP calls" in prompt


def test_generator_prompt_preserves_afq004_family_and_envelope_regressions():
    prompt = RUNNER.generator_prompt(
        {
            "id": "AFQ004",
            "category": "multi_domain",
            "difficulty": "advanced",
            "model": "AF-P00533-F1",
            "release": "2.08",
            "prompt": "Report approximate ranges and relationship levels.",
        }
    )

    normalized = " ".join(prompt.split())
    assert "family-discriminating evidence" in normalized
    assert "an automated match" in normalized
    assert "redundant structure-only hits" in normalized
    assert "Preserve the union or envelope of material hit ranges" in normalized
    assert "identify overlaps" in normalized
    assert "keep boundaries approximate" in normalized


def test_resume_performance_reconciles_prior_attempt_logs(tmp_path):
    first = {
        "attempt": 1,
        "duration_seconds": 10.5,
        "usage": {
            "input_tokens": 100,
            "cached_input_tokens": 40,
            "output_tokens": 10,
            "reasoning_output_tokens": 3,
            "cache_write_input_tokens": 0,
        },
        "total_call_count": 5,
        "issues": ["invalid calibration node"],
    }
    second = {
        "attempt": 2,
        "duration_seconds": 8.25,
        "usage": {
            "input_tokens": 80,
            "cached_input_tokens": 30,
            "output_tokens": 8,
            "reasoning_output_tokens": 2,
            "cache_write_input_tokens": 0,
        },
        "total_call_count": 4,
        "issues": [],
    }
    for attempt in (first, second):
        path = tmp_path / f"AFQ009.answer.a{attempt['attempt']}.audit.json"
        path.write_text(json.dumps(attempt), encoding="utf-8")

    result = RUNNER.reconcile_answer_performance(
        {"performance": {"attempt_count": 1}}, tmp_path, "AFQ009"
    )

    assert result["performance"] == {
        "attempt_count": 2,
        "duration_seconds": 18.75,
        "usage": {
            "input_tokens": 180,
            "cached_input_tokens": 70,
            "output_tokens": 18,
            "reasoning_output_tokens": 5,
            "cache_write_input_tokens": 0,
        },
        "mcp_call_count": 9,
        "rejected_attempts": [
            {"attempt": 1, "issues": ["invalid calibration node"]}
        ],
    }


def test_required_domain_evidence_is_collapsed_to_one_batch_tool():
    required = RUNNER.required_tools_for_question(
        {
            "required_tools": [
                "get_scop_domain",
                "get_scop_domain_lineage",
                "get_scop_domain_annotations",
                "get_scop_domain_homology",
            ],
            "category": "architecture_query",
        }
    )
    assert required == {"get_scop_domains"}


def test_regional_evidence_is_collapsed_to_joined_decision_tool():
    required = RUNNER.required_tools_for_question(
        {
            "required_tools": [
                "get_alphafold_model",
                "get_alphafold_hits",
                "get_alphafold_region_confidence",
                "get_scop_domain",
                "get_scop_fatcat_chain_benchmark",
            ],
            "category": "classification_level",
        }
    )
    assert required == {
        "get_alphafold_hits",
        "get_alphafold_decision_evidence",
    }


def test_answer_output_schema_gives_const_property_an_explicit_type():
    assert RUNNER.ANSWER_OUTPUT_SCHEMA["properties"]["claim_status"] == {
        "type": "string",
        "const": RUNNER.CLAIM_STATUS,
    }


def test_usage_parser_uses_only_completed_turn_records():
    events = "\n".join(
        [
            json.dumps({"type": "turn.started", "usage": {"input_tokens": 999}}),
            json.dumps(
                {
                    "type": "turn.completed",
                    "usage": {
                        "input_tokens": 100,
                        "cached_input_tokens": 40,
                        "output_tokens": 20,
                        "reasoning_output_tokens": 5,
                    },
                }
            ),
        ]
    )
    assert RUNNER.codex_usage(events) == {
        "input_tokens": 100,
        "cached_input_tokens": 40,
        "cache_write_input_tokens": 0,
        "output_tokens": 20,
        "reasoning_output_tokens": 5,
    }


def test_semantic_audit_enforces_total_mcp_call_budget():
    question, private_question, gold_answer, response, events = valid_attempt_inputs()
    result = RUNNER.audit_answer_attempt(
        question=question,
        private_question=private_question,
        gold_answer=gold_answer,
        response=response,
        event_log=events,
        question_sha256="a" * 64,
        skill_sha256="b" * 64,
        rest_base_url=REST_BASE,
        max_mcp_calls=2,
    )
    assert any("call budget exceeded" in issue for issue in result["issues"])


def test_codex_command_uses_only_the_external_sandbox():
    command = RUNNER.codex_command(
        Path("/tmp/workspace"),
        Path("/tmp/schema.json"),
        Path("/tmp/response.json"),
        "gpt-5.5",
        "high",
        "http://127.0.0.1:8000/mcp",
    )
    assert "--dangerously-bypass-approvals-and-sandbox" in command
    assert "--sandbox" not in command


def test_external_sandbox_preflight_can_read_copied_inputs(tmp_path):
    session_root = tmp_path / "session"
    workspace = session_root / "workspace"
    skill_dir = workspace / "alphafold-scope-homology"
    skill_dir.mkdir(parents=True)
    question = workspace / "questions_blind.jsonl"
    skill = skill_dir / "SKILL.md"
    question.write_text('{"model":"AF-TEST-F1"}\n')
    skill.write_text("# Test skill\n")
    expected = {
        "questions_blind.jsonl": hashlib.sha256(question.read_bytes()).hexdigest(),
        "alphafold-scope-homology/SKILL.md": hashlib.sha256(
            skill.read_bytes()
        ).hexdigest(),
    }
    RUNNER.verify_workspace_inputs(session_root, workspace, expected)


def test_fixture_parity_accepts_only_exact_canonical_responses(tmp_path):
    fixture = tmp_path / "evidence.json"
    fixture.write_bytes(RUNNER.canonical_json_bytes(b'{"value":1}'))
    expected_sha = hashlib.sha256(fixture.read_bytes()).hexdigest()
    manifest = {
        "entries": [
            {
                "path": "/evidence",
                "file": fixture.name,
                "sha256": expected_sha,
            }
        ]
    }
    result = RUNNER.verify_fixture_parity(
        tmp_path,
        manifest,
        REST_BASE,
        timeout=1,
        workers=1,
        fetcher=lambda base, path, timeout: RUNNER.canonical_json_bytes(
            b'{"value": 1}'
        ),
    )
    assert result == {
        "status": "passed",
        "checked_entries": 1,
        "rest_base_url": REST_BASE,
    }


def test_fixture_parity_rejects_live_snapshot_drift(tmp_path):
    fixture = tmp_path / "evidence.json"
    fixture.write_bytes(RUNNER.canonical_json_bytes(b'{"value":1}'))
    manifest = {
        "entries": [
            {
                "path": "/evidence",
                "file": fixture.name,
                "sha256": hashlib.sha256(fixture.read_bytes()).hexdigest(),
            }
        ]
    }
    try:
        RUNNER.verify_fixture_parity(
            tmp_path,
            manifest,
            REST_BASE,
            timeout=1,
            workers=1,
            fetcher=lambda base, path, timeout: RUNNER.canonical_json_bytes(
                b'{"value":2}'
            ),
        )
    except RuntimeError as error:
        assert "live response differs from frozen fixture" in str(error)
    else:
        raise AssertionError("snapshot drift should fail the preflight")


def test_fixture_parity_compares_canonical_json_after_raw_integrity_check(tmp_path):
    fixture = tmp_path / "evidence.json"
    fixture.write_bytes(b'{"z": 2, "a": 1}\n')
    manifest = {
        "entries": [
            {
                "path": "/evidence",
                "file": fixture.name,
                "sha256": hashlib.sha256(fixture.read_bytes()).hexdigest(),
            }
        ]
    }
    result = RUNNER.verify_fixture_parity(
        tmp_path,
        manifest,
        REST_BASE,
        timeout=1,
        workers=1,
        fetcher=lambda base, path, timeout: RUNNER.canonical_json_bytes(
            b'{"a": 1, "z": 2}'
        ),
    )
    assert result["status"] == "passed"


def test_summary_includes_distribution_validity_and_per_question_notes():
    evaluations = [
        {"question_id": "AFQ001_test", "score": 4, "rubric_notes": "Complete."},
        {"question_id": "AFQ002_test", "score": 1, "rubric_notes": "Too generic."},
    ]
    answer_results = {
        "AFQ001_test": {"audit": {"successful_call_count": 3, "failed_call_count": 0}},
        "AFQ002_test": {"audit": {"successful_call_count": 2, "failed_call_count": 1}},
    }
    summary = RUNNER.render_summary(evaluations, answer_results)
    assert "Mean score: 2.50/4" in summary
    assert "4: 1" in summary
    assert "Successful `scop_local` MCP calls in accepted attempts: 5" in summary
    assert "## Efficiency" in summary
    assert "Too generic." in summary
