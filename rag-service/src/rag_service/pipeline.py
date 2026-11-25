"""Index-job pipeline: 拉文件 → 切分 → 嵌入 → 入库 → 回报控制面.

状态写入 api._jobs 由调用方注册；本模块只做流程编排，无业务规则。
"""

import logging

from . import control_plane_client, embedding, events, indexer, store
from .models import CreateIndexJobRequest, _jobs

logger = logging.getLogger("rag_service.pipeline")


async def run_index_job(req: CreateIndexJobRequest) -> None:
    """Per-document pipeline; never raises -- failures are reported, process keeps running."""
    _jobs[req.jobId].status = "RUNNING"
    all_ok = True
    for document_id in req.documentIds:
        try:
            count = await _index_document(req, document_id)
            await control_plane_client.report_index_result(
                document_id,
                {"jobId": req.jobId, "status": "completed", "segmentCount": count},
            )
            await events.emit(
                req.jobId,
                "INDEX_PROGRESS",
                {"datasetId": req.datasetId, "documentId": document_id,
                 "stage": "completed", "segments": count},
            )
        except Exception as exc:  # noqa: BLE001
            all_ok = False
            error_msg = f"{type(exc).__name__}: {exc}"
            logger.warning("index doc %s failed: %s", document_id, error_msg)
            await control_plane_client.report_index_result(
                document_id, {"jobId": req.jobId, "status": "error", "error": error_msg}
            )
            await events.emit(
                req.jobId,
                "INDEX_PROGRESS",
                {"datasetId": req.datasetId, "documentId": document_id,
                 "stage": "error", "segments": 0},
            )
    _jobs[req.jobId].status = "DONE" if all_ok else "ERROR"


async def _index_document(req: CreateIndexJobRequest, document_id: str) -> int:
    # 1. Pull raw text from control plane by object-storage key (no bytes in requests).
    text = await control_plane_client.fetch_file_content(f"document:{document_id}")

    # 2. Split (段落优先，超长滑窗).
    await events.emit(
        req.jobId,
        "INDEX_PROGRESS",
        {"datasetId": req.datasetId, "documentId": document_id,
         "stage": "splitting", "segments": 0},
    )
    pieces = indexer.split_document(text, req.chunkSize, req.chunkOverlap)

    # 3. Embed each chunk (provider hash-v1) and write to the in-memory vector store.
    await events.emit(
        req.jobId,
        "INDEX_PROGRESS",
        {"datasetId": req.datasetId, "documentId": document_id,
         "stage": "indexing", "segments": len(pieces)},
    )
    chunks = [
        store.Chunk(
            segment_id=f"{document_id}-seg{i}",
            document_id=document_id,
            content=piece,
            vector=embedding.embed(piece, req.embeddingProfile.dim),
        )
        for i, piece in enumerate(pieces)
    ]
    store.add_chunks(req.datasetId, chunks)
    return len(chunks)
