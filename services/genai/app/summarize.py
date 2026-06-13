"""Document summarization using LangChain."""

from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate

from app.llm import get_llm, get_model_name

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


def summarize(content: str) -> tuple[str, str]:
    """Summarize document content.

    Returns:
        (summary, model_name)
    """
    llm = get_llm()
    chain = _PROMPT | llm | StrOutputParser()
    summary = chain.invoke({"content": content})
    return summary.strip(), get_model_name()
