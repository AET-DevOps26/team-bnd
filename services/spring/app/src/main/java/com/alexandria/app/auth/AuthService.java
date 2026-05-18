package com.alexandria.app.auth;

import com.alexandria.app.dto.AuthResponse;
import com.alexandria.app.dto.LoginRequest;
import com.alexandria.app.exception.InvalidCredentialsException;
import com.alexandria.app.user.User;
import com.alexandria.app.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Handles user authentication and token generation.
 */
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

    /**
     * Authenticates user and returns a JWT.
     *
     * @param request Login credentials.
     * @return AuthResponse with token and user ID.
     * @throws InvalidCredentialsException If credentials are invalid.
     */
    public AuthResponse login(LoginRequest request) {
        User user = userRepository
                .findByUsername(request.username())
                .orElseThrow(InvalidCredentialsException::new);

        if (!encoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getId());
    }

    /**
     * Generates a JWT for authenticated user.
     *
     * @param user Authenticated user.
     * @return JWT token string.
     */
    public String generateToken(User user) {
        return jwtService.generateToken(user);
    }
}