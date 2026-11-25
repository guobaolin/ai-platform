"""HTTP client for the Java control plane inner API (openapi-control-inner.yaml)."""

import logging

import httpx

from .config import CONTROL_BASE_URL, RUNTIME_TOKEN

logger = logging.getLogger("rag_service.control_plane_client")


def _headers() -> dict[str, str]:
    return {"Authorization": f"Bearer {RUNTIME_TOKEN}"}


async def fetch_file_content(key: str) -> str:
    """GET /inner/files?key=... -> raw text. Raises on non-2xx."""
    url = f"{CONTROL_BASE_URL}/inner/files"
    async with httpx.AsyncClient(timeout=30) as client:
        resp = await client.get(url, params={"key": key}, headers=_headers())
        resp.raise_for_status()
        return resp.text


async def report_index_result(document_id: str, body: dict[str, object]) -> None:
    """POST /inner/documents/{documentId}/index-result — best-effort report-back."""
    url = f"{CONTROL_BASE_URL}/inner/documents/{document_id}/index-result"
    try:
        async with httpx.AsyncClient(timeout=10) as client:
            resp = await client.post(url, json=body, headers=_headers())
            resp.raise_for_status()
    except Exception as exc:  # noqa: BLE001 -- never crash the job over a report failure
        logger.warning("index-result report for %s failed: %s", document_id, exc)
