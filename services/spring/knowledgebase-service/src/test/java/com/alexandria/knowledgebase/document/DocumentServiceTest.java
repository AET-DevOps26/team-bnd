package com.alexandria.knowledgebase.document;

import com.alexandria.knowledgebase.exception.DocumentNotFoundException;
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

    private static final String OWNER = "oidc|123";

    @BeforeEach
    void setup() {
        documentService = new DocumentService(documentRepository);
    }

    @Test
    void unit_document_savePersistsDocument() {
        Document document = new Document(OWNER, "report.docx", "/files/report.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 2048L);
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Document result = documentService.save(document);

        assertThat(result.getFileName()).isEqualTo("report.docx");
        assertThat(result.getOwnerSubject()).isEqualTo(OWNER);
        verify(documentRepository).save(document);
    }

    @Test
    void unit_document_findByIdReturnsDocument() {
        UUID documentId = UUID.randomUUID();
        Document document = new Document(OWNER, "notes.txt", "/files/notes.txt", "text/plain", 512L);
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
    void unit_document_findByOwnerSubjectReturnsDocuments() {
        Document doc1 = new Document(OWNER, "report.pdf", "/files/report.pdf", "application/pdf", 1024L);
        Document doc2 = new Document(OWNER, "data.xlsx", "/files/data.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 4096L);
        when(documentRepository.findByOwnerSubject(OWNER)).thenReturn(List.of(doc1, doc2));

        List<Document> result = documentService.findByOwnerSubject(OWNER);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Document::getFileName).containsExactly("report.pdf", "data.xlsx");
    }

    @Test
    void unit_document_findByOwnerSubjectReturnsEmptyListWhenNoDocuments() {
        when(documentRepository.findByOwnerSubject(OWNER)).thenReturn(List.of());

        List<Document> result = documentService.findByOwnerSubject(OWNER);

        assertThat(result).isEmpty();
    }

    @Test
    void unit_document_deleteSuccessDeletesDocument() {
        UUID documentId = UUID.randomUUID();
        when(documentRepository.existsByIdAndOwnerSubject(documentId, OWNER)).thenReturn(true);

        documentService.delete(documentId, OWNER);

        verify(documentRepository).deleteById(documentId);
    }

    @Test
    void unit_document_deleteNotFoundThrowsException() {
        UUID documentId = UUID.randomUUID();
        when(documentRepository.existsByIdAndOwnerSubject(documentId, OWNER)).thenReturn(false);

        assertThatThrownBy(() -> documentService.delete(documentId, OWNER))
                .isInstanceOf(DocumentNotFoundException.class)
                .hasMessageContaining(documentId.toString());

        verify(documentRepository, never()).deleteById(any());
    }

    @Test
    void unit_document_deleteWrongOwnerThrowsException() {
        UUID documentId = UUID.randomUUID();
        String wrongOwner = "oidc|other";
        when(documentRepository.existsByIdAndOwnerSubject(documentId, wrongOwner)).thenReturn(false);

        assertThatThrownBy(() -> documentService.delete(documentId, wrongOwner))
                .isInstanceOf(DocumentNotFoundException.class);

        verify(documentRepository, never()).deleteById(any());
    }
}
