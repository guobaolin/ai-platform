"""HTTP API layer: contract parsing, auth, in-flight registry. No logic beyond that."""

import asyncio
from dataclasses import dataclass
from typing import Literal

from fastapi import APIRouter, Depends, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, Field

from . import events, executor
from .config import RUNTIME_TOKEN

router = APIRouter()
_bearer = HTTPBearer(auto_error=False)


class CreateRunRequest(BaseModel):
    """Matches CreateRunRequest in openapi-agent-runtime.yaml."""

    runId: str
    appId: str
    tenantId: str
    userId: str
    query: str
    fileKeys: list[str] = Field(default_factory=list)


@dataclass
class RunRecord:
    status: Literal["ACCEPTED", "RUNNING", "FINISHED", "ERROR", "CANCELLED"]
    task: asyncio.Task


# In-flight / finished runs live only in process memory (prototype scope).
_registry: dict[str, RunRecord] = {}


def require_token(creds: HTTPAuthorizationCredentials | None = Depends(_bearer)) -> None:
    if creds is None or creds.scheme.lower() != "bearer" or creds.credentials != RUNTIME_TOKEN:
        raise HTTPException(status_code=401, detail="invalid or missing bearer token")


@router.get("/v1/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@router.post("/v1/runs", status_code=202, dependencies=[Depends(require_token)])
async def create_run(req: CreateRunRequest) -> dict[str, str]:
    if req.runId in _registry:  # idempotent reject: runId already seen
        raise HTTPException(status_code=409, detail="runId already exists")

    def set_status(status: str) -> None:
        _registry[req.runId].status = status  # type: ignore[assignment]

    task = asyncio.create_task(
        executor.run_agent(req.appId, req.tenantId, req.runId, req.query, set_status)
    )
    _registry[req.runId] = RunRecord(status="ACCEPTED", task=task)
    return {"runId": req.runId, "status": "ACCEPTED"}


@router.get("/v1/runs/{runId}", dependencies=[Depends(require_token)])
async def get_run(runId: str) -> dict[str, str]:
    record = _registry.get(runId)
    if record is None:
        raise HTTPException(status_code=404, detail="run not found")
    return {"runId": runId, "status": record.status}


@router.post("/v1/runs/{runId}/cancel", dependencies=[Depends(require_token)])
async def cancel_run(runId: str) -> dict[str, str]:
    record = _registry.get(runId)
    if record is None:
        raise HTTPException(status_code=404, detail="run not found")
    if record.status in ("ACCEPTED", "RUNNING") and not record.task.done():
        # Direct fast path: the executor emits RUN_ERROR(CANCELLED) best-effort.
        record.task.cancel()
        record.status = "CANCELLED"
    return {"runId": runId, "status": record.status}


async def close_redis() -> None:
    if events._event_client is not None:
        await events._event_client.aclose()
