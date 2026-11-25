"""HTTP API layer for rag-service: contract parsing, auth, job acceptance. No logic beyond that."""

import asyncio

from fastapi import APIRouter, Depends, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from . import embedding, events, store
from .config import RUNTIME_TOKEN
from .models import CreateIndexJobRequest, JobRecord, RetrievalRequest, _jobs
from .pipeline import run_index_job

router = APIRouter()
_bearer = HTTPBearer(auto_error=False)


def require_token(creds: HTTPAuthorizationCredentials | None = Depends(_bearer)) -> None:
    if creds is None or creds.scheme.lower() != "bearer" or creds.credentials != RUNTIME_TOKEN:
        raise HTTPException(status_code=401, detail="invalid or missing bearer token")


@router.get("/v1/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@router.post("/v1/index-jobs", status_code=202, dependencies=[Depends(require_token)])
async def create_index_job(req: CreateIndexJobRequest) -> dict[str, str]:
    if req.jobId in _jobs:  # idempotent reject: jobId already accepted
        raise HTTPException(status_code=409, detail="jobId already exists")
    task = asyncio.create_task(run_index_job(req))
    _jobs[req.jobId] = JobRecord(status="ACCEPTED", task=task)
    return {"jobId": req.jobId, "status": "ACCEPTED"}


@router.get("/v1/index-jobs/{jobId}", dependencies=[Depends(require_token)])
async def get_index_job(jobId: str) -> dict[str, str]:
    record = _jobs.get(jobId)
    if record is None:
        raise HTTPException(status_code=404, detail="job not found")
    return {"jobId": jobId, "status": record.status}


@router.post("/v1/retrieval", dependencies=[Depends(require_token)])
async def retrieval(req: RetrievalRequest) -> dict[str, list[dict[str, object]]]:
    chunks = store.get_chunks(req.datasetId)
    if not chunks:
        return {"results": []}
    dim = len(chunks[0].vector)  # embed query with the same dim as stored vectors
    q_vec = embedding.embed(req.query, dim)
    scored = sorted(
        ((embedding.cosine(q_vec, c.vector), c) for c in chunks),
        key=lambda item: item[0],
        reverse=True,
    )[: max(req.topK, 0)]
    results = [
        {"documentId": c.document_id, "segmentId": c.segment_id, "content": c.content, "score": s}
        for s, c in scored
    ]
    return {"results": results}


async def close_redis() -> None:
    if events._event_client is not None:
        await events._event_client.aclose()
