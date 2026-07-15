"""OpenTelemetry tracing setup for the GenAI service.

Tracing is opt-in. Unless ``OTEL_EXPORTER_OTLP_ENDPOINT`` is set the service
keeps the default no-op tracer, so the spans created around model calls (see
app/model_calls.py) cost effectively nothing and nothing is exported. Point that
variable at a collector to turn it on; the docker-compose.tracing.yml overlay
wires it to a local Jaeger.

The instrumentation is intentionally shallow: FastAPI request spans plus one
span per model/embedding call. That is enough to see where a slow /genai/ask
goes -- the query embed versus the chat call -- without a LangChain callback
integration, which doesn't support langchain 1.x yet.
"""

import os

from fastapi import FastAPI
from opentelemetry import trace
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor

# The Prometheus scrape and the health probe fire on a timer and would bury the
# actual AI work, so we don't open a span for them.
_EXCLUDED_URLS = "genai/metrics,genai/health"


def tracing_enabled() -> bool:
    """Return whether an OTLP endpoint is configured (tracing is opt-in)."""
    return bool(os.getenv("OTEL_EXPORTER_OTLP_ENDPOINT"))


def setup_tracing(app: FastAPI, *, service_name: str, service_version: str) -> None:
    """Export OTLP traces and instrument FastAPI when an endpoint is configured.

    A no-op when ``OTEL_EXPORTER_OTLP_ENDPOINT`` is unset, so the default stack
    and the test suite never try to reach a collector. ``OTEL_SERVICE_NAME``
    overrides the service name that shows up in the trace UI.
    """
    if not tracing_enabled():
        return

    resource = Resource.create(
        {
            "service.name": os.getenv("OTEL_SERVICE_NAME", service_name),
            "service.version": service_version,
        }
    )
    provider = TracerProvider(resource=resource)
    # The exporter reads the endpoint (and any headers) from the standard OTEL_*
    # environment variables, so the collector address lives in compose, not here.
    provider.add_span_processor(BatchSpanProcessor(OTLPSpanExporter()))
    trace.set_tracer_provider(provider)

    FastAPIInstrumentor.instrument_app(app, excluded_urls=_EXCLUDED_URLS)
