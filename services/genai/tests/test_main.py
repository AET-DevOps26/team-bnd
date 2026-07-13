"""Integration-style tests for the FastAPI endpoints."""

import os
from unittest.mock import patch

from fastapi.testclient import TestClient

from app.errors import ProviderError, ProviderTimeoutError
from app.extract import DEFAULT_MAX_ENTITIES
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
    response = client.get("/genai/openapi.json")
    assert response.status_code == 200
    paths = response.json()["paths"]
    assert "/genai/health" in paths
    assert "/genai/hello" in paths
    assert "/genai/summarize" in paths
    assert "/genai/extract" in paths
    assert "/genai/tag" in paths
    assert "/genai/ask" in paths
    assert "/genai/search" in paths
    assert "/genai/index" in paths
    assert "/genai/index/{object_key}" in paths
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


def test_metrics_endpoint_exposes_version_info():
    body = client.get("/genai/metrics").text
    assert f'application_version{{application="{SERVICE_NAME}",version="{SERVICE_VERSION}"}} 1.0' in body


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


def test_extract_forwards_max_entities_to_extractor():
    with (
        patch("app.main.fetch_text", return_value="some text"),
        patch("app.main.extract_entities", return_value=([], "test-model")) as mock_extract,
    ):
        response = client.post("/genai/extract", json={"objectKey": "k", "maxEntities": 5})

    assert response.status_code == 200
    assert mock_extract.call_args.args[1] == 5


def test_extract_defaults_max_entities_when_omitted():
    with (
        patch("app.main.fetch_text", return_value="some text"),
        patch("app.main.extract_entities", return_value=([], "test-model")) as mock_extract,
    ):
        response = client.post("/genai/extract", json={"objectKey": "k"})

    assert response.status_code == 200
    assert mock_extract.call_args.args[1] == DEFAULT_MAX_ENTITIES


def test_extract_rejects_out_of_range_max_entities():
    for bad in (0, 101):
        response = client.post("/genai/extract", json={"objectKey": "k", "maxEntities": bad})
        assert response.status_code == 422


# --- tag ---


def test_tag_returns_tags_and_model():
    with (
        patch("app.main.fetch_text", return_value="A paper about climate policy."),
        patch("app.main.generate_tags", return_value=(["climate policy", "environment"], "openai/gpt-oss-120b")),
    ):
        response = client.post("/genai/tag", json={"objectKey": "uploads/abc/paper.txt"})

    assert response.status_code == 200
    body = response.json()
    assert body["modelUsed"] == "openai/gpt-oss-120b"
    assert body["tags"] == ["climate policy", "environment"]


def test_tag_passes_fetched_text_to_model():
    captured = {}

    def fake_generate_tags(content, known_tags=None):
        captured["content"] = content
        return (["topic"], "test-model")

    with (
        patch("app.main.fetch_text", return_value="Fetched body."),
        patch("app.main.generate_tags", side_effect=fake_generate_tags),
    ):
        client.post("/genai/tag", json={"objectKey": "k"})

    assert captured["content"] == "Fetched body."


def test_tag_forwards_known_tags_to_generator():
    captured = {}

    def fake_generate_tags(content, known_tags=None):
        captured["known_tags"] = known_tags
        return (["finance"], "test-model")

    with (
        patch("app.main.fetch_text", return_value="text"),
        patch("app.main.generate_tags", side_effect=fake_generate_tags),
    ):
        response = client.post("/genai/tag", json={"objectKey": "k", "knownTags": ["finance", "markets"]})

    assert response.status_code == 200
    assert captured["known_tags"] == ["finance", "markets"]


def test_tag_returns_empty_tags_list():
    with (
        patch("app.main.fetch_text", return_value="Some text."),
        patch("app.main.generate_tags", return_value=([], "openai/gpt-oss-120b")),
    ):
        response = client.post("/genai/tag", json={"objectKey": "k"})

    assert response.status_code == 200
    assert response.json()["tags"] == []


def test_tag_returns_404_for_missing_object():
    with patch("app.main.fetch_text", side_effect=ObjectNotFoundError("missing")):
        response = client.post("/genai/tag", json={"objectKey": "missing"})
    assert response.status_code == 404


