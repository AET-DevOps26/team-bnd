"""Unit tests for the model-call instrumentation wrapper."""

import pytest
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import SimpleSpanProcessor
from opentelemetry.sdk.trace.export.in_memory_span_exporter import InMemorySpanExporter
from opentelemetry.trace import StatusCode

from app import model_calls
from app.errors import ProviderError, ProviderTimeoutError
from app.model_calls import run_model_call


@pytest.fixture
def span_exporter(monkeypatch):
    """Route the wrapper's spans into an in-memory exporter for assertions."""
    exporter = InMemorySpanExporter()
    provider = TracerProvider()
    provider.add_span_processor(SimpleSpanProcessor(exporter))
    monkeypatch.setattr(model_calls.trace, "get_tracer", lambda *args, **kwargs: provider.get_tracer("test"))
    return exporter


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


# --- tracing spans ---


def test_success_records_span_with_model_attributes(span_exporter):
    run_model_call(
        lambda: "value",
        operation="chat",
        provider="logos",
        fallback_model="cfg-model",
        model_resolver=lambda _: "served-model",
    )

    spans = span_exporter.get_finished_spans()
    assert len(spans) == 1
    span = spans[0]
    assert span.name == "genai.chat"
    assert span.attributes["gen_ai.operation.name"] == "chat"
    assert span.attributes["gen_ai.system"] == "logos"
    assert span.attributes["gen_ai.request.model"] == "cfg-model"
    assert span.attributes["gen_ai.response.model"] == "served-model"
    assert span.attributes["genai.outcome"] == "ok"
    # success leaves the status unset; only failures are marked as errors
    assert span.status.status_code == StatusCode.UNSET


def test_failure_span_marks_error_and_records_exception(span_exporter):
    def boom():
        raise RuntimeError("bad gateway response")

    with pytest.raises(ProviderError):
        run_model_call(boom, operation="embedding", provider="openai", fallback_model="cfg")

    span = span_exporter.get_finished_spans()[0]
    assert span.attributes["genai.outcome"] == "error"
    assert span.status.status_code == StatusCode.ERROR
    assert any(event.name == "exception" for event in span.events)


def test_timeout_span_marks_outcome_timeout(span_exporter):
    def slow():
        raise TimeoutError("timed out")

    with pytest.raises(ProviderTimeoutError):
        run_model_call(slow, operation="chat", provider="logos", fallback_model="cfg")

    span = span_exporter.get_finished_spans()[0]
    assert span.attributes["genai.outcome"] == "timeout"
    assert span.status.status_code == StatusCode.ERROR
