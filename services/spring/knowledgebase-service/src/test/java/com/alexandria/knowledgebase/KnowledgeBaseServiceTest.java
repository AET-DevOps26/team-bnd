package com.alexandria.knowledgebase;

import com.alexandria.knowledgebase.document.*;
import com.alexandria.knowledgebase.integration.GenAiClient;
import com.alexandria.knowledgebase.search.SearchQuery;
import com.alexandria.knowledgebase.search.SearchQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceTest {

    @Mock
    private DocumentService documentService;

    @Mock
    private SummaryRepository summaryRepository;

    @Mock
    private ExtractedEntityRepository extractedEntityRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private SearchQueryRepository searchQueryRepository;

    @Mock
    private GenAiClient genAiClient;

    @Mock
    private TextExtractor textExtractor;

    @Mock
    private ObjectStorageService objectStorageService;

    private KnowledgeBaseService knowledgeBaseService;

    private static final String OWNER = "oidc|123";

    @BeforeEach
    void setup() {
        knowledgeBaseService = new KnowledgeBaseService(
                documentService, summaryRepository, extractedEntityRepository, tagRepository, searchQueryRepository, genAiClient, textExtractor, objectStorageService
        );
    }

    @Test
    void unit_kb_createDocumentPersistsAndCallsGenAiForText() {
        when(documentService.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        when(genAiClient.summarize(anyString())).thenReturn(new GenAiClient.SummarizeResponse("summary", "model"));
        when(genAiClient.extract(anyString())).thenReturn(new GenAiClient.ExtractResponse(List.of(), "model"));
        when(genAiClient.index(anyString())).thenReturn(new GenAiClient.IndexResponse("/uploads/a.pdf", 3, "embed-model"));

        Document result = knowledgeBaseService.createDocument(
                OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 100L, "hello world");

        assertThat(result.getOwnerSubject()).isEqualTo(OWNER);
        verify(genAiClient).summarize("/uploads/a.pdf");
        verify(genAiClient).extract("/uploads/a.pdf");
        verify(genAiClient).index("/uploads/a.pdf");
    }

    @Test
    void unit_kb_createDocumentSkipsGenAiWhenNoText() {
        when(documentService.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        knowledgeBaseService.createDocument(OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 100L, "");

        verifyNoInteractions(genAiClient);
    }

    @Test
    void unit_kb_createDocumentSurvivesGenAiIndexFailure() {
        when(documentService.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        when(genAiClient.summarize(anyString())).thenReturn(new GenAiClient.SummarizeResponse("summary", "model"));
        when(genAiClient.extract(anyString())).thenReturn(new GenAiClient.ExtractResponse(List.of(), "model"));
        when(genAiClient.index(anyString())).thenThrow(new RuntimeException("genai down"));

        Document result = knowledgeBaseService.createDocument(
                OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 100L, "hello world");

        assertThat(result.getOwnerSubject()).isEqualTo(OWNER);
        verify(genAiClient).index("/uploads/a.pdf");
    }

    @Test
    void unit_kb_uploadDocumentCallsIndexAlongsideSummarizeAndExtract() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.pdf", "application/pdf", "hello world".getBytes());
        when(documentService.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        when(textExtractor.extract(file)).thenReturn("hello world");
        when(genAiClient.summarize(anyString())).thenReturn(new GenAiClient.SummarizeResponse("summary", "model"));
        when(genAiClient.extract(anyString())).thenReturn(new GenAiClient.ExtractResponse(List.of(), "model"));
        when(genAiClient.index(anyString())).thenReturn(new GenAiClient.IndexResponse("key", 1, "embed-model"));

        Document result = knowledgeBaseService.uploadDocument(OWNER, file);

        assertThat(result.getOwnerSubject()).isEqualTo(OWNER);
        verify(genAiClient).summarize(result.getObjectKey());
        verify(genAiClient).extract(result.getObjectKey());
        verify(genAiClient).index(result.getObjectKey());
    }

    @Test
    void unit_kb_uploadDocumentSurvivesGenAiIndexFailure() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.pdf", "application/pdf", "hello world".getBytes());
        when(documentService.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        when(textExtractor.extract(file)).thenReturn("hello world");
        when(genAiClient.summarize(anyString())).thenReturn(new GenAiClient.SummarizeResponse("summary", "model"));
        when(genAiClient.extract(anyString())).thenReturn(new GenAiClient.ExtractResponse(List.of(), "model"));
        when(genAiClient.index(anyString())).thenThrow(new RuntimeException("genai down"));

        Document result = knowledgeBaseService.uploadDocument(OWNER, file);

        assertThat(result.getOwnerSubject()).isEqualTo(OWNER);
        verify(genAiClient).index(result.getObjectKey());
    }

    @Test
    void unit_kb_getDocumentDelegatesToDocumentService() {
        UUID docId = UUID.randomUUID();
        Document doc = new Document(OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 100L);
        when(documentService.findByIdAndOwner(docId, OWNER)).thenReturn(doc);

        assertThat(knowledgeBaseService.getDocument(docId, OWNER).getFileName()).isEqualTo("a.pdf");
    }

    @Test
    void unit_kb_deleteDocumentRemovesFromS3AndDbAndGenAiIndex() {
        UUID docId = UUID.randomUUID();
        Document doc = new Document(OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 100L);
        when(documentService.findByIdAndOwner(docId, OWNER)).thenReturn(doc);
        when(genAiClient.deleteIndex(anyString())).thenReturn(new GenAiClient.DeleteIndexResponse("/uploads/a.pdf", 3));

        knowledgeBaseService.deleteDocument(docId, OWNER);

        org.mockito.InOrder inOrder = inOrder(documentService, objectStorageService, genAiClient);
        inOrder.verify(documentService).delete(docId, OWNER);
        inOrder.verify(objectStorageService).delete("/uploads/a.pdf");
        inOrder.verify(genAiClient).deleteIndex("/uploads/a.pdf");
    }

    @Test
    void unit_kb_deleteDocumentSkipsS3AndGenAiWhenDbDeleteFails() {
        UUID docId = UUID.randomUUID();
        Document doc = new Document(OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 100L);
        when(documentService.findByIdAndOwner(docId, OWNER)).thenReturn(doc);
        doThrow(new RuntimeException("db down")).when(documentService).delete(docId, OWNER);

        assertThatThrownBy(() -> knowledgeBaseService.deleteDocument(docId, OWNER)).isInstanceOf(RuntimeException.class);

        verify(objectStorageService, never()).delete(anyString());
        verify(genAiClient, never()).deleteIndex(anyString());
    }

    @Test
    void unit_kb_deleteDocumentSurvivesGenAiIndexFailure() {
        UUID docId = UUID.randomUUID();
        Document doc = new Document(OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 100L);
        when(documentService.findByIdAndOwner(docId, OWNER)).thenReturn(doc);
        when(genAiClient.deleteIndex(anyString())).thenThrow(new RuntimeException("genai down"));

        knowledgeBaseService.deleteDocument(docId, OWNER);

        verify(documentService).delete(docId, OWNER);
        verify(objectStorageService).delete("/uploads/a.pdf");
        verify(genAiClient).deleteIndex("/uploads/a.pdf");
    }

    @Test
    void unit_kb_searchStoresQueryAndReturnsResults() {
        Document doc = new Document(OWNER, "report.pdf", "/uploads/report.pdf", "application/pdf", 1L);
        when(documentService.searchByFileName(OWNER, "report")).thenReturn(List.of(doc));

        List<Document> results = knowledgeBaseService.search(OWNER, "report");

        assertThat(results).hasSize(1);
        verify(searchQueryRepository).save(any(SearchQuery.class));
    }

    @Test
    void unit_kb_deleteAllForUserPurgesEverything() {
        Document doc = new Document(OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 1L);
        when(documentService.findByOwnerSubject(OWNER)).thenReturn(List.of(doc));
        when(genAiClient.deleteIndex(anyString())).thenReturn(new GenAiClient.DeleteIndexResponse("/uploads/a.pdf", 3));

        knowledgeBaseService.deleteAllForUser(OWNER);

        verify(objectStorageService).delete("/uploads/a.pdf");
        verify(genAiClient).deleteIndex("/uploads/a.pdf");
        verify(documentService).deleteAllByOwner(OWNER);
        verify(searchQueryRepository).deleteByUserSubject(OWNER);
    }

    @Test
    void unit_kb_deleteAllForUserStillCallsGenAiWhenS3DeleteFails() {
        Document doc = new Document(OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 1L);
        when(documentService.findByOwnerSubject(OWNER)).thenReturn(List.of(doc));
        doThrow(new RuntimeException("s3 down")).when(objectStorageService).delete(anyString());
        when(genAiClient.deleteIndex(anyString())).thenReturn(new GenAiClient.DeleteIndexResponse("/uploads/a.pdf", 0));

        knowledgeBaseService.deleteAllForUser(OWNER);

        verify(genAiClient).deleteIndex("/uploads/a.pdf");
        verify(documentService).deleteAllByOwner(OWNER);
        verify(searchQueryRepository).deleteByUserSubject(OWNER);
    }

    @Test
    void unit_kb_deleteAllForUserSurvivesGenAiIndexFailure() {
        Document doc = new Document(OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 1L);
        when(documentService.findByOwnerSubject(OWNER)).thenReturn(List.of(doc));
        when(genAiClient.deleteIndex(anyString())).thenThrow(new RuntimeException("genai down"));

        knowledgeBaseService.deleteAllForUser(OWNER);

        verify(objectStorageService).delete("/uploads/a.pdf");
        verify(genAiClient).deleteIndex("/uploads/a.pdf");
        verify(documentService).deleteAllByOwner(OWNER);
        verify(searchQueryRepository).deleteByUserSubject(OWNER);
    }
}
