package com.alexandria.app.knowledgebase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.alexandria.app.document.Document;
import com.alexandria.app.exception.DocumentNotFoundException;
import com.alexandria.app.user.User;
import com.alexandria.app.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FileHandlingIntegrationTest {

  @Autowired private KnowledgeBaseService knowledgeBaseService;

  @Autowired private UserRepository userRepository;

  @MockitoBean private ObjectStorageService objectStorageService;

  @MockitoBean private GenAiClient genAiClient;

  private User testUser;

  @BeforeEach
  void setup() {
    testUser = userRepository.save(new User("oidc|file_test", "fileuser", "fileuser@example.com"));
  }

  @Test
  void integration_file_uploadTextFileStoresContentAndText() {
    MockMultipartFile file =
        new MockMultipartFile("file", "test.txt", "text/plain", "Hello World".getBytes());

    Document doc = knowledgeBaseService.uploadDocument(testUser, file);

    assertThat(doc.getId()).isNotNull();
    assertThat(doc.getFileName()).isEqualTo("test.txt");
    assertThat(doc.getFileType()).isEqualTo("text/plain");
    assertThat(doc.getRawTextContent()).isEqualTo("Hello World");
  }

  @Test
  void integration_file_downloadReturnsUploadedBytes() {
    byte[] content = "Test content for download".getBytes();
    MockMultipartFile file = new MockMultipartFile("file", "download.txt", "text/plain", content);

    Document doc = knowledgeBaseService.uploadDocument(testUser, file);
    when(objectStorageService.download(doc.getObjectKey())).thenReturn(content);

    Optional<byte[]> downloaded =
        knowledgeBaseService.getFileContent(doc.getId(), testUser.getId());

    assertThat(downloaded).isPresent();
    assertThat(downloaded.get()).isEqualTo(content);
  }

  @Test
  void integration_file_uploadWithNullBytesInTextStripsNulls() {
    byte[] content = "Text\u0000with\u0000nulls".getBytes();
    MockMultipartFile file = new MockMultipartFile("file", "nulls.txt", "text/plain", content);

    Document doc = knowledgeBaseService.uploadDocument(testUser, file);

    assertThat(doc.getRawTextContent()).doesNotContain("\u0000");
  }

  @Test
  void integration_file_getFileContentThrowsForNonexistentDocument() {
    assertThatThrownBy(
            () ->
                knowledgeBaseService.getFileContent(java.util.UUID.randomUUID(), testUser.getId()))
        .isInstanceOf(DocumentNotFoundException.class);
  }
}
