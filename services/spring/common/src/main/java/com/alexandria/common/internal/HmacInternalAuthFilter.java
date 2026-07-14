package com.alexandria.common.internal;

import com.alexandria.common.web.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class HmacInternalAuthFilter extends OncePerRequestFilter {

    public static final String ROLE_INTERNAL = "ROLE_INTERNAL";
    private static final String INTERNAL_PATH_PREFIX = "/internal/";
    private static final Logger log = LoggerFactory.getLogger(HmacInternalAuthFilter.class);

    private final HmacSigner signer;
    private final ObjectMapper objectMapper;

    public HmacInternalAuthFilter(HmacSigner signer, ObjectMapper objectMapper) {
        this.signer = signer;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path == null || !path.startsWith(INTERNAL_PATH_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(HmacSigner.HEADER_NAME);
        if (signer.verify(header, request.getMethod(), path)) {
            AnonymousAuthenticationToken auth = new AnonymousAuthenticationToken(
                    "internal-service", "internal-service", List.of(new SimpleGrantedAuthority(ROLE_INTERNAL)));
            SecurityContextHolder.getContext().setAuthentication(auth);
            chain.doFilter(request, response);
            return;
        }

        log.warn("Rejected internal call to {} {}: HMAC header {}", sanitize(request.getMethod()), sanitize(path), header == null ? "missing" : "invalid");
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new ErrorResponse("UNAUTHORIZED", "Invalid or missing internal signature"));
    }

    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        return value.replace('\n', '_').replace('\r', '_');
    }
}
