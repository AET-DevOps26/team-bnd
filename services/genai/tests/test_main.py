"""Integration-style tests for the FastAPI endpoints."""

from unittest.mock import patch

from fastapi.testclient import TestClient

from app.main import SERVICE_NAME, SERVICE_VERSION, app
from app.storage import ObjectNotFoundError, UnsupportedFileError

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
    # metrics is an operational endpoint, not part of the public API surface
    assert "/genai/metrics" not in paths


# --- metrics ---


def test_metrics_endpoint_returns_prometheus_text():
    response = client.get("/genai/metrics")
    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/plain")


def test_metrics_endpoint_records_http_requests():
    client.get("/genai/health")
    body = client.get("/genai/metrics").text
    assert "http_requests_total" in body
    assert 'handler="/genai/health"' in body


# --- summarize ---


def test_summarize_fetches_object_and_returns_summary():
    with (
        patch("app.main.fetch_text", return_value="Some document text."),
        patch("app.main.summarize", return_value=("Test summary.", "openai/gpt-oss-120b")),
    ):
        response = client.post("/genai/summarize", json={"objectKey": "uploads/abc/report.txt"})

    assert response.status_code == 200
    body = response.json()
    assert body["summary"] == "Test summary."
    assert body["modelUsed"] == "openai/gpt-oss-120b"


def test_summarize_passes_fetched_text_to_model():
    captured = {}

    def fake_summarize(content):
        captured["content"] = content
        return ("ok", "test-model")

    with (
        patch("app.main.fetch_text", return_value="Fetched body."),
        patch("app.main.summarize", side_effect=fake_summarize),
    ):
        client.post("/genai/summarize", json={"objectKey": "k"})

    assert captured["content"] == "Fetched body."


def test_summarize_returns_404_for_missing_object():
    with patch("app.main.fetch_text", side_effect=ObjectNotFoundError("missing")):
        response = client.post("/genai/summarize", json={"objectKey": "missing"})
    assert response.status_code == 404


def test_summarize_returns_415_for_unsupported_file():
    with patch("app.main.fetch_text", side_effect=UnsupportedFileError("not text")):
        response = client.post("/genai/summarize", json={"objectKey": "image.png"})
    assert response.status_code == 415


def test_summarize_returns_422_for_empty_document():
    with patch("app.main.fetch_text", return_value="   "):
        response = client.post("/genai/summarize", json={"objectKey": "blank.txt"})
    assert response.status_code == 422


def test_summarize_rejects_missing_object_key():
    response = client.post("/genai/summarize", json={})
    assert response.status_code == 422


# --- extract ---


def test_extract_returns_entities_and_model():
    fake_entities = [
        {"name": "Ada Lovelace", "type": "PERSON", "confidence": 0.97},
        {"name": "1843", "type": "DATE", "confidence": 0.91},
    ]
    with (
        patch("app.main.fetch_text", return_value="Ada Lovelace worked in 1843."),
        patch("app.main.extract_entities", return_value=(fake_entities, "openai/gpt-oss-120b")),
    ):
        response = client.post("/genai/extract", json={"objectKey": "uploads/abc/bio.txt"})

    assert response.status_code == 200
    body = response.json()
    assert body["modelUsed"] == "openai/gpt-oss-120b"
    assert len(body["entities"]) == 2
    assert body["entities"][0]["name"] == "Ada Lovelace"
    assert body["entities"][0]["type"] == "PERSON"
    assert body["entities"][0]["confidence"] == 0.97


def test_extract_returns_empty_entities_list():
    with (
        patch("app.main.fetch_text", return_value="The quick brown fox."),
        patch("app.main.extract_entities", return_value=([], "openai/gpt-oss-120b")),
    ):
        response = client.post("/genai/extract", json={"objectKey": "k"})

    assert response.status_code == 200
    assert response.json()["entities"] == []


def test_extract_returns_404_for_missing_object():
    with patch("app.main.fetch_text", side_effect=ObjectNotFoundError("missing")):
        response = client.post("/genai/extract", json={"objectKey": "missing"})
    assert response.status_code == 404


def test_extract_rejects_missing_object_key():
    response = client.post("/genai/extract", json={})
    assert response.status_code == 422


# --- ask ---


def test_ask_returns_answer_and_sources():
    with (
        patch("app.main.fetch_text", return_value="doc body"),
        patch(
            "app.main.answer_question",
            return_value=("The answer is 42.", ["key-1"], "openai/gpt-oss-120b"),
        ),
    ):
        response = client.post(
            "/genai/ask",
            json={"question": "What is the answer?", "objectKeys": ["key-1", "key-2"]},
        )

    assert response.status_code == 200
    body = response.json()
    assert body["answer"] == "The answer is 42."
    assert body["sourceObjectKeys"] == ["key-1"]
    assert body["modelUsed"] == "openai/gpt-oss-120b"


def test_ask_fetches_each_object_and_passes_documents():
    captured = {}

    def fake_answer(question, object_keys, documents):
        captured["documents"] = documents
        return ("Revenue grew 15%.", [d["id"] for d in documents], "openai/gpt-oss-120b")

    with (
        patch("app.main.fetch_text", side_effect=lambda k: f"content of {k}"),
        patch("app.main.answer_question", side_effect=fake_answer),
    ):
        response = client.post(
            "/genai/ask",
            json={"question": "What was revenue growth?", "objectKeys": ["key-1", "key-2"]},
        )

    assert response.status_code == 200
    assert captured["documents"] == [
        {"id": "key-1", "content": "content of key-1"},
        {"id": "key-2", "content": "content of key-2"},
    ]


def test_ask_skips_unreadable_objects():
    def flaky_fetch(key):
        if key == "bad":
            raise ObjectNotFoundError(key)
        return "good content"

    captured = {}

    def fake_answer(question, object_keys, documents):
        captured["documents"] = documents
        return ("answer", [d["id"] for d in documents], "test-model")

    with (
        patch("app.main.fetch_text", side_effect=flaky_fetch),
        patch("app.main.answer_question", side_effect=fake_answer),
    ):
        response = client.post(
            "/genai/ask",
            json={"question": "Q?", "objectKeys": ["good", "bad"]},
        )

    assert response.status_code == 200
    assert captured["documents"] == [{"id": "good", "content": "good content"}]
    assert response.json()["sourceObjectKeys"] == ["good"]


def test_ask_rejects_empty_question():
    response = client.post("/genai/ask", json={"question": "  ", "objectKeys": ["key-1"]})
    assert response.status_code == 422
