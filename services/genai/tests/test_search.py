"""Unit tests for the semantic search module.

Weaviate retrieval and the embedder are mocked, so no network or running
services are needed.
"""

import os
from unittest.mock import patch

import pytest

from app.search import search_documents


def _hit(object_key: str, chunk_index: int, text: str, distance: float) -> dict:
    return {"object_key": object_key, "chunk_index": chunk_index, "text": text, "distance": distance}


def test_returns_one_result_per_document_ranked_by_best_chunk():
    hits = [
        _hit("doc-1", 2, "closest chunk", 0.05),
        _hit("doc-2", 0, "second doc", 0.20),
        _hit("doc-1", 5, "same doc, farther chunk", 0.30),
    ]
    with (
        patch("app.search.embed_query", return_value=[0.1, 0.2]),
        patch("app.search.search", return_value=hits),
        patch("app.search.get_embedding_model_name", return_value="Qwen/Qwen3-Embedding-8B"),
    ):
        results, model = search_documents("query", ["doc-1", "doc-2"], limit=10)

    assert model == "Qwen/Qwen3-Embedding-8B"
    assert [r["object_key"] for r in results] == ["doc-1", "doc-2"]
    # doc-1 keeps its nearest chunk (index 2), not the later, farther one
    assert results[0]["chunk_index"] == 2
    assert results[0]["snippet"] == "closest chunk"


def test_score_is_similarity_derived_from_distance():
    with (
        patch("app.search.embed_query", return_value=[0.0]),
        patch("app.search.search", return_value=[_hit("doc-1", 0, "x", 0.25)]),
        patch("app.search.get_embedding_model_name", return_value="m"),
    ):
        results, _ = search_documents("q", ["doc-1"], limit=5)

    assert results[0]["score"] == 0.75


def test_limit_caps_number_of_documents():
    hits = [_hit(f"doc-{i}", 0, "t", 0.1 * i) for i in range(5)]
    with (
        patch("app.search.embed_query", return_value=[0.1]),
        patch("app.search.search", return_value=hits),
        patch("app.search.get_embedding_model_name", return_value="m"),
    ):
        results, _ = search_documents("q", ["doc-0"], limit=2)

    assert [r["object_key"] for r in results] == ["doc-0", "doc-1"]


def test_empty_object_keys_returns_no_results_without_searching():
    with (
        patch("app.search.search", side_effect=AssertionError("must not search")),
        patch("app.search.embed_query", side_effect=AssertionError("must not embed")),
        patch("app.search.get_embedding_model_name", return_value="m"),
    ):
        results, model = search_documents("q", [], limit=10)

    assert results == []
    assert model == "m"


def test_long_snippet_is_truncated():
    long_text = "word " * 200
    with (
        patch("app.search.embed_query", return_value=[0.1]),
        patch("app.search.search", return_value=[_hit("doc-1", 0, long_text, 0.1)]),
        patch("app.search.get_embedding_model_name", return_value="m"),
    ):
        results, _ = search_documents("q", ["doc-1"], limit=5)

    snippet = results[0]["snippet"]
    assert snippet.endswith("...")
    assert len(snippet) <= _snippet_ceiling()


def _snippet_ceiling() -> int:
    from app.search import _SNIPPET_MAX_CHARS

    return _SNIPPET_MAX_CHARS + len("...")


def test_candidate_over_fetch_is_read_from_env():
    captured = {}

    def fake_search(vector, keys, k):
        captured["k"] = k
        return []

    with (
        patch.dict(os.environ, {"SEARCH_CANDIDATES": "17"}),
        patch("app.search.embed_query", return_value=[0.1]),
        patch("app.search.search", side_effect=fake_search),
        patch("app.search.get_embedding_model_name", return_value="m"),
    ):
        search_documents("q", ["doc-1"], limit=5)

    assert captured["k"] == 17


def test_rejects_invalid_candidate_env():
    with (
        patch.dict(os.environ, {"SEARCH_CANDIDATES": "abc"}),
        patch("app.search.embed_query", return_value=[0.1]),
        patch("app.search.get_embedding_model_name", return_value="m"),
    ):
        with pytest.raises(RuntimeError, match="SEARCH_CANDIDATES"):
            search_documents("q", ["doc-1"], limit=5)
