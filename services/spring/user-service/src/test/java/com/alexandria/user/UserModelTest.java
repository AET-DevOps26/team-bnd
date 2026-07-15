package com.alexandria.user;

import com.alexandria.common.web.ErrorResponse;
import com.alexandria.user.exception.GlobalExceptionHandler;
import com.alexandria.user.exception.InvalidCredentialsException;
import com.alexandria.user.exception.PreferencesSerializationException;
import com.alexandria.user.exception.UserDeletionException;
import com.alexandria.user.exception.UserNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserModelTest {

    @Test
    void unit_user_entitySetters() {
        User user = new User("oidc|123", "testuser", "test@example.com");
        user.setOidcSubject("oidc|456");
        user.setUsername("renamed");
        user.setEmail("new@example.com");
        user.setPreferences("{\"darkTheme\":true}");

        assertThat(user.getOidcSubject()).isEqualTo("oidc|456");
        assertThat(user.getUsername()).isEqualTo("renamed");
        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(user.getPreferences()).isEqualTo("{\"darkTheme\":true}");
        assertThat(user.getId()).isNull();
        assertThat(user.getCreatedAt()).isNull();
    }

    @Test
    void unit_user_exceptionMessages() {
        UUID id = UUID.randomUUID();
        assertThat(new UserNotFoundException(id)).hasMessageContaining(id.toString());
        assertThat(new UserNotFoundException("oidc|1")).hasMessageContaining("oidc|1");
        assertThat(new InvalidCredentialsException()).hasMessageContaining("Invalid credentials");
        assertThat(new UserDeletionException(id, List.of("qa-service"))).hasMessageContaining("qa-service");
        assertThat(new PreferencesSerializationException("failed", new RuntimeException("cause"))).hasMessage("failed").hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void unit_user_exceptionHandlerMapsStatuses() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        UUID id = UUID.randomUUID();

        ResponseEntity<ErrorResponse> notFound = handler.handleUserNotFound(new UserNotFoundException(id));
        ResponseEntity<ErrorResponse> unauthorized = handler.handleUnauthorized(new InvalidCredentialsException());
        ResponseEntity<ErrorResponse> deletionFailed = handler.handleUserDeletionFailed(new UserDeletionException(id, List.of("qa-service")));

        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(deletionFailed.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }
}
