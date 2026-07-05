package com.alexandria.app.user.dto;

import jakarta.validation.constraints.Size;

public record UpdatePreferencesRequest(
        Boolean darkTheme,
        @Size(min = 2, max = 5, message = "Language code must be between 2 and 5 characters")
        String language) {
}
