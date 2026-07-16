# System Overview and Architecture

## 1. Initial System Structure

### Server: Spring Boot REST API

The server side consists of three Spring Boot microservices. Each service is its own Gradle sub-module under `services/spring/`, built into an individual Docker image and is routed independently by Traefik (or the Kubernetes ingress). They share one PostgreSQL instance but each service has its own schema.

1. `user-service` (`services/spring/user-service/`)
    - Owns the `users` table (schema `user`)
    - Handles user registration, login, and authentication. Users are created on first authenticated request (`OidcUserFilter`)
    - Manages user settings/preferences
    - Exposes `DELETE /api/v1/users/{id}` and fans out the request to `knowledgebase-service` and `qa-service` over the internal API endpoints on delete
    - Public routes: `/api/v1/users/**`, `/user-service/docs`, `/user-service/swagger-ui`, `/user-service/v3/api-docs`

2. `knowledgebase-service` (`services/spring/knowledgebase-service/`)
    - Owns the `documents`, `summaries`, `tags`, `document_tags`, `extracted_entities` and `search_queries` tables (schema `knowledgebase`)
    - Handles document upload and download to SeaweedFS (S3), tagging, and text search
    - Calls the GenAI service for document summarization and entity extraction
    - Public routes: `/api/v1/knowledgebase/**`, `/knowledgebase-service/docs`, `/knowledgebase-service/swagger-ui`, `/knowledgebase-service/v3/api-docs`
    - Internal routes (not routed by Traefik or the Ingress): `/internal/knowledgebase/**`

3. `qa-service` (`services/spring/qa-service/`)
    - Owns the `qa_interactions` and `qa_source_documents` tables (schema `qa`)
    - On `/api/v1/qa/ask`, fetches the caller's document object keys from `knowledgebase-service` and delegates answer generation to the GenAI service
    - Public routes: `/api/v1/qa/**`, `/qa-service/docs`, `/qa-service/swagger-ui`, `/qa-service/v3/api-docs`
    - Internal routes (not routed by Traefik or the Ingress): `/internal/qa/**`

All services communicate via REST over HTTP. The public API is documented in `api/openapi.yaml`. Internal endpoints are only described in the "Info" section. Each service exposes its own Prometheus scrape endpoint on `/actuator/prometheus` and shows up as its own job in `infra/prometheus/prometheus.yml`.

Team member responsible for this subsystem: Niklas Ladurner

### Client: React frontend

The client is a React application that provides:
- Dashboard with document tree, tags and file metadata
- Document upload interface (drag-and-drop, file picker)
- Document detail view showing full content, summary, extracted entities, and metadata
- Search bar with filtering
- Q&A interface for asking natural language questions about uploaded documents
- User authentication (login, registration)

Team member responsible for this subsystem: Bjarne Hansen

### GenAI Service: Python

The GenAI service is an independent Python microservice that handles all AI-related processing. It downloads the referenced document from object storage and extracts its text (plain UTF-8 or PDF) before processing.

- Summarize: returns a concise summary of the document at a given object key.
- Extract: extracts structured entities (dates, names, organizations, topics).
- Index: chunks the document, embeds each chunk, and stores the chunks in Weaviate. Re-indexing the same document replaces its chunks, so the call is idempotent.
- Delete: removes a document's chunks from Weaviate so the index stays in sync when a document is deleted.
- Ask: answers a natural-language question with retrieval-augmented generation and cites the source documents the answer came from.

Both the chat model and the embedding model are provider-configurable through environment variables, so switching between the TUM Logos gateway, OpenAI, and a local Ollama requires no code changes. Embeddings default to `Qwen/Qwen3-Embedding-8B` via Logos.

For the RAG pipeline, document text is chunked, embedded, and indexed in Weaviate when a document is added. On a question, the GenAI service embeds the question, retrieves the most similar chunks scoped to the user's documents, and passes those chunks to the LLM as context. The answer links back to the documents whose chunks fed it.

Team member responsible for this subsystem: Dominic Prinz

### Database: PostgreSQL

The database consists of a single PostgreSQL instance with schema separation for each Spring microservice:
- `user_service.users`
- `knowledgebase_service.documents`, `.summaries`, `.tags`, `.document_tags`, `.extracted_entities`, `.search_queries`
- `qa_service.qa_interactions`, `.qa_source_documents`

Cross-service references (`Document.owner_subject`, `SearchQuery.user_subject`, `QAInteraction.user_subject`) are stored as plain OIDC-subject strings, not foreign keys, so no service has to read another service's schema.

Team member responsible for this subsystem: Niklas Ladurner

### Vector Database: Weaviate

Weaviate stores document chunks for semantic retrieval: the chunk text, an externally supplied embedding vector, and source metadata (object key and chunk index). It runs as a separate in-network container, reached only by the GenAI service. Vectors are produced by the GenAI service rather than a Weaviate vectorizer module, so the collection uses self-provided vectors and is not tied to a specific embedding model. Because Weaviate locks the collection to the first vector's dimension, switching to an embedding model of a different size means re-indexing.

Team member responsible for this subsystem: Dominic Prinz

### Monitoring (Prometheus)

For monitoring the microservices, Prometheus is used to scrape metrics. These are then visualized using Grafana.

Team member responsible for this subsystem: Dominic Prinz

### Reverse Proxy / Gateway: Traefik

Traefik is the single entry point for the whole system in local dev and docker-compose. It routes external requests to the right container by path: the client at `/`, the Spring APIs under `/api/v1/**`, each service's Swagger UI, Keycloak under `/auth`, and the Grafana and Prometheus UIs. The internal `/internal/**` service-to-service endpoints are deliberately not routed, so they stay in-network. On Kubernetes the cluster ingress plays this role instead of Traefik. Configuration lives in `docs/traefik.md` and the compose/ingress definitions.

Team member responsible for this subsystem: Niklas Ladurner

### Authentication: Keycloak

Keycloak is the OIDC provider. The client redirects users to it for login/registration, and every Spring service validates the resulting JWT (via `OidcUserFilter` on user-service, which also creates the user record on first authenticated request). The realm is defined in `oidc/realm.json`. Tokens are passed as Bearer headers on API calls.

Team member responsible for this subsystem: Niklas Ladurner

### Infrastructure, Deployment and CI/CD

The system is containerised (one Dockerfile per component) and runs end-to-end via `docker-compose.yml` locally. It deploys to Kubernetes through a Helm chart (`infra/k8s/`), and an Azure VM option is provisioned with Terraform + Ansible (`infra/azure/`). GitHub Actions runs CI on every PR (build, test, lint, OpenAPI checks) and deploys on merge to main.

Team member responsible for Kubernetes: Bjarne Hansen
Team member responsible for Azure: Dominic Prinz
Team member responsible for CI: Dominic Prinz

## UML Diagrams

### Analysis Object Model
![Analysis Object Model](diagrams/analysis_object_model.png)

### Use Case Diagram
![Use Case Diagram](diagrams/use_case_diagram.png)

### Top-Level Architecture (UML Component Diagram)
![Component Diagram](diagrams/component_diagram.png)

## 2. First Product Backlog

See GitHub Project.
