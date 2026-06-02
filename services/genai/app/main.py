from fastapi import FastAPI
from fastapi.responses import PlainTextResponse

SERVICE_NAME = "alexandria-genai"
SERVICE_VERSION = "0.0.1"

app = FastAPI(
    title="Alexandria GenAI",
    description=("GenAI microservice for the Alexandria document platform."),
    version=SERVICE_VERSION,
    openapi_tags=[
        {"name": "health", "description": "Health check endpoints"},
        {"name": "hello", "description": "Hello endpoints"},
    ],
    servers=[
        {"url": "/"}
    ]
)


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
