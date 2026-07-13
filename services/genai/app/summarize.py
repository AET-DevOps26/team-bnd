"""Document summarization using LangChain."""

from langchain_core.prompts import ChatPromptTemplate

from app.llm import get_llm, get_model_name, get_provider, model_name_from_result
from app.model_calls import run_model_call

_PROMPT = ChatPromptTemplate.from_messages(
    [
        (
            "system",
            "You are a concise technical writer. Summarize the provided document text in 2-4 sentences, "
            "capturing the main topic, key points, and any important conclusions. "
            "Write in third person. Do not include phrases like 'this document' or 'the text'.",
        ),
        ("human", "{content}"),
    ]
)


def _content_text(message: object) -> str:
    content = getattr(message, "content", message)
    return content if isinstance(content, str) else str(content)


def summarize(content: str) -> tuple[str, str]:
    """Summarize document content.

    Returns:
        (summary, model_name) where model_name is the model the provider
        reported, falling back to the configured name.
    """
    llm = get_llm()
    fallback = get_model_name()
    message, model = run_model_call(
        lambda: (_PROMPT | llm).invoke({"content": content}),
        operation="chat",
        provider=get_provider(),
        fallback_model=fallback,
        model_resolver=lambda result: model_name_from_result(result, fallback),
    )
    return _content_text(message).strip(), model
