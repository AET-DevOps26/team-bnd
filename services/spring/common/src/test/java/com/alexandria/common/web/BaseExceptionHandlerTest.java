package com.alexandria.common.web;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BaseExceptionHandlerTest {

    private MockMvc mockMvc;
    private final TestAdvice advice = new TestAdvice();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController()).setControllerAdvice(advice).build();
    }

    @Test
    void invalidBody_returnsValidationError() throws Exception {
        mockMvc.perform(post("/test").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR")).andExpect(jsonPath("$.fieldErrors[?(@.field=='name')].message").exists());
    }

    @Test
    void malformedJson_returnsMalformedJsonError() throws Exception {
        mockMvc.perform(post("/test").contentType(MediaType.APPLICATION_JSON).content("{ not json")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
    }

    @Test
    void illegalArgument_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/illegal")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST")).andExpect(jsonPath("$.message").value("nope"));
    }

    @Test
    void unhandled_returnsInternalError() throws Exception {
        mockMvc.perform(get("/boom")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    @Test
    void typeMismatch_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/typed/not-a-uuid")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TYPE_MISMATCH")).andExpect(jsonPath("$.message").value("Parameter 'id' must be a valid UUID"));
    }

    @Test
    void missingParameter_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/needs-param")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MISSING_PARAMETER")).andExpect(jsonPath("$.message").value("Required parameter 'q' is missing"));
    }

    @Test
    void methodNotAllowed_returnsMethodNotAllowed() throws Exception {
        mockMvc.perform(get("/test")).andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unsupportedMediaType_returns415() throws Exception {
        mockMvc.perform(post("/test").contentType(MediaType.TEXT_PLAIN).content("hello")).andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void constraintViolation_returnsValidationError() {
        ConstraintViolationException ex = new ConstraintViolationException("bad", Set.of());
        ResponseEntity<ErrorResponse> resp = advice.handleConstraintViolation(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void noResourceFound_returns404() {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/missing", "missing");
        ResponseEntity<ErrorResponse> resp = advice.handleNoResource(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo("NOT_FOUND");
    }

    @Test
    void maxUploadSizeExceeded_returns413() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/knowledgebase/documents/upload");
        ResponseEntity<ErrorResponse> resp = advice.handleMaxUploadSize(new MaxUploadSizeExceededException(1048576L), request);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo("PAYLOAD_TOO_LARGE");
    }

    @Test
    void maxUploadSizeExceeded_messageNamesConfiguredLimit() {
        ReflectionTestUtils.setField(advice, "maxUploadSize", "25MB");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/knowledgebase/documents/upload");
        ResponseEntity<ErrorResponse> resp = advice.handleMaxUploadSize(new MaxUploadSizeExceededException(1048576L), request);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().message()).contains("25MB");
    }

    @Test
    void accessDenied_returns403() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/secret");
        request.setRemoteUser("mallory");
        ResponseEntity<ErrorResponse> resp = advice.handleAccessDenied(new AccessDeniedException("denied"), request);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo("FORBIDDEN");
    }

    @Test
    void authenticationException_returns401() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/private");
        ResponseEntity<ErrorResponse> resp = advice.handleAuthentication(new StubAuthenticationException("no token"), request);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void genericException_logsRequestContext() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/foo");
        request.setRemoteUser("alice");
        ResponseEntity<ErrorResponse> resp = advice.handleGenericException(new RuntimeException("boom"), request);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo("INTERNAL_ERROR");
    }

    @RestControllerAdvice
    static class TestAdvice extends BaseExceptionHandler {
    }

    @RestController
    static class TestController {
        @PostMapping("/test")
        String test(@Valid @RequestBody TestRequest r) {
            return "ok";
        }

        @GetMapping("/illegal")
        String illegal() {
            throw new IllegalArgumentException("nope");
        }

        @GetMapping("/boom")
        String boom() {
            throw new RuntimeException("boom");
        }

        @GetMapping("/typed/{id}")
        String typed(@PathVariable UUID id) {
            return id.toString();
        }

        @GetMapping("/needs-param")
        String needsParam(@RequestParam String q) {
            return q;
        }
    }

    record TestRequest(@NotBlank String name) {
    }

    static class StubAuthenticationException extends AuthenticationException {
        StubAuthenticationException(String msg) {
            super(msg);
        }
    }
}
