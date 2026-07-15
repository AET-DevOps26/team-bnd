package com.alexandria.knowledgebase;

import com.alexandria.knowledgebase.document.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalControllerTest {

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    @InjectMocks
    private InternalController controller;

    private static Document docWithId(String key, String fileName) {
        Document doc = new Document("owner", fileName, key, "application/pdf", 1L);
        ReflectionTestUtils.setField(doc, "id", UUID.randomUUID());
        return doc;
    }

    @Test
    void unit_kb_internalDeleteUserDataDelegates() {
        ResponseEntity<Void> response = controller.deleteUserData("owner");

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(knowledgeBaseService).deleteAllForUser("owner");
    }

    @Test
    void unit_kb_internalListDocumentKeysMapsObjectKeys() {
        when(knowledgeBaseService.getDocuments("owner")).thenReturn(List.of(docWithId("/uploads/a.pdf", "a.pdf")));

        ResponseEntity<List<String>> response = controller.listDocumentKeys("owner");

        assertThat(response.getBody()).containsExactly("/uploads/a.pdf");
    }

    @Test
    void unit_kb_internalResolveDocumentsMapsToReferences() {
        Document doc = docWithId("/uploads/a.pdf", "a.pdf");
        when(knowledgeBaseService.resolveDocuments("owner", List.of("/uploads/a.pdf"))).thenReturn(List.of(doc));

        ResponseEntity<List<InternalController.DocumentReferenceResponse>> response = controller.resolveDocuments("owner", new InternalController.ResolveDocumentsRequest(List.of("/uploads/a.pdf")));

        assertThat(response.getBody()).singleElement().satisfies(ref -> {
            assertThat(ref.objectKey()).isEqualTo("/uploads/a.pdf");
            assertThat(ref.fileName()).isEqualTo("a.pdf");
            assertThat(ref.documentId()).isEqualTo(doc.getId().toString());
        });
    }

    @Test
    void unit_kb_internalResolveDocumentsHandlesNullRequest() {
        when(knowledgeBaseService.resolveDocuments("owner", List.of())).thenReturn(List.of());

        ResponseEntity<List<InternalController.DocumentReferenceResponse>> response = controller.resolveDocuments("owner", null);

        assertThat(response.getBody()).isEmpty();
    }
}
