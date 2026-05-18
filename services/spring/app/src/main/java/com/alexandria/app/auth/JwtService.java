package com.alexandria.app.auth;

import com.alexandria.app.user.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

/*
   Note: The Auth/JWT structure was modelled after
   https://medium.com/@th.chousiadas/spring-security-architecture-of-jwt-authentication-a7967a8ee309
 */

/**
 * JWT token generation and validation.
 */
@Service
public class JwtService {
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private SecretKey signingKey;

    /**
     * Initializes signing key lazily.
     */
    private SecretKey getSigningKey() {
        if (signingKey == null) {
            signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        }
        return signingKey;
    }

    /**
     * Creates JWT with user ID as subject.
     *
     * @param user User to generate token for.
     * @return Signed JWT string.
     */
    public String generateToken(User user) {
        return Jwts.builder()
                .subject(user.getId().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Extracts user ID from token.
     *
     * @param token JWT string.
     * @return User UUID.
     */
    public UUID extractUserId(String token) {
        return UUID.fromString(Jwts.parser().verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload().getSubject());
    }

    /**
     * Extracts expiration date from token.
     */
    public Date extractExpiration(String token) {
        return Jwts.parser().verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token).getPayload().getExpiration();
    }

    /**
     * Validates token belongs to given user and has not expired.
     *
     * @param token JWT string.
     * @param user  User to validate against.
     * @return True if token is valid, false otherwise.
     */
    public Boolean validateToken(String token, User user) {
        final UUID userId = extractUserId(token);
        final Date expiration = extractExpiration(token);
        return userId.equals(user.getId()) && !expiration.before(new Date());
    }
}