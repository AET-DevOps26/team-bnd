# Alexandria GenAI service

Python/FastAPI microservice for AI-powered document processing. Uses LangChain to call an LLM for summarization, entity extraction, content-based tagging, semantic search, and document Q&A.

The processing endpoints take object keys, not raw text. The service downloads the referenced documents from the S3-compatible object storage (the same SeaweedFS bucket the Spring service uploads to), extracts their text (plain UTF-8 or PDF via pypdf), and feeds that to the LLM.

## Endpoints

| Method | Path                       | Description                                                      |
| ------ | -------------------------- | ---------------------------------------------------------------- |
| GET    | `/genai/health`            | Liveness/readiness probe                                         |
| GET    | `/genai/hello`             | Sanity check, returns a plain string                             |
| POST   | `/genai/summarize`         | Summarize the document at `objectKey`                            |
| POST   | `/genai/extract`           | Extract named entities from the document at `objectKey`          |
| POST   | `/genai/tag`               | Assign content-based topical tags to the document at `objectKey` |
| POST   | `/genai/index`             | Chunk, embed, and index the document at `objectKey`              |
| DELETE | `/genai/index/{objectKey}` | Remove a document's chunks from the index                        |
| POST   | `/genai/ask`               | Answer a question from indexed chunks of the given documents     |
| POST   | `/genai/search`            | Rank indexed documents by semantic similarity to a query         |
| GET    | `/genai/metrics`           | Prometheus metrics (in-network scraping only)                    |

`summarize`, `extract`, `tag`, and `index` take `{"objectKey": "..."}`. A key that is missing returns 404 (415 if the object is neither UTF-8 text nor a PDF, 422 if it has no extractable text).

Every error, whatever the status, uses the same `{code, message, fieldErrors}` shape as the Spring services (see "Errors" below).

`summarize`, `extract`, and `tag` run the whole document through a single prompt, so they cap the input first and report a `truncated` flag; see "Document size limits" below.

`summarize` returns `{"summary": "...", "modelUsed": "...", "truncated": false}`: a concise 2-4 sentence, third-person summary of the document's main points.

`extract` returns `{"entities": [...], "modelUsed": "...", "truncated": false}` with typed entities (`PERSON`, `DATE`, `TOPIC`, `ORGANIZATION`) and confidence scores. To keep a long document from producing an unscannable list, the result is capped: callers may pass `{"objectKey": "...", "maxEntities": N}` (1-100, default 20) and the highest-confidence entities up to that limit are returned.

`tag` returns `{"tags": [...], "modelUsed": "...", "truncated": false}`: a small set of broad, lowercase topical tags describing what the document is about, so the knowledge base can categorise and filter documents. Tags are restricted to lowercase ascii letters, digits, spaces, and hyphens (e.g. `machine learning`, `covid-19`); anything else the model returns is dropped. The result is capped at 5 tags and de-duplicated regardless of what the model returns, and the prompt steers it toward common, reusable terms rather than document-specific phrasing so the same topic lands on the same tag across documents. A document with no clear topic yields an empty list. Callers may pass `{"objectKey": "...", "knownTags": [...]}` to bias the model toward reusing existing labels, which keeps the vocabulary from fragmenting into one-off tags; those are validated against the same allowlist before they reach the prompt.

`index` chunks and embeds the document into Weaviate for retrieval; `DELETE /genai/index/{objectKey}` removes its chunks. See the RAG section below.

`ask` takes `{"question": "...", "objectKeys": [...]}` and answers with retrieval-augmented generation: the question is embedded, matched against the indexed chunks of the listed documents, and the top matches are passed to the LLM. The LLM answers via structured output, returning the answer as plain prose plus the ids of the sources it relied on, so the answer text carries no ad-hoc citation brackets. Those ids are resolved server-side into `citations`: an ordered list of `{marker, objectKey, snippet}`, one per document, with `marker` the number the client shows (1, 2, ...) and `snippet` the closest matching excerpt. When nothing relevant is found (or no documents are in scope), it says so instead of guessing and returns no citations.

`search` takes `{"query": "...", "objectKeys": [...], "limit": 10}` and returns documents ranked by semantic similarity to the query. The query (capped at 1500 characters) is embedded and matched against the indexed chunks scoped to `objectKeys`. Results are de-duplicated to one entry per document using Weaviate's `group_by` on `object_key`, so a document with many close chunks never crowds out the rest; each carries a `snippet` (the closest chunk, truncated at a word boundary) and a `score` in `[0, 1]` (higher is more relevant) so the client can show why a document matched. `limit` (1-50, default 10) caps the number of documents; an empty `objectKeys` returns no results. Only indexed documents are searchable, so this reuses the same embedding config and Weaviate collection as `/genai/index` and `/genai/ask`.

