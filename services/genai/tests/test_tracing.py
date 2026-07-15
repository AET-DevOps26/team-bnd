"""Tests for the opt-in OpenTelemetry tracing setup."""

from unittest.mock import patch

from fastapi import FastAPI

from app import tracing


def test_tracing_disabled_without_endpoint(monkeypatch):
    monkeypatch.delenv("OTEL_EXPORTER_OTLP_ENDPOINT", raising=False)
    assert tracing.tracing_enabled() is False


def test_tracing_enabled_with_endpoint(monkeypatch):
    monkeypatch.setenv("OTEL_EXPORTER_OTLP_ENDPOINT", "http://jaeger:4318")
    assert tracing.tracing_enabled() is True


def test_setup_tracing_is_noop_when_disabled(monkeypatch):
    monkeypatch.delenv("OTEL_EXPORTER_OTLP_ENDPOINT", raising=False)
    with (
        patch.object(tracing, "FastAPIInstrumentor") as instrumentor,
        patch.object(tracing.trace, "set_tracer_provider") as set_provider,
    ):
        tracing.setup_tracing(FastAPI(), service_name="svc", service_version="1.0.0")

    instrumentor.instrument_app.assert_not_called()
    set_provider.assert_not_called()


def test_setup_tracing_wires_exporter_and_instrumentation_when_enabled(monkeypatch):
    monkeypatch.setenv("OTEL_EXPORTER_OTLP_ENDPOINT", "http://jaeger:4318")
    with (
        patch.object(tracing, "OTLPSpanExporter") as exporter,
        patch.object(tracing, "BatchSpanProcessor") as processor,
        patch.object(tracing, "TracerProvider") as provider_cls,
        patch.object(tracing.trace, "set_tracer_provider") as set_provider,
        patch.object(tracing, "FastAPIInstrumentor") as instrumentor,
    ):
        tracing.setup_tracing(FastAPI(), service_name="svc", service_version="1.0.0")

    exporter.assert_called_once()
    provider = provider_cls.return_value
    provider.add_span_processor.assert_called_once_with(processor.return_value)
    set_provider.assert_called_once_with(provider)
    instrumentor.instrument_app.assert_called_once()
    assert "excluded_urls" in instrumentor.instrument_app.call_args.kwargs
