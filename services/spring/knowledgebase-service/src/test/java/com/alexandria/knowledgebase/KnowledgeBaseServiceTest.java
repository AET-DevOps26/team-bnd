package com.alexandria.knowledgebase;

import com.alexandria.knowledgebase.document.*;
import com.alexandria.knowledgebase.exception.DocumentNotFoundException;
import com.alexandria.knowledgebase.integration.GenAiClient;
import com.alexandria.knowledgebase.search.SearchQuery;
import com.alexandria.knowledgebase.search.SearchQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceTest {

    @Mock
    private DocumentRepository documentRepository;

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
                documentRepository,
                summaryRepository,
                extractedEntityRepository,
                tagRepository,
                searchQueryRepository,
                genAiClient,
                textExtractor,
                objectStorageService
        );
    }

    @Test
    void unit_kb_createDocumentPersistsAndCallsGenAiForText() {
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        when(genAiClient.summarize(anyString()))
                .thenReturn(new GenAiClient.SummarizeResponse("summary", "model"));
        when(genAiClient.extract(anyString()))
                .thenReturn(new GenAiClient.ExtractResponse(List.of(), "model"));

        Document result = knowledgeBaseService.createDocument(
                OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 100L, "hello world");

        assertThat(result.getOwnerSubject()).isEqualTo(OWNER);
        verify(genAiClient).summarize("/uploads/a.pdf");
        verify(genAiClient).extract("/uploads/a.pdf");
    }

    @Test
    void unit_kb_createDocumentSkipsGenAiWhenNoText() {
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        knowledgeBaseService.createDocument(OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 100L, "");

        verifyNoInteractions(genAiClient);
    }

    @Test
    void unit_kb_getDocumentEnforcesOwner() {
        UUID docId = UUID.randomUUID();
        Document doc = new Document(OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 100L);
        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));

        assertThat(knowledgeBaseService.getDocument(docId, OWNER).getFileName()).isEqualTo("a.pdf");
        assertThatThrownBy(() -> knowledgeBaseService.getDocument(docId, "other"))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void unit_kb_deleteDocumentRemovesFromS3AndDb() {
        UUID docId = UUID.randomUUID();
        Document doc = new Document(OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 100L);
        when(documentRepository.existsByIdAndOwnerSubject(docId, OWNER)).thenReturn(true);
        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));

        knowledgeBaseService.deleteDocument(docId, OWNER);

        verify(objectStorageService).delete("/uploads/a.pdf");
        verify(documentRepository).deleteById(docId);
    }

    @Test
    void unit_kb_searchStoresQueryAndReturnsResults() {
        Document doc = new Document(OWNER, "report.pdf", "/uploads/report.pdf", "application/pdf", 1L);
        when(documentRepository.findByOwnerSubjectAndFileNameContainingIgnoreCase(OWNER, "report"))
                .thenReturn(List.of(doc));

        List<Document> results = knowledgeBaseService.search(OWNER, "report");

        assertThat(results).hasSize(1);
        verify(searchQueryRepository).save(any(SearchQuery.class));
    }

    @Test
    void unit_kb_deleteAllForUserPurgesEverything() {
        Document doc = new Document(OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 1L);
        when(documentRepository.findByOwnerSubject(OWNER)).thenReturn(List.of(doc));

        knowledgeBaseService.deleteAllForUser(OWNER);

        verify(objectStorageService).delete("/uploads/a.pdf");
        verify(documentRepository).deleteByOwnerSubject(OWNER);
        verify(searchQueryRepository).deleteByUserSubject(OWNER);
    }
}
