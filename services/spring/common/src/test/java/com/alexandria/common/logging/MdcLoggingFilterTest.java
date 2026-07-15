package com.alexandria.common.logging;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class MdcLoggingFilterTest {

    private final MdcLoggingFilter filter = new MdcLoggingFilter();

    @Test
    void unit_common_generatesRequestIdWhenHeaderMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/knowledgebase/documents/create");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get("requestId")).isNotBlank();
            assertThat(MDC.get("route")).isEqualTo("POST /api/v1/knowledgebase/documents/create");
        });

        assertThat(response.getHeader(MdcLoggingFilter.REQUEST_ID_HEADER)).isNotBlank();
        assertThat(MDC.get("requestId")).isNull();
        assertThat(MDC.get("route")).isNull();
    }

    @Test
    void unit_common_reusesIncomingRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/qa/history");
        request.addHeader(MdcLoggingFilter.REQUEST_ID_HEADER, "trace-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> assertThat(MDC.get("requestId")).isEqualTo("trace-123"));

        assertThat(response.getHeader(MdcLoggingFilter.REQUEST_ID_HEADER)).isEqualTo("trace-123");
    }

    @Test
    void unit_common_reusesSameRequestIdOnErrorDispatch() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/qa/history");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String[] seenIds = new String[2];
        filter.doFilter(request, response, (req, res) -> seenIds[0] = MDC.get("requestId"));
        // Simulate the container re-running the filter for the ERROR dispatch to /error.
        filter.doFilter(request, response, (req, res) -> seenIds[1] = MDC.get("requestId"));

        assertThat(seenIds[0]).isNotBlank();
        assertThat(seenIds[1]).isEqualTo(seenIds[0]);
    }

    @Test
    void unit_common_clearsMdcEvenWhenChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/users/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilter(request, response, (req, res) -> {
                throw new RuntimeException("boom");
            });
        } catch (Exception ignored) {
        }

        assertThat(MDC.get("requestId")).isNull();
        assertThat(MDC.get("route")).isNull();
    }
}