FastAPI also exposes `/genai/openapi.json` and `/genai/docs` (Swagger UI) out of the box.

`modelUsed` reports the model the provider actually returned: the OpenAI-compatible providers put the served model in the response metadata, so if the gateway swaps in a different one, that is what you see. Providers that don't report a model (Ollama, and all embedding calls, so `embeddingModel`) fall back to the configured value.

## Document size limits

Our target documents are 30-40 page reports. `summarize`, `extract`, and `tag` feed the whole document into a single prompt, so a very large one can overflow the model's context window or just be slow and expensive. Before the model call, the text is capped at `MAX_DOCUMENT_CHARS` (default 120000, roughly 30k tokens, which fits our default model's context while still covering a dense report). Oversized text is cut at a word boundary near the cap and the response sets `"truncated": true` so the client can tell the user the result is based on the first part of the document. Smaller-context models should lower the cap. `index`/`ask`/`search` don't go through this: they bound their input by chunking and only send a handful of chunks to the model.

## Errors

Every error response uses the shared `{code, message, fieldErrors}` schema (the same one the Spring services return), not FastAPI's default `{"detail": ...}`. `code` is a stable string (`not_found`, `unsupported_media_type`, `validation_error`, `provider_error`, `provider_timeout`, `internal_error`, ...), `message` is human-readable, and `fieldErrors` lists per-field validation problems (empty for non-validation errors).

Model and embedding calls are the most likely thing to fail (auth, rate limit, a context-length rejection, or Logos being unreachable off the TUM network), so they are wrapped: a provider failure comes back as `502 provider_error`, a timeout as `504 provider_timeout`, both in the schema above rather than as an unhandled 500. The model client is given an explicit timeout (`LLM_TIMEOUT_SECONDS`, default 60s) and a small number of built-in retries for transient failures (`LLM_MAX_RETRIES`, default 2); embeddings have the same via `EMBEDDING_TIMEOUT_SECONDS` / `EMBEDDING_MAX_RETRIES`.

## Metrics

