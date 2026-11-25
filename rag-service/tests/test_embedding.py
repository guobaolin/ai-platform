"""hash-v1 embedding: determinism + L2 normalization."""

import math

from rag_service.embedding import cosine, embed


def test_embed_is_deterministic() -> None:
    a = embed("知识库切片测试文本", dim=64)
    b = embed("知识库切片测试文本", dim=64)
    assert a == b


def test_embed_l2_normalized() -> None:
    v = embed("hello world, 向量归一化", dim=64)
    assert len(v) == 64
    assert math.isclose(math.sqrt(sum(x * x for x in v)), 1.0, rel_tol=1e-9)


def test_embed_similar_texts_score_higher() -> None:
    q = embed("北京天气", dim=64)
    near = embed("北京天气预报", dim=64)
    far = embed("完全不相干的另一个话题数据库", dim=64)
    assert cosine(q, near) > cosine(q, far)
