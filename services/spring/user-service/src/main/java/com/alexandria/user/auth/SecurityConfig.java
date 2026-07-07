package com.alexandria.user.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationConverter jwtAuthConverter;
    private final OidcUserFilter oidcUserFilter;

    public SecurityConfig(JwtAuthenticationConverter jwtAuthConverter, OidcUserFilter oidcUserFilter) {
        this.jwtAuthConverter = jwtAuthConverter;
        this.oidcUserFilter = oidcUserFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable()).sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).authorizeHttpRequests(auth -> auth.requestMatchers("/user-service/docs", "/user-service/swagger-ui/**", "/user-service/v3/api-docs/**", "/user-service/v3/api-docs.yaml").permitAll().requestMatchers("/actuator/**").permitAll().requestMatchers("/user-service/hello").permitAll().anyRequest().authenticated()
        ).oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter))
        ).addFilterAfter(oidcUserFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }
}
