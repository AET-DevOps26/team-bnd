"""Unit tests for the Q&A module."""

from unittest.mock import patch

from langchain_core.messages import AIMessage
from langchain_core.runnables import RunnableLambda


def _make_fake_llm(response: str):
    return RunnableLambda(lambda _: AIMessage(content=response))


def test_ask_without_document_contents_returns_answer():
    fake_llm = _make_fake_llm("The answer is 42.")

    with patch("app.qa.get_llm", return_value=fake_llm), patch("app.qa.get_model_name", return_value="openai/gpt-oss-120b"):
        from app.qa import answer_question

        answer, source_keys, model = answer_question(
            question="What is the answer?",
            object_keys=["id-1", "id-2"],
            documents=None,
        )

    assert answer == "The answer is 42."
    assert source_keys == ["id-1", "id-2"]
    assert model == "openai/gpt-oss-120b"


def test_ask_with_document_contents_uses_context():
    fake_llm = _make_fake_llm("Based on the documents, the revenue grew by 15%.")

    with patch("app.qa.get_llm", return_value=fake_llm), patch("app.qa.get_model_name", return_value="openai/gpt-oss-120b"):
        from app.qa import answer_question

        answer, source_keys, model = answer_question(
            question="What was the revenue growth?",
            object_keys=["doc-1", "doc-2"],
            documents=[
                {"id": "doc-1", "content": "Q4 revenue grew by 15% year-over-year."},
                {"id": "doc-2", "content": "Operating costs remained stable."},
            ],
        )

    assert "15%" in answer
    assert set(source_keys) == {"doc-1", "doc-2"}
    assert model == "openai/gpt-oss-120b"


def test_ask_with_document_contents_returns_only_content_ids():
    fake_llm = _make_fake_llm("Some answer.")

    with patch("app.qa.get_llm", return_value=fake_llm), patch("app.qa.get_model_name", return_value="test-model"):
        from app.qa import answer_question

        _, source_keys, _ = answer_question(
            question="Something?",
            object_keys=["all-1", "all-2", "all-3"],
            documents=[{"id": "content-doc", "content": "relevant text"}],
        )

    # when content is provided, source_keys come from the content, not document_ids
    assert source_keys == ["content-doc"]


def test_ask_strips_whitespace_from_answer():
    fake_llm = _make_fake_llm("  Answer with spaces.  ")

    with patch("app.qa.get_llm", return_value=fake_llm), patch("app.qa.get_model_name", return_value="test-model"):
        from app.qa import answer_question

        answer, _, _ = answer_question("Question?", ["id-1"], None)

    assert answer == "Answer with spaces."