def test_tag_returns_415_for_unsupported_file():
    with patch("app.main.fetch_text", side_effect=UnsupportedFileError("not text")):
        response = client.post("/genai/tag", json={"objectKey": "image.png"})
    assert response.status_code == 415


def test_tag_returns_422_for_empty_document():
    with patch("app.main.fetch_text", return_value="   "):
        response = client.post("/genai/tag", json={"objectKey": "blank.txt"})
    assert response.status_code == 422


def test_tag_rejects_missing_object_key():
    response = client.post("/genai/tag", json={})
    assert response.status_code == 422


# --- ask ---


def test_ask_returns_answer_with_citations():
    from app.qa import AnswerResult, Citation

    result = AnswerResult(
        "The answer is 42.",
        [Citation(marker=1, object_key="key-1", snippet="the meaning of life")],
        "openai/gpt-oss-120b",
    )
    with patch("app.main.answer_question", return_value=result):
        response = client.post(
            "/genai/ask",
            json={"question": "What is the answer?", "objectKeys": ["key-1", "key-2"]},
        )

    assert response.status_code == 200
    body = response.json()
    assert body["answer"] == "The answer is 42."
    assert body["citations"] == [{"marker": 1, "objectKey": "key-1", "snippet": "the meaning of life"}]
    assert body["modelUsed"] == "openai/gpt-oss-120b"


def test_ask_passes_question_and_object_keys_to_retrieval():
    from app.qa import AnswerResult

    captured = {}

    def fake_answer(question, object_keys):
        captured["question"] = question
        captured["object_keys"] = object_keys
        return AnswerResult("answer", [], "test-model")

    with patch("app.main.answer_question", side_effect=fake_answer):
        response = client.post(
            "/genai/ask",
            json={"question": "What was revenue growth?", "objectKeys": ["key-1", "key-2"]},
        )

    assert response.status_code == 200
    assert captured["question"] == "What was revenue growth?"
    assert captured["object_keys"] == ["key-1", "key-2"]


def test_ask_rejects_empty_question():
    response = client.post("/genai/ask", json={"question": "  ", "objectKeys": ["key-1"]})
    assert response.status_code == 422


# --- search ---


def test_search_returns_ranked_documents_with_context():
    results = [
        {"object_key": "uploads/a/report.txt", "score": 0.91, "snippet": "revenue grew", "chunk_index": 2},
        {"object_key": "uploads/b/memo.txt", "score": 0.80, "snippet": "cost review", "chunk_index": 0},
    ]
    with patch("app.main.search_documents", return_value=(results, "Qwen/Qwen3-Embedding-8B")):
        response = client.post(
            "/genai/search",
            json={"query": "how did revenue change", "objectKeys": ["uploads/a/report.txt", "uploads/b/memo.txt"]},
        )

    assert response.status_code == 200
    body = response.json()
    assert body["embeddingModel"] == "Qwen/Qwen3-Embedding-8B"
    assert len(body["results"]) == 2
    first = body["results"][0]
    assert first["objectKey"] == "uploads/a/report.txt"
    assert first["score"] == 0.91
    assert first["snippet"] == "revenue grew"
    assert first["chunkIndex"] == 2


def test_search_passes_query_keys_and_limit_to_module():
    captured = {}

    def fake_search(query, object_keys, limit):
        captured["query"] = query
        captured["object_keys"] = object_keys
        captured["limit"] = limit
        return ([], "m")

    with patch("app.main.search_documents", side_effect=fake_search):
        client.post("/genai/search", json={"query": "climate", "objectKeys": ["k1", "k2"], "limit": 3})

    assert captured["query"] == "climate"
    assert captured["object_keys"] == ["k1", "k2"]
    assert captured["limit"] == 3


def test_search_defaults_limit_when_omitted():
    captured = {}

    def fake_search(query, object_keys, limit):
        captured["limit"] = limit
        return ([], "m")

    with patch("app.main.search_documents", side_effect=fake_search):
        client.post("/genai/search", json={"query": "q", "objectKeys": ["k1"]})

    assert captured["limit"] == 10


def test_search_returns_empty_results():
    with patch("app.main.search_documents", return_value=([], "m")):
        response = client.post("/genai/search", json={"query": "nothing matches", "objectKeys": ["k1"]})

    assert response.status_code == 200
    assert response.json()["results"] == []


