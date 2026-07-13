"""Tests that the custom GenAI metrics are recorded and exposed.

These drive the real summarize()/embed paths (with a mocked model/embedder) so
the run_model_call wrapper actually records, then scrape /genai/metrics to check
the series show up with the expected labels.
"""

import os
from unittest.mock import patch

from fastapi.testclient import TestClient
from langchain_core.messages import AIMessage
from langchain_core.runnables import RunnableLambda

from app.main import app

client = TestClient(app)


def _scrape() -> str:
    return client.get("/genai/metrics").text


def test_chat_call_records_model_request_with_served_model_label():
    fake_llm = RunnableLambda(lambda _: AIMessage(content="a summary", response_metadata={"model_name": "served-metric-model"}))

    with patch("app.summarize.get_llm", return_value=fake_llm), patch("app.summarize.get_model_name", return_value="configured"):
        from app.summarize import summarize

        _, model = summarize("some document")

    assert model == "served-metric-model"
    body = _scrape()
    assert "genai_model_requests_total" in body
    assert 'operation="chat"' in body
    assert 'model="served-metric-model"' in body
    assert 'status="ok"' in body
    assert "genai_model_request_duration_seconds" in body


def test_embedding_call_records_embedding_operation():
    from app.embeddings import Chunk

    class _FakeEmbeddings:
        def embed_documents(self, texts):
            return [[0.1, 0.2] for _ in texts]

    with (
        patch("app.embeddings.get_embeddings", return_value=_FakeEmbeddings()),
        patch("app.embeddings.get_embedding_model_name", return_value="embed-metric-model"),
    ):
        from app.embeddings import embed_chunks

        embed_chunks([Chunk("k", 0, "text")])

    body = _scrape()
    assert 'operation="embedding"' in body
    assert 'model="embed-metric-model"' in body


def test_truncation_is_counted_on_the_endpoint():
    with (
        patch.dict(os.environ, {"MAX_DOCUMENT_CHARS": "40"}),
        patch("app.main.fetch_text", return_value="word " * 100),
        patch("app.main.summarize", return_value=("s", "m")),
    ):
        response = client.post("/genai/summarize", json={"objectKey": "k"})

    assert response.status_code == 200
    body = _scrape()
    assert "genai_document_truncated_total" in body
    assert 'endpoint="summarize"' in body
