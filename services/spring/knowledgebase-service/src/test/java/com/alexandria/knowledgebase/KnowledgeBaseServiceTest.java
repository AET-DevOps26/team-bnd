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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
        when(documentService.searchByFileNameOrContent(OWNER, "report")).thenReturn(List.of(doc));

        List<Document> results = knowledgeBaseService.search(OWNER, "report");

        assertThat(results).hasSize(1);
        verify(searchQueryRepository).save(any(SearchQuery.class));
    }

    @Test
    void unit_kb_createDocumentPersistsAutoTagsFromGenAi() {
        when(documentService.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        when(genAiClient.summarize(anyString())).thenReturn(new GenAiClient.SummarizeResponse("summary", "model"));
        when(genAiClient.extract(anyString())).thenReturn(new GenAiClient.ExtractResponse(List.of(), "model"));
        when(genAiClient.index(anyString())).thenReturn(new GenAiClient.IndexResponse("/uploads/a.pdf", 1, "embed-model"));
        when(genAiClient.tag(anyString(), anyList())).thenReturn(new GenAiClient.TagResponse(List.of("finance", "report"), "model"));
        when(tagRepository.findByLabel(anyString())).thenReturn(java.util.Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));

        Document result = knowledgeBaseService.createDocument(
                OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 100L, "hello world");

        verify(genAiClient).tag("/uploads/a.pdf", List.of());
        assertThat(result.getTags()).extracting(Tag::getLabel).containsExactlyInAnyOrder("finance", "report");
        assertThat(result.getTags()).allMatch(t -> t.getSource() == TagSource.AUTO);
    }

    @Test
    void unit_kb_createDocumentReusesExistingTagLabel() {
        Tag existing = new Tag("finance", TagSource.USER);
        when(documentService.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        when(genAiClient.summarize(anyString())).thenReturn(new GenAiClient.SummarizeResponse("summary", "model"));
        when(genAiClient.extract(anyString())).thenReturn(new GenAiClient.ExtractResponse(List.of(), "model"));
        when(genAiClient.index(anyString())).thenReturn(new GenAiClient.IndexResponse("/uploads/a.pdf", 1, "embed-model"));
        when(genAiClient.tag(anyString(), anyList())).thenReturn(new GenAiClient.TagResponse(List.of("finance"), "model"));
        when(tagRepository.findByLabel("finance")).thenReturn(java.util.Optional.of(existing));

        Document result = knowledgeBaseService.createDocument(
                OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 100L, "hello world");

        verify(tagRepository, never()).save(any(Tag.class));
        assertThat(result.getTags()).containsExactly(existing);
    }

    @Test
    void unit_kb_createDocumentPassesKnownTagsFromOwner() {
        when(documentService.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        when(genAiClient.summarize(anyString())).thenReturn(new GenAiClient.SummarizeResponse("summary", "model"));
        when(genAiClient.extract(anyString())).thenReturn(new GenAiClient.ExtractResponse(List.of(), "model"));
        when(genAiClient.index(anyString())).thenReturn(new GenAiClient.IndexResponse("/uploads/a.pdf", 1, "embed-model"));
        when(genAiClient.tag(anyString(), anyList())).thenReturn(new GenAiClient.TagResponse(List.of(), "model"));
        when(tagRepository.findTagCountsByOwnerSubject(OWNER)).thenReturn(List.of(tagCount("finance", 2)));

        knowledgeBaseService.createDocument(OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 100L, "hello world");

        verify(genAiClient).tag("/uploads/a.pdf", List.of("finance"));
    }

    @Test
    void unit_kb_createDocumentSurvivesGenAiTagFailure() {
        when(documentService.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        when(genAiClient.summarize(anyString())).thenReturn(new GenAiClient.SummarizeResponse("summary", "model"));
        when(genAiClient.extract(anyString())).thenReturn(new GenAiClient.ExtractResponse(List.of(), "model"));
        when(genAiClient.index(anyString())).thenReturn(new GenAiClient.IndexResponse("/uploads/a.pdf", 1, "embed-model"));
        when(genAiClient.tag(anyString(), anyList())).thenThrow(new RuntimeException("genai down"));

        Document result = knowledgeBaseService.createDocument(
                OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 100L, "hello world");

        assertThat(result.getOwnerSubject()).isEqualTo(OWNER);
        assertThat(result.getTags()).isEmpty();
    }

    @Test
    void unit_kb_reprocessTagsReplacesAutoTagsButKeepsUserTags() {
        UUID docId = UUID.randomUUID();
        Document doc = new Document(OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 100L);
        Tag userTag = new Tag("keep-me", TagSource.USER);
        Tag oldAuto = new Tag("stale", TagSource.AUTO);
        doc.addTag(userTag);
        doc.addTag(oldAuto);
        when(documentService.findByIdAndOwner(docId, OWNER)).thenReturn(doc);
        when(documentService.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        when(genAiClient.tag(anyString(), anyList())).thenReturn(new GenAiClient.TagResponse(List.of("fresh"), "model"));
        when(tagRepository.findByLabel("fresh")).thenReturn(java.util.Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));

        knowledgeBaseService.reprocessTags(docId, OWNER);

        assertThat(doc.getTags()).extracting(Tag::getLabel).containsExactlyInAnyOrder("keep-me", "fresh");
    }

    private static TagRepository.TagCountProjection tagCount(String label, long count) {
        return new TagRepository.TagCountProjection() {
            @Override
            public String getLabel() {
                return label;
            }

            @Override
            public long getDocumentCount() {
                return count;
            }
        };
    }

    @Test
    void unit_kb_semanticSearchMapsHitsBackToUserDocumentsWithContext() {
        Document reportDoc = new Document(OWNER, "report.pdf", "/uploads/report.pdf", "application/pdf", 1L);
        Document notesDoc = new Document(OWNER, "notes.pdf", "/uploads/notes.pdf", "application/pdf", 1L);
        when(documentService.findByOwnerSubject(OWNER)).thenReturn(List.of(reportDoc, notesDoc));
        when(genAiClient.search(eq("budget"), anyList(), isNull())).thenReturn(new GenAiClient.SearchResponse(
                List.of(new GenAiClient.SearchResult("/uploads/report.pdf", 0.91, "the annual budget was...")), "embed-model"));

        List<com.alexandria.knowledgebase.dto.SemanticSearchResultDto> results = knowledgeBaseService.semanticSearch(OWNER, "budget", null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).document()).isEqualTo(reportDoc);
        assertThat(results.get(0).score()).isEqualTo(0.91);
        assertThat(results.get(0).snippet()).isEqualTo("the annual budget was...");
        verify(searchQueryRepository).save(any(SearchQuery.class));
    }

    @Test
    void unit_kb_semanticSearchScopesToUserObjectKeys() {
        Document reportDoc = new Document(OWNER, "report.pdf", "/uploads/report.pdf", "application/pdf", 1L);
        when(documentService.findByOwnerSubject(OWNER)).thenReturn(List.of(reportDoc));
        when(genAiClient.search(anyString(), anyList(), any())).thenReturn(new GenAiClient.SearchResponse(List.of(), "embed-model"));
        when(documentService.searchByFileNameOrContent(OWNER, "budget")).thenReturn(List.of());

        knowledgeBaseService.semanticSearch(OWNER, "budget", 5);

        verify(genAiClient).search("budget", List.of("/uploads/report.pdf"), 5);
    }

    @Test
    void unit_kb_semanticSearchFallsBackToKeywordOnGenAiFailure() {
        Document reportDoc = new Document(OWNER, "report.pdf", "/uploads/report.pdf", "application/pdf", 1L);
        when(documentService.findByOwnerSubject(OWNER)).thenReturn(List.of(reportDoc));
        when(genAiClient.search(anyString(), anyList(), any())).thenThrow(new RuntimeException("genai down"));
        when(documentService.searchByFileNameOrContent(OWNER, "report")).thenReturn(List.of(reportDoc));

        List<com.alexandria.knowledgebase.dto.SemanticSearchResultDto> results = knowledgeBaseService.semanticSearch(OWNER, "report", null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).document()).isEqualTo(reportDoc);
        assertThat(results.get(0).score()).isNull();
        assertThat(results.get(0).snippet()).isNull();
    }

    @Test
    void unit_kb_semanticSearchFallsBackToKeywordOnEmptyIndex() {
        Document reportDoc = new Document(OWNER, "report.pdf", "/uploads/report.pdf", "application/pdf", 1L);
        when(documentService.findByOwnerSubject(OWNER)).thenReturn(List.of(reportDoc));
        when(genAiClient.search(anyString(), anyList(), any())).thenReturn(new GenAiClient.SearchResponse(List.of(), "embed-model"));
        when(documentService.searchByFileNameOrContent(OWNER, "report")).thenReturn(List.of(reportDoc));

        List<com.alexandria.knowledgebase.dto.SemanticSearchResultDto> results = knowledgeBaseService.semanticSearch(OWNER, "report", null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).document()).isEqualTo(reportDoc);
        assertThat(results.get(0).snippet()).isNull();
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
