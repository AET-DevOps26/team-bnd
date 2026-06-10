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

from langchain_core.language_models.chat_models import BaseChatModel

_LOGOS_BASE_URL = "https://logos.aet.cit.tum.de/v1"
_OPENAI_BASE_URL = "https://api.openai.com/v1"

_DEFAULT_LOGOS_MODEL = "openai/gpt-oss-120b"
_DEFAULT_OPENAI_MODEL = "gpt-4o-mini"
_DEFAULT_OLLAMA_MODEL = "llama3.2"


def get_llm() -> BaseChatModel:
    """Return a configured LLM instance based on environment variables."""
    provider = os.getenv("LLM_PROVIDER", "logos").lower()

    if provider in ("logos", "openai"):
        from langchain_openai import ChatOpenAI

        if provider == "logos":
            base_url = os.getenv("LLM_BASE_URL", _LOGOS_BASE_URL)
            model = os.getenv("LLM_MODEL", _DEFAULT_LOGOS_MODEL)
        else:
            base_url = os.getenv("LLM_BASE_URL", _OPENAI_BASE_URL)
            model = os.getenv("LLM_MODEL", _DEFAULT_OPENAI_MODEL)

        return ChatOpenAI(base_url=base_url, api_key=lambda: os.getenv("LLM_API_KEY", ""), model=model)

    if provider == "ollama":
        from langchain_ollama import ChatOllama

        base_url = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
        model = os.getenv("LLM_MODEL", _DEFAULT_OLLAMA_MODEL)
        return ChatOllama(base_url=base_url, model=model)

    raise ValueError(f"Unknown LLM_PROVIDER '{provider}'. Use 'logos', 'openai', or 'ollama'.")


def get_model_name() -> str:
    """Return the configured model name for use in API responses."""
    provider = os.getenv("LLM_PROVIDER", "logos").lower()
    if provider == "logos":
        return os.getenv("LLM_MODEL", _DEFAULT_LOGOS_MODEL)
    if provider == "openai":
        return os.getenv("LLM_MODEL", _DEFAULT_OPENAI_MODEL)
    if provider == "ollama":
        return os.getenv("LLM_MODEL", _DEFAULT_OLLAMA_MODEL)
    return "unknown"
