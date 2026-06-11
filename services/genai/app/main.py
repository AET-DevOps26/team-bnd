from fastapi import FastAPI
from fastapi.responses import PlainTextResponse
from pydantic import BaseModel

SERVICE_NAME = "alexandria-genai"
SERVICE_VERSION = "0.0.1"

app = FastAPI(
    title="Alexandria GenAI",
    description=("GenAI microservice for the Alexandria document platform."),
    version=SERVICE_VERSION,
    openapi_tags=[
        {"name": "health", "description": "Health check endpoints"},
        {"name": "hello", "description": "Hello endpoints"},
        {"name": "ai", "description": "AI-powered document processing"},
    ],
    servers=[
        {"url": "/"}
    ]
)


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


class GenAiAskRequest(BaseModel):
    question: str
    documentIds: list[str]


class GenAiAskResponse(BaseModel):
    answer: str
    sourceDocumentIds: list[str]
    modelUsed: str


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
def summarize(request: GenAiSummarizeRequest) -> GenAiSummarizeResponse:
    """Generate a summary of the provided content."""
    content_preview = request.content[:100] if len(request.content) > 100 else request.content
    return GenAiSummarizeResponse(
        summary=f"Summary of: {content_preview}...",
        modelUsed="gpt-4"
    )


@app.post("/genai/extract", tags=["ai"], response_model=GenAiExtractResponse, openapi_extra={"security": []})
def extract(request: GenAiExtractRequest) -> GenAiExtractResponse:
    """Extract entities from the provided content."""
    entities = [
        GenAiExtractedEntity(name="Sample Person", type="PERSON", confidence=0.95),
        GenAiExtractedEntity(name="Sample Topic", type="TOPIC", confidence=0.88),
    ]
    return GenAiExtractResponse(entities=entities, modelUsed="gpt-4")


@app.post("/genai/ask", tags=["ai"], response_model=GenAiAskResponse, openapi_extra={"security": []})
def ask(request: GenAiAskRequest) -> GenAiAskResponse:
    """Answer a question based on the provided documents."""
    source_ids = request.documentIds
    return GenAiAskResponse(
        answer=f"Some answer to: {request.question}",
        sourceDocumentIds=source_ids,
        modelUsed="gpt-4"
    )
