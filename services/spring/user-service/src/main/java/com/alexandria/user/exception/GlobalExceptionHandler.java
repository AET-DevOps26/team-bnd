package com.alexandria.user.exception;

import com.alexandria.common.web.BaseExceptionHandler;
import com.alexandria.common.web.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps user-service specific exceptions onto the shared ErrorResponse schema, on top of the
 * common handlers in BaseExceptionHandler. A failed cross-service delete surfaces as 502 so
 * the caller knows to retry rather than assuming the account is fully gone.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends BaseExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException e) {
        log.warn("User not found: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(InvalidCredentialsException e) {
        log.warn("Invalid credentials: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("UNAUTHORIZED", e.getMessage()));
    }

    @ExceptionHandler(UserDeletionException.class)
    public ResponseEntity<ErrorResponse> handleUserDeletionFailed(UserDeletionException e) {
        log.warn("User deletion cleanup failed: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ErrorResponse("USER_DELETE_FAILED", "Could not fully delete the user account, please retry"));
    }
}
