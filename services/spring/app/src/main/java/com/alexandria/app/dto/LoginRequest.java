package com.alexandria.app.dto;

import jakarta.validation.constraints.*;

public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password
) {
}