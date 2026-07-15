"""Semantic search over indexed document chunks.

Embeds the query, runs a vector similarity search in Weaviate, and returns one
result per document (its closest-matching chunk) so the knowledge base can rank
documents by relevance without a single document's many chunks crowding out the
rest. Reuses the same embedding config and vector store as the RAG pipeline;
only documents that have been indexed are searchable.
"""

from app.embeddings import embed_query, get_embedding_model_name
from app.env import float_env
from app.vectorstore import search_grouped

_SNIPPET_MAX_CHARS = 300

_DEFAULT_SCORE_FLOOR = 0.15
_DEFAULT_SCORE_CEILING = 0.45


def _snippet(text: str) -> str:
    collapsed = " ".join(text.split())
    if len(collapsed) <= _SNIPPET_MAX_CHARS:
        return collapsed
    truncated = collapsed[:_SNIPPET_MAX_CHARS]
    # Cut at the last whitespace so the snippet doesn't end mid-word.
    cut = truncated.rfind(" ")
    if cut > 0:
        truncated = truncated[:cut]
    return truncated.rstrip() + "..."


def _score_bounds() -> tuple[float, float]:
    """Read the calibration anchors once, returning (floor, span)."""
    floor = float_env("SEARCH_SCORE_FLOOR", _DEFAULT_SCORE_FLOOR, minimum=0.0)
    ceiling = float_env("SEARCH_SCORE_CEILING", _DEFAULT_SCORE_CEILING, minimum=0.0)
    return floor, ceiling - floor


def _score(distance: float | None, floor: float, span: float) -> float:
    # Weaviate returns cosine distance (0 = identical, up to 2 for opposite vectors);
    # 1 - distance is the raw cosine similarity. Then calibrate onto [0, 1] so the
    # compressed similarity band reads as an intuitive relevance percentage.
    if distance is None:
        return 0.0
    similarity = 1.0 - distance
    calibrated = (similarity - floor) / span if span > 0 else similarity
    return round(min(1.0, max(0.0, calibrated)), 4)


def search_documents(query: str, object_keys: list[str], limit: int) -> tuple[list[dict], str]:
    """Return documents semantically similar to the query, ranked by relevance.

    Args:
        query: The free-text search query.
        object_keys: Object keys to scope the search to (e.g. a user's documents).
                     An empty list means "nothing to search".
        limit: Maximum number of documents to return.

    Returns:
        (results, embedding_model) where results is a list of dicts with
        object_key, score (in [0, 1], higher is more relevant), snippet, and
        chunk_index, one entry per document.
    """
    model = get_embedding_model_name()
    if not object_keys:
        return [], model

    hits = search_grouped(embed_query(query), object_keys, limit)
    floor, span = _score_bounds()
    return (
        [
            {
                "object_key": h["object_key"],
                "score": _score(h["distance"], floor, span),
                "snippet": _snippet(h["text"]),
                "chunk_index": h["chunk_index"],
            }
            for h in hits
        ],
        model,
    )
