"""Unit tests for the model-call instrumentation wrapper."""

import pytest

from app.errors import ProviderError, ProviderTimeoutError
from app.model_calls import run_model_call


def test_success_returns_result_and_configured_model():
    result, model = run_model_call(lambda: "value", operation="chat", provider="logos", fallback_model="cfg-model")

    assert result == "value"
    assert model == "cfg-model"


def test_model_resolver_overrides_the_fallback_on_success():
    result, model = run_model_call(
        lambda: "value",
        operation="chat",
        provider="logos",
        fallback_model="cfg-model",
        model_resolver=lambda _: "served-model",
    )

    assert result == "value"
    assert model == "served-model"


def test_generic_failure_maps_to_provider_error():
    def boom():
        raise RuntimeError("bad gateway response")

    with pytest.raises(ProviderError):
        run_model_call(boom, operation="chat", provider="logos", fallback_model="cfg")


def test_builtin_timeout_maps_to_provider_timeout():
    def slow():
        raise TimeoutError("timed out")

    with pytest.raises(ProviderTimeoutError):
        run_model_call(slow, operation="embedding", provider="logos", fallback_model="cfg")


def test_timeout_detected_by_class_name():
    class APITimeoutError(Exception):
        pass

    def slow():
        raise APITimeoutError("the request timed out")

    with pytest.raises(ProviderTimeoutError):
        run_model_call(slow, operation="chat", provider="openai", fallback_model="cfg")


def test_timeout_detected_through_the_cause_chain():
    class ConnectionError_(Exception):
        pass

    def slow():
        try:
            raise TimeoutError("inner timeout")
        except TimeoutError as inner:
            raise ConnectionError_("wrapper") from inner

    with pytest.raises(ProviderTimeoutError):
        run_model_call(slow, operation="chat", provider="logos", fallback_model="cfg")
