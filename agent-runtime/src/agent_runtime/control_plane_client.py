"""HTTP client for the Java control plane inner API (openapi-control-inner.yaml)."""

import json
import logging
from collections.abc import AsyncIterator
from typing import Any

import httpx

from .config import CONTROL_BASE_URL, RUNTIME_TOKEN

logger = logging.getLogger("agent_runtime.control_plane_client")


def _headers() -> dict[str, str]:
    return {"Authorization": f"Bearer {RUNTIME_TOKEN}"}


async def fetch_app_config(app_id: str) -> dict[str, Any]:
    """GET /inner/apps/{appId}/config -> AppConfig dict. Raises on non-2xx."""
    url = f"{CONTROL_BASE_URL}/inner/apps/{app_id}/config"
    async with httpx.AsyncClient(timeout=10) as client:
        resp = await client.get(url, headers=_headers())
        resp.raise_for_status()
        return resp.json()


async def stream_llm_tokens(
    app_id: str, run_id: str, messages: list[dict[str, str]]
) -> AsyncIterator[str]:
    """POST /inner/llm/invoke, consume SSE, yield each token chunk text.

    SSE lines look like `data: {"text":"..."}`; stream ends with `data: [DONE]`.
    """
    url = f"{CONTROL_BASE_URL}/inner/llm/invoke"
    body = {"appId": app_id, "runId": run_id, "messages": messages}
    async with httpx.AsyncClient(timeout=None) as client:
        async with client.stream("POST", url, json=body, headers=_headers()) as resp:
            resp.raise_for_status()
            async for line in resp.aiter_lines():
                if not line.startswith("data:"):
                    continue
                payload = line[len("data:"):].strip()
                if payload == "[DONE]":
                    return
                try:
                    chunk = json.loads(payload)
                except json.JSONDecodeError:
                    logger.warning("skip malformed SSE payload: %r", payload)
                    continue
                text = chunk.get("text", "")
                if text:
                    yield text
