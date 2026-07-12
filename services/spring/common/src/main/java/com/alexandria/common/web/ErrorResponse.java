package com.alexandria.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** Unified error payload returned by every Alexandria Spring service. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, List<FieldError> fieldErrors) {
    public ErrorResponse(String code, String message) {
        this(code, message, null);
    }
}
