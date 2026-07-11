package com.alexandria.common.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BaseExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController()).setControllerAdvice(new TestAdvice()).build();
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
    }

    record TestRequest(@NotBlank String name) {
    }
}
