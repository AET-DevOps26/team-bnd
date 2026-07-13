"""Chunking and embedding for the RAG pipeline.

Splits extracted document text into overlapping chunks and turns each chunk
into an embedding vector via a provider-configurable client, mirroring the
provider selection in app/llm.py. Indexing and retrieval both build on this
module; it works on plain text and knows nothing about object storage or
Weaviate.

Chunking:
  CHUNK_SIZE      characters per chunk (default 1000)
  CHUNK_OVERLAP   character overlap between chunks (default 200)

Embeddings:
  EMBEDDING_PROVIDER  "logos" (default) | "openai" | "ollama"
  EMBEDDING_MODEL     model id (provider-specific default)

The logos/openai providers default to the LLM gateway credentials
(LLM_BASE_URL, LLM_API_KEY) because embeddings usually run on the same
OpenAI-compatible endpoint as the chat model; EMBEDDING_BASE_URL /
EMBEDDING_API_KEY override that when embeddings should run elsewhere. Ollama
reuses OLLAMA_BASE_URL.

Vector dimensions differ per provider/model (Qwen3-Embedding-8B, OpenAI's
text-embedding-3-small and the Ollama models all emit different sizes), so the
Weaviate collection must not pin a fixed dimension -- it stores whatever the
configured embedder produces.
"""

import os
from dataclasses import dataclass

from langchain_core.embeddings import Embeddings
from langchain_text_splitters import RecursiveCharacterTextSplitter

from app.env import float_env, int_env
from app.model_calls import run_model_call

_LOGOS_BASE_URL = "https://logos.aet.cit.tum.de/v1"
_OPENAI_BASE_URL = "https://api.openai.com/v1"

_DEFAULT_LOGOS_MODEL = "Qwen/Qwen3-Embedding-8B"
_DEFAULT_OPENAI_MODEL = "text-embedding-3-small"
_DEFAULT_OLLAMA_MODEL = "nomic-embed-text"

_DEFAULT_CHUNK_SIZE = 1000
_DEFAULT_CHUNK_OVERLAP = 200

# Quicker than chat but hit the same gateway, so same timeout/retry guardrails.
_DEFAULT_TIMEOUT_SECONDS = 30.0
_DEFAULT_MAX_RETRIES = 2


def get_embedding_provider() -> str:
    """Return the configured embedding provider ("logos", "openai", or "ollama")."""
    return os.getenv("EMBEDDING_PROVIDER", "logos").lower()


@dataclass(frozen=True)
class Chunk:
    """A slice of a document, with enough metadata to link back to its source."""

    object_key: str
    chunk_index: int
    text: str


def chunk_text(text: str, object_key: str) -> list[Chunk]:
    """Split a document's text into overlapping chunks tagged with their source.

    Chunk size and overlap come from CHUNK_SIZE / CHUNK_OVERLAP. Returns an empty
    list for blank input so callers can treat "nothing to index" uniformly.
    """
    if not text.strip():
        return []

    splitter = RecursiveCharacterTextSplitter(
        chunk_size=int_env("CHUNK_SIZE", _DEFAULT_CHUNK_SIZE, minimum=1),
        chunk_overlap=int_env("CHUNK_OVERLAP", _DEFAULT_CHUNK_OVERLAP, minimum=0),
    )
    pieces = splitter.split_text(text)
    return [Chunk(object_key=object_key, chunk_index=i, text=piece) for i, piece in enumerate(pieces)]


def get_embeddings() -> Embeddings:
    """Return a configured embedding client based on environment variables."""
    provider = os.getenv("EMBEDDING_PROVIDER", "logos").lower()

    if provider in ("logos", "openai"):
        from langchain_openai import OpenAIEmbeddings

        if provider == "logos":
            default_base_url = _LOGOS_BASE_URL
            model = os.getenv("EMBEDDING_MODEL", _DEFAULT_LOGOS_MODEL)
        else:
            default_base_url = _OPENAI_BASE_URL
            model = os.getenv("EMBEDDING_MODEL", _DEFAULT_OPENAI_MODEL)

        # Embeddings can run on a different endpoint than the chat model. Prefer
        # the embedding-specific vars, fall back to the chat client's, then the
        # provider default, so existing single-provider setups keep working.
        base_url = os.getenv("EMBEDDING_BASE_URL") or os.getenv("LLM_BASE_URL", default_base_url)
        api_key = os.getenv("EMBEDDING_API_KEY") or os.getenv("LLM_API_KEY", "")

        # check_embedding_ctx_length forces a tiktoken tokenization pass that
        # assumes an OpenAI model. The Logos-hosted Qwen model isn't one, so we
        # disable it and rely on our own chunking to keep inputs within range.
        return OpenAIEmbeddings(
            base_url=base_url,
            api_key=api_key,
            model=model,
            check_embedding_ctx_length=False,
            timeout=float_env("EMBEDDING_TIMEOUT_SECONDS", _DEFAULT_TIMEOUT_SECONDS, minimum=0.0),
            max_retries=int_env("EMBEDDING_MAX_RETRIES", _DEFAULT_MAX_RETRIES, minimum=0),
        )

    if provider == "ollama":
        from langchain_ollama import OllamaEmbeddings

        base_url = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
        model = os.getenv("EMBEDDING_MODEL", _DEFAULT_OLLAMA_MODEL)
        return OllamaEmbeddings(base_url=base_url, model=model)

    raise ValueError(f"Unknown EMBEDDING_PROVIDER '{provider}'. Use 'logos', 'openai', or 'ollama'.")


def get_embedding_model_name() -> str:
    """Return the configured embedding model id for use in API responses."""
    provider = os.getenv("EMBEDDING_PROVIDER", "logos").lower()
    if provider == "logos":
        return os.getenv("EMBEDDING_MODEL", _DEFAULT_LOGOS_MODEL)
    if provider == "openai":
        return os.getenv("EMBEDDING_MODEL", _DEFAULT_OPENAI_MODEL)
    if provider == "ollama":
        return os.getenv("EMBEDDING_MODEL", _DEFAULT_OLLAMA_MODEL)
    return "unknown"


def embed_chunks(chunks: list[Chunk]) -> list[list[float]]:
    """Embed a list of chunks, returning one vector per chunk in the same order."""
    if not chunks:
        return []
    vectors, _ = run_model_call(
        lambda: get_embeddings().embed_documents([chunk.text for chunk in chunks]),
        operation="embedding",
        provider=get_embedding_provider(),
        fallback_model=get_embedding_model_name(),
    )
    return vectors


def embed_query(text: str) -> list[float]:
    """Embed a single query string into one vector."""
    vector, _ = run_model_call(
        lambda: get_embeddings().embed_query(text),
        operation="embedding",
        provider=get_embedding_provider(),
        fallback_model=get_embedding_model_name(),
    )
    return vector
