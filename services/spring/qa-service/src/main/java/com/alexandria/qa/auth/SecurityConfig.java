package com.alexandria.qa.auth;

import com.alexandria.common.web.ErrorResponseAccessDeniedHandler;
import com.alexandria.common.web.ErrorResponseAuthenticationEntryPoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

// final (+ proxyBeanMethods=false so Spring won't try to CGLIB-subclass it) closes the
// finalizer-attack window SpotBugs flags via CT_CONSTRUCTOR_THROW on the constructor.
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public final class SecurityConfig {

    private final JwtAuthenticationConverter jwtAuthConverter;
    private final ErrorResponseAuthenticationEntryPoint authenticationEntryPoint;
    private final ErrorResponseAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(JwtAuthenticationConverter jwtAuthConverter, ObjectProvider<ObjectMapper> objectMapper) {
        this.jwtAuthConverter = jwtAuthConverter;
        ObjectMapper mapper = objectMapper.getIfAvailable(ObjectMapper::new);
        this.authenticationEntryPoint = new ErrorResponseAuthenticationEntryPoint(mapper);
        this.accessDeniedHandler = new ErrorResponseAccessDeniedHandler(mapper);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable()).sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).authorizeHttpRequests(auth -> auth.requestMatchers("/qa-service/docs", "/qa-service/swagger-ui/**", "/qa-service/v3/api-docs/**", "/qa-service/v3/api-docs.yaml").permitAll().requestMatchers("/actuator/**").permitAll().requestMatchers("/qa-service/hello").permitAll()
                // Internal fan-out endpoints are only reachable from the alexandria container network
                .requestMatchers("/internal/**").permitAll().anyRequest().authenticated()
        ).oauth2ResourceServer(oauth2 -> oauth2.authenticationEntryPoint(authenticationEntryPoint).accessDeniedHandler(accessDeniedHandler).jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter))
        ).exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint).accessDeniedHandler(accessDeniedHandler)
        );

        return http.build();
    }
}
