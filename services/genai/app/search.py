"""Semantic search over indexed document chunks.

Embeds the query, runs a vector similarity search in Weaviate, and returns one
result per document (its closest-matching chunk) so the knowledge base can rank
documents by relevance without a single document's many chunks crowding out the
rest. Reuses the same embedding config and vector store as the RAG pipeline;
only documents that have been indexed are searchable.
"""

from app.embeddings import embed_query, get_embedding_model_name
from app.vectorstore import search_grouped

_SNIPPET_MAX_CHARS = 300


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


def _score(distance: float | None) -> float:
    # Weaviate returns cosine distance (0 = identical, up to 2 for opposite
    # vectors). Flip it into a similarity score where higher means more relevant,
    # clamped at 0 so the score always stays in [0, 1].
    if distance is None:
        return 0.0
    return round(max(0.0, 1.0 - distance), 4)


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
    return (
        [
            {
                "object_key": h["object_key"],
                "score": _score(h["distance"]),
                "snippet": _snippet(h["text"]),
                "chunk_index": h["chunk_index"],
            }
            for h in hits
        ],
        model,
    )
