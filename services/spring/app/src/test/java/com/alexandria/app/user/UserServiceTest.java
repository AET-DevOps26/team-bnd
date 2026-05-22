package com.alexandria.app.user;

import com.alexandria.app.exception.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setupUserService() {
        userService = new UserService(userRepository);
    }

    @Test
    void unit_user_findOrCreateReturnsExistingUser() {
        String oidcSubject = "auth0|123456";
        User existingUser = new User(oidcSubject, "testuser", "test@example.com");
        when(userRepository.findByOidcSubject(oidcSubject)).thenReturn(Optional.of(existingUser));

        User result = userService.findOrCreateByOidcSubject(oidcSubject, "testuser", "test@example.com");

        assertThat(result.getOidcSubject()).isEqualTo(oidcSubject);
        assertThat(result.getUsername()).isEqualTo("testuser");
        verify(userRepository, never()).save(any());
    }

    @Test
    void unit_user_findOrCreateCreatesNewUser() {
        String oidcSubject = "auth0|789012";
        when(userRepository.findByOidcSubject(oidcSubject)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.findOrCreateByOidcSubject(oidcSubject, "newuser", "new@example.com");

        assertThat(result.getOidcSubject()).isEqualTo(oidcSubject);
        assertThat(result.getUsername()).isEqualTo("newuser");
        assertThat(result.getEmail()).isEqualTo("new@example.com");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void unit_user_findByOidcSubjectReturnsUser() {
        String oidcSubject = "auth0|123456";
        User user = new User(oidcSubject, "testuser", "test@example.com");
        when(userRepository.findByOidcSubject(oidcSubject)).thenReturn(Optional.of(user));

        Optional<User> result = userService.findByOidcSubject(oidcSubject);

        assertThat(result).isPresent();
        assertThat(result.get().getOidcSubject()).isEqualTo(oidcSubject);
    }

    @Test
    void unit_user_findByOidcSubjectReturnsEmptyWhenNotFound() {
        when(userRepository.findByOidcSubject("nonexistent")).thenReturn(Optional.empty());

        Optional<User> result = userService.findByOidcSubject("nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    void unit_user_deleteSuccessDeletesExistingUser() {
        UUID userId = UUID.randomUUID();
        when(userRepository.existsById(userId)).thenReturn(true);

        userService.deleteUser(userId);

        verify(userRepository).deleteById(userId);
    }

    @Test
    void unit_user_deleteNotFoundThrowsException() {
        UUID userId = UUID.randomUUID();
        when(userRepository.existsById(userId)).thenReturn(false);

        assertThatThrownBy(() -> userService.deleteUser(userId))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining(userId.toString());

        verify(userRepository, never()).deleteById(any());
    }
}
