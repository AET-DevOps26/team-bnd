package com.alexandria.knowledgebase;

import com.alexandria.knowledgebase.document.*;
import com.alexandria.knowledgebase.dto.DocumentRefDto;
import com.alexandria.knowledgebase.dto.SemanticSearchResponseDto;
import com.alexandria.knowledgebase.dto.UpdateDocumentRequest;
import com.alexandria.knowledgebase.integration.GenAiClient;
import com.alexandria.knowledgebase.search.SearchQuery;
import com.alexandria.knowledgebase.search.SearchQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private ObjectProvider<KnowledgeBaseService> self;

    private KnowledgeBaseService knowledgeBaseService;

    private static final String OWNER = "oidc|123";

    @BeforeEach
    void setup() {
        knowledgeBaseService = new KnowledgeBaseService(
                documentService, summaryRepository, extractedEntityRepository, tagRepository, searchQueryRepository, genAiClient, textExtractor, objectStorageService, self
        );
        // The async pipeline is entered via the self-proxy and reloads the document by id.
        // Route the proxy back to the instance and let findById return the last saved doc,
        // so the pipeline runs inline on the same object the create/upload tests assert on.
        lenient().when(self.getObject()).thenReturn(knowledgeBaseService);
        lenient().when(documentService.findById(any(UUID.class))).thenAnswer(inv -> lastSavedDocument);
    }

    private Document lastSavedDocument;

    private Document recordSaved(org.mockito.invocation.InvocationOnMock inv) {
        Document saved = inv.getArgument(0);
        if (saved.getId() == null) {
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
        }
        lastSavedDocument = saved;
        return saved;
    }

    @Test
    void unit_kb_createDocumentPersistsAndCallsGenAiForText() {
        when(documentService.save(any(Document.class))).thenAnswer(this::recordSaved);
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
        when(documentService.save(any(Document.class))).thenAnswer(this::recordSaved);

        knowledgeBaseService.createDocument(OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 100L, "");

        verifyNoInteractions(genAiClient);
    }

    @Test
    void unit_kb_createDocumentSurvivesGenAiIndexFailure() {
        when(documentService.save(any(Document.class))).thenAnswer(this::recordSaved);
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
        when(documentService.save(any(Document.class))).thenAnswer(this::recordSaved);
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
        when(documentService.save(any(Document.class))).thenAnswer(this::recordSaved);
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

        List<DocumentRefDto> results = knowledgeBaseService.search(OWNER, "report");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).fileName()).isEqualTo("report.pdf");
        verify(searchQueryRepository).save(any(SearchQuery.class));
    }

    @Test
    void unit_kb_createDocumentPersistsAutoTagsFromGenAi() {
        when(documentService.save(any(Document.class))).thenAnswer(this::recordSaved);
        when(genAiClient.summarize(anyString())).thenReturn(new GenAiClient.SummarizeResponse("summary", "model"));
        when(genAiClient.extract(anyString())).thenReturn(new GenAiClient.ExtractResponse(List.of(), "model"));
        when(genAiClient.index(anyString())).thenReturn(new GenAiClient.IndexResponse("/uploads/a.pdf", 1, "embed-model"));
        when(genAiClient.tag(anyString(), anyList())).thenReturn(new GenAiClient.TagResponse(List.of("finance", "report"), "model"));
        when(tagRepository.findByLabel(anyString())).thenReturn(Optional.empty());
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
        when(documentService.save(any(Document.class))).thenAnswer(this::recordSaved);
        when(genAiClient.summarize(anyString())).thenReturn(new GenAiClient.SummarizeResponse("summary", "model"));
        when(genAiClient.extract(anyString())).thenReturn(new GenAiClient.ExtractResponse(List.of(), "model"));
        when(genAiClient.index(anyString())).thenReturn(new GenAiClient.IndexResponse("/uploads/a.pdf", 1, "embed-model"));
        when(genAiClient.tag(anyString(), anyList())).thenReturn(new GenAiClient.TagResponse(List.of("finance"), "model"));
        when(tagRepository.findByLabel("finance")).thenReturn(Optional.of(existing));

        Document result = knowledgeBaseService.createDocument(
                OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 100L, "hello world");

        verify(tagRepository, never()).save(any(Tag.class));
        assertThat(result.getTags()).containsExactly(existing);
    }

    @Test
    void unit_kb_createDocumentPassesKnownTagsFromOwner() {
        when(documentService.save(any(Document.class))).thenAnswer(this::recordSaved);
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
        when(documentService.save(any(Document.class))).thenAnswer(this::recordSaved);
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
        when(documentService.save(any(Document.class))).thenAnswer(this::recordSaved);
        when(genAiClient.tag(anyString(), anyList())).thenReturn(new GenAiClient.TagResponse(List.of("fresh"), "model"));
        when(tagRepository.findByLabel("fresh")).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));

        knowledgeBaseService.reprocessTags(docId, OWNER);

        assertThat(doc.getTags()).extracting(Tag::getLabel).containsExactlyInAnyOrder("keep-me", "fresh");
    }

    @Test
    void unit_kb_getDocumentsDelegates() {
        Document doc = new Document(OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 1L);
        when(documentService.findByOwnerSubject(OWNER)).thenReturn(List.of(doc));
        assertThat(knowledgeBaseService.getDocuments(OWNER)).containsExactly(doc);
    }

    @Test
    void unit_kb_resolveDocumentsReturnsEmptyForNoKeys() {
        assertThat(knowledgeBaseService.resolveDocuments(OWNER, List.of())).isEmpty();
        assertThat(knowledgeBaseService.resolveDocuments(OWNER, null)).isEmpty();
        verify(documentService, never()).findByOwnerSubjectAndObjectKeyIn(anyString(), anyList());
    }

    @Test
    void unit_kb_resolveDocumentsDelegatesForKeys() {
        Document doc = new Document(OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 1L);
        when(documentService.findByOwnerSubjectAndObjectKeyIn(OWNER, List.of("/uploads/a.pdf"))).thenReturn(List.of(doc));
        assertThat(knowledgeBaseService.resolveDocuments(OWNER, List.of("/uploads/a.pdf"))).containsExactly(doc);
    }

    @Test
    void unit_kb_getDocumentSummaryAndEntitiesReadThroughDocument() {
        UUID docId = UUID.randomUUID();
        Document doc = new Document(OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 1L);
        Summary summary = new Summary(doc, "text", "model");
        doc.setSummary(summary);
        ExtractedEntity entity = new ExtractedEntity(doc, "Ada", EntityType.PERSON, 0.9);
        doc.setExtractedEntities(List.of(entity));
        when(documentService.findByIdAndOwner(docId, OWNER)).thenReturn(doc);

        assertThat(knowledgeBaseService.getDocumentSummary(docId, OWNER)).isSameAs(summary);
        assertThat(knowledgeBaseService.getDocumentEntities(docId, OWNER)).containsExactly(entity);
    }

    @Test
    void unit_kb_getFileContentReturnsBytes() {
        UUID docId = UUID.randomUUID();
        Document doc = new Document(OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 1L);
        when(documentService.findByIdAndOwner(docId, OWNER)).thenReturn(doc);
        when(objectStorageService.download("/uploads/a.pdf")).thenReturn("hi".getBytes());

        assertThat(knowledgeBaseService.getFileContent(docId, OWNER)).contains("hi".getBytes());
    }

    @Test
    void unit_kb_getFileContentEmptyOnDownloadFailure() {
        UUID docId = UUID.randomUUID();
        Document doc = new Document(OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 1L);
        when(documentService.findByIdAndOwner(docId, OWNER)).thenReturn(doc);
        when(objectStorageService.download("/uploads/a.pdf")).thenThrow(new RuntimeException("s3 down"));

        assertThat(knowledgeBaseService.getFileContent(docId, OWNER)).isEmpty();
    }

    @Test
    void unit_kb_updateDocumentChangesFileName() {
        UUID docId = UUID.randomUUID();
        Document doc = new Document(OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 1L);
        when(documentService.findByIdAndOwner(docId, OWNER)).thenReturn(doc);
        when(documentService.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        knowledgeBaseService.updateDocument(docId, new UpdateDocumentRequest("b.pdf"), OWNER);

        assertThat(doc.getFileName()).isEqualTo("b.pdf");
    }

    @Test
    void unit_kb_updateDocumentIgnoresNullFileName() {
        UUID docId = UUID.randomUUID();
        Document doc = new Document(OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 1L);
        when(documentService.findByIdAndOwner(docId, OWNER)).thenReturn(doc);
        when(documentService.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        knowledgeBaseService.updateDocument(docId, new UpdateDocumentRequest(null), OWNER);

        assertThat(doc.getFileName()).isEqualTo("a.pdf");
    }

    @Test
    void unit_kb_addTagReusesOrCreates() {
        UUID docId = UUID.randomUUID();
        Document doc = new Document(OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 1L);
        when(documentService.findByIdAndOwner(docId, OWNER)).thenReturn(doc);
        when(tagRepository.findByLabel("finance")).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));

        knowledgeBaseService.addTag(docId, OWNER, "finance", TagSource.USER);

        assertThat(doc.getTags()).extracting(Tag::getLabel).containsExactly("finance");
        verify(documentService).save(doc);
    }

    @Test
    void unit_kb_removeTagWhenPresent() {
        UUID docId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();
        Document doc = new Document(OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 1L);
        Tag tag = new Tag("finance", TagSource.USER);
        doc.addTag(tag);
        when(documentService.findByIdAndOwner(docId, OWNER)).thenReturn(doc);
        when(tagRepository.findById(tagId)).thenReturn(Optional.of(tag));

        knowledgeBaseService.removeTag(docId, OWNER, tagId);

        assertThat(doc.getTags()).isEmpty();
        verify(documentService).save(doc);
    }

    @Test
    void unit_kb_removeTagNoopWhenMissing() {
        UUID docId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();
        Document doc = new Document(OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 1L);
        when(documentService.findByIdAndOwner(docId, OWNER)).thenReturn(doc);
        when(tagRepository.findById(tagId)).thenReturn(Optional.empty());

        knowledgeBaseService.removeTag(docId, OWNER, tagId);

        verify(documentService, never()).save(any(Document.class));
    }

    @Test
    void unit_kb_reprocessSummaryReusesExistingRowAndRegenerates() {
        UUID docId = UUID.randomUUID();
        Document doc = new Document(OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 1L);
        Summary existing = new Summary(doc, "old", "model");
        doc.setSummary(existing);
        when(documentService.findByIdAndOwner(docId, OWNER)).thenReturn(doc);
        when(genAiClient.summarize("/uploads/a.pdf")).thenReturn(new GenAiClient.SummarizeResponse("new", "model"));
        when(summaryRepository.save(any(Summary.class))).thenAnswer(inv -> inv.getArgument(0));

        knowledgeBaseService.reprocessSummary(docId, OWNER);

        // regenerated in place, never deleted, so the summary status stays visible during reprocess
        verify(summaryRepository, never()).delete(any(Summary.class));
        assertThat(doc.getSummary()).isSameAs(existing);
        assertThat(existing.getContent()).isEqualTo("new");
        assertThat(existing.getStatus()).isEqualTo(SummaryStatus.COMPLETED);
    }

    @Test
    void unit_kb_reprocessEntitiesRebuildsFromGenAi() {
        UUID docId = UUID.randomUUID();
        Document doc = new Document(OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 1L);
        when(documentService.findByIdAndOwner(docId, OWNER)).thenReturn(doc);
        when(genAiClient.extract("/uploads/a.pdf")).thenReturn(new GenAiClient.ExtractResponse(List.of(new GenAiClient.ExtractedEntityDto("Ada", EntityType.PERSON, 0.9)), "model"));
        when(extractedEntityRepository.save(any(ExtractedEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        knowledgeBaseService.reprocessEntities(docId, OWNER);

        verify(extractedEntityRepository).deleteByDocumentId(docId);
        org.mockito.ArgumentCaptor<ExtractedEntity> captor = org.mockito.ArgumentCaptor.forClass(ExtractedEntity.class);
        verify(extractedEntityRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Ada");
    }

    @Test
    void unit_kb_getSearchHistoryDelegates() {
        SearchQuery query = new SearchQuery(OWNER, "report", 1);
        when(searchQueryRepository.findByUserSubjectOrderByTimestampDesc(OWNER)).thenReturn(List.of(query));
        assertThat(knowledgeBaseService.getSearchHistory(OWNER)).containsExactly(query);
    }

    @Test
    void unit_kb_deleteSearchHistoryDelegates() {
        knowledgeBaseService.deleteSearchHistory(OWNER);
        verify(searchQueryRepository).deleteByUserSubject(OWNER);
    }

    @Test
    void unit_kb_getTagsForUserWithCountMapsProjection() {
        when(tagRepository.findTagCountsByOwnerSubject(OWNER)).thenReturn(List.of(tagCount("finance", 3)));
        assertThat(knowledgeBaseService.getTagsForUserWithCount(OWNER)).containsEntry("finance", 3L);
    }

    @Test
    void unit_kb_processDocumentAsyncSkipsMissingDocument() {
        UUID docId = UUID.randomUUID();
        when(documentService.findById(docId)).thenThrow(new RuntimeException("gone"));

        knowledgeBaseService.processDocumentAsync(docId);

        verifyNoInteractions(genAiClient);
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
        when(documentService.findObjectKeysByOwnerSubject(OWNER)).thenReturn(List.of("/uploads/report.pdf", "/uploads/notes.pdf"));
        when(genAiClient.search(eq("budget"), anyList(), eq(10))).thenReturn(new GenAiClient.SearchResponse(
                List.of(new GenAiClient.SearchResult("/uploads/report.pdf", 0.91, "the annual budget was...")), "embed-model"));
        when(documentService.findByOwnerSubjectAndObjectKeyIn(OWNER, List.of("/uploads/report.pdf"))).thenReturn(List.of(reportDoc));

        SemanticSearchResponseDto response = knowledgeBaseService.semanticSearch(OWNER, "budget", 10);

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).document().fileName()).isEqualTo("report.pdf");
        assertThat(response.results().get(0).score()).isEqualTo(0.91);
        assertThat(response.results().get(0).snippet()).isEqualTo("the annual budget was...");
        assertThat(response.fallbackUsed()).isFalse();
        verify(searchQueryRepository).save(any(SearchQuery.class));
    }

    @Test
    void unit_kb_semanticSearchScopesToUserObjectKeys() {
        when(documentService.findObjectKeysByOwnerSubject(OWNER)).thenReturn(List.of("/uploads/report.pdf"));
        when(genAiClient.search(anyString(), anyList(), any())).thenReturn(new GenAiClient.SearchResponse(List.of(), "embed-model"));
        when(documentService.searchByFileNameOrContent(OWNER, "budget")).thenReturn(List.of());

        knowledgeBaseService.semanticSearch(OWNER, "budget", 5);

        verify(genAiClient).search("budget", List.of("/uploads/report.pdf"), 5);
    }

    @Test
    void unit_kb_semanticSearchFallsBackToKeywordOnGenAiFailure() {
        Document reportDoc = new Document(OWNER, "report.pdf", "/uploads/report.pdf", "application/pdf", 1L);
        when(documentService.findObjectKeysByOwnerSubject(OWNER)).thenReturn(List.of("/uploads/report.pdf"));
        when(genAiClient.search(anyString(), anyList(), any())).thenThrow(new RuntimeException("genai down"));
        when(documentService.searchByFileNameOrContent(OWNER, "report")).thenReturn(List.of(reportDoc));

        SemanticSearchResponseDto response = knowledgeBaseService.semanticSearch(OWNER, "report", 10);

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).document().fileName()).isEqualTo("report.pdf");
        assertThat(response.results().get(0).score()).isNull();
        assertThat(response.results().get(0).snippet()).isNull();
        assertThat(response.fallbackUsed()).isTrue();
    }

    @Test
    void unit_kb_semanticSearchFallsBackToKeywordOnEmptyIndex() {
        Document reportDoc = new Document(OWNER, "report.pdf", "/uploads/report.pdf", "application/pdf", 1L);
        when(documentService.findObjectKeysByOwnerSubject(OWNER)).thenReturn(List.of("/uploads/report.pdf"));
        when(genAiClient.search(anyString(), anyList(), any())).thenReturn(new GenAiClient.SearchResponse(List.of(), "embed-model"));
        when(documentService.searchByFileNameOrContent(OWNER, "report")).thenReturn(List.of(reportDoc));

        SemanticSearchResponseDto response = knowledgeBaseService.semanticSearch(OWNER, "report", 10);

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).document().fileName()).isEqualTo("report.pdf");
        assertThat(response.results().get(0).snippet()).isNull();
        assertThat(response.fallbackUsed()).isTrue();
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

    @Test
    void unit_kb_createDocumentDefersAsyncProcessingUntilAfterCommit() {
        KnowledgeBaseService selfProxy = mock(KnowledgeBaseService.class);
        when(self.getObject()).thenReturn(selfProxy);
        when(documentService.save(any(Document.class))).thenAnswer(this::recordSaved);

        TransactionSynchronizationManager.initSynchronization();
        try {
            Document result = knowledgeBaseService.createDocument(
                    OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 100L, "hello world");

            // While the transaction is open the async pipeline must not run yet.
            verify(selfProxy, never()).processDocumentAsync(any(UUID.class));

            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }
            verify(selfProxy).processDocumentAsync(result.getId());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
