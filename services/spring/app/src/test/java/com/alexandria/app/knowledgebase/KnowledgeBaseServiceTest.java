package com.alexandria.app.knowledgebase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.alexandria.app.document.*;
import com.alexandria.app.exception.DocumentNotFoundException;
import com.alexandria.app.knowledgebase.dto.UpdateDocumentRequest;
import com.alexandria.app.qa.QAInteraction;
import com.alexandria.app.qa.QAInteractionRepository;
import com.alexandria.app.search.SearchQuery;
import com.alexandria.app.search.SearchQueryRepository;
import com.alexandria.app.user.User;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceTest {

  @Mock private DocumentRepository documentRepository;

  @Mock private SummaryRepository summaryRepository;

  @Mock private ExtractedEntityRepository extractedEntityRepository;

  @Mock private TagRepository tagRepository;

  @Mock private SearchQueryRepository searchQueryRepository;

  @Mock private QAInteractionRepository qaInteractionRepository;

  @Mock private GenAiClient genAiClient;

  @Mock private TextExtractor textExtractor;

  @Mock private ObjectStorageService objectStorageService;

  private KnowledgeBaseService knowledgeBaseService;

  private User testUser;

  @BeforeEach
  void setup() throws Exception {
    knowledgeBaseService =
        new KnowledgeBaseService(
            documentRepository,
            summaryRepository,
            extractedEntityRepository,
            tagRepository,
            searchQueryRepository,
            qaInteractionRepository,
            genAiClient,
            textExtractor,
            objectStorageService);
    testUser = new User("oidc|123", "testuser", "test@example.com");
    setUserId(testUser, UUID.randomUUID());
  }

  private void setUserId(User user, UUID id) throws Exception {
    Field idField = User.class.getDeclaredField("id");
    idField.setAccessible(true);
    idField.set(user, id);
  }

  private void setDocumentId(Document doc, UUID id) throws Exception {
    Field idField = Document.class.getDeclaredField("id");
    idField.setAccessible(true);
    idField.set(doc, id);
  }

  @Test
  void unit_kb_createDocumentSavesDocumentWithoutContent() {
    Document savedDoc = new Document(testUser, "image.png", "/files/image.png", "image/png", 5120L);
    when(documentRepository.save(any(Document.class))).thenReturn(savedDoc);

    Document result =
        knowledgeBaseService.createDocument(
            testUser, "image.png", "/files/image.png", "image/png", 5120L, null);

    assertThat(result.getFileName()).isEqualTo("image.png");
    verify(documentRepository).save(any(Document.class));
    verify(genAiClient, never()).summarize(anyString());
    verify(genAiClient, never()).extract(anyString());
  }

  @Test
  void unit_kb_createDocumentWithContentCallsGenAi() {
    String objectKey = "/files/report.docx";
    Document savedDoc =
        new Document(
            testUser,
            "report.docx",
            objectKey,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            3072L);
    savedDoc.setRawTextContent("Some text content");
    when(documentRepository.save(any(Document.class))).thenReturn(savedDoc);

    GenAiClient.SummarizeResponse summaryResponse =
        new GenAiClient.SummarizeResponse("Summary text", "gpt-4");
    when(genAiClient.summarize(objectKey)).thenReturn(summaryResponse);

    List<GenAiClient.ExtractedEntityDto> entities =
        List.of(new GenAiClient.ExtractedEntityDto("John Doe", EntityType.PERSON, 0.95));
    GenAiClient.ExtractResponse extractResponse =
        new GenAiClient.ExtractResponse(entities, "gpt-4");
    when(genAiClient.extract(objectKey)).thenReturn(extractResponse);

    Document result =
        knowledgeBaseService.createDocument(
            testUser,
            "report.docx",
            objectKey,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            3072L,
            "Some text content");

    assertThat(result).isNotNull();
    verify(genAiClient).summarize(objectKey);
    verify(genAiClient).extract(objectKey);
    verify(summaryRepository).save(any(Summary.class));
    verify(extractedEntityRepository).save(any(ExtractedEntity.class));
  }

  @Test
  void unit_kb_createDocumentContinuesWhenGenAiFails() {
    String objectKey = "/files/notes.md";
    Document savedDoc = new Document(testUser, "notes.md", objectKey, "text/markdown", 256L);
    when(documentRepository.save(any(Document.class))).thenReturn(savedDoc);
    when(genAiClient.summarize(objectKey)).thenThrow(new RuntimeException("GenAI unavailable"));
    when(genAiClient.extract(objectKey)).thenThrow(new RuntimeException("GenAI unavailable"));

    Document result =
        knowledgeBaseService.createDocument(
            testUser, "notes.md", objectKey, "text/markdown", 256L, "Some text content");

    assertThat(result).isNotNull();
    verify(summaryRepository, never()).save(any());
    verify(extractedEntityRepository, never()).save(any());
  }

  @Test
  void unit_kb_getDocumentsReturnsUserDocuments() {
    UUID ownerId = testUser.getId();
    Document doc1 =
        new Document(testUser, "report.pdf", "/files/report.pdf", "application/pdf", 1024L);
    Document doc2 = new Document(testUser, "data.csv", "/files/data.csv", "text/csv", 2048L);
    when(documentRepository.findByOwnerId(ownerId)).thenReturn(List.of(doc1, doc2));

    List<Document> result = knowledgeBaseService.getDocuments(ownerId);

    assertThat(result).hasSize(2);
    assertThat(result).extracting(Document::getFileName).containsExactly("report.pdf", "data.csv");
  }

  @Test
  void unit_kb_getDocumentReturnsDocumentForOwner() throws Exception {
    UUID documentId = UUID.randomUUID();
    Document document =
        new Document(testUser, "meeting.txt", "/files/meeting.txt", "text/plain", 1024L);
    setDocumentId(document, documentId);
    when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

    Document result = knowledgeBaseService.getDocument(documentId, testUser.getId());

    assertThat(result.getFileName()).isEqualTo("meeting.txt");
  }

  @Test
  void unit_kb_getDocumentThrowsForWrongOwner() throws Exception {
    UUID documentId = UUID.randomUUID();
    UUID wrongOwnerId = UUID.randomUUID();
    Document document =
        new Document(
            testUser,
            "secret.docx",
            "/files/secret.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            1024L);
    setDocumentId(document, documentId);
    when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

    assertThatThrownBy(() -> knowledgeBaseService.getDocument(documentId, wrongOwnerId))
        .isInstanceOf(DocumentNotFoundException.class);
  }

  @Test
  void unit_kb_getDocumentThrowsWhenNotFound() {
    UUID documentId = UUID.randomUUID();
    when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> knowledgeBaseService.getDocument(documentId, testUser.getId()))
        .isInstanceOf(DocumentNotFoundException.class);
  }

  @Test
  void unit_kb_deleteDocumentSucceeds() {
    UUID documentId = UUID.randomUUID();
    when(documentRepository.existsByIdAndOwnerId(documentId, testUser.getId())).thenReturn(true);

    knowledgeBaseService.deleteDocument(documentId, testUser.getId());

    verify(documentRepository).deleteById(documentId);
  }

  @Test
  void unit_kb_deleteDocumentThrowsWhenNotFound() {
    UUID documentId = UUID.randomUUID();
    when(documentRepository.existsByIdAndOwnerId(documentId, testUser.getId())).thenReturn(false);

    assertThatThrownBy(() -> knowledgeBaseService.deleteDocument(documentId, testUser.getId()))
        .isInstanceOf(DocumentNotFoundException.class);

    verify(documentRepository, never()).deleteById(any());
  }

  @Test
  void unit_kb_searchReturnsMatchingDocuments() {
    Document doc =
        new Document(testUser, "report.pdf", "/files/report.pdf", "application/pdf", 1024L);
    when(documentRepository.findByOwnerIdAndFileNameContainingIgnoreCase(
            testUser.getId(), "report"))
        .thenReturn(List.of(doc));
    when(searchQueryRepository.save(any(SearchQuery.class))).thenAnswer(inv -> inv.getArgument(0));

    List<Document> results = knowledgeBaseService.search(testUser, "report");

    assertThat(results).hasSize(1);
    assertThat(results.get(0).getFileName()).isEqualTo("report.pdf");
    verify(searchQueryRepository).save(any(SearchQuery.class));
  }

  @Test
  void unit_kb_askQueriesGenAiAndSavesInteraction() {
    UUID docId = UUID.randomUUID();
    Document doc = new Document(testUser, "manual.html", "/files/manual.html", "text/html", 8192L);
    when(documentRepository.findByOwnerId(testUser.getId())).thenReturn(List.of(doc));

    GenAiClient.AskResponse askResponse =
        new GenAiClient.AskResponse("The answer is 42", List.of("/files/manual.html"), "gpt-4");
    when(genAiClient.ask(anyString(), any())).thenReturn(askResponse);
    when(qaInteractionRepository.save(any(QAInteraction.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    QAInteraction result = knowledgeBaseService.ask(testUser, "What is the answer?");

    assertThat(result.getQuestion()).isEqualTo("What is the answer?");
    assertThat(result.getAnswer()).isEqualTo("The answer is 42");
    assertThat(result.getModelUsed()).isEqualTo("gpt-4");
    verify(genAiClient).ask(eq("What is the answer?"), any());
    verify(qaInteractionRepository).save(any(QAInteraction.class));
  }

  @Test
  void unit_kb_addTagCreatesNewTag() throws Exception {
    UUID documentId = UUID.randomUUID();
    Document document =
        new Document(testUser, "contract.pdf", "/files/contract.pdf", "application/pdf", 1024L);
    setDocumentId(document, documentId);
    when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
    when(tagRepository.findByLabel("important")).thenReturn(Optional.empty());
    Tag newTag = new Tag("important", TagSource.USER);
    when(tagRepository.save(any(Tag.class))).thenReturn(newTag);
    when(documentRepository.save(any(Document.class))).thenReturn(document);

    knowledgeBaseService.addTag(documentId, testUser.getId(), "important", TagSource.USER);

    verify(tagRepository).save(any(Tag.class));
    verify(documentRepository).save(document);
  }

  @Test
  void unit_kb_addTagUsesExistingTag() throws Exception {
    UUID documentId = UUID.randomUUID();
    Document document =
        new Document(
            testUser,
            "invoice.xlsx",
            "/files/invoice.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            2048L);
    setDocumentId(document, documentId);
    Tag existingTag = new Tag("important", TagSource.USER);
    when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
    when(tagRepository.findByLabel("important")).thenReturn(Optional.of(existingTag));
    when(documentRepository.save(any(Document.class))).thenReturn(document);

    knowledgeBaseService.addTag(documentId, testUser.getId(), "important", TagSource.USER);

    verify(tagRepository, never()).save(any(Tag.class));
    verify(documentRepository).save(document);
  }

  @Test
  void unit_kb_removeTagRemovesTagFromDocument() throws Exception {
    UUID documentId = UUID.randomUUID();
    UUID tagId = UUID.randomUUID();
    Document document =
        new Document(testUser, "archive.zip", "/files/archive.zip", "application/zip", 10240L);
    setDocumentId(document, documentId);
    Tag tag = new Tag("important", TagSource.USER);
    document.addTag(tag);

    when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
    when(tagRepository.findById(tagId)).thenReturn(Optional.of(tag));
    when(documentRepository.save(any(Document.class))).thenReturn(document);

    knowledgeBaseService.removeTag(documentId, testUser.getId(), tagId);

    verify(documentRepository).save(document);
    assertThat(document.getTags()).doesNotContain(tag);
  }

  @Test
  void unit_kb_getQAHistoryReturnsUserHistory() {
    QAInteraction interaction =
        new QAInteraction(testUser, "Question?", "Answer", List.of(), "gpt-4");
    when(qaInteractionRepository.findByUserIdOrderByTimestampDesc(testUser.getId()))
        .thenReturn(List.of(interaction));

    List<QAInteraction> result = knowledgeBaseService.getQAHistory(testUser.getId());

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getQuestion()).isEqualTo("Question?");
  }

  @Test
  void unit_kb_getSearchHistoryReturnsUserHistory() {
    SearchQuery query = new SearchQuery(testUser, "search term", 5);
    when(searchQueryRepository.findByUserIdOrderByTimestampDesc(testUser.getId()))
         .thenReturn(List.of(query));

    List<SearchQuery> result = knowledgeBaseService.getSearchHistory(testUser.getId());

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getQueryText()).isEqualTo("search term");
  }

  @Test
  void unit_kb_updateDocumentRenamesFile() throws Exception {
    UUID documentId = UUID.randomUUID();
    Document document =
        new Document(testUser, "old-name.pdf", "/files/old.pdf", "application/pdf", 1024L);
    setDocumentId(document, documentId);
    when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
    when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

    UpdateDocumentRequest request = new UpdateDocumentRequest("new-name.pdf");
    Document result = knowledgeBaseService.updateDocument(documentId, request, testUser.getId());

    assertThat(result.getFileName()).isEqualTo("new-name.pdf");
    verify(documentRepository).save(document);
  }

  @Test
  void unit_kb_updateDocumentIgnoresNullFileName() throws Exception {
    UUID documentId = UUID.randomUUID();
    Document document =
        new Document(testUser, "keep.pdf", "/files/keep.pdf", "application/pdf", 1024L);
    setDocumentId(document, documentId);
    when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
    when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

    UpdateDocumentRequest request = new UpdateDocumentRequest(null);
    Document result = knowledgeBaseService.updateDocument(documentId, request, testUser.getId());

    assertThat(result.getFileName()).isEqualTo("keep.pdf");
  }

  @Test
  void unit_kb_getDocumentSummaryReturnsSummary() throws Exception {
    UUID documentId = UUID.randomUUID();
    Document document =
        new Document(testUser, "paper.pdf", "/files/paper.pdf", "application/pdf", 4096L);
    setDocumentId(document, documentId);
    Summary summary = new Summary(document, "This is a summary", "gpt-4");
    document.setSummary(summary);
    when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

    Summary result = knowledgeBaseService.getDocumentSummary(documentId, testUser.getId());

    assertThat(result.getContent()).isEqualTo("This is a summary");
    assertThat(result.getModelUsed()).isEqualTo("gpt-4");
  }

  @Test
  void unit_kb_getDocumentEntitiesReturnsList() throws Exception {
    UUID documentId = UUID.randomUUID();
    Document document =
        new Document(testUser, "report.pdf", "/files/report.pdf", "application/pdf", 2048L);
    setDocumentId(document, documentId);
    ExtractedEntity entity =
        new ExtractedEntity(document, "Acme Corp", EntityType.ORGANIZATION, 0.9);
    document.setExtractedEntities(List.of(entity));
    when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

    List<ExtractedEntity> result =
        knowledgeBaseService.getDocumentEntities(documentId, testUser.getId());

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getName()).isEqualTo("Acme Corp");
  }

  @Test
  void unit_kb_reprocessSummaryDeletesOldAndCallsGenAi() throws Exception {
    UUID documentId = UUID.randomUUID();
    Document document =
        new Document(testUser, "doc.pdf", "/files/doc.pdf", "application/pdf", 1024L);
    setDocumentId(document, documentId);
    Summary oldSummary = new Summary(document, "Old summary", "gpt-3");
    document.setSummary(oldSummary);
    when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
    GenAiClient.SummarizeResponse response =
        new GenAiClient.SummarizeResponse("New summary", "gpt-4");
    when(genAiClient.summarize("/files/doc.pdf")).thenReturn(response);

    knowledgeBaseService.reprocessSummary(documentId, testUser.getId());

    verify(summaryRepository).delete(oldSummary);
    verify(summaryRepository).flush();
    verify(genAiClient).summarize("/files/doc.pdf");
    verify(summaryRepository).save(any(Summary.class));
    assertThat(document.getSummary()).isNotNull();
    assertThat(document.getSummary().getContent()).isEqualTo("New summary");
  }

  @Test
  void unit_kb_reprocessSummaryWithoutExistingSkipsDelete() throws Exception {
    UUID documentId = UUID.randomUUID();
    Document document =
        new Document(testUser, "doc.pdf", "/files/doc.pdf", "application/pdf", 1024L);
    setDocumentId(document, documentId);
    when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
    GenAiClient.SummarizeResponse response =
        new GenAiClient.SummarizeResponse("New summary", "gpt-4");
    when(genAiClient.summarize("/files/doc.pdf")).thenReturn(response);

    knowledgeBaseService.reprocessSummary(documentId, testUser.getId());

    verify(summaryRepository, never()).delete(any(Summary.class));
    verify(summaryRepository).save(any(Summary.class));
  }

  @Test
  void unit_kb_reprocessEntitiesDeletesOldAndCallsGenAi() throws Exception {
    UUID documentId = UUID.randomUUID();
    Document document =
        new Document(testUser, "doc.pdf", "/files/doc.pdf", "application/pdf", 1024L);
    setDocumentId(document, documentId);
    when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
    List<GenAiClient.ExtractedEntityDto> entities =
        List.of(new GenAiClient.ExtractedEntityDto("Entity", EntityType.TOPIC, 0.8));
    GenAiClient.ExtractResponse response = new GenAiClient.ExtractResponse(entities, "gpt-4");
    when(genAiClient.extract("/files/doc.pdf")).thenReturn(response);

    knowledgeBaseService.reprocessEntities(documentId, testUser.getId());

    verify(extractedEntityRepository).deleteByDocumentId(documentId);
    verify(extractedEntityRepository).flush();
    verify(genAiClient).extract("/files/doc.pdf");
    verify(extractedEntityRepository).save(any(ExtractedEntity.class));
  }

  @Test
  void unit_kb_deleteQAHistoryDeletesByUserId() {
    knowledgeBaseService.deleteQAHistory(testUser.getId());

    verify(qaInteractionRepository).deleteByUserId(testUser.getId());
  }

  @Test
  void unit_kb_deleteSearchHistoryDeletesByUserId() {
    knowledgeBaseService.deleteSearchHistory(testUser.getId());

    verify(searchQueryRepository).deleteByUserId(testUser.getId());
  }

  @Test
  void unit_kb_getTagsForUserWithCountDelegatesToDatabase() {
    TagRepository.TagCountProjection financeCount = mock(TagRepository.TagCountProjection.class);
    when(financeCount.getLabel()).thenReturn("finance");
    when(financeCount.getDocumentCount()).thenReturn(2L);
    TagRepository.TagCountProjection legalCount = mock(TagRepository.TagCountProjection.class);
    when(legalCount.getLabel()).thenReturn("legal");
    when(legalCount.getDocumentCount()).thenReturn(1L);
    when(tagRepository.findTagCountsByOwnerId(testUser.getId()))
        .thenReturn(List.of(financeCount, legalCount));

    Map<String, Long> result = knowledgeBaseService.getTagsForUserWithCount(testUser.getId());

    assertThat(result).containsEntry("finance", 2L);
    assertThat(result).containsEntry("legal", 1L);
    verify(documentRepository, never()).findByOwnerId(any());
  }
}
