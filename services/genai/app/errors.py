"""Shared error schema and exception handling.

Every error uses the same {code, message, fieldErrors} shape as the Spring
services, not FastAPI's default {detail: ...}.

The OpenAPI components for it are injected by hand (see customize_openapi) so
they match the Spring definitions exactly. `redocly join` merges the four
service specs and rejects two same-named components that differ, and a
pydantic-generated schema carries extra title/type/required keys the Spring ones
don't.
"""

import copy

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.openapi.utils import get_openapi
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

# Kept in sync (by value) with the Spring services' schemas so redocly join
# dedupes them into one shared component instead of flagging a conflict.
_ERROR_RESPONSE_SCHEMA = {
    "properties": {
        "code": {"type": "string"},
        "message": {"type": "string"},
        "fieldErrors": {
            "type": "array",
            "items": {"$ref": "#/components/schemas/FieldError"},
        },
    },
}
_FIELD_ERROR_SCHEMA = {
    "properties": {
        "field": {"type": "string"},
        "message": {"type": "string"},
    },
}

_ERROR_CONTENT = {"application/json": {"schema": {"$ref": "#/components/schemas/ErrorResponse"}}}


class GenAiError(Exception):
    """An error we can map onto the shared schema with a chosen status code."""

    def __init__(self, message: str, *, code: str, status_code: int) -> None:
        super().__init__(message)
        self.message = message
        self.code = code
        self.status_code = status_code


class ProviderError(GenAiError):
    """The model or embedding provider failed (auth, rate limit, bad response, unreachable)."""

    def __init__(self, message: str = "the AI provider failed to process the request") -> None:
        super().__init__(message, code="provider_error", status_code=502)


class ProviderTimeoutError(GenAiError):
    """The model or embedding provider did not respond within the timeout."""

    def __init__(self, message: str = "the AI provider did not respond in time") -> None:
        super().__init__(message, code="provider_timeout", status_code=504)


# Status code -> the stable string put in the error body. Unlisted statuses
# fall back to a generic code.
_STATUS_CODE_NAMES = {
    400: "bad_request",
    404: "not_found",
    415: "unsupported_media_type",
    422: "validation_error",
    500: "internal_error",
    502: "provider_error",
    503: "service_unavailable",
    504: "provider_timeout",
}


def _body(code: str, message: str, field_errors: list[dict] | None = None) -> dict:
    return {"code": code, "message": message, "fieldErrors": field_errors or []}


def _field_path(location: tuple) -> str:
    # Drop the leading "body"/"query"/... segment so the field reads like the
    # request field the client sent (e.g. "objectKey", "maxEntities").
    parts = [str(p) for p in location if p not in ("body", "query", "path")]
    return ".".join(parts)


def register_error_handlers(app: FastAPI) -> None:
    """Install handlers that render every error as the shared schema."""

    @app.exception_handler(GenAiError)
    async def _genai_error(_: Request, exc: GenAiError) -> JSONResponse:
        return JSONResponse(status_code=exc.status_code, content=_body(exc.code, exc.message))

    @app.exception_handler(RequestValidationError)
    async def _validation_error(_: Request, exc: RequestValidationError) -> JSONResponse:
        field_errors = [{"field": _field_path(e.get("loc", ())), "message": e.get("msg", "")} for e in exc.errors()]
        return JSONResponse(status_code=422, content=_body("validation_error", "request validation failed", field_errors))

    @app.exception_handler(StarletteHTTPException)
    async def _http_error(_: Request, exc: StarletteHTTPException) -> JSONResponse:
        code = _STATUS_CODE_NAMES.get(exc.status_code, "error")
        return JSONResponse(status_code=exc.status_code, content=_body(code, str(exc.detail)))

    @app.exception_handler(Exception)
    async def _unhandled_error(_: Request, exc: Exception) -> JSONResponse:
        return JSONResponse(status_code=500, content=_body("internal_error", "an unexpected error occurred"))


def customize_openapi(app: FastAPI) -> None:
    """Swap FastAPI's default validation schema for the shared ErrorResponse.

    Removes the auto-generated HTTPValidationError/ValidationError components,
    injects ErrorResponse/FieldError, and points every error response at them.
    AI endpoints additionally advertise 500/502/504 so the provider-failure
    contract shows up in the spec.
    """

    def build() -> dict:
        if app.openapi_schema:
            return app.openapi_schema
        schema = get_openapi(
            title=app.title,
            version=app.version,
            description=app.description,
            routes=app.routes,
            tags=app.openapi_tags,
            servers=app.servers,
        )
        components = schema.setdefault("components", {}).setdefault("schemas", {})
        components.pop("HTTPValidationError", None)
        components.pop("ValidationError", None)
        components["ErrorResponse"] = copy.deepcopy(_ERROR_RESPONSE_SCHEMA)
        components["FieldError"] = copy.deepcopy(_FIELD_ERROR_SCHEMA)
        _rewire_error_responses(schema)
        app.openapi_schema = schema
        return schema

    app.openapi = build


def _rewire_error_responses(schema: dict) -> None:
    for path_item in schema.get("paths", {}).values():
        for operation in path_item.values():
            if not isinstance(operation, dict) or "responses" not in operation:
                continue
            responses = operation["responses"]
            for status, response in responses.items():
                if status[:1] in ("4", "5"):
                    response["content"] = copy.deepcopy(_ERROR_CONTENT)
            if "ai" in operation.get("tags", []):
                for status, description in (("500", "Internal error"), ("502", "AI provider error"), ("504", "AI provider timeout")):
                    responses.setdefault(status, {"description": description, "content": copy.deepcopy(_ERROR_CONTENT)})
