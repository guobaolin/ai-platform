"""Shared request models and the in-memory job registry (prototype scope)."""

import asyncio
from dataclasses import dataclass
from typing import Literal

from pydantic import BaseModel, Field


class EmbeddingProfile(BaseModel):
    provider: str = "hash-v1"
    dim: int = 64


class CreateIndexJobRequest(BaseModel):
    """Matches CreateIndexJobRequest in openapi-rag-service.yaml."""

    jobId: str
    datasetId: str
    documentIds: list[str]
    embeddingProfile: EmbeddingProfile = Field(default_factory=EmbeddingProfile)
    chunkSize: int = 500
    chunkOverlap: int = 50


class RetrievalRequest(BaseModel):
    datasetId: str
    query: str
    topK: int = 3


@dataclass
class JobRecord:
    status: Literal["ACCEPTED", "RUNNING", "DONE", "ERROR"]
    task: asyncio.Task


# Jobs live only in process memory (prototype scope).
_jobs: dict[str, JobRecord] = {}
