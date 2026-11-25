"""In-memory vector store (prototype).

原型期内存向量库，生产替换 Milvus/pgvector，接口不变。
Keys are dataset ids; each chunk keeps its source ids, text and normalized vector.
"""

from dataclasses import dataclass


@dataclass
class Chunk:
    segment_id: str
    document_id: str
    content: str
    vector: list[float]


# dict[dataset_id, list[Chunk]]
_datasets: dict[str, list[Chunk]] = {}


def add_chunks(dataset_id: str, chunks: list[Chunk]) -> None:
    _datasets.setdefault(dataset_id, []).extend(chunks)


def get_chunks(dataset_id: str) -> list[Chunk]:
    """Empty list for unknown datasets (retrieval returns [] for empty store)."""
    return _datasets.get(dataset_id, [])


def clear() -> None:
    """Test helper."""
    _datasets.clear()
