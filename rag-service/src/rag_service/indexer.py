"""Document splitting: paragraph-first, sliding-window fallback for long text.

中文友好：按空行/换行切段落；单段超过 chunk_size 时按字符滑窗切，
窗间保留 overlap 个字符重叠。纯函数，可单测。
"""


def split_document(text: str, chunk_size: int = 500, chunk_overlap: int = 50) -> list[str]:
    if chunk_size <= 0:
        raise ValueError("chunk_size must be positive")
    if not 0 <= chunk_overlap < chunk_size:
        raise ValueError("chunk_overlap must be in [0, chunk_size)")
    if not text.strip():
        return []

    paragraphs = [p.strip() for p in text.split("\n") if p.strip()]
    chunks: list[str] = []
    for para in paragraphs:
        if len(para) <= chunk_size:
            chunks.append(para)
        else:  # long paragraph -> sliding window with overlap
            step = chunk_size - chunk_overlap
            for start in range(0, len(para), step):
                window = para[start : start + chunk_size]
                chunks.append(window)
                if start + chunk_size >= len(para):
                    break
    return chunks
