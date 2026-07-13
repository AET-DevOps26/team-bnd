from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from fastapi.responses import PlainTextResponse
from prometheus_client import Gauge
from prometheus_fastapi_instrumentator import Instrumentator
from pydantic import BaseModel, Field, field_validator

from app.embeddings import chunk_text, embed_chunks, get_embedding_model_name
from app.errors import customize_openapi, register_error_handlers
from app.extract import DEFAULT_MAX_ENTITIES, extract_entities
from app.limits import bound_document
from app.metrics import record_truncation
from app.qa import answer_question
from app.search import search_documents
from app.storage import ObjectNotFoundError, UnsupportedFileError, fetch_text
from app.summarize import summarize
from app.tag import generate_tags
from app.vectorstore import close_client, delete_document, index_chunks

SERVICE_NAME = "alexandria-genai"
SERVICE_VERSION = "5.0.0"


@asynccontextmanager
async def lifespan(_: FastAPI):
    yield
    close_client()


app = FastAPI(
    title="Alexandria GenAI",
    description="GenAI microservice for the Alexandria document platform.",
    version=SERVICE_VERSION,
    docs_url="/genai/docs",
    redoc_url="/genai/redoc",
    openapi_url="/genai/openapi.json",
    openapi_tags=[
        {"name": "health", "description": "Health check endpoints"},
        {"name": "hello", "description": "Hello endpoints"},
        {"name": "ai", "description": "AI-powered document processing"},
    ],
    servers=[{"url": "/"}],
    lifespan=lifespan,
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

# Render errors as the shared {code, message, fieldErrors} shape, in responses
# and in the generated spec.
register_error_handlers(app)
customize_openapi(app)

# Constant info gauge so Grafana can show which version is running, matching the
# Spring services' application_version metric (value is always 1, version in a
# label). The name avoids an _info suffix on purpose: Micrometer strips it on the
# Spring side, so the two exporters would otherwise disagree on the series name.
_application_version = Gauge("application_version", "Running application build info", ["application", "version"])
_application_version.labels(application=SERVICE_NAME, version=SERVICE_VERSION).set(1)


# --- request / response models ---


class GenAiSummarizeRequest(BaseModel):
    objectKey: str


class GenAiSummarizeResponse(BaseModel):
    summary: str
    modelUsed: str
    truncated: bool = False


class GenAiExtractedEntity(BaseModel):
    name: str
    type: str
    confidence: float


class GenAiExtractRequest(BaseModel):
    objectKey: str
    maxEntities: int = Field(default=DEFAULT_MAX_ENTITIES, ge=1, le=100)


class GenAiExtractResponse(BaseModel):
    entities: list[GenAiExtractedEntity]
    modelUsed: str
    truncated: bool = False


class GenAiTagRequest(BaseModel):
    objectKey: str
    knownTags: list[str] = []


class GenAiTagResponse(BaseModel):
    tags: list[str]
    modelUsed: str
    truncated: bool = False


def _reject_blank(value: str) -> str:
    if not value.strip():
        raise ValueError("must not be blank")
    return value


class GenAiAskRequest(BaseModel):
    question: str
    objectKeys: list[str]

    _validate_question = field_validator("question")(_reject_blank)


class GenAiCitation(BaseModel):
    marker: int
    objectKey: str
    snippet: str


class GenAiAskResponse(BaseModel):
    answer: str
    citations: list[GenAiCitation]
    modelUsed: str


class GenAiSearchRequest(BaseModel):
    query: str = Field(max_length=1500)
    objectKeys: list[str]
    limit: int = Field(default=10, ge=1, le=50)

    _validate_query = field_validator("query")(_reject_blank)


class GenAiSearchResult(BaseModel):
    objectKey: str
    score: float
    snippet: str
    chunkIndex: int


class GenAiSearchResponse(BaseModel):
    results: list[GenAiSearchResult]
    embeddingModel: str


class GenAiIndexRequest(BaseModel):
    objectKey: str


class GenAiIndexResponse(BaseModel):
    objectKey: str
    chunksIndexed: int
    embeddingModel: str


class GenAiDeleteIndexResponse(BaseModel):
    objectKey: str
    chunksDeleted: int


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


# Error responses _load_document can return, declared so they show up in the
# OpenAPI spec for every endpoint that loads a document.
_DOCUMENT_ERROR_RESPONSES = {
    404: {"description": "Object not found"},
    415: {"description": "Object is not a supported file type"},
}


def _load_bounded_document(object_key: str, endpoint: str) -> tuple[str, bool]:
    """Fetch a document and cap it to the model input limit for single-prompt endpoints."""
    text, truncated = bound_document(_load_document(object_key))
    if truncated:
        record_truncation(endpoint)
    return text, truncated


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


@app.post("/genai/summarize", tags=["ai"], response_model=GenAiSummarizeResponse, responses=_DOCUMENT_ERROR_RESPONSES, openapi_extra={"security": []})
def summarize_document(request: GenAiSummarizeRequest) -> GenAiSummarizeResponse:
    """Summarize the document stored under the given object key."""
    content, truncated = _load_bounded_document(request.objectKey, "summarize")
    summary, model = summarize(content)
    return GenAiSummarizeResponse(summary=summary, modelUsed=model, truncated=truncated)


@app.post("/genai/extract", tags=["ai"], response_model=GenAiExtractResponse, responses=_DOCUMENT_ERROR_RESPONSES, openapi_extra={"security": []})
def extract(request: GenAiExtractRequest) -> GenAiExtractResponse:
    """Extract named entities from the document stored under the given object key."""
    content, truncated = _load_bounded_document(request.objectKey, "extract")
    entities, model = extract_entities(content, request.maxEntities)
    return GenAiExtractResponse(
        entities=[GenAiExtractedEntity(**e) for e in entities],
        modelUsed=model,
        truncated=truncated,
    )


@app.post("/genai/tag", tags=["ai"], response_model=GenAiTagResponse, responses=_DOCUMENT_ERROR_RESPONSES, openapi_extra={"security": []})
def tag(request: GenAiTagRequest) -> GenAiTagResponse:
    """Assign content-based topical tags to the document stored under the given object key."""
    content, truncated = _load_bounded_document(request.objectKey, "tag")
    tags, model = generate_tags(content, request.knownTags)
    return GenAiTagResponse(tags=tags, modelUsed=model, truncated=truncated)


@app.post("/genai/ask", tags=["ai"], response_model=GenAiAskResponse, openapi_extra={"security": []})
def ask(request: GenAiAskRequest) -> GenAiAskResponse:
    """Answer a question from chunks retrieved for the given documents.

    The question is embedded and matched against the indexed chunks scoped to the
    requested object keys; the answer cites the documents its chunks came from.
    """
    result = answer_question(request.question, request.objectKeys)
    return GenAiAskResponse(
        answer=result.answer,
        citations=[GenAiCitation(marker=c.marker, objectKey=c.object_key, snippet=c.snippet) for c in result.citations],
        modelUsed=result.model,
    )


@app.post("/genai/search", tags=["ai"], response_model=GenAiSearchResponse, openapi_extra={"security": []})
def search(request: GenAiSearchRequest) -> GenAiSearchResponse:
    """Rank indexed documents by semantic similarity to the query.

    The query is embedded and matched against the indexed chunks scoped to the
    requested object keys; chunk hits are rolled up to one entry per document,
    each carrying the closest chunk's snippet and a relevance score.
    """
    results, model = search_documents(request.query, request.objectKeys, request.limit)
    return GenAiSearchResponse(
        results=[GenAiSearchResult(objectKey=r["object_key"], score=r["score"], snippet=r["snippet"], chunkIndex=r["chunk_index"]) for r in results],
        embeddingModel=model,
    )


@app.post("/genai/index", tags=["ai"], response_model=GenAiIndexResponse, responses=_DOCUMENT_ERROR_RESPONSES, openapi_extra={"security": []})
def index(request: GenAiIndexRequest) -> GenAiIndexResponse:
    """Chunk, embed, and index the document at the given object key into Weaviate.

    Re-indexing the same key replaces its existing chunks, so the call is safe to
    repeat (e.g. when Spring re-processes a document).
    """
    content = _load_document(request.objectKey)
    chunks = chunk_text(content, request.objectKey)
    vectors = embed_chunks(chunks)
    count = index_chunks(request.objectKey, chunks, vectors)
    return GenAiIndexResponse(
        objectKey=request.objectKey,
        chunksIndexed=count,
        embeddingModel=get_embedding_model_name(),
    )


@app.delete("/genai/index/{object_key:path}", tags=["ai"], response_model=GenAiDeleteIndexResponse, openapi_extra={"security": []})
def delete_index(object_key: str) -> GenAiDeleteIndexResponse:
    """Remove all indexed chunks for the document at the given object key.

    Idempotent: deleting a document that was never indexed removes nothing and
    still returns 200. The :path converter keeps object keys that contain slashes
    intact.
    """
    removed = delete_document(object_key)
    return GenAiDeleteIndexResponse(objectKey=object_key, chunksDeleted=removed)
