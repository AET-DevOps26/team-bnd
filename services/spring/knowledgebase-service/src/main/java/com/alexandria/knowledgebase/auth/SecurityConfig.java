package com.alexandria.knowledgebase.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationConverter jwtAuthConverter;

    public SecurityConfig(JwtAuthenticationConverter jwtAuthConverter) {
        this.jwtAuthConverter = jwtAuthConverter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable()).sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).authorizeHttpRequests(auth -> auth.requestMatchers("/knowledgebase-service/swagger-ui/**", "/knowledgebase-service/v3/api-docs/**", "/knowledgebase-service/v3/api-docs.yaml").permitAll().requestMatchers("/actuator/**").permitAll().requestMatchers("/knowledgebase-service/hello").permitAll()
                // Internal fan-out endpoints live under a prefix that is not routed
                // publicly, so they are only reachable inside the cluster network
                .requestMatchers("/internal/**").permitAll().anyRequest().authenticated()
        ).oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter))
        );

        return http.build();
    }
}
