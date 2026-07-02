"""Weaviate vector store for the RAG pipeline.

Stores document chunks (text + externally supplied embedding vector + source
metadata) in a single collection, and supports indexing, retrieval, and
deletion scoped to a document.

Connection:
  WEAVIATE_URL        HTTP endpoint (default http://weaviate:8080)
  WEAVIATE_GRPC_PORT  gRPC port (default 50051)

The collection uses self-provided vectors (no Weaviate-side vectorizer): the
genai service supplies one vector per chunk. No fixed dimension is configured,
so any embedding provider's output size works (see app/embeddings.py).
"""

import os
from functools import lru_cache
from urllib.parse import urlparse

import weaviate
from weaviate.classes.config import Configure, DataType, Property
from weaviate.classes.data import DataObject
from weaviate.classes.query import Filter, MetadataQuery
from weaviate.exceptions import UnexpectedStatusCodeError
from weaviate.util import generate_uuid5

from app.embeddings import Chunk
from app.env import int_env

COLLECTION_NAME = "DocumentChunk"

_DEFAULT_URL = "http://weaviate:8080"
_DEFAULT_GRPC_PORT = 50051


def _connection_params() -> dict:
    parsed = urlparse(os.getenv("WEAVIATE_URL", _DEFAULT_URL))
    secure = parsed.scheme == "https"
    host = parsed.hostname or "weaviate"
    http_port = parsed.port or (443 if secure else 80)
    grpc_port = int_env("WEAVIATE_GRPC_PORT", _DEFAULT_GRPC_PORT, minimum=1)
    return {
        "http_host": host,
        "http_port": http_port,
        "http_secure": secure,
        "grpc_host": host,
        "grpc_port": grpc_port,
        "grpc_secure": secure,
    }


@lru_cache(maxsize=1)
def _client() -> weaviate.WeaviateClient:
    client = weaviate.connect_to_custom(**_connection_params())
    _ensure_collection(client)
    return client


def _ensure_collection(client: weaviate.WeaviateClient) -> None:
    """Create the chunk collection on first use if it doesn't exist yet.

    exists() + create() isn't atomic, so two concurrent first requests can race
    to create it. If create fails but the collection now exists, another worker
    won the race and we treat it as success; otherwise the failure is real.
    """
    if client.collections.exists(COLLECTION_NAME):
        return
    try:
        client.collections.create(
            name=COLLECTION_NAME,
            vector_config=Configure.Vectors.self_provided(),
            properties=[
                Property(name="text", data_type=DataType.TEXT),
                Property(name="object_key", data_type=DataType.TEXT),
                Property(name="chunk_index", data_type=DataType.INT),
            ],
        )
    except UnexpectedStatusCodeError:
        if not client.collections.exists(COLLECTION_NAME):
            raise


def _chunk_uuid(object_key: str, chunk_index: int) -> str:
    """Deterministic per-chunk id so a re-insert overwrites instead of duplicating."""
    return generate_uuid5(f"{object_key}:{chunk_index}")


def index_chunks(object_key: str, chunks: list[Chunk], vectors: list[list[float]]) -> int:
    """Replace a document's chunks in the index with the given chunks and vectors.

    Existing chunks for the object key are deleted first, so re-indexing a
    document that now has fewer chunks doesn't leave stale ones behind. Returns
    the number of chunks written.
    """
    if len(chunks) != len(vectors):
        raise ValueError(f"chunks ({len(chunks)}) and vectors ({len(vectors)}) length mismatch")
    if any(chunk.object_key != object_key for chunk in chunks):
        raise ValueError(f"all chunks must belong to object_key {object_key!r}")

    collection = _client().collections.get(COLLECTION_NAME)
    delete_document(object_key)

    if not chunks:
        return 0

    objects = [
        DataObject(
            properties={"text": chunk.text, "object_key": object_key, "chunk_index": chunk.chunk_index},
            vector=vector,
            uuid=_chunk_uuid(object_key, chunk.chunk_index),
        )
        for chunk, vector in zip(chunks, vectors, strict=True)
    ]
    result = collection.data.insert_many(objects)
    if result.has_errors:
        raise RuntimeError(f"failed to index {len(result.errors)} of {len(objects)} chunks for {object_key}: {result.errors}")
    return len(objects)


def delete_document(object_key: str) -> int:
    """Remove all chunks for a document. A no-op (returns 0) if none are indexed."""
    collection = _client().collections.get(COLLECTION_NAME)
    result = collection.data.delete_many(where=Filter.by_property("object_key").equal(object_key))
    return result.successful


def search(query_vector: list[float], object_keys: list[str], limit: int) -> list[dict]:
    """Return the top chunks nearest to the query vector, scoped to object_keys.

    Each result carries its text, source object key, chunk index, and distance.
    An empty object_keys list searches the whole collection.
    """
    collection = _client().collections.get(COLLECTION_NAME)
    filters = Filter.by_property("object_key").contains_any(object_keys) if object_keys else None

    response = collection.query.near_vector(
        near_vector=query_vector,
        limit=limit,
        filters=filters,
        return_metadata=MetadataQuery(distance=True),
    )
    return [
        {
            "text": obj.properties["text"],
            "object_key": obj.properties["object_key"],
            "chunk_index": int(obj.properties["chunk_index"]),
            "distance": obj.metadata.distance,
        }
        for obj in response.objects
    ]


def close_client() -> None:
    """Close the cached Weaviate client and clear the cache (used on shutdown)."""
    if _client.cache_info().currsize:
        _client().close()
        _client.cache_clear()
