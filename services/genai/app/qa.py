"""Retrieval-augmented question answering over indexed document chunks.

Embeds the question, retrieves the most similar chunks from Weaviate scoped to
the requested documents, and asks the LLM to answer from those chunks using
structured output. The model returns the answer plus the ids of the sources it
relied on, so citations are structured data resolved to real documents rather
than free-form references the model formats into the prose. That keeps the
answer text free of ad-hoc bracket styles and lets the client render the sources
consistently.

  RAG_TOP_K   number of chunks to retrieve (default 5)
"""

from dataclasses import dataclass

from langchain_core.prompts import ChatPromptTemplate
from pydantic import BaseModel, Field

from app.embeddings import embed_query
from app.env import int_env
from app.llm import get_llm, get_model_name
from app.vectorstore import search

_DEFAULT_TOP_K = 5
_SNIPPET_MAX_CHARS = 300

_NO_CONTEXT_MESSAGE = "I couldn't find anything relevant in the selected documents to answer that question."


class _CitedAnswer(BaseModel):
    answer: str = Field(description="The answer in plain prose, with no inline citation markers, brackets, or source labels")
    source_ids: list[int] = Field(
        default_factory=list,
        description="Ids of the numbered sources that support the answer, most relevant first; empty when the answer draws on no source",
    )


_PROMPT = ChatPromptTemplate.from_messages(
    [
        (
            "system",
            "You answer questions strictly from the numbered sources provided. "
            "Write the answer as plain prose, without inline citations, brackets, or source labels. "
            "Separately, list the ids of the sources that actually support your answer, most relevant first. "
            "If the sources do not contain the answer, say so plainly and list no sources.",
        ),
        (
            "human",
            "Sources:\n{context}\n\nQuestion: {question}",
        ),
    ]
)


@dataclass
class Citation:
    """A resolved source: its display number and the document it points at."""

    marker: int
    object_key: str
    snippet: str


@dataclass
class AnswerResult:
    answer: str
    citations: list[Citation]
    model: str


@dataclass
class _Source:
    object_key: str
    text: str
    snippet: str


def _top_k() -> int:
    return int_env("RAG_TOP_K", _DEFAULT_TOP_K, minimum=1)


def _snippet(text: str) -> str:
    collapsed = " ".join(text.split())
    if len(collapsed) <= _SNIPPET_MAX_CHARS:
        return collapsed
    truncated = collapsed[:_SNIPPET_MAX_CHARS]
    cut = truncated.rfind(" ")
    if cut > 0:
        truncated = truncated[:cut]
    return truncated.rstrip() + "..."


def _group_sources(chunks: list[dict]) -> list[_Source]:
    """Collapse retrieved chunks into unique documents in retrieval order.

    A document that matched on several chunks becomes one numbered source whose
    text is those chunks joined; the snippet comes from its first (closest) chunk.
    """
    order: list[str] = []
    texts: dict[str, list[str]] = {}
    snippets: dict[str, str] = {}
    for chunk in chunks:
        key = chunk["object_key"]
        if key not in texts:
            order.append(key)
            texts[key] = []
            snippets[key] = _snippet(chunk["text"])
        texts[key].append(chunk["text"])
    return [_Source(object_key=key, text="\n".join(texts[key]), snippet=snippets[key]) for key in order]


def _build_citations(source_ids: list[int], sources: list[_Source]) -> list[Citation]:
    """Resolve the model's 1-based source ids into ordered, numbered citations.

    Ids outside the provided range are dropped and duplicates collapsed, keeping
    the model's ordering. When the model names no valid source we fall back to
    every retrieved document, so an answer never loses its provenance.
    """
    seen: set[int] = set()
    chosen: list[_Source] = []
    for sid in source_ids:
        if 1 <= sid <= len(sources) and sid not in seen:
            seen.add(sid)
            chosen.append(sources[sid - 1])
    if not chosen:
        chosen = sources
    return [Citation(marker=i, object_key=s.object_key, snippet=s.snippet) for i, s in enumerate(chosen, start=1)]


def answer_question(question: str, object_keys: list[str]) -> AnswerResult:
    """Answer a question from chunks retrieved for the given documents.

    Args:
        question: The user's question.
        object_keys: Object keys of the documents to search within. Retrieval is
                     scoped to these, so an empty list means "nothing to search".

    Returns:
        An AnswerResult carrying the answer text and the numbered citations that
        support it; citations are empty when nothing was retrieved.
    """
    model = get_model_name()

    if not object_keys:
        return AnswerResult(_NO_CONTEXT_MESSAGE, [], model)

    retrieved = search(embed_query(question), object_keys, _top_k())
    if not retrieved:
        return AnswerResult(_NO_CONTEXT_MESSAGE, [], model)

    sources = _group_sources(retrieved)
    context = "\n\n---\n\n".join(f"Source {i}:\n{source.text}" for i, source in enumerate(sources, start=1))
    structured_llm = get_llm().with_structured_output(_CitedAnswer)
    chain = _PROMPT | structured_llm
    result: _CitedAnswer = chain.invoke({"context": context, "question": question})  # ty:ignore[invalid-assignment]

    return AnswerResult(result.answer.strip(), _build_citations(result.source_ids, sources), model)
