from fastapi import FastAPI, HTTPException
from fastapi.responses import PlainTextResponse
from prometheus_fastapi_instrumentator import Instrumentator
from pydantic import BaseModel

from app.extract import extract_entities
from app.qa import answer_question
from app.storage import ObjectNotFoundError, UnsupportedFileError, fetch_text
from app.summarize import summarize

SERVICE_NAME = "alexandria-genai"
SERVICE_VERSION = "0.0.1"

app = FastAPI(
    title="Alexandria GenAI",
    description="GenAI microservice for the Alexandria document platform.",
    version=SERVICE_VERSION,
    openapi_tags=[
        {"name": "health", "description": "Health check endpoints"},
        {"name": "hello", "description": "Hello endpoints"},
        {"name": "ai", "description": "AI-powered document processing"},
    ],
    servers=[{"url": "/"}],
)

# should_group_untemplated rolls unmatched URLs (404s, port scans) into one
# series to bound label cardinality. The endpoint is for in-network Prometheus
# scraping only, it is intentionally left out of the Traefik/ingress allow-list.
Instrumentator(should_group_untemplated=True).instrument(app).expose(
    app,
    endpoint="/genai/metrics",
    include_in_schema=False,
    tags=["metrics"],
)


# --- request / response models ---


class GenAiSummarizeRequest(BaseModel):
    objectKey: str


class GenAiSummarizeResponse(BaseModel):
    summary: str
    modelUsed: str


class GenAiExtractedEntity(BaseModel):
    name: str
    type: str
    confidence: float


class GenAiExtractRequest(BaseModel):
    objectKey: str


class GenAiExtractResponse(BaseModel):
    entities: list[GenAiExtractedEntity]
    modelUsed: str


class GenAiAskRequest(BaseModel):
    question: str
    objectKeys: list[str]


class GenAiAskResponse(BaseModel):
    answer: str
    sourceObjectKeys: list[str]
    modelUsed: str


# --- helpers ---


def _load_document(object_key: str) -> str:
    """Fetch a document's text from object storage, mapping failures to HTTP errors."""
    try:
        text = fetch_text(object_key)
    except ObjectNotFoundError:
        raise HTTPException(status_code=404, detail=f"object not found: {object_key}") from None
    except UnsupportedFileError as e:
        raise HTTPException(status_code=415, detail=str(e)) from None
    if not text.strip():
        raise HTTPException(status_code=422, detail=f"object has no extractable text: {object_key}")
    return text


# --- endpoints ---


@app.get("/genai/health", tags=["health"], openapi_extra={"security": []})
def health() -> dict[str, str]:
    """Health check endpoint."""
    return {
        "status": "ok",
        "service": SERVICE_NAME,
        "version": SERVICE_VERSION,
    }


@app.get("/genai/hello", response_class=PlainTextResponse, tags=["hello"], openapi_extra={"security": []})
def hello() -> str:
    """Hello endpoint."""
    return "Hello from Alexandria GenAI!"


@app.post("/genai/summarize", tags=["ai"], response_model=GenAiSummarizeResponse, openapi_extra={"security": []})
def summarize_document(request: GenAiSummarizeRequest) -> GenAiSummarizeResponse:
    """Summarize the document stored under the given object key."""
    content = _load_document(request.objectKey)
    summary, model = summarize(content)
    return GenAiSummarizeResponse(summary=summary, modelUsed=model)


@app.post("/genai/extract", tags=["ai"], response_model=GenAiExtractResponse, openapi_extra={"security": []})
def extract(request: GenAiExtractRequest) -> GenAiExtractResponse:
    """Extract named entities from the document stored under the given object key."""
    content = _load_document(request.objectKey)
    entities, model = extract_entities(content)
    return GenAiExtractResponse(
        entities=[GenAiExtractedEntity(**e) for e in entities],
        modelUsed=model,
    )


@app.post("/genai/ask", tags=["ai"], response_model=GenAiAskResponse, openapi_extra={"security": []})
def ask(request: GenAiAskRequest) -> GenAiAskResponse:
    """Answer a question grounded in the documents stored under the given object keys.

    Each object key is fetched from storage; keys that cannot be read are skipped so a
    single missing or unreadable document does not fail the whole request.
    """
    if not request.question.strip():
        raise HTTPException(status_code=422, detail="question must not be empty")

    documents: list[dict[str, str]] = []
    for object_key in request.objectKeys:
        try:
            documents.append({"id": object_key, "content": fetch_text(object_key)})
        except ObjectNotFoundError, UnsupportedFileError:
            continue

    answer, source_keys, model = answer_question(request.question, request.objectKeys, documents or None)
    return GenAiAskResponse(answer=answer, sourceObjectKeys=source_keys, modelUsed=model)
