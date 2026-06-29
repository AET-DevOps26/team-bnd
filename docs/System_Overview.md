# System Overview and Architecture

## 1. Initial System Structure

### Server: Spring Boot REST API

The server side consists of three Spring Boot microservices:

1. User Service
    - Handles user registration, login, and authentication (JWT-based)
    - Manages user settings/preferences
    - Provides endpoints for session management
    - Database schema: `users`

2. Document Management Service
    - Manages document lifecycle (upload, process, update, delete)
    - Handles document storage and retrieval
    - Stores document metadata (title, upload date, file size etc.)
    - Triggers the GenAI service for summarization and entity extraction
    - Database schema: `documents`

3. Search and Indexing Service
    - Manages document tags (auto-generated as well as user-defined)
    - Performs search queries across the knowledge base
    - Generates answers to natural language questions based on the knowledge base
    - Provides filtered browsing and full-text search
    - Database schema: `search`

All services communicate via REST over HTTP. All endpoints are documented by an OpenAPI spec.

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

The database consists of a single PostgreSQL instance with schema separation for each microservice:
- `users`: user accounts, credentials, preferences
- `documents`: file metadata, summaries, entities
- `search`: tags, categories, index data

Team member responsible for this subsystem: Niklas Ladurner

### Vector Database: Weaviate

Weaviate stores document chunks for semantic retrieval: the chunk text, an externally supplied embedding vector, and source metadata (object key and chunk index). It runs as a separate in-network container, reached only by the GenAI service. Vectors are produced by the GenAI service rather than a Weaviate vectorizer module, so the collection uses self-provided vectors and is not tied to a specific embedding model. Because Weaviate locks the collection to the first vector's dimension, switching to an embedding model of a different size means re-indexing.

Team member responsible for this subsystem: Dominic Prinz

### Monitoring (Prometheus)

For monitoring the microservices, Prometheus is used to scrape metrics. These are then visualized using Grafana.

## UML Diagrams

### Analysis Object Model
![Analysis Object Model](diagrams/analysis_object_model.png)

### Use Case Diagram
![Use Case Diagram](diagrams/use_case_diagram.png)

### Top-Level Architecture (UML Component Diagram)
![Component Diagram](diagrams/component_diagram.png)

## 2. First Product Backlog

See GitHub Project.
