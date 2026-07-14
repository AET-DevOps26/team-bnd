"""Unit tests for the LLM factory and result helpers.

The provider factory tests patch the LangChain client classes so no client is
ever constructed and no network or API key is touched; we only assert the
service passes the right config through.
"""

import os
from unittest.mock import patch

import pytest
from langchain_core.messages import AIMessage

from app.errors import ProviderError
from app.llm import (
    get_llm,
    get_model_name,
    get_provider,
    model_name_from_result,
    require_parsed,
)

# --- provider selection ---


def test_get_provider_defaults_to_logos():
    with patch.dict(os.environ, {}, clear=False):
        os.environ.pop("LLM_PROVIDER", None)
        assert get_provider() == "logos"


def test_get_provider_is_case_insensitive():
    with patch.dict(os.environ, {"LLM_PROVIDER": "OpenAI"}):
        assert get_provider() == "openai"


# --- get_llm factory ---


def test_get_llm_logos_uses_logos_defaults():
    with patch("langchain_openai.ChatOpenAI") as mock_chat, patch.dict(os.environ, {"LLM_PROVIDER": "logos"}, clear=False):
        for var in ("LLM_BASE_URL", "LLM_MODEL", "LLM_TIMEOUT_SECONDS", "LLM_MAX_RETRIES"):
            os.environ.pop(var, None)
        get_llm()

    kwargs = mock_chat.call_args.kwargs
    assert kwargs["base_url"] == "https://logos.aet.cit.tum.de/v1"
    assert kwargs["model"] == "openai/gpt-oss-120b"
    assert kwargs["timeout"] == 60.0
    assert kwargs["max_retries"] == 2


def test_get_llm_openai_uses_openai_defaults():
    with patch("langchain_openai.ChatOpenAI") as mock_chat, patch.dict(os.environ, {"LLM_PROVIDER": "openai"}, clear=False):
        for var in ("LLM_BASE_URL", "LLM_MODEL"):
            os.environ.pop(var, None)
        get_llm()

    kwargs = mock_chat.call_args.kwargs
    assert kwargs["base_url"] == "https://api.openai.com/v1"
    assert kwargs["model"] == "gpt-4o-mini"


def test_get_llm_openai_compatible_reads_key_lazily():
    # api_key is a callable so a rotated key is picked up per call, not frozen at
    # construction; resolve it inside the patched env to prove it reads live.
    with patch("langchain_openai.ChatOpenAI") as mock_chat, patch.dict(os.environ, {"LLM_PROVIDER": "logos", "LLM_API_KEY": "lg-secret"}):
        get_llm()
        api_key = mock_chat.call_args.kwargs["api_key"]
        assert callable(api_key)
        assert api_key() == "lg-secret"


def test_get_llm_honours_timeout_and_retry_overrides():
    env = {"LLM_PROVIDER": "logos", "LLM_TIMEOUT_SECONDS": "12.5", "LLM_MAX_RETRIES": "5"}
    with patch("langchain_openai.ChatOpenAI") as mock_chat, patch.dict(os.environ, env):
        get_llm()

    kwargs = mock_chat.call_args.kwargs
    assert kwargs["timeout"] == 12.5
    assert kwargs["max_retries"] == 5


def test_get_llm_ollama_uses_ollama_defaults():
    with patch("langchain_ollama.ChatOllama") as mock_ollama, patch.dict(os.environ, {"LLM_PROVIDER": "ollama"}, clear=False):
        for var in ("LLM_MODEL", "OLLAMA_BASE_URL"):
            os.environ.pop(var, None)
        get_llm()

    kwargs = mock_ollama.call_args.kwargs
    assert kwargs["base_url"] == "http://localhost:11434"
    assert kwargs["model"] == "llama3.2"


def test_get_llm_rejects_unknown_provider():
    with patch.dict(os.environ, {"LLM_PROVIDER": "bogus"}):
        with pytest.raises(ValueError, match="bogus"):
            get_llm()


# --- get_model_name ---


def test_get_model_name_per_provider_defaults():
    with patch.dict(os.environ, {"LLM_PROVIDER": "logos"}, clear=False):
        os.environ.pop("LLM_MODEL", None)
        assert get_model_name() == "openai/gpt-oss-120b"

    with patch.dict(os.environ, {"LLM_PROVIDER": "openai"}, clear=False):
        os.environ.pop("LLM_MODEL", None)
        assert get_model_name() == "gpt-4o-mini"

    with patch.dict(os.environ, {"LLM_PROVIDER": "ollama", "LLM_MODEL": "llama3.1"}):
        assert get_model_name() == "llama3.1"


def test_get_model_name_unknown_provider_returns_unknown():
    with patch.dict(os.environ, {"LLM_PROVIDER": "bogus"}):
        assert get_model_name() == "unknown"


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
