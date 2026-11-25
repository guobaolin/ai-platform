"""hash-v1 deterministic embedding provider.

Pure function, no external dependency: character 2-gram / 3-gram features are
hashed (blake2b) into `dim` buckets with signed counts, then L2-normalized.
Deterministic across runs/machines, unit-tested. Production: replace provider
behind the same signature.
"""

import hashlib
import math


def embed(text: str, dim: int = 64) -> list[float]:
    """Embed text into a unit-length vector of size dim using the hash-v1 provider."""
    if dim <= 0:
        raise ValueError("dim must be positive")
    vector = [0.0] * dim
    for n in (2, 3):  # 2-gram + 3-gram character shingles, CJK-friendly
        for i in range(len(text) - n + 1):
            gram = text[i : i + n]
            digest = hashlib.blake2b(gram.encode("utf-8"), digest_size=8).digest()
            code = int.from_bytes(digest, "big")
            bucket = code % dim
            sign = 1.0 if (code >> 63) & 1 == 0 else -1.0
            vector[bucket] += sign
    norm = math.sqrt(sum(v * v for v in vector))
    if norm == 0.0:  # empty / single-char text: return zero vector
        return vector
    return [v / norm for v in vector]


def cosine(vec_a: list[float], vec_b: list[float]) -> float:
    """Similarity for L2-normalized vectors == dot product."""
    return sum(a * b for a, b in zip(vec_a, vec_b, strict=True))
