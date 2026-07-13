"""LLM provider factory.

Controlled by environment variables:

  LLM_PROVIDER   "logos" (default) | "openai" | "ollama"

OpenAI-compatible (logos / openai):
  LLM_BASE_URL   base URL of the API
                 logos default: https://logos.aet.cit.tum.de/v1
                 openai default: https://api.openai.com/v1
  LLM_API_KEY    API key (lg-... for Logos, sk-... for OpenAI)
  LLM_MODEL      model id
                 logos default: openai/gpt-oss-120b
                 openai default: gpt-4o-mini

Ollama (local):
  OLLAMA_BASE_URL  default: http://localhost:11434
  OLLAMA_MODEL     default: llama3.2

The Logos instance is only reachable from the TUM network or via eduVPN.
"""

import os
from typing import Any

from langchain_core.language_models.chat_models import BaseChatModel

from app.env import float_env

_LOGOS_BASE_URL = "https://logos.aet.cit.tum.de/v1"
_OPENAI_BASE_URL = "https://api.openai.com/v1"

_DEFAULT_LOGOS_MODEL = "openai/gpt-oss-120b"
_DEFAULT_OPENAI_MODEL = "gpt-4o-mini"
_DEFAULT_OLLAMA_MODEL = "llama3.2"

# Per-call ceiling. Generous because long-document calls are slow; it's there to
# stop a hung provider wedging a request, not to enforce a tight SLA.
_DEFAULT_TIMEOUT_SECONDS = 60.0

# Client-side retries on transient failures (timeouts, 429, 5xx), with backoff.
_DEFAULT_MAX_RETRIES = 2


def get_provider() -> str:
    """Return the configured chat provider ("logos", "openai", or "ollama")."""
    return os.getenv("LLM_PROVIDER", "logos").lower()


def _timeout_seconds() -> float:
    return float_env("LLM_TIMEOUT_SECONDS", _DEFAULT_TIMEOUT_SECONDS, minimum=0.0)


def _max_retries() -> int:
    from app.env import int_env

    return int_env("LLM_MAX_RETRIES", _DEFAULT_MAX_RETRIES, minimum=0)


def get_llm() -> BaseChatModel:
    """Return a configured LLM instance based on environment variables."""
    provider = get_provider()

    if provider in ("logos", "openai"):
        from langchain_openai import ChatOpenAI

        if provider == "logos":
            base_url = os.getenv("LLM_BASE_URL", _LOGOS_BASE_URL)
            model = os.getenv("LLM_MODEL", _DEFAULT_LOGOS_MODEL)
        else:
            base_url = os.getenv("LLM_BASE_URL", _OPENAI_BASE_URL)
            model = os.getenv("LLM_MODEL", _DEFAULT_OPENAI_MODEL)

        return ChatOpenAI(
            base_url=base_url,
            api_key=lambda: os.getenv("LLM_API_KEY", ""),
            model=model,
            timeout=_timeout_seconds(),
            max_retries=_max_retries(),
        )

    if provider == "ollama":
        from langchain_ollama import ChatOllama

        base_url = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
        model = os.getenv("LLM_MODEL", _DEFAULT_OLLAMA_MODEL)
        return ChatOllama(base_url=base_url, model=model)

    raise ValueError(f"Unknown LLM_PROVIDER '{provider}'. Use 'logos', 'openai', or 'ollama'.")


def get_model_name() -> str:
    """Return the configured model name (the fallback when the provider response has none)."""
    provider = get_provider()
    if provider == "logos":
        return os.getenv("LLM_MODEL", _DEFAULT_LOGOS_MODEL)
    if provider == "openai":
        return os.getenv("LLM_MODEL", _DEFAULT_OPENAI_MODEL)
    if provider == "ollama":
        return os.getenv("LLM_MODEL", _DEFAULT_OLLAMA_MODEL)
    return "unknown"


def raw_message(result: Any) -> Any:
    """Return the underlying AIMessage (or None).

    ``with_structured_output(..., include_raw=True)`` yields a dict of
    {"raw", "parsed", "parsing_error"}; a plain chat call yields the AIMessage.
    """
    if isinstance(result, dict):
        return result.get("raw")
    return result


def require_parsed(result: Any) -> Any:
    """Return the parsed model from a structured-output result, or fail cleanly.

    ``include_raw=True`` reports a parse failure as ``parsed=None`` rather than
    raising, so map that to a provider failure (502) instead of letting it slip
    through as an AttributeError. A bare parsed object (no ``raw`` wrapper) is
    passed through as-is.
    """
    parsed = result["parsed"] if isinstance(result, dict) and "parsed" in result else result
    if parsed is None:
        from app.errors import ProviderError

        raise ProviderError("the AI provider returned an unparsable response")
    return parsed


def model_name_from_result(result: Any, fallback: str) -> str:
    """Read the model the provider actually reported, falling back to config.

    The OpenAI-compatible providers put the served model in the response
    metadata as ``model_name`` (or ``model``); Ollama and any provider that
    omits it fall back to the configured name.
    """
    message = raw_message(result)
    metadata = getattr(message, "response_metadata", None) or {}
    return metadata.get("model_name") or metadata.get("model") or fallback
