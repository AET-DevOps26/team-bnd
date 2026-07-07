package com.alexandria.qa.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;

@Configuration
@Profile({"openapi", "test"})
public class MockJwtDecoderConfig {

    @Bean
    public JwtDecoder jwtDecoder() {
        return token -> Jwt.withTokenValue(token).header("alg", "none").claim("sub", "mock-user").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
    }
}
