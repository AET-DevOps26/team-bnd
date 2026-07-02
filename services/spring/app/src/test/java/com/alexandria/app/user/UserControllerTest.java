package com.alexandria.app.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.alexandria.app.knowledgebase.GenAiClient;
import com.alexandria.app.knowledgebase.ObjectStorageService;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private ObjectStorageService objectStorageService;

    @MockitoBean
    private GenAiClient genAiClient;

    private User testUser;

    @BeforeEach
    void setup() {
        testUser = userRepository.save(new User("sub123", "testuser", "test@example.com"));
    }

    @Test
    void integration_user_getMeReturnsProfile() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .with(jwt().jwt(j -> j.subject("sub123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.oidcSubject").value("sub123"));
    }

    @Test
    void integration_user_getMeReturns401WithoutAuth() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void integration_user_deleteOwnAccountReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/users/" + testUser.getId())
                        .with(jwt().jwt(j -> j.subject("sub123"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void integration_user_deleteOtherAccountReturns403() throws Exception {
        User otherUser = userRepository.save(new User("other-sub", "other", "other@example.com"));

        mockMvc.perform(delete("/api/v1/users/" + otherUser.getId())
                        .with(jwt().jwt(j -> j.subject("sub123"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void integration_user_updatePreferencesReturnsUpdated() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/preferences")
                        .with(jwt().jwt(j -> j.subject("sub123")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"darkTheme\":true,\"language\":\"de\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.darkTheme").value(true))
                .andExpect(jsonPath("$.language").value("de"));
    }

    @Test
    void integration_user_updatePreferencesPartialUpdate() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/preferences")
                        .with(jwt().jwt(j -> j.subject("sub123")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"fr\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.darkTheme").value(false))
                .andExpect(jsonPath("$.language").value("fr"));
    }
}
