"""indexer.split_document: paragraph-first + sliding-window boundary cases."""

from rag_service.indexer import split_document


def test_short_paragraphs_kept_as_is() -> None:
    chunks = split_document("第一段\n\n第二段", chunk_size=100, chunk_overlap=10)
    assert chunks == ["第一段", "第二段"]


def test_long_paragraph_sliding_window_overlap() -> None:
    text = "a" * 250
    chunks = split_document(text, chunk_size=100, chunk_overlap=20)
    # step = 80 -> starts 0, 80, 160; last window clipped to 100 chars
    assert [len(c) for c in chunks] == [100, 100, 90]
    assert chunks[1][:20] == chunks[0][-20:]  # overlap carried across windows


def test_exact_chunk_size_boundary() -> None:
    chunks = split_document("x" * 100, chunk_size=100, chunk_overlap=0)
    assert chunks == ["x" * 100]


def test_blank_text_returns_nothing() -> None:
    assert split_document("   \n\n  ", chunk_size=10, chunk_overlap=1) == []
