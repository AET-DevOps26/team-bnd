package com.alexandria.app.dto;

import jakarta.validation.constraints.*;

import java.util.UUID;

public record AuthResponse(String token, UUID userId) {
}