def test_search_rejects_empty_query():
    response = client.post("/genai/search", json={"query": "  ", "objectKeys": ["k1"]})
    assert response.status_code == 422


def test_search_rejects_missing_query():
    response = client.post("/genai/search", json={"objectKeys": ["k1"]})
    assert response.status_code == 422


def test_search_rejects_out_of_range_limit():
    response = client.post("/genai/search", json={"query": "q", "objectKeys": ["k1"], "limit": 0})
    assert response.status_code == 422


def test_search_rejects_limit_above_max():
    response = client.post("/genai/search", json={"query": "q", "objectKeys": ["k1"], "limit": 51})
    assert response.status_code == 422


def test_search_rejects_over_long_query():
    response = client.post(
        "/genai/search",
        json={"query": "a" * 1501, "objectKeys": ["k1"]},
    )
    assert response.status_code == 422


# --- index ---


def test_index_chunks_embeds_and_stores_document():
    captured = {}

    def fake_index(object_key, chunks, vectors):
        captured["object_key"] = object_key
        captured["num_chunks"] = len(chunks)
        return len(chunks)

    fake_chunks = [object(), object(), object()]

    with (
        patch("app.main.fetch_text", return_value="a long document body"),
        patch("app.main.chunk_text", return_value=fake_chunks),
        patch("app.main.embed_chunks", return_value=[[0.1], [0.2], [0.3]]),
        patch("app.main.index_chunks", side_effect=fake_index),
        patch("app.main.get_embedding_model_name", return_value="Qwen/Qwen3-Embedding-8B"),
    ):
        response = client.post("/genai/index", json={"objectKey": "uploads/abc/report.txt"})

    assert response.status_code == 200
    body = response.json()
    assert body == {
        "objectKey": "uploads/abc/report.txt",
        "chunksIndexed": 3,
        "embeddingModel": "Qwen/Qwen3-Embedding-8B",
    }
    assert captured["object_key"] == "uploads/abc/report.txt"
    assert captured["num_chunks"] == 3


def test_index_returns_404_for_missing_object():
    with patch("app.main.fetch_text", side_effect=ObjectNotFoundError("missing")):
        response = client.post("/genai/index", json={"objectKey": "missing"})
    assert response.status_code == 404


def test_index_returns_415_for_unsupported_file():
    with patch("app.main.fetch_text", side_effect=UnsupportedFileError("not text")):
        response = client.post("/genai/index", json={"objectKey": "image.png"})
    assert response.status_code == 415


def test_index_returns_422_for_empty_document():
    with patch("app.main.fetch_text", return_value="   "):
        response = client.post("/genai/index", json={"objectKey": "blank.txt"})
    assert response.status_code == 422


def test_index_rejects_missing_object_key():
    response = client.post("/genai/index", json={})
    assert response.status_code == 422


# --- delete index ---


def test_delete_index_removes_chunks_for_object_key():
    with patch("app.main.delete_document", return_value=4) as mock_delete:
        response = client.delete("/genai/index/uploads/abc/report.txt")

    assert response.status_code == 200
    assert response.json() == {"objectKey": "uploads/abc/report.txt", "chunksDeleted": 4}
    mock_delete.assert_called_once_with("uploads/abc/report.txt")


def test_delete_index_is_idempotent_for_unindexed_document():
    with patch("app.main.delete_document", return_value=0):
        response = client.delete("/genai/index/never-indexed.txt")

    assert response.status_code == 200
    assert response.json()["chunksDeleted"] == 0


# --- error schema (shared {code, message, fieldErrors}) ---


def _assert_shared_error_shape(body: dict):
    assert set(body) == {"code", "message", "fieldErrors"}
    assert isinstance(body["code"], str)
    assert isinstance(body["message"], str)
    assert isinstance(body["fieldErrors"], list)


def test_document_error_uses_shared_schema():
    with patch("app.main.fetch_text", side_effect=ObjectNotFoundError("missing")):
        response = client.post("/genai/summarize", json={"objectKey": "missing"})

    assert response.status_code == 404
    body = response.json()
    _assert_shared_error_shape(body)
    assert body["code"] == "not_found"
    assert body["fieldErrors"] == []


