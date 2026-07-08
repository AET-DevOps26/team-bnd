"""Unit tests for the content-based tagging module."""

from unittest.mock import MagicMock, patch

from langchain_core.runnables import RunnableLambda


def _make_llm(tags):
    from app.tag import _TaggingResult

    fake_structured_llm = RunnableLambda(lambda _: _TaggingResult(tags=tags))
    mock_llm = MagicMock()
    mock_llm.with_structured_output.return_value = fake_structured_llm
    return mock_llm


def test_generate_tags_returns_tags_and_model_name():
    mock_llm = _make_llm(["machine learning", "nlp", "research"])

    with patch("app.tag.get_llm", return_value=mock_llm), patch("app.tag.get_model_name", return_value="openai/gpt-oss-120b"):
        from app.tag import generate_tags

        tags, model = generate_tags("A paper about NLP research methods.")

    assert model == "openai/gpt-oss-120b"
    assert tags == ["machine learning", "nlp", "research"]


def test_generate_tags_lowercases_trims_and_deduplicates():
    mock_llm = _make_llm(["Finance", "finance", "  Markets  ", ""])

    with patch("app.tag.get_llm", return_value=mock_llm), patch("app.tag.get_model_name", return_value="test-model"):
        from app.tag import generate_tags

        tags, _ = generate_tags("Quarterly financial report.")

    assert tags == ["finance", "markets"]


def test_generate_tags_caps_number_of_tags():
    from app.tag import _MAX_TAGS

    too_many = [f"topic-{i}" for i in range(_MAX_TAGS + 3)]
    mock_llm = _make_llm(too_many)

    with patch("app.tag.get_llm", return_value=mock_llm), patch("app.tag.get_model_name", return_value="test-model"):
        from app.tag import generate_tags

        tags, _ = generate_tags("A very broad document.")

    assert len(tags) == _MAX_TAGS
    assert tags == too_many[:_MAX_TAGS]


def test_generate_tags_returns_empty_list_when_no_tags():
    mock_llm = _make_llm([])

    with patch("app.tag.get_llm", return_value=mock_llm), patch("app.tag.get_model_name", return_value="test-model"):
        from app.tag import generate_tags

        tags, _ = generate_tags("The quick brown fox.")

    assert tags == []
