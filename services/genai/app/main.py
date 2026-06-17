from fastapi import FastAPI, HTTPException
from fastapi.responses import PlainTextResponse
from prometheus_fastapi_instrumentator import Instrumentator
from pydantic import BaseModel

from app.extract import extract_entities
from app.qa import answer_question
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
    content: str


class GenAiSummarizeResponse(BaseModel):
    summary: str
    modelUsed: str


class GenAiExtractedEntity(BaseModel):
    name: str
    type: str
    confidence: float


class GenAiExtractRequest(BaseModel):
    content: str


class GenAiExtractResponse(BaseModel):
    entities: list[GenAiExtractedEntity]
    modelUsed: str


class DocumentContent(BaseModel):
    id: str
    content: str


class GenAiAskRequest(BaseModel):
    question: str
    documentIds: list[str]
    # When provided, the answer is grounded in the actual document text.
    # Spring passes this by fetching rawTextContent for each document ID.
    documentContents: list[DocumentContent] | None = None


class GenAiAskResponse(BaseModel):
    answer: str
    sourceDocumentIds: list[str]
    modelUsed: str


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
    """Generate a concise summary of the provided document text."""
    if not request.content.strip():
        raise HTTPException(status_code=422, detail="content must not be empty")
    summary, model = summarize(request.content)
    return GenAiSummarizeResponse(summary=summary, modelUsed=model)


@app.post("/genai/extract", tags=["ai"], response_model=GenAiExtractResponse, openapi_extra={"security": []})
def extract(request: GenAiExtractRequest) -> GenAiExtractResponse:
    """Extract named entities (people, dates, topics, organizations) from document text."""
    if not request.content.strip():
        raise HTTPException(status_code=422, detail="content must not be empty")
    entities, model = extract_entities(request.content)
    return GenAiExtractResponse(
        entities=[GenAiExtractedEntity(**e) for e in entities],
        modelUsed=model,
    )


@app.post("/genai/ask", tags=["ai"], response_model=GenAiAskResponse, openapi_extra={"security": []})
def ask(request: GenAiAskRequest) -> GenAiAskResponse:
    """Answer a natural language question about a set of documents.

    If documentContents is provided, the answer is grounded in the actual document text.
    Without it, the model answers from general knowledge and returns all documentIds as sources.
    """
    if not request.question.strip():
        raise HTTPException(status_code=422, detail="question must not be empty")
    doc_contents = [dc.model_dump() for dc in request.documentContents] if request.documentContents else None
    answer, source_ids, model = answer_question(request.question, request.documentIds, doc_contents)
    return GenAiAskResponse(answer=answer, sourceDocumentIds=source_ids, modelUsed=model)
