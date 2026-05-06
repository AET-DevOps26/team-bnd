from fastapi.testclient import TestClient

from app.main import SERVICE_NAME, SERVICE_VERSION, app

client = TestClient(app)


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


def test_openapi_schema_exposes_both_endpoints():
    response = client.get("/openapi.json")
    assert response.status_code == 200
    paths = response.json()["paths"]
    assert "/genai/health" in paths
    assert "/genai/hello" in paths
