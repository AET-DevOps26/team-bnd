package com.alexandria.app.auth;

import com.alexandria.app.dto.AuthResponse;
import com.alexandria.app.dto.LoginRequest;
import com.alexandria.app.user.User;
import com.alexandria.app.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    private void setUserId(User user, UUID id) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setupAuthService() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void unit_auth_loginSuccessReturnsTokenAndUserId() {
        var request = new LoginRequest("testuser", "password123");
        var user = new User("testuser", "test@example.com", "hashed_password");

        // use reflection to set the ID since there's no setter
        setUserId(user, UUID.randomUUID());
        
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed_password")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("jwt_token_123");

        AuthResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("jwt_token_123");
        assertThat(response.userId()).isEqualTo(user.getId());
    }

    @Test
    void unit_auth_loginNonexistentUserThrowsException() {
        var request = new LoginRequest("nonexistent", "password123");
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid credentials.");
    }

    @Test
    void unit_auth_loginWrongPasswordThrowsException() {
        var request = new LoginRequest("testuser", "wrong_password");
        var user = new User("testuser", "test@example.com", "hashed_password");
        
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong_password", "hashed_password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid credentials.");
    }

    @Test
    void unit_auth_tokenGenerationDelegatedToJwtService() {
        var user = new User("testuser", "test@example.com", "hashed_password");
        when(jwtService.generateToken(user)).thenReturn("generated_token");

        String token = authService.generateToken(user);

        assertThat(token).isEqualTo("generated_token");
    }
}
