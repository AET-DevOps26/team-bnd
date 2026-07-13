"""Unit tests for LLM result helpers (model name resolution and parsing)."""

import pytest
from langchain_core.messages import AIMessage

from app.errors import ProviderError
from app.llm import model_name_from_result, require_parsed


def test_model_name_read_from_plain_message_metadata():
    message = AIMessage(content="hi", response_metadata={"model_name": "served-42"})

    assert model_name_from_result(message, "configured") == "served-42"


def test_model_name_read_from_structured_include_raw_result():
    result = {
        "raw": AIMessage(content="hi", response_metadata={"model_name": "served-structured"}),
        "parsed": object(),
        "parsing_error": None,
    }

    assert model_name_from_result(result, "configured") == "served-structured"


def test_model_name_falls_back_to_config_when_metadata_missing():
    assert model_name_from_result(AIMessage(content="hi"), "configured") == "configured"
    assert model_name_from_result({"raw": None, "parsed": object()}, "configured") == "configured"


def test_model_name_accepts_bare_model_field():
    message = AIMessage(content="hi", response_metadata={"model": "alt-name"})

    assert model_name_from_result(message, "configured") == "alt-name"


def test_require_parsed_returns_parsed_from_dict():
    sentinel = object()

    assert require_parsed({"raw": AIMessage(content="x"), "parsed": sentinel}) is sentinel


def test_require_parsed_returns_bare_object():
    sentinel = object()

    assert require_parsed(sentinel) is sentinel


def test_require_parsed_raises_provider_error_on_parse_failure():
    with pytest.raises(ProviderError):
        require_parsed({"raw": AIMessage(content="x"), "parsed": None, "parsing_error": "boom"})
