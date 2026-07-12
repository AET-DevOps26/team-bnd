package com.alexandria.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * Emits the unified {@link ErrorResponse} for 403 responses. An AccessDeniedExceptionin in an
 * oauth2ResourceServer setup is raised in the filter chain before the DispatcherServlet,
 * so @RestControllerAdvice never sees it; this handler handles it there.
 */
public class ErrorResponseAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(ErrorResponseAccessDeniedHandler.class);

    private final ObjectMapper objectMapper;

    public ErrorResponseAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException {
        String method = request.getMethod() == null ? null : request.getMethod().replace('\n', '_').replace('\r', '_');
        String uri = request.getRequestURI() == null ? null : request.getRequestURI().replace('\n', '_').replace('\r', '_');
        String user = request.getRemoteUser() == null ? null : request.getRemoteUser().replace('\n', '_').replace('\r', '_');
        log.warn("Access denied at {} {} (user={})", method, uri, user);

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new ErrorResponse("FORBIDDEN", "Access denied"));
    }
}
