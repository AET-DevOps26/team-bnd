package com.alexandria.app.auth;

import com.alexandria.app.dto.AuthResponse;
import com.alexandria.app.dto.LoginRequest;
import com.alexandria.app.dto.RegisterRequest;
import com.alexandria.app.user.User;
import com.alexandria.app.user.UserRepository;
import com.alexandria.app.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void integration_auth_registerLoginEndToEnd() {
        var registerRequest = new RegisterRequest("integrationuser", "integration@example.com", "securepassword123");
        User registeredUser = userService.register(registerRequest);

        var loginRequest = new LoginRequest("integrationuser", "securepassword123");
        AuthResponse response = authService.login(loginRequest);

        assertThat(response.token()).isNotBlank();
        assertThat(response.userId()).isEqualTo(registeredUser.getId());

        var extractedId = jwtService.extractUserId(response.token());
        assertThat(extractedId).isEqualTo(registeredUser.getId());
    }

    @Test
    void integration_auth_loginWithPasswordStoredInDatabase() {
        var registerRequest = new RegisterRequest("testpw", "mail@example.com", "mypassword123");
        userService.register(registerRequest);

        User storedUser = userRepository.findByUsername("testpw").orElseThrow();
        assertThat(storedUser.getPasswordHash()).isNotEqualTo("mypassword123");

        var loginRequest = new LoginRequest("testpw", "mypassword123");
        AuthResponse response = authService.login(loginRequest);

        assertThat(response.token()).isNotBlank();
    }

    @Test
    void integration_auth_jwtTokenGenerationValid() {
        var registerRequest = new RegisterRequest("jwtuser", "jwt@example.com", "jwt&pwd");
        User user = userService.register(registerRequest);

        String token = jwtService.generateToken(user);

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUserId(token)).isEqualTo(user.getId());
        assertThat(jwtService.validateToken(token, user)).isTrue();
    }

    @Test
    void integration_auth_deleteUserAfterRegistrationInDatabase() {
        var registerRequest = new RegisterRequest("deleteuser", "delete@example.com", "pwddeleteusr");
        User user = userService.register(registerRequest);

        assertThat(userRepository.existsById(user.getId())).isTrue();

        userService.deleteUser(user.getId());

        assertThat(userRepository.existsById(user.getId())).isFalse();
    }
}
