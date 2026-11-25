"""Event publishing to Redis Stream per contracts/run-events.md.

Envelope format (payload JSON): {"eventId", "runId", "type", "ts", "data"}.
Stream key: run:events:{runId}; XADD single field `payload`; XTRIM ~ maxlen 1000.
"""

import json
import logging
import time
from typing import Any

import redis.asyncio as aioredis

from .config import REDIS_URL

logger = logging.getLogger("agent_runtime.events")

_event_client: aioredis.Redis | None = None


def build_envelope(run_id: str, event_type: str, data: dict[str, Any]) -> dict[str, Any]:
    """Build the event envelope (pure function, unit-tested)."""
    return {
        "eventId": "",  # placeholder: consumer may backfill from stream entry id
        "runId": run_id,
        "type": event_type,
        "ts": int(time.time() * 1000),  # epoch milliseconds
        "data": data,
    }


def get_event_client() -> aioredis.Redis:
    global _event_client
    if _event_client is None:
        _event_client = aioredis.from_url(REDIS_URL, decode_responses=True)
    return _event_client


async def emit(run_id: str, event_type: str, data: dict[str, Any]) -> None:
    """XADD one event; best-effort: Redis failures are logged, never raised."""
    envelope = build_envelope(run_id, event_type, data)
    key = f"run:events:{run_id}"
    try:
        client = get_event_client()
        await client.xadd(key, {"payload": json.dumps(envelope, ensure_ascii=False)})
        await client.xtrim(key, maxlen=1000, approximate=True)
    except Exception as exc:  # noqa: BLE001 -- prototype: report-and-continue
        logger.warning("emit %s to %s failed: %s", event_type, key, exc)


async def subscribe_commands(run_id: str) -> Any:
    """Return (pubsub, channel) for run control commands; failures are logged."""
    channel = f"run:commands:{run_id}"
    try:
        client = get_event_client()
        pubsub = client.pubsub()
        await pubsub.subscribe(channel)
        return pubsub, channel
    except Exception as exc:  # noqa: BLE001
        logger.warning("subscribe %s failed: %s", channel, exc)
        return None, channel
