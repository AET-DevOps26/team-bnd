package com.alexandria.app.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.alexandria.app.user.User;
import com.alexandria.app.user.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class DocumentRepositoryIntegrationTest {

  @Autowired private DocumentRepository documentRepository;

  @Autowired private UserRepository userRepository;

  private User testUser;

  @BeforeEach
  void setup() {
    testUser = new User("oidc|doc_test_user", "docuser", "docuser@example.com");
    testUser = userRepository.save(testUser);
  }

  @Test
  void integration_docRepo_saveAndFindByIdWorks() {
    Document document =
        new Document(
            testUser,
            "report.docx",
            "/files/report.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            2048L);

    Document saved = documentRepository.save(document);

    assertThat(saved.getId()).isNotNull();
    Optional<Document> found = documentRepository.findById(saved.getId());
    assertThat(found).isPresent();
    assertThat(found.get().getFileName()).isEqualTo("report.docx");
    assertThat(found.get().getObjectKey()).isEqualTo("/files/report.docx");
    assertThat(found.get().getFileType())
        .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    assertThat(found.get().getFileSize()).isEqualTo(2048L);
    assertThat(found.get().getCreatedAt()).isNotNull();
  }

  @Test
  void integration_docRepo_findByOwnerIdWorks() {
    Document doc1 =
        new Document(
            testUser,
            "presentation.pptx",
            "/files/presentation.pptx",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            4096L);
    Document doc2 = new Document(testUser, "data.csv", "/files/data.csv", "text/csv", 512L);
    documentRepository.save(doc1);
    documentRepository.save(doc2);

    List<Document> found = documentRepository.findByOwnerId(testUser.getId());

    assertThat(found).hasSize(2);
    assertThat(found)
        .extracting(Document::getFileName)
        .containsExactlyInAnyOrder("presentation.pptx", "data.csv");
  }

  @Test
  void integration_docRepo_findByOwnerIdReturnsEmptyForDifferentUser() {
    User otherUser = new User("oidc|other_user", "other", "other@example.com");
    otherUser = userRepository.save(otherUser);

    Document document =
        new Document(testUser, "secret.txt", "/files/secret.txt", "text/plain", 256L);
    documentRepository.save(document);

    List<Document> found = documentRepository.findByOwnerId(otherUser.getId());

    assertThat(found).isEmpty();
  }

  @Test
  void integration_docRepo_findByOwnerIdAndFileNameContainingIgnoreCaseWorks() {
    Document doc1 =
        new Document(testUser, "report_2026.pdf", "/files/report.pdf", "application/pdf", 1024L);
    Document doc2 =
        new Document(
            testUser, "REPORT_summary.pdf", "/files/summary.pdf", "application/pdf", 2048L);
    Document doc3 = new Document(testUser, "notes.txt", "/files/notes.txt", "text/plain", 512L);
    documentRepository.saveAll(List.of(doc1, doc2, doc3));

    List<Document> found =
        documentRepository.findByOwnerIdAndFileNameContainingIgnoreCase(testUser.getId(), "report");

    assertThat(found).hasSize(2);
    assertThat(found)
        .extracting(Document::getFileName)
        .containsExactlyInAnyOrder("report_2026.pdf", "REPORT_summary.pdf");
  }

  @Test
  void integration_docRepo_existsByIdAndOwnerIdReturnsTrue() {
    Document document =
        new Document(testUser, "image.jpg", "/files/image.jpg", "image/jpeg", 8192L);
    Document saved = documentRepository.save(document);

    boolean exists = documentRepository.existsByIdAndOwnerId(saved.getId(), testUser.getId());

    assertThat(exists).isTrue();
  }

  @Test
  void integration_docRepo_existsByIdAndOwnerIdReturnsFalseForWrongOwner() {
    User otherUser = new User("oidc|wrong_owner", "wrong", "wrong@example.com");
    otherUser = userRepository.save(otherUser);

    Document document =
        new Document(testUser, "diagram.png", "/files/diagram.png", "image/png", 4096L);
    Document saved = documentRepository.save(document);

    boolean exists = documentRepository.existsByIdAndOwnerId(saved.getId(), otherUser.getId());

    assertThat(exists).isFalse();
  }

  @Test
  void integration_docRepo_existsByIdAndOwnerIdReturnsFalseForNonexistentDocument() {
    boolean exists =
        documentRepository.existsByIdAndOwnerId(java.util.UUID.randomUUID(), testUser.getId());

    assertThat(exists).isFalse();
  }

  @Test
  void integration_docRepo_deleteRemovesDocument() {
    Document document =
        new Document(testUser, "temp.json", "/files/temp.json", "application/json", 128L);
    Document saved = documentRepository.save(document);

    documentRepository.deleteById(saved.getId());

    assertThat(documentRepository.findById(saved.getId())).isEmpty();
  }

  @Test
  void integration_docRepo_rawTextContentCanBeStored() {
    Document document =
        new Document(testUser, "article.html", "/files/article.html", "text/html", 3072L);
    document.setRawTextContent("This is the extracted text content from the HTML document.");
    Document saved = documentRepository.save(document);

    Optional<Document> found = documentRepository.findById(saved.getId());

    assertThat(found).isPresent();
    assertThat(found.get().getRawTextContent())
        .isEqualTo("This is the extracted text content from the HTML document.");
  }

  @Test
  void integration_docRepo_multipleDocumentsForSameUserWorks() {
    String[] extensions = {"pdf", "docx", "txt", "xlsx", "md"};
    String[] mimeTypes = {
      "application/pdf",
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      "text/plain",
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      "text/markdown"
    };
    for (int i = 0; i < 5; i++) {
      Document doc =
          new Document(
              testUser,
              "doc" + i + "." + extensions[i],
              "/files/doc" + i + "." + extensions[i],
              mimeTypes[i],
              1024L * (i + 1));
      documentRepository.save(doc);
    }

    List<Document> found = documentRepository.findByOwnerId(testUser.getId());

    assertThat(found).hasSize(5);
  }
}
