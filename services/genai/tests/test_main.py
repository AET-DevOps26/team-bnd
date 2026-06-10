"""Integration-style tests for the FastAPI endpoints."""

from unittest.mock import patch

from fastapi.testclient import TestClient

from app.main import SERVICE_NAME, SERVICE_VERSION, app

client = TestClient(app)


# --- health / hello ---


def test_health_returns_ok_with_identity():
    response = client.get("/genai/health")
    assert response.status_code == 200
    body = response.json()
    assert body == {
        "status": "ok",
        "service": SERVICE_NAME,
        "version": SERVICE_VERSION,
    }


def test_hello_returns_plain_text_greeting():
    response = client.get("/genai/hello")
    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/plain")
    assert "Alexandria GenAI" in response.text


def test_openapi_schema_exposes_all_endpoints():
    response = client.get("/openapi.json")
    assert response.status_code == 200
    paths = response.json()["paths"]
    assert "/genai/health" in paths
    assert "/genai/hello" in paths
    assert "/genai/summarize" in paths
    assert "/genai/extract" in paths
    assert "/genai/ask" in paths


# --- summarize ---


def test_summarize_returns_summary_and_model():
    with patch("app.main.summarize", return_value=("Test summary.", "openai/gpt-oss-120b")):
        response = client.post("/genai/summarize", json={"content": "Some document text."})

    assert response.status_code == 200
    body = response.json()
    assert body["summary"] == "Test summary."
    assert body["modelUsed"] == "openai/gpt-oss-120b"


def test_summarize_rejects_empty_content():
    response = client.post("/genai/summarize", json={"content": "   "})
    assert response.status_code == 422


def test_summarize_rejects_missing_content():
    response = client.post("/genai/summarize", json={})
    assert response.status_code == 422


# --- extract ---


def test_extract_returns_entities_and_model():
    fake_entities = [
        {"name": "Ada Lovelace", "type": "PERSON", "confidence": 0.97},
        {"name": "1843", "type": "DATE", "confidence": 0.91},
    ]
    with patch("app.main.extract_entities", return_value=(fake_entities, "openai/gpt-oss-120b")):
        response = client.post("/genai/extract", json={"content": "Ada Lovelace worked in 1843."})

    assert response.status_code == 200
    body = response.json()
    assert body["modelUsed"] == "openai/gpt-oss-120b"
    assert len(body["entities"]) == 2
    assert body["entities"][0]["name"] == "Ada Lovelace"
    assert body["entities"][0]["type"] == "PERSON"
    assert body["entities"][0]["confidence"] == 0.97


def test_extract_returns_empty_entities_list():
    with patch("app.main.extract_entities", return_value=([], "openai/gpt-oss-120b")):
        response = client.post("/genai/extract", json={"content": "The quick brown fox."})

    assert response.status_code == 200
    assert response.json()["entities"] == []


def test_extract_rejects_empty_content():
    response = client.post("/genai/extract", json={"content": ""})
    assert response.status_code == 422


# --- ask ---


def test_ask_returns_answer_and_sources():
    with patch(
        "app.main.answer_question",
        return_value=("The answer is 42.", ["doc-1"], "openai/gpt-oss-120b"),
    ):
        response = client.post(
            "/genai/ask",
            json={"question": "What is the answer?", "documentIds": ["doc-1", "doc-2"]},
        )

    assert response.status_code == 200
    body = response.json()
    assert body["answer"] == "The answer is 42."
    assert body["sourceDocumentIds"] == ["doc-1"]
    assert body["modelUsed"] == "openai/gpt-oss-120b"


def test_ask_passes_document_contents_when_provided():
    doc_contents = [{"id": "doc-1", "content": "Revenue grew 15% this year."}]

    captured = {}

    def fake_answer(question, document_ids, document_contents):
        captured["document_contents"] = document_contents
        return ("Revenue grew 15%.", ["doc-1"], "openai/gpt-oss-120b")

    with patch("app.main.answer_question", side_effect=fake_answer):
        response = client.post(
            "/genai/ask",
            json={
                "question": "What was revenue growth?",
                "documentIds": ["doc-1"],
                "documentContents": doc_contents,
            },
        )

    assert response.status_code == 200
    assert captured["document_contents"] == doc_contents


def test_ask_works_without_document_contents():
    with patch(
        "app.main.answer_question",
        return_value=("Some answer.", ["doc-1"], "openai/gpt-oss-120b"),
    ):
        response = client.post(
            "/genai/ask",
            json={"question": "Anything?", "documentIds": ["doc-1"]},
        )

    assert response.status_code == 200


def test_ask_rejects_empty_question():
    response = client.post("/genai/ask", json={"question": "  ", "documentIds": ["doc-1"]})
    assert response.status_code == 422
