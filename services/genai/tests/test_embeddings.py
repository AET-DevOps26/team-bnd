"""Unit tests for the chunking and embedding module.

Embedding tests mock the client so no API key or network access is required,
mirroring the LLM-mocked tests for summarize/qa.
"""

import os
from unittest.mock import patch

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


# --- provider selection ---


def test_get_embeddings_defaults_to_logos():
    with patch.dict(os.environ, {"LLM_API_KEY": "lg-test"}, clear=False):
        os.environ.pop("EMBEDDING_PROVIDER", None)
        client = get_embeddings()

    assert type(client).__name__ == "OpenAIEmbeddings"


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

    assert vector == [5.0, 0.0]
