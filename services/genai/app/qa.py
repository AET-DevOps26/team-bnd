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
    document_ids: list[str],
    document_contents: list[dict] | None = None,
) -> tuple[str, list[str], str]:
    """Answer a question, optionally grounded in document content.

    Args:
        question: The user's question.
        document_ids: IDs of documents in scope (used as source attribution).
        document_contents: Optional list of {"id": ..., "content": ...} dicts.
                           When provided, answers are grounded in the document text.

    Returns:
        (answer, source_document_ids, model_name)
    """
    llm = get_llm()

    if document_contents:
        context_parts = []
        for doc in document_contents:
            context_parts.append(f"[Document {doc['id']}]\n{doc['content']}")
        context = "\n\n---\n\n".join(context_parts)

        chain = _PROMPT_WITH_CONTEXT | llm | StrOutputParser()
        answer = chain.invoke({"context": context, "question": question})
        source_ids = [doc["id"] for doc in document_contents]
    else:
        chain = _PROMPT_NO_CONTEXT | llm | StrOutputParser()
        answer = chain.invoke({"question": question})
        source_ids = document_ids

    return answer.strip(), source_ids, get_model_name()
