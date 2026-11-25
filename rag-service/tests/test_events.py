"""Event envelope structure per contracts/run-events.md (INDEX_PROGRESS path)."""

import json

from rag_service.events import build_envelope


def test_envelope_shape() -> None:
    env = build_envelope(
        "job-1",
        "INDEX_PROGRESS",
        {"datasetId": "d", "documentId": "doc", "stage": "splitting", "segments": 0},
    )
    assert set(env) == {"eventId", "runId", "type", "ts", "data"}
    assert env["runId"] == "job-1"  # jobId occupies the stream's runId slot
    assert env["type"] == "INDEX_PROGRESS"
    assert env["data"]["stage"] == "splitting"


def test_envelope_json_roundtrip() -> None:
    env = build_envelope("j", "INDEX_PROGRESS", {"stage": "completed", "segments": 3})
    text = json.dumps(env, ensure_ascii=False)
    assert json.loads(text) == env
    assert isinstance(env["ts"], int) and env["ts"] > 1_600_000_000_000
