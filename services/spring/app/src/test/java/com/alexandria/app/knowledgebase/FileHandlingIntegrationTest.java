package com.alexandria.app.knowledgebase;

import com.alexandria.app.document.Document;
import com.alexandria.app.document.DocumentRepository;
import com.alexandria.app.user.User;
import com.alexandria.app.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FileHandlingIntegrationTest {

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setup() {
        testUser = userRepository.save(new User("oidc|file_test", "fileuser", "fileuser@example.com"));
    }

    @Test
    void integration_file_uploadTextFileStoresContentAndText() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "Hello World".getBytes());

        Document doc = knowledgeBaseService.uploadDocument(testUser, file);

        assertThat(doc.getId()).isNotNull();
        assertThat(doc.getFileName()).isEqualTo("test.txt");
        assertThat(doc.getFileType()).isEqualTo("text/plain");
        assertThat(doc.getRawTextContent()).isEqualTo("Hello World");
        assertThat(doc.getFileContent()).isNotNull();
    }

    @Test
    void integration_file_downloadReturnsUploadedBytes() {
        byte[] content = "Test content for download".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "download.txt", "text/plain", content);

        Document doc = knowledgeBaseService.uploadDocument(testUser, file);
        Optional<byte[]> downloaded = knowledgeBaseService.getFileContent(doc.getId());

        assertThat(downloaded).isPresent();
        assertThat(downloaded.get()).isEqualTo(content);
    }

    @Test
    void integration_file_uploadWithNullBytesInTextStripsNulls() {
        byte[] content = "Text\u0000with\u0000nulls".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "nulls.txt", "text/plain", content);

        Document doc = knowledgeBaseService.uploadDocument(testUser, file);

        assertThat(doc.getRawTextContent()).doesNotContain("\u0000");
    }

    @Test
    void integration_file_getFileContentReturnsEmptyForNonexistent() {
        Optional<byte[]> content = knowledgeBaseService.getFileContent(java.util.UUID.randomUUID());

        assertThat(content).isEmpty();
    }
}