def test_unsupported_file_error_uses_shared_schema():
    with patch("app.main.fetch_text", side_effect=UnsupportedFileError("not text")):
        response = client.post("/genai/summarize", json={"objectKey": "image.png"})

    assert response.status_code == 415
    assert response.json()["code"] == "unsupported_media_type"


def test_validation_error_uses_shared_schema_with_field_errors():
    response = client.post("/genai/summarize", json={})

    assert response.status_code == 422
    body = response.json()
    _assert_shared_error_shape(body)
    assert body["code"] == "validation_error"
    assert any(fe["field"] == "objectKey" for fe in body["fieldErrors"])


def test_validation_error_keeps_field_named_like_a_location_marker():
    # loc is ("body", "query"); only the leading "body" marker should be dropped.
    response = client.post("/genai/search", json={"query": "  ", "objectKeys": ["k1"]})

    assert response.status_code == 422
    assert any(fe["field"] == "query" for fe in response.json()["fieldErrors"])


def test_summarize_maps_provider_failure_to_502():
    with (
        patch("app.main.fetch_text", return_value="document body"),
        patch("app.main.summarize", side_effect=ProviderError()),
    ):
        response = client.post("/genai/summarize", json={"objectKey": "k"})

    assert response.status_code == 502
    body = response.json()
    _assert_shared_error_shape(body)
    assert body["code"] == "provider_error"


def test_summarize_maps_provider_timeout_to_504():
    with (
        patch("app.main.fetch_text", return_value="document body"),
        patch("app.main.summarize", side_effect=ProviderTimeoutError()),
    ):
        response = client.post("/genai/summarize", json={"objectKey": "k"})

    assert response.status_code == 504
    assert response.json()["code"] == "provider_timeout"


def test_unhandled_error_becomes_structured_500():
    lenient = TestClient(app, raise_server_exceptions=False)
    with (
        patch("app.main.fetch_text", return_value="document body"),
        patch("app.main.summarize", side_effect=RuntimeError("boom")),
    ):
        response = lenient.post("/genai/summarize", json={"objectKey": "k"})

    assert response.status_code == 500
    body = response.json()
    _assert_shared_error_shape(body)
    assert body["code"] == "internal_error"


# --- document bounding (truncated flag) ---


def test_summarize_reports_not_truncated_for_small_document():
    with (
        patch("app.main.fetch_text", return_value="a short document"),
        patch("app.main.summarize", return_value=("summary", "model")),
    ):
        response = client.post("/genai/summarize", json={"objectKey": "k"})

    assert response.status_code == 200
    assert response.json()["truncated"] is False


def test_summarize_truncates_oversized_document_and_flags_it():
    captured = {}

    def fake_summarize(content):
        captured["length"] = len(content)
        return ("summary", "model")

    with (
        patch.dict(os.environ, {"MAX_DOCUMENT_CHARS": "50"}),
        patch("app.main.fetch_text", return_value="word " * 100),
        patch("app.main.summarize", side_effect=fake_summarize),
    ):
        response = client.post("/genai/summarize", json={"objectKey": "k"})

    assert response.status_code == 200
    assert response.json()["truncated"] is True
    assert captured["length"] <= 50


def test_extract_and_tag_expose_truncated_flag():
    with (
        patch("app.main.fetch_text", return_value="short"),
        patch("app.main.extract_entities", return_value=([], "model")),
        patch("app.main.generate_tags", return_value=([], "model")),
    ):
        extract_response = client.post("/genai/extract", json={"objectKey": "k"})
        tag_response = client.post("/genai/tag", json={"objectKey": "k"})

    assert extract_response.json()["truncated"] is False
    assert tag_response.json()["truncated"] is False


# --- OpenAPI error schema ---


def test_openapi_uses_shared_error_response_schema():
    schema = client.get("/genai/openapi.json").json()
    components = schema["components"]["schemas"]

    assert "ErrorResponse" in components
    assert "FieldError" in components
    assert "HTTPValidationError" not in components
    # summarize should advertise the provider-error contract
    summarize_responses = schema["paths"]["/genai/summarize"]["post"]["responses"]
    assert summarize_responses["502"]["content"]["application/json"]["schema"]["$ref"] == "#/components/schemas/ErrorResponse"
