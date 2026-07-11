package com.alexandria.common.web;

/** Single field-level validation error attached to an {@link ErrorResponse}. */
public record FieldError(String field, String message) {
}
