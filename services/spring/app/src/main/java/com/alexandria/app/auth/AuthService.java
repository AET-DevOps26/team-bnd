package com.alexandria.app.auth;

import com.alexandria.app.dto.AuthResponse;
import com.alexandria.app.dto.LoginRequest;
import com.alexandria.app.user.User;
import com.alexandria.app.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder encoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.encoder = encoder;
        this.jwtService = jwtService;
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository
                .findByUsername(request.username())
                .orElseThrow(
                        () -> new IllegalArgumentException("Invalid credentials.")
                );

        if (!encoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials.");
        }

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getId());
    }

    public String generateToken(User user) {
        return jwtService.generateToken(user);
    }
}