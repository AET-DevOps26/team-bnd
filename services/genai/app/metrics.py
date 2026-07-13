"""Custom Prometheus metrics for the GenAI service.

The instrumentator already exposes HTTP request rate, latency, and process
CPU/memory. These add what those can't show: which model ran, how long the model
call itself took (not the whole HTTP round trip), and how often a provider call
failed.

They register on the default Prometheus registry, which the instrumentator
serves at /genai/metrics, so no extra wiring is needed.
"""

from prometheus_client import Counter, Histogram

# Model/embedding calls per (operation, provider, model), split by outcome.
# operation is "chat" or "embedding"; status is "ok", "error", or "timeout".
MODEL_REQUESTS = Counter(
    "genai_model_requests_total",
    "Model and embedding calls, labelled by operation, provider, model, and outcome.",
    ("operation", "provider", "model", "status"),
)

# Time in the model/embedding call itself, measured around the client call not
# the HTTP handler. Buckets span sub-second embeds up to slow long-document
# generations.
MODEL_LATENCY = Histogram(
    "genai_model_request_duration_seconds",
    "Latency of a single model or embedding call, measured separately from HTTP latency.",
    ("operation", "provider", "model"),
    buckets=(0.25, 0.5, 1, 2, 4, 8, 15, 30, 60, 120),
)

# Bumped when an oversized document is truncated before a model call.
DOCUMENT_TRUNCATED = Counter(
    "genai_document_truncated_total",
    "Documents truncated because they exceeded the model input character cap.",
    ("endpoint",),
)


def record_model_call(operation: str, provider: str, model: str, status: str, duration_seconds: float) -> None:
    """Record one model/embedding call: bump the counter and observe its latency."""
    MODEL_REQUESTS.labels(operation, provider, model, status).inc()
    MODEL_LATENCY.labels(operation, provider, model).observe(duration_seconds)


def record_truncation(endpoint: str) -> None:
    """Count a document that was truncated to fit the input cap on ``endpoint``."""
    DOCUMENT_TRUNCATED.labels(endpoint).inc()
