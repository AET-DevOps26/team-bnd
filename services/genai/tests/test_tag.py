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


def test_generate_tags_injects_known_tags_into_prompt():
    from app.tag import _TaggingResult

    captured = {}

    def fake_llm(inp):
        captured["prompt"] = str(inp)
        return _TaggingResult(tags=["finance"])

    fake_structured_llm = RunnableLambda(fake_llm)
    mock_llm = MagicMock()
    mock_llm.with_structured_output.return_value = fake_structured_llm

    with patch("app.tag.get_llm", return_value=mock_llm), patch("app.tag.get_model_name", return_value="test-model"):
        from app.tag import generate_tags

        tags, _ = generate_tags("A finance report.", known_tags=["finance", "markets"])

    assert "These tags already exist" in captured["prompt"]
    assert "finance" in captured["prompt"]
    assert "markets" in captured["prompt"]
    assert tags == ["finance"]


def test_generate_tags_ignores_known_tags_when_empty():
    from app.tag import _TaggingResult

    captured = {}

    def fake_llm(inp):
        captured["prompt"] = str(inp)
        return _TaggingResult(tags=["topic"])

    fake_structured_llm = RunnableLambda(fake_llm)
    mock_llm = MagicMock()
    mock_llm.with_structured_output.return_value = fake_structured_llm

    with patch("app.tag.get_llm", return_value=mock_llm), patch("app.tag.get_model_name", return_value="test-model"):
        from app.tag import generate_tags

        generate_tags("Some text.")

    assert "These tags already exist" not in captured["prompt"]


def test_generate_tags_filters_unsafe_known_tags():
    from app.tag import _TaggingResult

    captured = {}

    def fake_llm(inp):
        captured["prompt"] = str(inp)
        return _TaggingResult(tags=["finance"])

    fake_structured_llm = RunnableLambda(fake_llm)
    mock_llm = MagicMock()
    mock_llm.with_structured_output.return_value = fake_structured_llm

    long_tag = "x" * 30
    with patch("app.tag.get_llm", return_value=mock_llm), patch("app.tag.get_model_name", return_value="test-model"):
        from app.tag import generate_tags

        generate_tags(
            "text",
            known_tags=["finance", "in\njected", "with\rtab", "with\ttab", long_tag, ""],
        )

    prompt = captured["prompt"]
    assert "finance" in prompt
    assert "in\njected" not in prompt
    assert "with\rtab" not in prompt
    assert "with\ttab" not in prompt
    assert long_tag not in prompt


def test_generate_tags_drops_overlong_output_tags():
    mock_llm = _make_llm(["a-tag", "x" * 30])

    with patch("app.tag.get_llm", return_value=mock_llm), patch("app.tag.get_model_name", return_value="test-model"):
        from app.tag import generate_tags

        tags, _ = generate_tags("text")

    assert tags == ["a-tag"]


def test_generate_tags_drops_tags_with_disallowed_characters():
    mock_llm = _make_llm(["machine learning", "f1", "tags!", "café", "trailing-", "good-one"])

    with patch("app.tag.get_llm", return_value=mock_llm), patch("app.tag.get_model_name", return_value="test-model"):
        from app.tag import generate_tags

        tags, _ = generate_tags("text")

    assert tags == ["machine learning", "f1", "good-one"]


def test_generate_tags_lowercases_and_filters_known_tags():
    from app.tag import _TaggingResult

    captured = {}

    def fake_llm(inp):
        captured["prompt"] = str(inp)
        return _TaggingResult(tags=["finance"])

    fake_structured_llm = RunnableLambda(fake_llm)
    mock_llm = MagicMock()
    mock_llm.with_structured_output.return_value = fake_structured_llm

    with patch("app.tag.get_llm", return_value=mock_llm), patch("app.tag.get_model_name", return_value="test-model"):
        from app.tag import generate_tags

        generate_tags("text", known_tags=["Finance", "hot/dogs", "quantum"])

    prompt = captured["prompt"]
    assert "finance" in prompt
    assert "quantum" in prompt
    assert "Finance" not in prompt
    assert "hot/dogs" not in prompt
