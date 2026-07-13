"""Instrumentation shared by every model and embedding call.

Times the call, records metrics, and maps provider failures to the service's
error types. Modules call ``run_model_call`` instead of the client directly, so
a timeout, auth failure, rate limit, or unreachable Logos endpoint surfaces as a
structured 502/504 rather than an unhandled 500.
"""

import time
from collections.abc import Callable

from app.errors import ProviderError, ProviderTimeoutError
from app.metrics import record_model_call


def _is_timeout(exc: BaseException) -> bool:
    """Walk the exception chain for a timeout, provider-agnostically.

    The OpenAI client raises APITimeoutError, httpx/Ollama raise their own
    timeout types; rather than import and enumerate them all we match the
    built-in TimeoutError and any class whose name mentions "timeout".
    """
    seen: set[int] = set()
    current: BaseException | None = exc
    while current is not None and id(current) not in seen:
        seen.add(id(current))
        if isinstance(current, TimeoutError) or "timeout" in type(current).__name__.lower():
            return True
        current = current.__cause__ or current.__context__
    return False


def run_model_call[T](
    call: Callable[[], T],
    *,
    operation: str,
    provider: str,
    fallback_model: str,
    model_resolver: Callable[[T], str] | None = None,
) -> tuple[T, str]:
    """Run ``call``, recording latency and outcome and mapping failures.

    ``fallback_model`` (the configured name) labels the metric on failure and is
    the default when no ``model_resolver`` is given; on success ``model_resolver``
    reads the model the provider actually reported off the result.

    Returns ``(result, model_name)``. Raises ProviderTimeoutError on a timeout,
    ProviderError on any other failure.
    """
    start = time.perf_counter()
    try:
        result = call()
    except Exception as exc:
        duration = time.perf_counter() - start
        if _is_timeout(exc):
            record_model_call(operation, provider, fallback_model, "timeout", duration)
            raise ProviderTimeoutError() from exc
        record_model_call(operation, provider, fallback_model, "error", duration)
        raise ProviderError() from exc
    duration = time.perf_counter() - start
    model = model_resolver(result) if model_resolver else fallback_model
    record_model_call(operation, provider, model, "ok", duration)
    return result, model
