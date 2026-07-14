package com.alexandria.qa;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QAControllerTest {

    private static final String SUBJECT = "mock-user";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QAService qaService;

    @Test
    void integration_qa_ask() throws Exception {
        QAInteraction interaction = new QAInteraction(SUBJECT, "q?", "answer", List.of(), "model");
        when(qaService.ask(eq(SUBJECT), eq("q?"))).thenReturn(interaction);

        mockMvc.perform(post("/api/v1/qa/ask").with(jwt().jwt(j -> j.subject(SUBJECT))).contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"q?\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.answer").value("answer"));
    }

    @Test
    void integration_qa_askRejectsBlankQuestion() throws Exception {
        mockMvc.perform(post("/api/v1/qa/ask").with(jwt().jwt(j -> j.subject(SUBJECT))).contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void integration_qa_askRequiresAuth() throws Exception {
        mockMvc.perform(post("/api/v1/qa/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"q?\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void integration_qa_getHistory() throws Exception {
        when(qaService.getHistory(SUBJECT)).thenReturn(List.of(new QAInteraction(SUBJECT, "q?", "a", List.of(), "m")));

        mockMvc.perform(get("/api/v1/qa/history").with(jwt().jwt(j -> j.subject(SUBJECT)))).andExpect(status().isOk()).andExpect(jsonPath("$[0].question").value("q?"));
    }

    @Test
    void integration_qa_deleteHistory() throws Exception {
        mockMvc.perform(delete("/api/v1/qa/history").with(jwt().jwt(j -> j.subject(SUBJECT)))).andExpect(status().isNoContent());
        verify(qaService).deleteHistory(SUBJECT);
    }
}
