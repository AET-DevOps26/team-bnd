from fastapi import FastAPI
from fastapi.responses import PlainTextResponse

SERVICE_NAME = "alexandria-genai"
SERVICE_VERSION = "0.0.1"

app = FastAPI(
    title="Alexandria GenAI",
    description=("GenAI microservice for the Alexandria document platform."),
    version=SERVICE_VERSION,
)


@app.get("/genai/health", tags=["health"])
def health() -> dict[str, str]:
    return {
        "status": "ok",
        "service": SERVICE_NAME,
        "version": SERVICE_VERSION,
    }


@app.get("/genai/hello", response_class=PlainTextResponse, tags=["hello"])
def hello() -> str:
    return "Hello from Alexandria GenAI!"
