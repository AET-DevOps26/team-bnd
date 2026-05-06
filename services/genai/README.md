# Alexandria GenAI service

Python/FastAPI microservice. Right now it's a stub - just `/genai/health` and `/genai/hello`. Real GenAI endpoints (summarize, entity extraction, Q&A/RAG) will land here next.

## Endpoints

| Method | Path            | Description                          |
| ------ | --------------- | ------------------------------------ |
| GET    | `/genai/health` | Liveness/readiness probe             |
| GET    | `/genai/hello`  | Sanity check, returns a plain string |

FastAPI also exposes `/openapi.json` and `/docs` (Swagger UI) out of the box.

## Local dev

Requires [uv](https://docs.astral.sh/uv/) and Python 3.14 (uv can install it for you: `uv python install 3.14`).

```bash
uv sync --extra dev
uv run uvicorn app.main:app --reload --port 8000
```

Then: `curl http://localhost:8000/genai/hello`.

## Tests

```bash
uv run pytest
```

## Docker

From the repo root:

```bash
docker compose up --build genai
```

Or just this service:

```bash
docker build -t alexandria-genai services/genai
docker run --rm -p 8000:8000 alexandria-genai
```

## TODO

- LangChain integration + real LLM calls (OpenAI + local model support)
- Endpoints for summarize / extract / ask
- OpenTelemetry tracing + Prometheus metrics
- Config via env vars (model backend, API keys, timeouts)
- Generated client published via OpenAPI generator
