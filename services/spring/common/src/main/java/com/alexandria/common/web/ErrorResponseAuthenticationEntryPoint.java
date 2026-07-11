package com.alexandria.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * Emits the unified {@link ErrorResponse} for 401 responses. An AuthenticationException in an
 * oauth2ResourceServer setup is raised in the filter chain before the DispatcherServlet,
 * so @RestControllerAdvice never sees it; this handler handles it there.
 */
public class ErrorResponseAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(ErrorResponseAuthenticationEntryPoint.class);

    private final ObjectMapper objectMapper;

    public ErrorResponseAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {
        String method = request.getMethod() == null ? null : request.getMethod().replace('\n', '_').replace('\r', '_');
        String uri = request.getRequestURI() == null ? null : request.getRequestURI().replace('\n', '_').replace('\r', '_');
        log.warn("Authentication failed at {} {}", method, uri);

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new ErrorResponse("UNAUTHORIZED", "Authentication required"));
    }
}
