package com.alexandria.common.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class HmacInternalAuthFilterTest {

    private HmacSigner signer;
    private HmacInternalAuthFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        signer = new HmacSigner("shared-test-secret", 300);
        objectMapper = new ObjectMapper();
        filter = new HmacInternalAuthFilter(signer, objectMapper);
    }

    @AfterEach
    void teardown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_passesThroughNonInternalPathsWithoutHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/knowledgebase/documents");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_acceptsSignedInternalRequestAndSetsRoleInternal() throws Exception {
        String path = "/internal/knowledgebase/users/abc/document-keys";
        String header = signer.sign("GET", path);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.addHeader(HmacSigner.HEADER_NAME, header);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities()).extracting(a -> a.getAuthority()).contains(HmacInternalAuthFilter.ROLE_INTERNAL);
    }

    @Test
    void doFilter_rejectsInternalRequestWithoutHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/knowledgebase/users/abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("UNAUTHORIZED");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void doFilter_rejectsInternalRequestWithInvalidSignature() throws Exception {
        String path = "/internal/knowledgebase/users/abc";
        HmacSigner otherSigner = new HmacSigner("different-secret", 300);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.addHeader(HmacSigner.HEADER_NAME, otherSigner.sign("GET", path));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void doFilter_rejectsSignatureForDifferentPath() throws Exception {
        String signedPath = "/internal/knowledgebase/users/legit";
        String targetPath = "/internal/knowledgebase/users/victim";
        String header = signer.sign("GET", signedPath);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", targetPath);
        request.addHeader(HmacSigner.HEADER_NAME, header);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }
}
