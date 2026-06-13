"""Unit tests for the entity extraction module."""

from unittest.mock import MagicMock, patch

from langchain_core.runnables import RunnableLambda


def _fake_extraction_result():
    from app.extract import _Entity, _ExtractionResult

    return _ExtractionResult(
        entities=[
            _Entity(name="Ada Lovelace", type="PERSON", confidence=0.97),
            _Entity(name="1843", type="DATE", confidence=0.91),
            _Entity(name="Analytical Engine", type="TOPIC", confidence=0.88),
            _Entity(name="Royal Society", type="ORGANIZATION", confidence=0.85),
        ]
    )


def test_extract_returns_entities_and_model_name():
    fake_result = _fake_extraction_result()
    fake_structured_llm = RunnableLambda(lambda _: fake_result)

    mock_llm = MagicMock()
    mock_llm.with_structured_output.return_value = fake_structured_llm

    with patch("app.extract.get_llm", return_value=mock_llm), patch("app.extract.get_model_name", return_value="openai/gpt-oss-120b"):
        from app.extract import extract_entities

        entities, model = extract_entities("Ada Lovelace worked on the Analytical Engine in 1843.")

    assert model == "openai/gpt-oss-120b"
    assert len(entities) == 4
    names = [e["name"] for e in entities]
    assert "Ada Lovelace" in names


def test_extract_entity_has_required_fields():
    fake_result = _fake_extraction_result()
    fake_structured_llm = RunnableLambda(lambda _: fake_result)

    mock_llm = MagicMock()
    mock_llm.with_structured_output.return_value = fake_structured_llm

    with patch("app.extract.get_llm", return_value=mock_llm), patch("app.extract.get_model_name", return_value="test-model"):
        from app.extract import extract_entities

        entities, _ = extract_entities("Some text.")

    for entity in entities:
        assert "name" in entity
        assert "type" in entity
        assert "confidence" in entity
        assert entity["type"] in ("PERSON", "DATE", "TOPIC", "ORGANIZATION")
        assert 0.0 <= entity["confidence"] <= 1.0


def test_extract_returns_empty_list_for_no_entities():
    from app.extract import _ExtractionResult

    empty_result = _ExtractionResult(entities=[])
    fake_structured_llm = RunnableLambda(lambda _: empty_result)

    mock_llm = MagicMock()
    mock_llm.with_structured_output.return_value = fake_structured_llm

    with patch("app.extract.get_llm", return_value=mock_llm), patch("app.extract.get_model_name", return_value="test-model"):
        from app.extract import extract_entities

        entities, _ = extract_entities("The quick brown fox.")

    assert entities == []
