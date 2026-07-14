"""Unit tests for the retrieval-augmented Q&A module.

Weaviate retrieval, the embedder, and the LLM are all mocked, so no network or
running services are needed. The LLM is mocked at the structured-output layer:
`with_structured_output` returns a runnable that yields a `_CitedAnswer`.
"""

import os
from unittest.mock import MagicMock, patch

import pytest
from langchain_core.runnables import RunnableLambda

from app.qa import _NO_CONTEXT_MESSAGE, answer_question


def _hit(object_key: str, chunk_index: int, text: str) -> dict:
    return {"object_key": object_key, "chunk_index": chunk_index, "text": text, "distance": 0.1}


def _structured_llm(answer: str, source_ids: list[int]):
    """A fake LLM whose structured output yields the given answer and source ids."""
    from app.qa import _CitedAnswer

    result = _CitedAnswer(answer=answer, source_ids=source_ids)
    llm = MagicMock()
    llm.with_structured_output.return_value = RunnableLambda(lambda _: result)
    return llm


def test_answer_and_citations_follow_model_selection():
    hits = [_hit("doc-1", 0, "Revenue grew 15%."), _hit("doc-2", 3, "Costs were flat.")]

    with (
        patch("app.qa.embed_query", return_value=[0.1, 0.2]),
        patch("app.qa.search", return_value=hits),
        patch("app.qa.get_llm", return_value=_structured_llm("Revenue grew by 15%.", [1, 2])),
        patch("app.qa.get_model_name", return_value="openai/gpt-oss-120b"),
    ):
        result = answer_question("How did revenue change?", ["doc-1", "doc-2"])

    assert result.answer == "Revenue grew by 15%."
    assert result.model == "openai/gpt-oss-120b"
    assert [(c.marker, c.object_key) for c in result.citations] == [(1, "doc-1"), (2, "doc-2")]


def test_citations_use_model_order_and_subset():
    hits = [_hit("doc-1", 0, "a"), _hit("doc-2", 0, "b"), _hit("doc-3", 0, "c")]

    with (
        patch("app.qa.embed_query", return_value=[0.0]),
        patch("app.qa.search", return_value=hits),
        patch("app.qa.get_llm", return_value=_structured_llm("answer", [3, 1])),
        patch("app.qa.get_model_name", return_value="m"),
    ):
        result = answer_question("q", ["doc-1", "doc-2", "doc-3"])

    assert [(c.marker, c.object_key) for c in result.citations] == [(1, "doc-3"), (2, "doc-1")]


def test_out_of_range_and_duplicate_ids_are_dropped():
    hits = [_hit("doc-1", 0, "a"), _hit("doc-2", 0, "b")]

    with (
        patch("app.qa.embed_query", return_value=[0.0]),
        patch("app.qa.search", return_value=hits),
        patch("app.qa.get_llm", return_value=_structured_llm("answer", [2, 2, 9, -1])),
        patch("app.qa.get_model_name", return_value="m"),
    ):
        result = answer_question("q", ["doc-1", "doc-2"])

    assert [(c.marker, c.object_key) for c in result.citations] == [(1, "doc-2")]


def test_empty_source_ids_yields_no_citations():
    hits = [_hit("doc-1", 0, "a"), _hit("doc-2", 0, "b")]

    with (
        patch("app.qa.embed_query", return_value=[0.0]),
        patch("app.qa.search", return_value=hits),
        patch("app.qa.get_llm", return_value=_structured_llm("nothing here supports that", [])),
        patch("app.qa.get_model_name", return_value="m"),
    ):
        result = answer_question("q", ["doc-1", "doc-2"])

    assert result.citations == []


def test_all_invalid_source_ids_fall_back_to_all_retrieved():
    hits = [_hit("doc-1", 0, "a"), _hit("doc-2", 0, "b")]

    with (
        patch("app.qa.embed_query", return_value=[0.0]),
        patch("app.qa.search", return_value=hits),
        patch("app.qa.get_llm", return_value=_structured_llm("answer", [9, -1])),
        patch("app.qa.get_model_name", return_value="m"),
    ):
        result = answer_question("q", ["doc-1", "doc-2"])

    assert [c.object_key for c in result.citations] == ["doc-1", "doc-2"]


