import importlib.util
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "check_development_gates.py"
SPEC = importlib.util.spec_from_file_location("check_development_gates", SCRIPT)
GATES = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GATES)


def example_run():
    ids = {f"Q{i}" for i in range(12)}
    answers = [
        {
            "record": {"question_id": question_id},
            "audit": {"issues": []},
            "performance": {
                "attempt_count": 1,
                "mcp_call_count": 3,
                "usage": {"input_tokens": 100_000},
            },
        }
        for question_id in sorted(ids)
    ]
    evaluations = [
        {"question_id": question_id, "score": 3} for question_id in sorted(ids)
    ]
    return answers, evaluations, ids


def test_successful_audits_do_not_waive_accuracy_or_performance_gates():
    answers, evaluations, ids = example_run()
    passed = GATES.assess_development_gates(answers, evaluations, ids, 178_228)
    assert passed["quantitative_development_gates_passed"] is True
    for answer in answers:
        answer["performance"]["usage"]["input_tokens"] = 150_000
    evaluations[0]["score"] = 2
    rejected = GATES.assess_development_gates(answers, evaluations, ids, 178_228)
    assert set(rejected["failed_gates"]) == {
        "mean_scientific_score_at_least_3",
        "median_input_reduction_at_least_30_percent",
    }


def test_gates_reject_missing_cases_retries_call_overrun_and_invalid_audits():
    answers, evaluations, ids = example_run()
    assert GATES.assess_development_gates(answers[:-1], evaluations, ids, 178_228)[
        "failed_gates"
    ] == ["complete_run_and_baseline_required"]
    for answer in answers[:3]:
        answer["performance"]["attempt_count"] = 2
    answers[0]["performance"]["mcp_call_count"] = 9
    answers[0]["audit"]["issues"] = ["fixture missing"]
    rejected = GATES.assess_development_gates(answers, evaluations, ids, 178_228)
    assert set(rejected["failed_gates"]) == {
        "first_attempt_passes_at_least_10_of_12",
        "maximum_calls_at_most_8",
        "deterministic_audits_pass",
    }
