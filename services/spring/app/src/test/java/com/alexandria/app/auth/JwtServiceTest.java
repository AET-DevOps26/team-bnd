package com.alexandria.app.auth;

import com.alexandria.app.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;

    private User createUserWithId(UUID id) {
        User user = new User("testuser", "test@example.com", "hashed_password");
        try {
            Field field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return user;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @BeforeEach
    void setupJwtService() throws Exception {
        jwtService = new JwtService();
        setField(jwtService, "secret", "test-secret-key-that-is-at-least-32-bytes-long-for-hmac");
        setField(jwtService, "expiration", 3600000L); // 1 hour
    }

    @Test
    void unit_jwt_tokenContainsUserId() {
        User user = createUserWithId(UUID.randomUUID());

        String token = jwtService.generateToken(user);
        assertThat(token).isNotBlank();

        UUID extractedId = jwtService.extractUserId(token);
        assertThat(extractedId).isEqualTo(user.getId());
    }

    @Test
    void unit_jwt_extractUserIdReturnsCorrectUserId() {
        UUID userId = UUID.randomUUID();
        User user = createUserWithId(userId);

        String token = jwtService.generateToken(user);

        UUID extractedId = jwtService.extractUserId(token);
        assertThat(extractedId).isEqualTo(userId);
    }

    @Test
    void unit_jwt_extractExpirationReturnsCorrectExpiration() {
        User user = createUserWithId(UUID.randomUUID());
        String token = jwtService.generateToken(user);

        Date expiration = jwtService.extractExpiration(token);

        assertThat(expiration).isAfter(new Date());
    }

    @Test
    void unit_jwt_validateTokenTrueForValidToken() {
        UUID userId = UUID.randomUUID();
        User user = createUserWithId(userId);
        String token = jwtService.generateToken(user);

        Boolean isValid = jwtService.validateToken(token, user);

        assertThat(isValid).isTrue();
    }

    @Test
    void unit_jwt_validateTokenFalseForInvalidUserId() {
        User originalUser = createUserWithId(UUID.randomUUID());
        User differentUser = createUserWithId(UUID.randomUUID());
        String token = jwtService.generateToken(originalUser);

        Boolean isValid = jwtService.validateToken(token, differentUser);

        assertThat(isValid).isFalse();
    }

    @Test
    void unit_jwt_validateTokenFalseForExpiredToken() throws Exception {
        JwtService shortLivedService = new JwtService();
        setField(shortLivedService, "secret", "test-secret-key-that-is-at-least-32-bytes-long-for-hmac");
        setField(shortLivedService, "expiration", -1000L);

        User user = createUserWithId(UUID.randomUUID());
        String token = shortLivedService.generateToken(user);

        assertThatThrownBy(() -> jwtService.extractUserId(token))
                .isInstanceOf(Exception.class);
    }

    @Test
    void unit_jwt_extractUserIdThrowsExceptionForInvalidToken() {
        assertThatThrownBy(() -> jwtService.extractUserId("invalid.token.here"))
                .isInstanceOf(Exception.class);
    }
}
