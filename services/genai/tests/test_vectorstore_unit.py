"""Unit tests for the Weaviate vector store with a mocked client.

These complement tests/test_vectorstore.py, which exercises the real thing and
skips without a running Weaviate. Here the client is mocked so the wiring around
it (connection params, collection setup, insert/delete/search shaping) runs in
CI without any live instance. No network, no gRPC.
"""

import os
from unittest.mock import MagicMock, patch

import httpx
import pytest
from weaviate.exceptions import UnexpectedStatusCodeError
from weaviate.util import generate_uuid5

from app import vectorstore
from app.embeddings import Chunk


def _client_and_collection() -> tuple[MagicMock, MagicMock]:
    """A mock client whose collections.get(...) always yields the same collection."""
    client = MagicMock()
    collection = client.collections.get.return_value
    return client, collection


def _status_error() -> UnexpectedStatusCodeError:
    response = httpx.Response(status_code=500, request=httpx.Request("POST", "http://weaviate/v1/schema"))
    return UnexpectedStatusCodeError("create failed", response)


# --- connection params ---


def test_connection_params_defaults_to_in_network_weaviate():
    with patch.dict(os.environ, {}, clear=False):
        os.environ.pop("WEAVIATE_URL", None)
        os.environ.pop("WEAVIATE_GRPC_PORT", None)
        params = vectorstore._connection_params()

    assert params == {
        "http_host": "weaviate",
        "http_port": 8080,
        "http_secure": False,
        "grpc_host": "weaviate",
        "grpc_port": 50051,
        "grpc_secure": False,
    }


def test_connection_params_uses_https_port_and_secure_for_https_url():
    with patch.dict(os.environ, {"WEAVIATE_URL": "https://vec.example.com"}, clear=False):
        os.environ.pop("WEAVIATE_GRPC_PORT", None)
        params = vectorstore._connection_params()

    assert params["http_host"] == "vec.example.com"
    assert params["http_port"] == 443
    assert params["http_secure"] is True
    assert params["grpc_secure"] is True


def test_connection_params_honours_explicit_ports():
    with patch.dict(os.environ, {"WEAVIATE_URL": "http://host:9999", "WEAVIATE_GRPC_PORT": "50052"}):
        params = vectorstore._connection_params()

    assert params["http_port"] == 9999
    assert params["grpc_port"] == 50052


# --- collection setup ---


def test_ensure_collection_is_noop_when_it_already_exists():
    client = MagicMock()
    client.collections.exists.return_value = True

    vectorstore._ensure_collection(client)

    client.collections.create.assert_not_called()


def test_ensure_collection_creates_it_when_absent():
    client = MagicMock()
    client.collections.exists.return_value = False

    vectorstore._ensure_collection(client)

    client.collections.create.assert_called_once()
    assert client.collections.create.call_args.kwargs["name"] == vectorstore.COLLECTION_NAME


def test_ensure_collection_swallows_create_race_when_now_present():
    # First exists() says no, create loses the race and 500s, second exists() says yes.
    client = MagicMock()
    client.collections.exists.side_effect = [False, True]
    client.collections.create.side_effect = _status_error()

    vectorstore._ensure_collection(client)  # must not raise


def test_ensure_collection_reraises_when_create_fails_for_real():
    client = MagicMock()
    client.collections.exists.side_effect = [False, False]
    client.collections.create.side_effect = _status_error()

    with pytest.raises(UnexpectedStatusCodeError):
        vectorstore._ensure_collection(client)


# --- chunk ids ---


def test_chunk_uuid_is_deterministic_and_matches_weaviate_scheme():
    assert vectorstore._chunk_uuid("doc", 0) == generate_uuid5("doc:0")


def test_chunk_uuid_differs_per_chunk_index():
    assert vectorstore._chunk_uuid("doc", 0) != vectorstore._chunk_uuid("doc", 1)


# --- indexing ---


