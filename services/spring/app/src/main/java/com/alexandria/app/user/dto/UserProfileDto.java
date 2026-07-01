package com.alexandria.app.user.dto;

import java.time.Instant;
import java.util.UUID;

public record UserProfileDto(
        UUID id,
        String oidcSubject,
        String username,
        String email,
        Instant createdAt,
        String preferences) {
}
