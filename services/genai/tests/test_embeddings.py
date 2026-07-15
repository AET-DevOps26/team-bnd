"""Unit tests for the chunking and embedding module.

Embedding tests mock the client so no API key or network access is required,
mirroring the LLM-mocked tests for summarize/qa.
"""

import os
from unittest.mock import patch

import pytest

from app.embeddings import (
    Chunk,
    chunk_text,
    embed_chunks,
    embed_query,
    get_embedding_model_name,
    get_embeddings,
)


class _FakeEmbeddings:
    """Stand-in for a LangChain Embeddings client returning deterministic vectors."""

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        return [[float(len(t)), float(i)] for i, t in enumerate(texts)]

    def embed_query(self, text: str) -> list[float]:
        return [float(len(text)), 0.0]


# --- chunking ---


def test_chunk_text_carries_source_metadata():
    chunks = chunk_text("some text " * 300, "documents/report.pdf")

    assert len(chunks) > 1
    assert all(isinstance(c, Chunk) for c in chunks)
    assert all(c.object_key == "documents/report.pdf" for c in chunks)
    assert [c.chunk_index for c in chunks] == list(range(len(chunks)))


def test_chunk_text_blank_returns_empty():
    assert chunk_text("   \n  ", "k") == []


def test_chunk_text_respects_env_size_and_overlap():
    text = "abcdefghij" * 50  # 500 chars, no whitespace to split on

    with patch.dict(os.environ, {"CHUNK_SIZE": "100", "CHUNK_OVERLAP": "0"}):
        chunks = chunk_text(text, "k")

    assert len(chunks) == 5
    assert all(len(c.text) <= 100 for c in chunks)


def test_chunk_text_single_short_chunk():
    chunks = chunk_text("short doc", "k")

    assert len(chunks) == 1
    assert chunks[0].text == "short doc"
    assert chunks[0].chunk_index == 0


def test_chunk_text_rejects_invalid_chunk_size():
    with patch.dict(os.environ, {"CHUNK_SIZE": "abc"}):
        with pytest.raises(RuntimeError, match="CHUNK_SIZE"):
            chunk_text("some text to split", "k")


# --- provider selection ---


def test_get_embeddings_defaults_to_logos():
    with patch.dict(os.environ, {"LLM_API_KEY": "lg-test"}, clear=False):
        os.environ.pop("EMBEDDING_PROVIDER", None)
        client = get_embeddings()

    assert type(client).__name__ == "OpenAIEmbeddings"


def test_embedding_creds_default_to_provider_when_nothing_set():
    with patch("langchain_openai.OpenAIEmbeddings") as mock_emb, patch.dict(os.environ, {"EMBEDDING_PROVIDER": "logos"}, clear=False):
        for var in ("EMBEDDING_BASE_URL", "LLM_BASE_URL", "EMBEDDING_API_KEY", "LLM_API_KEY"):
            os.environ.pop(var, None)
        get_embeddings()

    kwargs = mock_emb.call_args.kwargs
    assert kwargs["base_url"] == "https://logos.aet.cit.tum.de/v1"
    assert kwargs["api_key"] == ""


def test_embedding_creds_reuse_llm_vars_when_no_override():
    env = {"EMBEDDING_PROVIDER": "logos", "LLM_BASE_URL": "http://llm.local/v1", "LLM_API_KEY": "lg-llm"}
    with patch("langchain_openai.OpenAIEmbeddings") as mock_emb, patch.dict(os.environ, env, clear=False):
        os.environ.pop("EMBEDDING_BASE_URL", None)
        os.environ.pop("EMBEDDING_API_KEY", None)
        get_embeddings()

    kwargs = mock_emb.call_args.kwargs
    assert kwargs["base_url"] == "http://llm.local/v1"
    assert kwargs["api_key"] == "lg-llm"


def test_embedding_specific_vars_override_llm_vars():
    env = {
        "EMBEDDING_PROVIDER": "openai",
        "LLM_BASE_URL": "http://llm.local/v1",
        "LLM_API_KEY": "lg-llm",
        "EMBEDDING_BASE_URL": "https://api.openai.com/v1",
        "EMBEDDING_API_KEY": "sk-emb",
    }
    with patch("langchain_openai.OpenAIEmbeddings") as mock_emb, patch.dict(os.environ, env):
        get_embeddings()

    kwargs = mock_emb.call_args.kwargs
    assert kwargs["base_url"] == "https://api.openai.com/v1"
    assert kwargs["api_key"] == "sk-emb"


def test_get_embeddings_ollama_selectable():
    with patch.dict(os.environ, {"EMBEDDING_PROVIDER": "ollama"}):
        client = get_embeddings()

    assert type(client).__name__ == "OllamaEmbeddings"


