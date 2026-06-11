package com.alexandria.app.document;

import com.alexandria.app.exception.DocumentNotFoundException;
import com.alexandria.app.user.User;
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
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    private DocumentService documentService;

    private User testUser;

    @BeforeEach
    void setup() {
        documentService = new DocumentService(documentRepository);
        testUser = new User("oidc|123", "testuser", "test@example.com");
    }

    @Test
    void unit_document_savePersistsDocument() {
        Document document = new Document(testUser, "report.docx", "/files/report.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 2048L);
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Document result = documentService.save(document);

        assertThat(result.getFileName()).isEqualTo("report.docx");
        assertThat(result.getOwner()).isEqualTo(testUser);
        verify(documentRepository).save(document);
    }

    @Test
    void unit_document_findByIdReturnsDocument() {
        UUID documentId = UUID.randomUUID();
        Document document = new Document(testUser, "notes.txt", "/files/notes.txt", "text/plain", 512L);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

        Document result = documentService.findById(documentId);

        assertThat(result.getFileName()).isEqualTo("notes.txt");
    }

    @Test
    void unit_document_findByIdNotFoundThrowsException() {
        UUID documentId = UUID.randomUUID();
        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.findById(documentId))
                .isInstanceOf(DocumentNotFoundException.class)
                .hasMessageContaining(documentId.toString());
    }

    @Test
    void unit_document_findByOwnerIdReturnsDocuments() {
        UUID ownerId = UUID.randomUUID();
        Document doc1 = new Document(testUser, "report.pdf", "/files/report.pdf", "application/pdf", 1024L);
        Document doc2 = new Document(testUser, "data.xlsx", "/files/data.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 4096L);
        when(documentRepository.findByOwnerId(ownerId)).thenReturn(List.of(doc1, doc2));

        List<Document> result = documentService.findByOwnerId(ownerId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Document::getFileName).containsExactly("report.pdf", "data.xlsx");
    }

    @Test
    void unit_document_findByOwnerIdReturnsEmptyListWhenNoDocuments() {
        UUID ownerId = UUID.randomUUID();
        when(documentRepository.findByOwnerId(ownerId)).thenReturn(List.of());

        List<Document> result = documentService.findByOwnerId(ownerId);

        assertThat(result).isEmpty();
    }

    @Test
    void unit_document_deleteSuccessDeletesDocument() {
        UUID documentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(documentRepository.existsByIdAndOwnerId(documentId, ownerId)).thenReturn(true);

        documentService.delete(documentId, ownerId);

        verify(documentRepository).deleteById(documentId);
    }

    @Test
    void unit_document_deleteNotFoundThrowsException() {
        UUID documentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(documentRepository.existsByIdAndOwnerId(documentId, ownerId)).thenReturn(false);

        assertThatThrownBy(() -> documentService.delete(documentId, ownerId))
                .isInstanceOf(DocumentNotFoundException.class)
                .hasMessageContaining(documentId.toString());

        verify(documentRepository, never()).deleteById(any());
    }

    @Test
    void unit_document_deleteWrongOwnerThrowsException() {
        UUID documentId = UUID.randomUUID();
        UUID wrongOwnerId = UUID.randomUUID();
        when(documentRepository.existsByIdAndOwnerId(documentId, wrongOwnerId)).thenReturn(false);

        assertThatThrownBy(() -> documentService.delete(documentId, wrongOwnerId))
                .isInstanceOf(DocumentNotFoundException.class);

        verify(documentRepository, never()).deleteById(any());
    }
}