def test_chunks_of_one_document_collapse_to_a_single_source():
    # doc-2 matched twice; the model cites source 1 (doc-2) and 2 (doc-1).
    hits = [_hit("doc-2", 0, "first"), _hit("doc-1", 1, "b"), _hit("doc-2", 2, "third")]

    with (
        patch("app.qa.embed_query", return_value=[0.0]),
        patch("app.qa.search", return_value=hits),
        patch("app.qa.get_llm", return_value=_structured_llm("answer", [1, 2])),
        patch("app.qa.get_model_name", return_value="m"),
    ):
        result = answer_question("q", ["doc-1", "doc-2"])

    assert [(c.marker, c.object_key) for c in result.citations] == [(1, "doc-2"), (2, "doc-1")]


def test_citation_carries_snippet_from_closest_chunk():
    hits = [_hit("doc-1", 0, "  Revenue   grew   sharply.  ")]

    with (
        patch("app.qa.embed_query", return_value=[0.0]),
        patch("app.qa.search", return_value=hits),
        patch("app.qa.get_llm", return_value=_structured_llm("answer", [1])),
        patch("app.qa.get_model_name", return_value="m"),
    ):
        result = answer_question("q", ["doc-1"])

    assert result.citations[0].snippet == "Revenue grew sharply."


def test_empty_object_keys_returns_fallback_without_searching():
    with (
        patch("app.qa.search", side_effect=AssertionError("must not search")),
        patch("app.qa.embed_query", side_effect=AssertionError("must not embed")),
        patch("app.qa.get_model_name", return_value="m"),
    ):
        result = answer_question("q", [])

    assert result.answer == _NO_CONTEXT_MESSAGE
    assert result.citations == []
    assert result.model == "m"


def test_empty_retrieval_returns_fallback_without_calling_llm():
    with (
        patch("app.qa.embed_query", return_value=[0.1]),
        patch("app.qa.search", return_value=[]),
        patch("app.qa.get_llm", side_effect=AssertionError("must not call llm")),
        patch("app.qa.get_model_name", return_value="m"),
    ):
        result = answer_question("q", ["doc-1"])

    assert result.answer == _NO_CONTEXT_MESSAGE
    assert result.citations == []


def test_top_k_is_read_from_env():
    captured = {}

    def fake_search(vector, keys, k):
        captured["k"] = k
        return [_hit("doc-1", 0, "x")]

    with (
        patch.dict(os.environ, {"RAG_TOP_K": "9"}),
        patch("app.qa.embed_query", return_value=[0.1]),
        patch("app.qa.search", side_effect=fake_search),
        patch("app.qa.get_llm", return_value=_structured_llm("a", [1])),
        patch("app.qa.get_model_name", return_value="m"),
    ):
        answer_question("q", ["doc-1"])

    assert captured["k"] == 9


def test_answer_question_rejects_invalid_top_k():
    with (
        patch.dict(os.environ, {"RAG_TOP_K": "abc"}),
        patch("app.qa.embed_query", return_value=[0.1]),
        patch("app.qa.search", return_value=[]),
        patch("app.qa.get_model_name", return_value="m"),
    ):
        with pytest.raises(RuntimeError, match="RAG_TOP_K"):
            answer_question("q", ["doc-1"])


def test_answer_is_stripped():
    with (
        patch("app.qa.embed_query", return_value=[0.1]),
        patch("app.qa.search", return_value=[_hit("doc-1", 0, "x")]),
        patch("app.qa.get_llm", return_value=_structured_llm("  spaced answer  ", [1])),
        patch("app.qa.get_model_name", return_value="m"),
    ):
        result = answer_question("q", ["doc-1"])

    assert result.answer == "spaced answer"


def test_citation_snippet_is_truncated_at_word_boundary_for_long_chunk():
    from app.qa import _SNIPPET_MAX_CHARS

    long_text = "word " * 200
    with (
        patch("app.qa.embed_query", return_value=[0.1]),
        patch("app.qa.search", return_value=[_hit("doc-1", 0, long_text)]),
        patch("app.qa.get_llm", return_value=_structured_llm("answer", [1])),
        patch("app.qa.get_model_name", return_value="m"),
    ):
        result = answer_question("q", ["doc-1"])

    snippet = result.citations[0].snippet
    assert snippet.endswith("...")
    assert len(snippet) <= _SNIPPET_MAX_CHARS + len("...")
    assert not snippet[:-3].endswith(" ")
