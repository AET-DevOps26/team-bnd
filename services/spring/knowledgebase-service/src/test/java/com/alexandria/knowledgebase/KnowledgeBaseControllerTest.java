package com.alexandria.knowledgebase;

import com.alexandria.knowledgebase.document.Document;
import com.alexandria.knowledgebase.document.EntityType;
import com.alexandria.knowledgebase.document.ExtractedEntity;
import com.alexandria.knowledgebase.document.Summary;
import com.alexandria.knowledgebase.document.TagSource;
import com.alexandria.knowledgebase.dto.SemanticSearchResponseDto;
import com.alexandria.knowledgebase.dto.SemanticSearchResultDto;
import com.alexandria.knowledgebase.dto.DocumentRefDto;
import com.alexandria.knowledgebase.dto.UpdateDocumentRequest;
import com.alexandria.knowledgebase.exception.DocumentNotFoundException;
import com.alexandria.knowledgebase.search.SearchQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KnowledgeBaseControllerTest {

    private static final String SUBJECT = "mock-user";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KnowledgeBaseService knowledgeBaseService;

    private static Document doc() {
        Document d = new Document(SUBJECT, "a.pdf", "/uploads/a.pdf", "application/pdf", 100L);
        return d;
    }

    @Test
    void integration_kb_listDocuments() throws Exception {
        when(knowledgeBaseService.getDocuments(SUBJECT)).thenReturn(List.of(doc()));

        mockMvc.perform(get("/api/v1/knowledgebase/documents").with(jwt().jwt(j -> j.subject(SUBJECT)))).andExpect(status().isOk()).andExpect(jsonPath("$[0].fileName").value("a.pdf"));
    }

    @Test
    void integration_kb_listDocumentsRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/knowledgebase/documents")).andExpect(status().isUnauthorized());
    }

    @Test
    void integration_kb_createDocument() throws Exception {
        when(knowledgeBaseService.createDocument(eq(SUBJECT), anyString(), anyString(), anyString(), any(), anyString())).thenReturn(doc());

        mockMvc.perform(post("/api/v1/knowledgebase/documents/create").with(jwt().jwt(j -> j.subject(SUBJECT))).contentType(MediaType.APPLICATION_JSON).content("{\"fileName\":\"a.pdf\",\"objectKey\":\"/uploads/a.pdf\",\"fileType\":\"application/pdf\",\"fileSize\":100,\"textContent\":\"hi\"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.fileName").value("a.pdf"));
    }

    @Test
    void integration_kb_uploadDocument() throws Exception {
        when(knowledgeBaseService.uploadDocument(eq(SUBJECT), any())).thenReturn(doc());
        MockMultipartFile file = new MockMultipartFile("file", "a.pdf", "application/pdf", "hi".getBytes());

        mockMvc.perform(multipart("/api/v1/knowledgebase/documents/upload").file(file).with(jwt().jwt(j -> j.subject(SUBJECT)))).andExpect(status().isCreated());
    }

    @Test
    void integration_kb_getDocument() throws Exception {
        UUID id = UUID.randomUUID();
        when(knowledgeBaseService.getDocument(id, SUBJECT)).thenReturn(doc());

        mockMvc.perform(get("/api/v1/knowledgebase/documents/" + id).with(jwt().jwt(j -> j.subject(SUBJECT)))).andExpect(status().isOk());
    }

    @Test
    void integration_kb_getDocumentNotFoundReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(knowledgeBaseService.getDocument(id, SUBJECT)).thenThrow(new DocumentNotFoundException(id));

        mockMvc.perform(get("/api/v1/knowledgebase/documents/" + id).with(jwt().jwt(j -> j.subject(SUBJECT)))).andExpect(status().isNotFound());
    }

    @Test
    void integration_kb_deleteDocument() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/knowledgebase/documents/" + id).with(jwt().jwt(j -> j.subject(SUBJECT)))).andExpect(status().isNoContent());
        verify(knowledgeBaseService).deleteDocument(id, SUBJECT);
    }

    @Test
    void integration_kb_updateDocument() throws Exception {
        UUID id = UUID.randomUUID();
        when(knowledgeBaseService.updateDocument(eq(id), any(UpdateDocumentRequest.class), eq(SUBJECT))).thenReturn(doc());

        mockMvc.perform(patch("/api/v1/knowledgebase/documents/" + id).with(jwt().jwt(j -> j.subject(SUBJECT))).contentType(MediaType.APPLICATION_JSON).content("{\"fileName\":\"b.pdf\"}")).andExpect(status().isOk());
    }

    @Test
    void integration_kb_downloadDocumentReturnsBytes() throws Exception {
        UUID id = UUID.randomUUID();
        when(knowledgeBaseService.getDocument(id, SUBJECT)).thenReturn(doc());
        when(knowledgeBaseService.getFileContent(id, SUBJECT)).thenReturn(Optional.of("hi".getBytes()));

        mockMvc.perform(get("/api/v1/knowledgebase/documents/" + id + "/download").with(jwt().jwt(j -> j.subject(SUBJECT)))).andExpect(status().isOk()).andExpect(content().bytes("hi".getBytes()));
    }

    @Test
    void integration_kb_downloadDocumentMissingContentReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(knowledgeBaseService.getDocument(id, SUBJECT)).thenReturn(doc());
        when(knowledgeBaseService.getFileContent(id, SUBJECT)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/knowledgebase/documents/" + id + "/download").with(jwt().jwt(j -> j.subject(SUBJECT)))).andExpect(status().isNotFound());
    }

    @Test
    void integration_kb_searchText() throws Exception {
        DocumentRefDto ref = new DocumentRefDto(UUID.randomUUID(), "a.pdf", "application/pdf", 100L, Instant.now());
        when(knowledgeBaseService.search(SUBJECT, "report")).thenReturn(List.of(ref));

        mockMvc.perform(get("/api/v1/knowledgebase/search/text").param("q", "report").with(jwt().jwt(j -> j.subject(SUBJECT)))).andExpect(status().isOk()).andExpect(jsonPath("$.results[0].fileName").value("a.pdf"));
    }

    @Test
    void integration_kb_searchSemantic() throws Exception {
        DocumentRefDto ref = new DocumentRefDto(UUID.randomUUID(), "a.pdf", "application/pdf", 100L, Instant.now());
        SemanticSearchResponseDto response = new SemanticSearchResponseDto(List.of(new SemanticSearchResultDto(ref, 0.9, "snippet")), false);
        when(knowledgeBaseService.semanticSearch(SUBJECT, "budget", 10)).thenReturn(response);

        mockMvc.perform(get("/api/v1/knowledgebase/search/semantic").param("q", "budget").with(jwt().jwt(j -> j.subject(SUBJECT)))).andExpect(status().isOk()).andExpect(jsonPath("$.results[0].score").value(0.9));
    }

    @Test
    void integration_kb_searchSemanticRejectsInvalidLimit() throws Exception {
        mockMvc.perform(get("/api/v1/knowledgebase/search/semantic").param("q", "budget").param("limit", "0").with(jwt().jwt(j -> j.subject(SUBJECT)))).andExpect(status().isBadRequest());
    }

    @Test
    void integration_kb_addTag() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/knowledgebase/documents/" + id + "/tags").with(jwt().jwt(j -> j.subject(SUBJECT))).contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"finance\"}")).andExpect(status().isNoContent());
        verify(knowledgeBaseService).addTag(id, SUBJECT, "finance", TagSource.USER);
    }

    @Test
    void integration_kb_removeTag() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/knowledgebase/documents/" + docId + "/tags/" + tagId).with(jwt().jwt(j -> j.subject(SUBJECT)))).andExpect(status().isNoContent());
        verify(knowledgeBaseService).removeTag(docId, SUBJECT, tagId);
    }

    @Test
    void integration_kb_getSummaryReturnsDto() throws Exception {
        UUID id = UUID.randomUUID();
        Summary summary = new Summary(doc(), "content", "model");
        when(knowledgeBaseService.getDocumentSummary(id, SUBJECT)).thenReturn(summary);

        mockMvc.perform(get("/api/v1/knowledgebase/documents/" + id + "/summary").with(jwt().jwt(j -> j.subject(SUBJECT)))).andExpect(status().isOk()).andExpect(jsonPath("$.summary").value("content"));
    }

    @Test
    void integration_kb_getSummaryReturns204WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(knowledgeBaseService.getDocumentSummary(id, SUBJECT)).thenReturn(null);

        mockMvc.perform(get("/api/v1/knowledgebase/documents/" + id + "/summary").with(jwt().jwt(j -> j.subject(SUBJECT)))).andExpect(status().isNoContent());
    }

    @Test
    void integration_kb_getEntities() throws Exception {
        UUID id = UUID.randomUUID();
        ExtractedEntity entity = new ExtractedEntity(doc(), "Ada", EntityType.PERSON, 0.9);
        when(knowledgeBaseService.getDocumentEntities(id, SUBJECT)).thenReturn(List.of(entity));

        mockMvc.perform(get("/api/v1/knowledgebase/documents/" + id + "/entities").with(jwt().jwt(j -> j.subject(SUBJECT)))).andExpect(status().isOk()).andExpect(jsonPath("$.entities[0].name").value("Ada"));
    }

    @Test
    void integration_kb_reprocessSummary() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/knowledgebase/documents/" + id + "/reprocess/summary").with(jwt().jwt(j -> j.subject(SUBJECT)))).andExpect(status().isNoContent());
        verify(knowledgeBaseService).reprocessSummary(id, SUBJECT);
    }

    @Test
    void integration_kb_reprocessEntities() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/knowledgebase/documents/" + id + "/reprocess/entities").with(jwt().jwt(j -> j.subject(SUBJECT)))).andExpect(status().isNoContent());
        verify(knowledgeBaseService).reprocessEntities(id, SUBJECT);
    }

    @Test
    void integration_kb_reprocessTags() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/knowledgebase/documents/" + id + "/reprocess/tags").with(jwt().jwt(j -> j.subject(SUBJECT)))).andExpect(status().isNoContent());
        verify(knowledgeBaseService).reprocessTags(id, SUBJECT);
    }

    @Test
    void integration_kb_getSearchHistory() throws Exception {
        when(knowledgeBaseService.getSearchHistory(SUBJECT)).thenReturn(List.of(new SearchQuery(SUBJECT, "report", 1)));

        mockMvc.perform(get("/api/v1/knowledgebase/history/search").with(jwt().jwt(j -> j.subject(SUBJECT)))).andExpect(status().isOk()).andExpect(jsonPath("$[0].queryText").value("report"));
    }

    @Test
    void integration_kb_deleteSearchHistory() throws Exception {
        mockMvc.perform(delete("/api/v1/knowledgebase/history/search").with(jwt().jwt(j -> j.subject(SUBJECT)))).andExpect(status().isNoContent());
        verify(knowledgeBaseService).deleteSearchHistory(SUBJECT);
    }

    @Test
    void integration_kb_getTagsSortedByCount() throws Exception {
        when(knowledgeBaseService.getTagsForUserWithCount(SUBJECT)).thenReturn(Map.of("finance", 3L, "report", 1L));

        mockMvc.perform(get("/api/v1/knowledgebase/tags").with(jwt().jwt(j -> j.subject(SUBJECT)))).andExpect(status().isOk()).andExpect(jsonPath("$.tags[0].name").value("finance")).andExpect(jsonPath("$.tags[0].documentCount").value(3));
    }
}
