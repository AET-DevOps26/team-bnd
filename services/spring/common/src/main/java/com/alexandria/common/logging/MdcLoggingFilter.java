package com.alexandria.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Puts a request id and route onto the SLF4J (Simple Logging Facade for Java) MDC so every
 * log line for a request can be correlated, and echoes the id back in the X-Request-Id
 * response header.
 *
 * <p>An incoming X-Request-Id is reused when it looks like a trace id, otherwise a fresh
 * UUID is generated. Runs at highest precedence so the context is set before anything else
 * logs, and is always cleared in a finally block to avoid leaking ids across pooled threads.
 *
 * <p>The filter also runs on the container's ERROR dispatch (see
 * {@link #shouldNotFilterErrorDispatch()}) and stashes the id as a request attribute, so the
 * same id is restored onto the MDC when the /error controller renders and logs a failure.
 */
public class MdcLoggingFilter extends OncePerRequestFilter implements Ordered {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String REQUEST_ID = "requestId";
    private static final String ROUTE = "route";
    private static final String REQUEST_ID_ATTRIBUTE = MdcLoggingFilter.class.getName() + ".requestId";
    // Only accept caller-supplied ids that look like a trace id, otherwise we would
    // reflect arbitrary input straight back into a response header.
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        MDC.put(REQUEST_ID, requestId);
        MDC.put(ROUTE, request.getMethod() + " " + request.getRequestURI());
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(REQUEST_ID);
            MDC.remove(ROUTE);
        }
    }

    // Reuse the id carried over from the initial dispatch so an error dispatch keeps the same
    // trace id, otherwise fall back to the header or a fresh UUID.
    private String resolveRequestId(HttpServletRequest request) {
        Object carried = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        if (carried instanceof String s && !s.isBlank()) {
            return s;
        }
        String fromHeader = request.getHeader(REQUEST_ID_HEADER);
        if (fromHeader != null && SAFE_REQUEST_ID.matcher(fromHeader).matches()) {
            return fromHeader;
        }
        return UUID.randomUUID().toString();
    }

    // Also run on the ERROR dispatch so the /error controller logs with the request id set.
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
