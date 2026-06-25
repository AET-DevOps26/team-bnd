# Alexandria GenAI service

Python/FastAPI microservice for AI-powered document processing. Uses LangChain to call an LLM for summarization, entity extraction, and document Q&A.

The processing endpoints take object keys, not raw text. The service downloads the referenced documents from the S3-compatible object storage (the same SeaweedFS bucket the Spring service uploads to), extracts their text (plain UTF-8 or PDF via pypdf), and feeds that to the LLM.

## Endpoints

| Method | Path               | Description                                                 |
| ------ | ------------------ | ----------------------------------------------------------- |
| GET    | `/genai/health`    | Liveness/readiness probe                                    |
| GET    | `/genai/hello`     | Sanity check, returns a plain string                        |
| POST   | `/genai/summarize` | Summarize the document at `objectKey`                       |
| POST   | `/genai/extract`   | Extract named entities from the document at `objectKey`     |
| POST   | `/genai/ask`       | Answer a question grounded in the documents at `objectKeys` |
| GET    | `/genai/metrics`   | Prometheus metrics (in-network scraping only)               |

`summarize` and `extract` take `{"objectKey": "..."}`; `ask` takes `{"question": "...", "objectKeys": [...]}` and returns `sourceObjectKeys`. A key that is missing returns 404 (415 if the object is neither UTF-8 text nor a PDF). For `ask`, unreadable keys are skipped so one bad document does not fail the whole request.

FastAPI also exposes `/openapi.json` and `/docs` (Swagger UI) out of the box.

`/genai/metrics` is served by [prometheus-fastapi-instrumentator](https://github.com/trallnag/prometheus-fastapi-instrumentator) and exposes HTTP request count, latency, and Python process metrics. Prometheus scrapes it directly over the internal network (`http://genai:8000/genai/metrics`), so it is not routed through Traefik or the k8s ingress.

## LLM configuration

The service is configured entirely via environment variables. Copy `.env.example` to `.env` and fill in your key.

| Variable          | Default                           | Description                                    |
| ----------------- | --------------------------------- | ---------------------------------------------- |
| `LLM_PROVIDER`    | `logos`                           | `logos`, `openai`, or `ollama`                 |
| `LLM_BASE_URL`    | `https://logos.aet.cit.tum.de/v1` | API base URL (for logos/openai providers)      |
| `LLM_MODEL`       | `openai/gpt-oss-120b`             | Model identifier                               |
| `LLM_API_KEY`     | _(required for logos/openai)_     | API key (`lg-...` for Logos, `sk-...` for OAI) |
| `OLLAMA_BASE_URL` | `http://localhost:11434`          | Ollama server URL (provider=ollama only)       |

## Object storage configuration

The service reads documents from the S3-compatible object storage. These match the variables the Spring service uses so both point at the same bucket.

| Variable        | Default                  | Description                         |
| --------------- | ------------------------ | ----------------------------------- |
| `S3_ENDPOINT`   | `http://s3-storage:8333` | S3 gateway URL                      |
| `S3_REGION`     | `eu-central-1`           | Region label (SeaweedFS ignores it) |
| `S3_ACCESS_KEY` | `admin`                  | Access key id                       |
| `S3_SECRET_KEY` | `locals3password`        | Secret access key                   |
| `S3_BUCKET`     | `alexandria-storage`     | Bucket documents are stored in      |

### Logos (default -- TUM course API)

The course organizers provide [Logos](https://logos.aet.cit.tum.de), an OpenAI-compatible API gateway serving f.e. `openai/gpt-oss-120b`.

The Logos instance is only reachable from the TUM network or via eduVPN. For off-campus development, either connect to eduVPN or switch to Ollama.

### Ollama (local dev)

```bash
# start Ollama with llama3.2
ollama run llama3.2

# run the service pointing at local Ollama
LLM_PROVIDER=ollama uv run uvicorn app.main:app --reload --port 8000
```

## Local dev

Requires [uv](https://docs.astral.sh/uv/) and Python 3.14 (`uv python install 3.14` installs it).

```bash
cd services/genai
uv sync --group dev
LLM_API_KEY=lg-... uv run uvicorn app.main:app --reload --port 8000
```

Then: `curl http://localhost:8000/genai/health`.

## Tests

Tests mock the LLM so no API key or network access is required.

```bash
uv run pytest -v
```

## Docker

From the repo root:

```bash
docker compose up --build genai
```

The `LLM_API_KEY` environment variable must be set (e.g., in `.env`).
