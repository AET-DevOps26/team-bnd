"""Integration tests for the Weaviate vector store.

These run against a real Weaviate and are skipped when none is reachable. The
intended way to run them is the compose test profile, which starts a throwaway
Weaviate and runs the suite in a container against it:

    docker compose run --rm genai-test

Each test uses unique object keys and cleans up the keys it created, so the
suite never deletes whole collections or touches data it didn't write. Weaviate
locks a self-provided collection to the dimension of its first inserted vector,
so every test here uses the same vector width.
"""

import os
import uuid

import pytest

from app.embeddings import Chunk

pytestmark = pytest.mark.integration

_DIM = 3


def _vec(*values: float) -> list[float]:
    """Pad/truncate to the fixed test dimension so all inserts agree."""
    v = list(values) + [0.0] * _DIM
    return v[:_DIM]


@pytest.fixture(scope="module")
def store():
    os.environ.setdefault("WEAVIATE_URL", "http://localhost:8080")
    vectorstore = pytest.importorskip("app.vectorstore")
    try:
        vectorstore._client()
    except Exception as e:  # noqa: BLE001 - any connection failure means "no Weaviate, skip"
        pytest.skip(f"Weaviate not reachable: {e}")

    yield vectorstore

    vectorstore.close_client()


def _key() -> str:
    return f"test/{uuid.uuid4()}.txt"


def test_index_then_search_returns_scoped_chunks(store):
    key = _key()
    chunks = [Chunk(key, 0, "alpha"), Chunk(key, 1, "beta"), Chunk(key, 2, "gamma")]
    vectors = [_vec(1.0, 0.0, 0.0), _vec(0.0, 1.0, 0.0), _vec(0.0, 0.0, 1.0)]

    try:
        assert store.index_chunks(key, chunks, vectors) == 3

        hits = store.search(_vec(1.0, 0.0, 0.0), [key], 3)
        assert hits[0]["chunk_index"] == 0
        assert hits[0]["object_key"] == key
        assert {h["chunk_index"] for h in hits} == {0, 1, 2}
    finally:
        store.delete_document(key)


def test_reindex_with_fewer_chunks_drops_stale(store):
    key = _key()
    chunks = [Chunk(key, 0, "a"), Chunk(key, 1, "b"), Chunk(key, 2, "c")]
    vectors = [_vec(1.0), _vec(0.0, 1.0), _vec(1.0, 1.0)]

    try:
        store.index_chunks(key, chunks, vectors)
        store.index_chunks(key, chunks[:1], vectors[:1])

        hits = store.search(_vec(1.0), [key], 10)
        assert [h["chunk_index"] for h in hits] == [0]
    finally:
        store.delete_document(key)


def test_search_is_scoped_to_requested_keys(store):
    key_a, key_b = _key(), _key()
    try:
        store.index_chunks(key_a, [Chunk(key_a, 0, "a")], [_vec(1.0, 0.0)])
        store.index_chunks(key_b, [Chunk(key_b, 0, "b")], [_vec(1.0, 0.0)])

        hits = store.search(_vec(1.0, 0.0), [key_a], 10)
        assert all(h["object_key"] == key_a for h in hits)
    finally:
        store.delete_document(key_a)
        store.delete_document(key_b)


def test_delete_is_idempotent(store):
    key = _key()
    store.index_chunks(key, [Chunk(key, 0, "x")], [_vec(1.0)])

    assert store.delete_document(key) == 1
    assert store.delete_document(key) == 0
