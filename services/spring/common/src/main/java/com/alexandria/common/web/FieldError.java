package com.alexandria.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Single field-level validation error attached to an {@link ErrorResponse}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldError(String field, String message) {
}
