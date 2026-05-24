package com.alexandria.app.exception;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(UUID id) {
        super("User not found: " + id);
    }

    public UserNotFoundException(String oidcSubject) {
        super("User not found for OIDC subject: " + oidcSubject);
    }
}
