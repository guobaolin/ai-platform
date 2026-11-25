"""Entry point: `uv run uvicorn rag_service.main:app --port 8002`."""

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

from .api import close_redis, router


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    yield
    await close_redis()


logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(levelname)s %(message)s")

app = FastAPI(
    title="rag-service",
    version="1.0.0",
    description="Thin run-plane indexing/retrieval per contracts/openapi-rag-service.yaml",
    lifespan=lifespan,
)
app.include_router(router)
