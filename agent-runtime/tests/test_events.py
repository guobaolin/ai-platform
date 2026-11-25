"""Event envelope structure per contracts/run-events.md."""

import json
import math

from agent_runtime.events import build_envelope


def test_envelope_fields_and_types() -> None:
    env = build_envelope("run-1", "RUN_STARTED", {"appId": "a", "tenantId": "t"})
    assert set(env) == {"eventId", "runId", "type", "ts", "data"}
    assert env["runId"] == "run-1"
    assert env["type"] == "RUN_STARTED"
    assert env["data"] == {"appId": "a", "tenantId": "t"}


def test_envelope_ts_is_epoch_millis() -> None:
    env = build_envelope("r", "LLM_TOKEN", {"text": "hi"})
    assert isinstance(env["ts"], int)
    assert not math.isclose(env["ts"], env["ts"] / 1000)  # ms, not seconds
    assert env["ts"] > 1_600_000_000_000  # after 2020 in ms


def test_envelope_json_serializable() -> None:
    env = build_envelope("r", "USAGE", {"model": "m", "promptTokens": 1, "completionTokens": 2})
    restored = json.loads(json.dumps(env, ensure_ascii=False))
    assert restored == env
