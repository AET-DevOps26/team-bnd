package com.alexandria.app.user;

import com.alexandria.app.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setupUserService() {
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Test
    void unit_user_registerSuccessCreatesUserWithHashedPassword() {
        var request = new RegisterRequest("testuser", "test@example.com", "password123");
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed_password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.register(request);

        assertThat(result.getUsername()).isEqualTo("testuser");
        assertThat(result.getEmail()).isEqualTo("test@example.com");
        assertThat(result.getPasswordHash()).isEqualTo("hashed_password");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void unit_user_registerDuplicateUsernameThrowsException() {
        var request = new RegisterRequest("existing", "new@example.com", "password123");
        when(userRepository.existsByUsername("existing")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Username already taken.");

        verify(userRepository, never()).save(any());
    }

    @Test
    void unit_user_registerDuplicateEmailThrowsException() {
        var request = new RegisterRequest("newuser", "existing@example.com", "password123");
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email already registered.");

        verify(userRepository, never()).save(any());
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
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User with given id does not exist.");

        verify(userRepository, never()).deleteById(any());
    }
}
