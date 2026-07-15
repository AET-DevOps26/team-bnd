package com.alexandria.knowledgebase.auth;

import com.alexandria.common.internal.HmacInternalAuthFilter;
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
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless security for the knowledgebase-service.
 *
 * <p>Runs as an OAuth2 resource server: public API calls need a valid JWT, internal
 * fan-out endpoints instead require an HMAC signature (checked by a filter placed before
 * the bearer-token filter). The Swagger docs, actuator and health endpoints are open.
 * Auth failures are rendered through the shared ErrorResponse handlers.
 */
// final (+ proxyBeanMethods=false so Spring won't try to CGLIB-subclass it) closes the
// finalizer-attack window on the constructor.
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public final class SecurityConfig {

    private final JwtAuthenticationConverter jwtAuthConverter;
    private final HmacInternalAuthFilter hmacInternalAuthFilter;
    private final ErrorResponseAuthenticationEntryPoint authenticationEntryPoint;
    private final ErrorResponseAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(JwtAuthenticationConverter jwtAuthConverter, HmacInternalAuthFilter hmacInternalAuthFilter, ObjectProvider<ObjectMapper> objectMapper) {
        this.jwtAuthConverter = jwtAuthConverter;
        this.hmacInternalAuthFilter = hmacInternalAuthFilter;
        ObjectMapper mapper = objectMapper.getIfAvailable(ObjectMapper::new);
        this.authenticationEntryPoint = new ErrorResponseAuthenticationEntryPoint(mapper);
        this.accessDeniedHandler = new ErrorResponseAccessDeniedHandler(mapper);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable()).sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).addFilterBefore(hmacInternalAuthFilter, BearerTokenAuthenticationFilter.class).authorizeHttpRequests(auth -> auth.requestMatchers("/knowledgebase-service/docs", "/knowledgebase-service/swagger-ui/**", "/knowledgebase-service/v3/api-docs/**", "/knowledgebase-service/v3/api-docs.yaml").permitAll().requestMatchers("/actuator/**").permitAll().requestMatchers("/knowledgebase-service/hello").permitAll()
                // internal fan-out endpoints require an HMAC signature
                .requestMatchers("/internal/**").hasAuthority(HmacInternalAuthFilter.ROLE_INTERNAL).anyRequest().authenticated()
        ).oauth2ResourceServer(oauth2 -> oauth2.authenticationEntryPoint(authenticationEntryPoint).accessDeniedHandler(accessDeniedHandler).jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter))
        ).exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint).accessDeniedHandler(accessDeniedHandler)
        );

        return http.build();
    }
}
