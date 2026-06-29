"""Unit tests for the retrieval-augmented Q&A module.

Weaviate retrieval, the embedder, and the LLM are all mocked, so no network or
running services are needed.
"""

import os
from unittest.mock import patch

from langchain_core.messages import AIMessage
from langchain_core.runnables import RunnableLambda

from app.qa import _NO_CONTEXT_MESSAGE, answer_question


def _fake_llm(response: str):
    return RunnableLambda(lambda _: AIMessage(content=response))


def _hit(object_key: str, chunk_index: int, text: str) -> dict:
    return {"object_key": object_key, "chunk_index": chunk_index, "text": text, "distance": 0.1}


def test_answer_uses_retrieved_chunks_and_cites_sources():
    hits = [_hit("doc-1", 0, "Revenue grew 15%."), _hit("doc-2", 3, "Costs were flat.")]

    with (
        patch("app.qa.embed_query", return_value=[0.1, 0.2]),
        patch("app.qa.search", return_value=hits),
        patch("app.qa.get_llm", return_value=_fake_llm("Revenue grew by 15%.")),
        patch("app.qa.get_model_name", return_value="openai/gpt-oss-120b"),
    ):
        answer, sources, model = answer_question("How did revenue change?", ["doc-1", "doc-2"])

    assert answer == "Revenue grew by 15%."
    assert sources == ["doc-1", "doc-2"]
    assert model == "openai/gpt-oss-120b"


def test_sources_are_deduplicated_in_retrieval_order():
    hits = [_hit("doc-2", 0, "a"), _hit("doc-1", 1, "b"), _hit("doc-2", 2, "c")]

    with (
        patch("app.qa.embed_query", return_value=[0.0]),
        patch("app.qa.search", return_value=hits),
        patch("app.qa.get_llm", return_value=_fake_llm("answer")),
        patch("app.qa.get_model_name", return_value="m"),
    ):
        _, sources, _ = answer_question("q", ["doc-1", "doc-2"])

    assert sources == ["doc-2", "doc-1"]


def test_empty_object_keys_returns_fallback_without_searching():
    with (
        patch("app.qa.search", side_effect=AssertionError("must not search")),
        patch("app.qa.embed_query", side_effect=AssertionError("must not embed")),
        patch("app.qa.get_model_name", return_value="m"),
    ):
        answer, sources, model = answer_question("q", [])

    assert answer == _NO_CONTEXT_MESSAGE
    assert sources == []
    assert model == "m"


def test_empty_retrieval_returns_fallback_without_calling_llm():
    with (
        patch("app.qa.embed_query", return_value=[0.1]),
        patch("app.qa.search", return_value=[]),
        patch("app.qa.get_llm", side_effect=AssertionError("must not call llm")),
        patch("app.qa.get_model_name", return_value="m"),
    ):
        answer, sources, _ = answer_question("q", ["doc-1"])

    assert answer == _NO_CONTEXT_MESSAGE
    assert sources == []


def test_top_k_is_read_from_env():
    captured = {}

    def fake_search(vector, keys, k):
        captured["k"] = k
        return [_hit("doc-1", 0, "x")]

    with (
        patch.dict(os.environ, {"RAG_TOP_K": "9"}),
        patch("app.qa.embed_query", return_value=[0.1]),
        patch("app.qa.search", side_effect=fake_search),
        patch("app.qa.get_llm", return_value=_fake_llm("a")),
        patch("app.qa.get_model_name", return_value="m"),
    ):
        answer_question("q", ["doc-1"])

    assert captured["k"] == 9


def test_answer_is_stripped():
    with (
        patch("app.qa.embed_query", return_value=[0.1]),
        patch("app.qa.search", return_value=[_hit("doc-1", 0, "x")]),
        patch("app.qa.get_llm", return_value=_fake_llm("  spaced answer  ")),
        patch("app.qa.get_model_name", return_value="m"),
    ):
        answer, _, _ = answer_question("q", ["doc-1"])

    assert answer == "spaced answer"
