"""Retrieval-augmented question answering over indexed document chunks.

Embeds the question, retrieves the most similar chunks from Weaviate scoped to
the requested documents, and asks the LLM to answer from those chunks. Sources
are the documents whose chunks actually fed the answer (chunk-level attribution),
not the whole set the caller passed in.

  RAG_TOP_K   number of chunks to retrieve (default 5)
"""

from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate

from app.embeddings import embed_query
from app.env import int_env
from app.llm import get_llm, get_model_name
from app.vectorstore import search

_DEFAULT_TOP_K = 5

_NO_CONTEXT_MESSAGE = "I couldn't find anything relevant in the selected documents to answer that question."

_PROMPT = ChatPromptTemplate.from_messages(
    [
        (
            "system",
            "You are a helpful assistant that answers questions strictly based on the provided document excerpts. "
            "Each excerpt is labelled with its source. If the answer cannot be found in the excerpts, say so clearly. "
            "Cite the sources that support your answer.",
        ),
        (
            "human",
            "Excerpts:\n{context}\n\nQuestion: {question}",
        ),
    ]
)


def _top_k() -> int:
    return int_env("RAG_TOP_K", _DEFAULT_TOP_K, minimum=1)


def _unique_sources(chunks: list[dict]) -> list[str]:
    """Source object keys in retrieval order, deduplicated."""
    seen: set[str] = set()
    sources: list[str] = []
    for chunk in chunks:
        key = chunk["object_key"]
        if key not in seen:
            seen.add(key)
            sources.append(key)
    return sources


def answer_question(question: str, object_keys: list[str]) -> tuple[str, list[str], str]:
    """Answer a question from chunks retrieved for the given documents.

    Args:
        question: The user's question.
        object_keys: Object keys of the documents to search within. Retrieval is
                     scoped to these, so an empty list means "nothing to search".

    Returns:
        (answer, source_object_keys, model_name). source_object_keys are the
        documents whose chunks fed the answer; empty when nothing was retrieved.
    """
    model = get_model_name()

    if not object_keys:
        return _NO_CONTEXT_MESSAGE, [], model

    retrieved = search(embed_query(question), object_keys, _top_k())
    if not retrieved:
        return _NO_CONTEXT_MESSAGE, [], model

    context = "\n\n---\n\n".join(f"[Source: {chunk['object_key']}]\n{chunk['text']}" for chunk in retrieved)
    chain = _PROMPT | get_llm() | StrOutputParser()
    answer = chain.invoke({"context": context, "question": question})

    return answer.strip(), _unique_sources(retrieved), model
