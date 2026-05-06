FROM ghcr.io/astral-sh/uv:python3.14-alpine AS builder

ENV PYTHONFAULTHANDLER=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONHASHSEED=random \
    UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    UV_PYTHON_DOWNLOADS=0 \
    UV_CACHE_DIR="/opt/uv-cache" \
    UV_FROZEN=1 \
    TZ="Europe/Berlin"

WORKDIR /app

RUN --mount=type=cache,target=$UV_CACHE_DIR \
    --mount=type=bind,source=uv.lock,target=uv.lock \
    --mount=type=bind,source=pyproject.toml,target=pyproject.toml \
    uv sync --no-install-project --no-dev

COPY app/ ./app/

RUN --mount=type=cache,target=$UV_CACHE_DIR \
    --mount=type=bind,source=uv.lock,target=uv.lock \
    --mount=type=bind,source=pyproject.toml,target=pyproject.toml \
    uv sync --frozen --no-dev

FROM python:3.14-alpine AS runtime

ARG RELEASE_TYPE=staging
ARG APP_VERSION=dev
ARG IMAGE_TAG=unknown

ENV PYTHONFAULTHANDLER=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONHASHSEED=random \
    TZ="Europe/Berlin" \
    VIRTUAL_ENV="/app/.venv" \
    PATH="/app/.venv/bin:$PATH"

WORKDIR /app

RUN adduser -S -u 1000 -H genai
WORKDIR /app
COPY --from=builder --chown=genai:genai /app /app

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/genai/health').read()" || exit 1

ENTRYPOINT ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
# ENTRYPOINT ["sleep", "600"]