def test_get_embeddings_unknown_provider_raises():
    with patch.dict(os.environ, {"EMBEDDING_PROVIDER": "bogus"}):
        try:
            get_embeddings()
            raise AssertionError("expected ValueError")
        except ValueError as e:
            assert "bogus" in str(e)


def test_get_embedding_model_name_per_provider():
    with patch.dict(os.environ, {"EMBEDDING_PROVIDER": "logos"}, clear=False):
        os.environ.pop("EMBEDDING_MODEL", None)
        assert get_embedding_model_name() == "Qwen/Qwen3-Embedding-8B"

    with patch.dict(os.environ, {"EMBEDDING_PROVIDER": "openai"}, clear=False):
        os.environ.pop("EMBEDDING_MODEL", None)
        assert get_embedding_model_name() == "text-embedding-3-small"

    with patch.dict(os.environ, {"EMBEDDING_PROVIDER": "ollama", "EMBEDDING_MODEL": "custom-embed"}):
        assert get_embedding_model_name() == "custom-embed"


def test_get_embedding_model_name_unknown_provider_returns_unknown():
    with patch.dict(os.environ, {"EMBEDDING_PROVIDER": "bogus"}):
        assert get_embedding_model_name() == "unknown"


# --- embedding with a mocked client ---


def test_embed_chunks_returns_one_vector_per_chunk():
    chunks = [Chunk("k", 0, "alpha"), Chunk("k", 1, "beta beta")]

    with patch("app.embeddings.get_embeddings", return_value=_FakeEmbeddings()):
        vectors = embed_chunks(chunks)

    assert len(vectors) == len(chunks)
    assert vectors[0] == [5.0, 0.0]
    assert vectors[1] == [9.0, 1.0]


def test_embed_chunks_empty_input_skips_client():
    with patch("app.embeddings.get_embeddings", side_effect=AssertionError("must not call client")):
        assert embed_chunks([]) == []


def test_embed_query_returns_single_vector():
    with patch("app.embeddings.get_embeddings", return_value=_FakeEmbeddings()):
        vector = embed_query("hello")

    assert len(vector) == 2
    assert vector[1] == 0.0


class _RecordingEmbeddings:
    """Records the exact strings handed to the embedding client."""

    def __init__(self) -> None:
        self.queries: list[str] = []
        self.documents: list[str] = []

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        self.documents.extend(texts)
        return [[0.0] for _ in texts]

    def embed_query(self, text: str) -> list[float]:
        self.queries.append(text)
        return [0.0]


def test_embed_query_wraps_text_in_qwen_instruction():
    recorder = _RecordingEmbeddings()
    with (
        patch("app.embeddings.get_embeddings", return_value=recorder),
        patch("app.embeddings.get_embedding_model_name", return_value="Qwen/Qwen3-Embedding-8B"),
    ):
        embed_query("Streik")

    assert recorder.queries == ["Instruct: Given a web search query, retrieve relevant passages that answer the query\nQuery:Streik"]


def test_embed_query_uses_nomic_search_query_prefix():
    recorder = _RecordingEmbeddings()
    with (
        patch("app.embeddings.get_embeddings", return_value=recorder),
        patch("app.embeddings.get_embedding_model_name", return_value="nomic-embed-text"),
    ):
        embed_query("Streik")

    assert recorder.queries == ["search_query: Streik"]


def test_embed_query_is_plain_for_non_instruction_model():
    recorder = _RecordingEmbeddings()
    with (
        patch("app.embeddings.get_embeddings", return_value=recorder),
        patch("app.embeddings.get_embedding_model_name", return_value="text-embedding-3-small"),
    ):
        embed_query("Streik")

    assert recorder.queries == ["Streik"]


def test_embed_chunks_keeps_documents_plain_for_qwen():
    recorder = _RecordingEmbeddings()
    with (
        patch("app.embeddings.get_embeddings", return_value=recorder),
        patch("app.embeddings.get_embedding_model_name", return_value="Qwen/Qwen3-Embedding-8B"),
    ):
        embed_chunks([Chunk("k", 0, "plain document text")])

    assert recorder.documents == ["plain document text"]


def test_embed_chunks_uses_nomic_search_document_prefix():
    recorder = _RecordingEmbeddings()
    with (
        patch("app.embeddings.get_embeddings", return_value=recorder),
        patch("app.embeddings.get_embedding_model_name", return_value="nomic-embed-text"),
    ):
        embed_chunks([Chunk("k", 0, "plain document text")])

    assert recorder.documents == ["search_document: plain document text"]