def test_index_chunks_writes_one_object_per_chunk_after_clearing_old():
    client, collection = _client_and_collection()
    collection.data.insert_many.return_value.has_errors = False
    chunks = [Chunk("doc", 0, "alpha"), Chunk("doc", 1, "beta")]

    with patch("app.vectorstore._client", return_value=client):
        written = vectorstore.index_chunks("doc", chunks, [[0.1], [0.2]])

    assert written == 2
    collection.data.delete_many.assert_called_once()  # stale chunks cleared first
    objects = collection.data.insert_many.call_args.args[0]
    assert [o.properties["chunk_index"] for o in objects] == [0, 1]
    assert objects[0].uuid == generate_uuid5("doc:0")


def test_index_chunks_with_no_chunks_clears_and_returns_zero():
    client, collection = _client_and_collection()

    with patch("app.vectorstore._client", return_value=client):
        assert vectorstore.index_chunks("doc", [], []) == 0

    collection.data.delete_many.assert_called_once()
    collection.data.insert_many.assert_not_called()


def test_index_chunks_raises_when_weaviate_reports_insert_errors():
    client, collection = _client_and_collection()
    result = collection.data.insert_many.return_value
    result.has_errors = True
    result.errors = {0: "boom"}

    with patch("app.vectorstore._client", return_value=client):
        with pytest.raises(RuntimeError, match="failed to index"):
            vectorstore.index_chunks("doc", [Chunk("doc", 0, "a")], [[0.1]])


def test_index_chunks_rejects_length_mismatch_before_touching_weaviate():
    with patch("app.vectorstore._client", side_effect=AssertionError("must not touch weaviate")):
        with pytest.raises(ValueError, match="length mismatch"):
            vectorstore.index_chunks("doc", [Chunk("doc", 0, "a")], [[0.1], [0.2]])


# --- deletion ---


def test_delete_document_returns_successful_count():
    client, collection = _client_and_collection()
    collection.data.delete_many.return_value.successful = 3

    with patch("app.vectorstore._client", return_value=client):
        assert vectorstore.delete_document("doc") == 3


# --- search ---


def test_search_maps_objects_to_result_dicts():
    obj = MagicMock()
    obj.properties = {"text": "hello", "object_key": "doc", "chunk_index": 2}
    obj.metadata.distance = 0.15
    client, collection = _client_and_collection()
    collection.query.near_vector.return_value.objects = [obj]

    with patch("app.vectorstore._client", return_value=client):
        hits = vectorstore.search([0.1, 0.2], ["doc"], 5)

    assert hits == [{"text": "hello", "object_key": "doc", "chunk_index": 2, "distance": 0.15}]


def test_search_grouped_returns_one_result_per_group_and_skips_empty_groups():
    best = MagicMock()
    best.properties = {"text": "closest", "chunk_index": 1}
    best.metadata.distance = 0.2
    group = MagicMock()
    group.objects = [best]
    group.name = "doc-1"
    empty = MagicMock()
    empty.objects = []
    empty.name = "doc-2"

    client, collection = _client_and_collection()
    collection.query.near_vector.return_value.groups = {"doc-1": group, "doc-2": empty}

    with patch("app.vectorstore._client", return_value=client):
        results = vectorstore.search_grouped([0.1], ["doc-1", "doc-2"], 5)

    assert results == [{"text": "closest", "object_key": "doc-1", "chunk_index": 1, "distance": 0.2}]


# --- client lifecycle ---


def test_close_client_is_noop_when_nothing_cached():
    vectorstore._client.cache_clear()

    vectorstore.close_client()  # must not construct or close anything


def test_close_client_closes_and_clears_the_cached_client():
    fake = MagicMock()
    with patch("app.vectorstore.weaviate.connect_to_custom", return_value=fake), patch("app.vectorstore._ensure_collection"):
        vectorstore._client.cache_clear()
        try:
            vectorstore._client()  # populate the cache
            assert vectorstore._client.cache_info().currsize == 1

            vectorstore.close_client()

            fake.close.assert_called_once()
            assert vectorstore._client.cache_info().currsize == 0
        finally:
            vectorstore._client.cache_clear()
