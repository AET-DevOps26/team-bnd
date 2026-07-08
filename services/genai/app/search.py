"""Semantic search over indexed document chunks.

Embeds the query, runs a vector similarity search in Weaviate, and rolls the
chunk hits up to the document level so each document appears once, ranked by its
closest-matching chunk. Reuses the same embedding config and vector store as the
RAG pipeline; only documents that have been indexed are searchable.

  SEARCH_CANDIDATES   chunk hits to pull before de-duplicating to documents
                      (default 50)
"""

from app.embeddings import embed_query, get_embedding_model_name
from app.env import int_env
from app.vectorstore import search

_DEFAULT_CANDIDATES = 50
_SNIPPET_MAX_CHARS = 300


def _candidates() -> int:
    return int_env("SEARCH_CANDIDATES", _DEFAULT_CANDIDATES, minimum=1)


def _snippet(text: str) -> str:
    collapsed = " ".join(text.split())
    if len(collapsed) <= _SNIPPET_MAX_CHARS:
        return collapsed
    return collapsed[:_SNIPPET_MAX_CHARS].rstrip() + "..."


def _score(distance: float | None) -> float:
    # Weaviate returns cosine distance (0 = identical). Flip it into a similarity
    # score where higher means more relevant, which is friendlier for the client.
    if distance is None:
        return 0.0
    return round(1.0 - distance, 4)


def _best_per_document(hits: list[dict], limit: int) -> list[dict]:
    """Collapse chunk hits to one entry per document, keeping the nearest chunk.

    Weaviate returns hits sorted by ascending distance, so the first hit seen for
    an object key is its best match. Insertion order is preserved, so documents
    come out ranked by their closest chunk.
    """
    best: dict[str, dict] = {}
    for hit in hits:
        key = hit["object_key"]
        if key in best:
            continue
        best[key] = {
            "object_key": key,
            "score": _score(hit["distance"]),
            "snippet": _snippet(hit["text"]),
            "chunk_index": hit["chunk_index"],
        }
        if len(best) == limit:
            break
    return list(best.values())


def search_documents(query: str, object_keys: list[str], limit: int) -> tuple[list[dict], str]:
    """Return documents semantically similar to the query, ranked by relevance.

    Args:
        query: The free-text search query.
        object_keys: Object keys to scope the search to (e.g. a user's documents).
                     An empty list means "nothing to search".
        limit: Maximum number of documents to return.

    Returns:
        (results, embedding_model) where results is a list of dicts with
        object_key, score, snippet, and chunk_index, one entry per document.
    """
    model = get_embedding_model_name()
    if not object_keys:
        return [], model

    hits = search(embed_query(query), object_keys, _candidates())
    return _best_per_document(hits, limit), model
