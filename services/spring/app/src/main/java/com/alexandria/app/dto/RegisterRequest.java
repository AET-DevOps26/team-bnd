package com.alexandria.app.dto;

import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank String username,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 10) String password
) {
}