`/genai/metrics` is served by [prometheus-fastapi-instrumentator](https://github.com/trallnag/prometheus-fastapi-instrumentator) and exposes HTTP request count, latency, and Python process metrics for free. Prometheus scrapes it directly over the internal network (`http://genai:8000/genai/metrics`), so it is not routed through Traefik or the k8s ingress.

On top of those we add domain metrics, so the dashboard shows what the AI is doing rather than just generic HTTP timing:

| Metric                                 | Type      | Labels                                     | Meaning                                                                                                                                                                                      |
| -------------------------------------- | --------- | ------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `genai_model_requests_total`           | counter   | `operation`, `provider`, `model`, `status` | Model/embedding calls, split by which model and provider actually ran and whether they succeeded (`ok`/`error`/`timeout`). `operation` is `chat` or `embedding`.                             |
| `genai_model_request_duration_seconds` | histogram | `operation`, `provider`, `model`           | Latency of the model/embedding call itself, measured around the client call rather than the whole HTTP handler, so a slow LLM call isn't hidden by (or averaging with) fast infra endpoints. |
| `genai_document_truncated_total`       | counter   | `endpoint`                                 | Documents truncated because they exceeded `MAX_DOCUMENT_CHARS`.                                                                                                                              |

The service also exposes the shared `application_version` gauge (same as the Spring services), so the running version shows up on the overview dashboard's version table.

The `model` label comes from the same provider response as `modelUsed`. The Grafana GenAI dashboard (`infra/grafana/dashboards/genai.json`) charts model call rate by model/provider, call outcomes, per-operation model latency, and truncations. The `GenAiSlowModelCalls` and `GenAiModelErrors` alerts (`infra/prometheus/alert.rules.yml`) fire per operation, so a slow or failing chat model isn't masked by fast embedding calls.

## LLM configuration

The service is configured entirely via environment variables. Copy `.env.example` to `.env` and fill in your key.

| Variable              | Default                           | Description                                                  |
| --------------------- | --------------------------------- | ------------------------------------------------------------ |
| `LLM_PROVIDER`        | `logos`                           | `logos`, `openai`, or `ollama`                               |
| `LLM_BASE_URL`        | `https://logos.aet.cit.tum.de/v1` | API base URL (for logos/openai providers)                    |
| `LLM_MODEL`           | `openai/gpt-oss-120b`             | Model identifier                                             |
| `LLM_API_KEY`         | _(required for logos/openai)_     | API key (`lg-...` for Logos, `sk-...` for OAI)               |
| `OLLAMA_BASE_URL`     | `http://localhost:11434`          | Ollama server URL (provider=ollama only)                     |
| `LLM_TIMEOUT_SECONDS` | `60`                              | Per-call timeout for the chat model (logos/openai)           |
| `LLM_MAX_RETRIES`     | `2`                               | Client retries on transient chat failures                    |
| `MAX_DOCUMENT_CHARS`  | `120000`                          | Character cap fed to a single prompt (summarize/extract/tag) |

## Embeddings and chunking (RAG)

Document text is split into overlapping chunks and embedded into vectors for the RAG pipeline (see `app/embeddings.py`). The embedding provider is selected the same way as the LLM. By default the logos/openai providers reuse `LLM_BASE_URL` and `LLM_API_KEY` (so a single-provider setup needs no extra config), but you can point embeddings at a different endpoint/key via `EMBEDDING_BASE_URL` / `EMBEDDING_API_KEY`, e.g. to run chat on Logos and embeddings on OpenAI.

| Variable                    | Default                          | Description                                    |
| --------------------------- | -------------------------------- | ---------------------------------------------- |
| `EMBEDDING_PROVIDER`        | `logos`                          | `logos`, `openai`, or `ollama`                 |
| `EMBEDDING_MODEL`           | `Qwen/Qwen3-Embedding-8B`        | Embedding model id (provider-specific default) |
| `EMBEDDING_BASE_URL`        | _(falls back to `LLM_BASE_URL`)_ | Override the embedding endpoint                |
| `EMBEDDING_API_KEY`         | _(falls back to `LLM_API_KEY`)_  | Override the embedding API key                 |
| `EMBEDDING_TIMEOUT_SECONDS` | `30`                             | Per-call timeout for embeddings (logos/openai) |
| `EMBEDDING_MAX_RETRIES`     | `2`                              | Client retries on transient embedding failures |
| `CHUNK_SIZE`                | `1000`                           | Characters per chunk                           |
| `CHUNK_OVERLAP`             | `200`                            | Character overlap between consecutive chunks   |
| `WEAVIATE_URL`              | `http://weaviate:8080`           | Weaviate HTTP endpoint                         |
| `WEAVIATE_GRPC_PORT`        | `50051`                          | Weaviate gRPC port (batch/query)               |
| `RAG_TOP_K`                 | `5`                              | Chunks retrieved per question in `/genai/ask`  |

OpenAI defaults to `text-embedding-3-small`, Ollama to `nomic-embed-text`. Each provider/model emits a different vector size, and the Weaviate collection stores whatever the configured embedder produces rather than pinning a fixed dimension. Weaviate locks the collection to the first vector's width, so switching embedding providers to one with a different dimension means dropping and re-indexing the collection.

### How indexing and Q&A work

`POST /genai/index` pulls the document text from object storage, splits it into chunks, embeds each chunk, and stores them in Weaviate keyed by object key and chunk index. Re-indexing the same key replaces its chunks, so the call is idempotent. `DELETE /genai/index/{objectKey}` removes a document's chunks so the index stays in sync when a document is deleted; deleting an unindexed document is a no-op.

`POST /genai/ask` embeds the question, retrieves the `RAG_TOP_K` nearest chunks scoped to the requested `objectKeys`, and asks the LLM to answer from those excerpts. The retrieval is scoped per request, so a user only ever sees answers grounded in the documents they pass in.

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

No test needs a real API key. Unit tests mock the LLM and embedder:

```bash
uv run pytest -v
```

The Weaviate integration tests (`tests/test_vectorstore.py`) use synthetic vectors, not a real embedder, but they do need a reachable Weaviate; they skip automatically when there isn't one. To run the full suite including them, use the compose test profile, which starts a throwaway Weaviate (in-network only, ephemeral) and runs the suite in a container against it:

```bash
docker compose run --rm genai-test
```

## Docker

From the repo root:

```bash
docker compose up --build genai
```

The `LLM_API_KEY` environment variable must be set (e.g., in `.env`).
