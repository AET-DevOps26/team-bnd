# Problem Statement

## What is the main functionality?

Alexandria is a document management and knowledge extraction platform. Users upload documents, e.g., research papers, reports, manuals, meeting notes, and the system automatically organizes, tags, and summarizes them.
Users get a concise summary and can ask questions about their documents, instead of having to read through a 40-page report to find what they need.

The core workflow is: Upload a document, get an auto-generated summary with extracted key entities, browse and search your knowledge base, and optionally query the GenAI for specific answers concerning your uploaded content.

## Who are the intended users?

- Students and researchers managing large collections of papers and notes
- Small teams that produce internal documents (meeting minutes, specs, reports, documentation) and need a way to quickly find and understand what they have
- Anyone struggling with reading long text documents

## How will we integrate GenAI meaningfully?

The GenAI service is central to what Alexandria does. Every document that gets uploaded passes through the GenAI pipeline:

1. Automatic summarization: When a document is uploaded, the GenAI service generates a brief summary that captures the main points. This summary is stored and shown alongside the document.
2. Entity extraction: The GenAI identifies and tags key entities like dates, names, organizations, and main topics. These tags are used in the search and indexing system to categorize documents without manual tagging.
3. Question answering (RAG): Users can ask natural-language questions about their documents. Using retrieval-augmented generation with a vector database (Weaviate), the system retrieves relevant document chunks and generates answers with references back to the source material.

Alexandria supports both cloud-based models (the TUM Logos gateway or the OpenAI API) and local models (via Ollama) so it can run in environments where sending data to external APIs is not acceptable. The same applies to the embedding model used for retrieval, which is provider-configurable in the same way.

## Scenarios

### Scenario 1: Uploading and summarizing a research paper

A student uploads a 30-page research paper about distributed systems. Within a few seconds, Alexandria shows a summary of the paper's key contributions, extracted author names, publication date, and main topics. The student can now look at the summary and decide on whether they want to use this paper in their own research, or not.

### Scenario 2: Searching across a knowledge base

A team lead has uploaded 15 internal reports over the past months. They type "budget allocation Q3" into the search bar. The search service returns matching documents with highlighted tags and summaries so they can quickly identify the right document without opening each one.

### Scenario 3: Asking a question about uploaded documents

A researcher has uploaded several papers on machine learning. They ask: "What methods are proposed for improving machine learning models?" Alexandria retrieves relevant passages from the uploaded papers via vector search, generates an answer, and cites which documents the answer came from.

### Scenario 4: Organizing documents with auto-generated tags

A user uploads a batch of meeting notes. Alexandria automatically extracts topics discussed, action items mentioned, and people referenced. The user can then filter their knowledge base by topic or person without having manually tagged anything.
