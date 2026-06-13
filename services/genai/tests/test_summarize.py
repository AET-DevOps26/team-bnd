"""Unit tests for the summarization module."""

from unittest.mock import patch

from langchain_core.messages import AIMessage
from langchain_core.runnables import RunnableLambda


def _make_fake_llm(response: str):
    """Return a LangChain-compatible Runnable that yields a fixed AIMessage."""
    return RunnableLambda(lambda _: AIMessage(content=response))


def test_summarize_returns_text_and_model_name():
    fake_llm = _make_fake_llm("Researchers at TUM developed a new method for NLP.")

    with patch("app.summarize.get_llm", return_value=fake_llm), patch("app.summarize.get_model_name", return_value="openai/gpt-oss-120b"):
        from app.summarize import summarize

        summary, model = summarize("Some long academic paper text about NLP methods.")

    assert summary == "Researchers at TUM developed a new method for NLP."
    assert model == "openai/gpt-oss-120b"


def test_summarize_strips_leading_trailing_whitespace():
    fake_llm = _make_fake_llm("  Summary with extra whitespace.  ")

    with patch("app.summarize.get_llm", return_value=fake_llm), patch("app.summarize.get_model_name", return_value="test-model"):
        from app.summarize import summarize

        summary, _ = summarize("Some document text.")

    assert summary == "Summary with extra whitespace."
