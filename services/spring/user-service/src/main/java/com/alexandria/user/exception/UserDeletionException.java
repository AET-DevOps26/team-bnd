package com.alexandria.user.exception;

import java.util.List;
import java.util.UUID;

public class UserDeletionException extends RuntimeException {
    public UserDeletionException(UUID id, List<String> failedServices) {
        super("Failed to delete user " + id + ": downstream cleanup failed for "
                + String.join(", ", failedServices));
    }
}
