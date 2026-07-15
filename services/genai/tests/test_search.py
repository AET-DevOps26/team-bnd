"""Unit tests for the semantic search module.

Weaviate retrieval and the embedder are mocked, so no network or running
services are needed.
"""

import os
from unittest.mock import patch

from app.search import search_documents


def _hit(object_key: str, chunk_index: int, text: str, distance: float) -> dict:
    return {"object_key": object_key, "chunk_index": chunk_index, "text": text, "distance": distance}


def test_returns_one_result_per_document_ranked_by_best_chunk():
    hits = [
        _hit("doc-1", 2, "closest chunk", 0.05),
        _hit("doc-2", 0, "second doc", 0.20),
    ]
    with (
        patch("app.search.embed_query", return_value=[0.1, 0.2]),
        patch("app.search.search_grouped", return_value=hits),
        patch("app.search.get_embedding_model_name", return_value="Qwen/Qwen3-Embedding-8B"),
    ):
        results, model = search_documents("query", ["doc-1", "doc-2"], limit=10)

    assert model == "Qwen/Qwen3-Embedding-8B"
    assert [r["object_key"] for r in results] == ["doc-1", "doc-2"]
    assert results[0]["chunk_index"] == 2
    assert results[0]["snippet"] == "closest chunk"


def test_score_maps_midband_similarity_onto_calibrated_range():
    # distance 0.70 -> cosine 0.30 -> (0.30 - 0.15) / (0.45 - 0.15) = 0.5
    with (
        patch("app.search.embed_query", return_value=[0.0]),
        patch("app.search.search_grouped", return_value=[_hit("doc-1", 0, "x", 0.70)]),
        patch("app.search.get_embedding_model_name", return_value="m"),
    ):
        results, _ = search_documents("q", ["doc-1"], limit=5)

    assert results[0]["score"] == 0.5


def test_score_saturates_at_ceiling():
    # cosine 0.70 sits above the 0.45 ceiling, so it reads as a full 1.0.
    with (
        patch("app.search.embed_query", return_value=[0.0]),
        patch("app.search.search_grouped", return_value=[_hit("doc-1", 0, "x", 0.30)]),
        patch("app.search.get_embedding_model_name", return_value="m"),
    ):
        results, _ = search_documents("q", ["doc-1"], limit=5)

    assert results[0]["score"] == 1.0


def test_score_is_zero_below_floor():
    # cosine 0.10 sits below the 0.15 floor, so it reads as 0.
    with (
        patch("app.search.embed_query", return_value=[0.0]),
        patch("app.search.search_grouped", return_value=[_hit("doc-1", 0, "x", 0.90)]),
        patch("app.search.get_embedding_model_name", return_value="m"),
    ):
        results, _ = search_documents("q", ["doc-1"], limit=5)

    assert results[0]["score"] == 0.0


def test_score_is_zero_when_distance_is_missing():
    with (
        patch("app.search.embed_query", return_value=[0.0]),
        patch("app.search.search_grouped", return_value=[_hit("doc-1", 0, "x", None)]),
        patch("app.search.get_embedding_model_name", return_value="m"),
    ):
        results, _ = search_documents("q", ["doc-1"], limit=5)

    assert results[0]["score"] == 0.0


def test_score_clamps_to_zero_for_very_distant_chunks():
    with (
        patch("app.search.embed_query", return_value=[0.0]),
        patch("app.search.search_grouped", return_value=[_hit("doc-1", 0, "x", 1.6)]),
        patch("app.search.get_embedding_model_name", return_value="m"),
    ):
        results, _ = search_documents("q", ["doc-1"], limit=5)

    assert results[0]["score"] == 0.0


def test_score_anchors_are_env_configurable():
    # Floor 0 / ceiling 1 disables calibration: score is the raw cosine similarity.
    with (
        patch.dict(os.environ, {"SEARCH_SCORE_FLOOR": "0.0", "SEARCH_SCORE_CEILING": "1.0"}),
        patch("app.search.embed_query", return_value=[0.0]),
        patch("app.search.search_grouped", return_value=[_hit("doc-1", 0, "x", 0.25)]),
        patch("app.search.get_embedding_model_name", return_value="m"),
    ):
        results, _ = search_documents("q", ["doc-1"], limit=5)

    assert results[0]["score"] == 0.75


def test_empty_object_keys_returns_no_results_without_searching():
    with (
        patch("app.search.search_grouped", side_effect=AssertionError("must not search")),
        patch("app.search.embed_query", side_effect=AssertionError("must not embed")),
        patch("app.search.get_embedding_model_name", return_value="m"),
    ):
        results, model = search_documents("q", [], limit=10)

    assert results == []
    assert model == "m"


def test_search_grouped_returns_empty_for_empty_object_keys_without_querying():
    from app.vectorstore import search_grouped

    with patch("app.vectorstore._client", side_effect=AssertionError("must not touch weaviate")):
        results = search_grouped([0.1, 0.2], [], limit=5)

    assert results == []


def test_search_returns_empty_for_empty_object_keys_without_querying():
    from app.vectorstore import search

    with patch("app.vectorstore._client", side_effect=AssertionError("must not touch weaviate")):
        results = search([0.1, 0.2], [], limit=5)

    assert results == []


def test_long_snippet_is_truncated_at_word_boundary():
    long_text = "word " * 200
    with (
        patch("app.search.embed_query", return_value=[0.1]),
        patch("app.search.search_grouped", return_value=[_hit("doc-1", 0, long_text, 0.1)]),
        patch("app.search.get_embedding_model_name", return_value="m"),
    ):
        results, _ = search_documents("q", ["doc-1"], limit=5)

    snippet = results[0]["snippet"]
    assert snippet.endswith("...")
    assert len(snippet) <= _snippet_ceiling()
    assert not snippet[:-3].endswith(" "), snippet
    assert "  " not in snippet


def _snippet_ceiling() -> int:
    from app.search import _SNIPPET_MAX_CHARS

    return _SNIPPET_MAX_CHARS + len("...")
