package com.alexandria.app.auth;

import com.alexandria.app.user.User;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;

import io.jsonwebtoken.security.Keys;

import java.util.Date;
import java.util.UUID;

// similar to https://medium.com/@th.chousiadas/spring-security-architecture-of-jwt-authentication-a7967a8ee309
@Service
public class JwtService {
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private SecretKey signingKey;

    private SecretKey getSigningKey() {
        if (signingKey == null) {
            signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        }
        return signingKey;
    }

    public String generateToken(User user) {
        return Jwts.builder()
                .subject(user.getId().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(Jwts.parser().verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload().getSubject());
    }

    public Date extractExpiration(String token) {
        return Jwts.parser().verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token).getPayload().getExpiration();
    }

    public Boolean validateToken(String token, User user) {
        final UUID userId = extractUserId(token);
        final Date expiration = extractExpiration(token);
        return userId.equals(user.getId()) && !expiration.before(new Date());
    }
}