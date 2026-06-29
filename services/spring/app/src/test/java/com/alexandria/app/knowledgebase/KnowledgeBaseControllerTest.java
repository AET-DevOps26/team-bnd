package com.alexandria.app.knowledgebase;

import com.alexandria.app.document.Document;
import com.alexandria.app.user.User;
import com.alexandria.app.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class KnowledgeBaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

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
    void integration_controller_listDocumentsReturnsJson() throws Exception {
        knowledgeBaseService.createDocument(
                testUser, "test.pdf", "/files/test.pdf", "application/pdf", 1024L, "Sample text");

        mockMvc.perform(get("/api/v1/knowledgebase/documents")
                        .with(jwt().jwt(j -> j.subject("sub123"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].fileName").value("test.pdf"))
                .andExpect(jsonPath("$[0].fileContent").doesNotExist());
    }

    @Test
    void integration_controller_getDocumentReturnsJson() throws Exception {
        Document doc = knowledgeBaseService.createDocument(
                testUser, "report.pdf", "/files/report.pdf", "application/pdf", 2048L, "Report content");

        mockMvc.perform(get("/api/v1/knowledgebase/documents/" + doc.getId())
                        .with(jwt().jwt(j -> j.subject("sub123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("report.pdf"))
                .andExpect(jsonPath("$.fileType").value("application/pdf"));
    }

    @Test
    void integration_controller_getDocumentReturns404ForNonexistent() throws Exception {
        mockMvc.perform(get("/api/v1/knowledgebase/documents/" + java.util.UUID.randomUUID())
                        .with(jwt().jwt(j -> j.subject("sub123"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void integration_controller_downloadReturnsFileBytes() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "download.txt", "text/plain", "hello world".getBytes());
        Document doc = knowledgeBaseService.uploadDocument(testUser, file);
        when(objectStorageService.download(doc.getObjectKey())).thenReturn("hello world".getBytes());

        mockMvc.perform(get("/api/v1/knowledgebase/documents/" + doc.getId() + "/download")
                        .with(jwt().jwt(j -> j.subject("sub123"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/plain"))
                .andExpect(content().bytes("hello world".getBytes()));
    }
}
