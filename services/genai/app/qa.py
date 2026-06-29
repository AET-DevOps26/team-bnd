"""Question answering over documents using LangChain."""

from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate

from app.llm import get_llm, get_model_name

_PROMPT_WITH_CONTEXT = ChatPromptTemplate.from_messages(
    [
        (
            "system",
            "You are a helpful assistant that answers questions strictly based on the provided document excerpts. "
            "If the answer cannot be found in the documents, say so clearly. "
            "When relevant, mention which documents support your answer.",
        ),
        (
            "human",
            "Documents:\n{context}\n\nQuestion: {question}",
        ),
    ]
)

_PROMPT_NO_CONTEXT = ChatPromptTemplate.from_messages(
    [
        (
            "system",
            "You are a helpful assistant. Answer the question as best you can. "
            "Note: no document content was provided, so this answer is based on general knowledge only.",
        ),
        ("human", "{question}"),
    ]
)


def answer_question(
    question: str,
    object_keys: list[str],
    documents: list[dict] | None = None,
) -> tuple[str, list[str], str]:
    """Answer a question, optionally grounded in document content.

    Args:
        question: The user's question.
        object_keys: Object keys of documents in scope (used as source attribution).
        documents: Optional list of {"id": <object key>, "content": ...} dicts.
                   When provided, answers are grounded in the document text and only
                   the keys that were actually readable are returned as sources.

    Returns:
        (answer, source_object_keys, model_name)
    """
    llm = get_llm()

    if documents:
        context_parts = []
        for doc in documents:
            context_parts.append(f"[Document {doc['id']}]\n{doc['content']}")
        context = "\n\n---\n\n".join(context_parts)

        chain = _PROMPT_WITH_CONTEXT | llm | StrOutputParser()
        answer = chain.invoke({"context": context, "question": question})
        source_keys = [doc["id"] for doc in documents]
    else:
        chain = _PROMPT_NO_CONTEXT | llm | StrOutputParser()
        answer = chain.invoke({"question": question})
        source_keys = object_keys

    return answer.strip(), source_keys, get_model_name()
