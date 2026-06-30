"""Environment helpers shared across the service."""

import os


def int_env(name: str, default: int, *, minimum: int) -> int:
    """Read an integer environment variable, failing loudly on bad input.

    Returns the default when the variable is unset or empty. Raises RuntimeError
    when the value isn't an integer or falls below ``minimum`` so a typo surfaces
    at startup rather than as a confusing failure deep in a request.
    """
    raw = os.getenv(name)
    if not raw:
        return default
    try:
        value = int(raw)
    except ValueError as exc:
        raise RuntimeError(f"{name} must be an integer, got {raw!r}") from exc
    if value < minimum:
        raise RuntimeError(f"{name} must be >= {minimum}, got {value}")
    return value
