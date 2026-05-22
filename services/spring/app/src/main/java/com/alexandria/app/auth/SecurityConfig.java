package com.alexandria.app.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/*
   Note: The Auth/JWT structure was modelled after
   https://medium.com/th.chousiadas/spring-security-architecture-of-jwt-authentication-a7967a8ee309

   The OAuth2 Resource Server setup after
   https://medium.com/@dev.jefster/oauth2-in-spring-security-understanding-the-client-authorization-server-and-resource-server-e90c14630b20
 */

/**
 * Configures Spring Security with OAuth2 Resource Server.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationConverter jwtAuthConverter;
    private final OidcUserFilter oidcUserFilter;

    public SecurityConfig(JwtAuthenticationConverter jwtAuthConverter, OidcUserFilter oidcUserFilter) {
        this.jwtAuthConverter = jwtAuthConverter;
        this.oidcUserFilter = oidcUserFilter;
    }

    /**
     * Configures security filter chain with OAuth2 resource server.
     * Publicly accessible: Swagger/API, actuator and hello
     * All other endpoints require a valid JWT from OIDC provider.
     *
     * @param http HttpSecurity builder.
     * @return Configured SecurityFilterChain.
     * @throws Exception If configuration fails.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/hello").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter))
                )
                .addFilterAfter(oidcUserFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }
}