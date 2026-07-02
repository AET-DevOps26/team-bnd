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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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

    @Test
    void integration_controller_updateDocumentRenames() throws Exception {
        Document doc = knowledgeBaseService.createDocument(
                testUser, "old.pdf", "/files/old.pdf", "application/pdf", 1024L, null);

        mockMvc.perform(patch("/api/v1/knowledgebase/documents/" + doc.getId())
                        .with(jwt().jwt(j -> j.subject("sub123")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"new.pdf\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("new.pdf"));
    }

    @Test
    void integration_controller_updateDocumentReturns404ForUnknown() throws Exception {
        mockMvc.perform(patch("/api/v1/knowledgebase/documents/" + java.util.UUID.randomUUID())
                        .with(jwt().jwt(j -> j.subject("sub123")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"new.pdf\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void integration_controller_deleteDocumentReturns204() throws Exception {
        Document doc = knowledgeBaseService.createDocument(
                testUser, "delete-me.pdf", "/files/del.pdf", "application/pdf", 512L, null);

        mockMvc.perform(delete("/api/v1/knowledgebase/documents/" + doc.getId())
                        .with(jwt().jwt(j -> j.subject("sub123"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void integration_controller_getHistoryQaReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/v1/knowledgebase/history/qa")
                        .with(jwt().jwt(j -> j.subject("sub123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void integration_controller_deleteHistoryQaReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/knowledgebase/history/qa")
                        .with(jwt().jwt(j -> j.subject("sub123"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void integration_controller_getHistorySearchReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/v1/knowledgebase/history/search")
                        .with(jwt().jwt(j -> j.subject("sub123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void integration_controller_deleteHistorySearchReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/knowledgebase/history/search")
                        .with(jwt().jwt(j -> j.subject("sub123"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void integration_controller_getTagsReturnsTagList() throws Exception {
        Document doc = knowledgeBaseService.createDocument(
                testUser, "tagged.pdf", "/files/tagged.pdf", "application/pdf", 1024L, null);
        knowledgeBaseService.addTag(
                doc.getId(), testUser.getId(), "finance",
                com.alexandria.app.document.TagSource.USER);

        mockMvc.perform(get("/api/v1/knowledgebase/tags")
                        .with(jwt().jwt(j -> j.subject("sub123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[0].name").value("finance"))
                .andExpect(jsonPath("$.tags[0].documentCount").value(1));
    }

    @Test
    void integration_controller_addTagReturns204() throws Exception {
        Document doc = knowledgeBaseService.createDocument(
                testUser, "doc.pdf", "/files/doc.pdf", "application/pdf", 1024L, null);

        mockMvc.perform(post("/api/v1/knowledgebase/documents/" + doc.getId() + "/tags")
                        .with(jwt().jwt(j -> j.subject("sub123")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"important\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void integration_controller_searchReturnsResults() throws Exception {
        knowledgeBaseService.createDocument(
                testUser, "quarterly-report.pdf", "/files/qr.pdf", "application/pdf", 4096L, null);

        mockMvc.perform(get("/api/v1/knowledgebase/search")
                        .param("q", "quarterly")
                        .with(jwt().jwt(j -> j.subject("sub123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileName").value("quarterly-report.pdf"));
    }
}
