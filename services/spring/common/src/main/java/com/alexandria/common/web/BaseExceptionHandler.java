package com.alexandria.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

public abstract class BaseExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(BaseExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e, HttpServletRequest request) {
        log.warn("IllegalArgumentException at {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return ResponseEntity.badRequest().body(new ErrorResponse("BAD_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream().map(err -> new FieldError(err.getField(), err.getDefaultMessage())).toList();
        log.debug("Validation failed: {}", fieldErrors);
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", "Request validation failed", fieldErrors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        List<FieldError> fieldErrors = e.getConstraintViolations().stream().map(v -> new FieldError(v.getPropertyPath().toString(), v.getMessage())).toList();
        log.debug("Constraint violation: {}", fieldErrors);
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", "Request validation failed", fieldErrors));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidation(HandlerMethodValidationException e) {
        List<FieldError> fieldErrors = e.getParameterValidationResults().stream().flatMap(r -> r.getResolvableErrors().stream().map(err -> new FieldError(r.getMethodParameter().getParameterName(), err.getDefaultMessage()))).toList();
        log.debug("Handler-method validation failed: {}", fieldErrors);
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", "Request validation failed", fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(HttpMessageNotReadableException e) {
        log.debug("Malformed request body: {}", e.getMessage());
        return ResponseEntity.badRequest().body(new ErrorResponse("MALFORMED_JSON", "Malformed request body"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String expected = e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "expected type";
        String message = "Parameter '" + e.getName() + "' must be a valid " + expected;
        log.debug("Type mismatch on parameter '{}': {}", e.getName(), e.getValue());
        return ResponseEntity.badRequest().body(new ErrorResponse("TYPE_MISMATCH", message));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException e) {
        log.debug("Missing request parameter '{}'", e.getParameterName());
        return ResponseEntity.badRequest().body(new ErrorResponse("MISSING_PARAMETER", "Required parameter '" + e.getParameterName() + "' is missing"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        log.debug("Method not allowed: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(new ErrorResponse("METHOD_NOT_ALLOWED", e.getMessage()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException e) {
        log.debug("Unsupported media type: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(new ErrorResponse("UNSUPPORTED_MEDIA_TYPE", e.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        log.debug("No resource found: {}", e.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("NOT_FOUND", "Resource not found"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        log.warn("Access denied at {} {} (user={}): {}", request.getMethod(), request.getRequestURI(), request.getRemoteUser(), e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse("FORBIDDEN", "Access denied"));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException e, HttpServletRequest request) {
        log.warn("Authentication failed at {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("UNAUTHORIZED", "Authentication required"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {} {} (user={})", request.getMethod(), request.getRequestURI(), request.getRemoteUser(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("INTERNAL_ERROR", "Internal server error"));
    }
}
