package com.alexandria.qa;

import com.alexandria.qa.integration.GenAiClient;
import com.alexandria.qa.integration.KnowledgeBaseClient;
import com.alexandria.qa.integration.KnowledgeBaseClient.DocumentReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QAServiceTest {

    @Mock
    private QAInteractionRepository repository;

    @Mock
    private GenAiClient genAiClient;

    @Mock
    private KnowledgeBaseClient knowledgeBaseClient;

    private QAService qaService;

    @BeforeEach
    void setup() {
        qaService = new QAService(repository, genAiClient, knowledgeBaseClient);
    }

    @Test
    void unit_qa_askResolvesCitationsAndPersists() {
        String subject = "oidc|123";
        when(knowledgeBaseClient.listDocumentKeys(subject)).thenReturn(List.of("/uploads/a.pdf"));
        when(genAiClient.ask("q?", List.of("/uploads/a.pdf"))).thenReturn(new GenAiClient.AskResponse("answer", List.of(new GenAiClient.Citation(1, "/uploads/a.pdf", "snippet A")), "gpt-oss-120b"));
        when(knowledgeBaseClient.resolveDocuments(subject, List.of("/uploads/a.pdf"))).thenReturn(List.of(new DocumentReference("/uploads/a.pdf", "11111111-1111-1111-1111-111111111111", "a.pdf")));
        when(repository.save(any(QAInteraction.class))).thenAnswer(inv -> inv.getArgument(0));

        QAInteraction result = qaService.ask(subject, "q?");

        assertThat(result.getUserSubject()).isEqualTo(subject);
        assertThat(result.getQuestion()).isEqualTo("q?");
        assertThat(result.getAnswer()).isEqualTo("answer");
        assertThat(result.getModelUsed()).isEqualTo("gpt-oss-120b");
        assertThat(result.getCitations()).hasSize(1);
        QaCitation citation = result.getCitations().get(0);
        assertThat(citation.getMarker()).isEqualTo(1);
        assertThat(citation.getObjectKey()).isEqualTo("/uploads/a.pdf");
        assertThat(citation.getDocumentId()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(citation.getFileName()).isEqualTo("a.pdf");
        assertThat(citation.getSnippet()).isEqualTo("snippet A");
        verify(knowledgeBaseClient).listDocumentKeys(subject);
        verify(genAiClient).ask("q?", List.of("/uploads/a.pdf"));
        verify(knowledgeBaseClient).resolveDocuments(subject, List.of("/uploads/a.pdf"));
    }

    @Test
    void unit_qa_askKeepsCitationWhenDocumentResolutionFails() {
        String subject = "oidc|123";
        when(knowledgeBaseClient.listDocumentKeys(subject)).thenReturn(List.of("/uploads/gone.pdf"));
        when(genAiClient.ask(anyString(), any())).thenReturn(new GenAiClient.AskResponse("a", List.of(new GenAiClient.Citation(1, "/uploads/gone.pdf", "snip")), "m"));
        when(knowledgeBaseClient.resolveDocuments(subject, List.of("/uploads/gone.pdf"))).thenReturn(List.of());
        when(repository.save(any(QAInteraction.class))).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<QAInteraction> captor = ArgumentCaptor.forClass(QAInteraction.class);
        qaService.ask(subject, "q?");
        verify(repository).save(captor.capture());

        assertThat(captor.getValue().getCitations()).singleElement().satisfies(c -> {
            assertThat(c.getObjectKey()).isEqualTo("/uploads/gone.pdf");
            assertThat(c.getDocumentId()).isNull();
            assertThat(c.getFileName()).isNull();
            assertThat(c.getSnippet()).isEqualTo("snip");
        });
    }

    @Test
    void unit_qa_askDropsCitationsWithoutObjectKey() {
        String subject = "oidc|123";
        when(knowledgeBaseClient.listDocumentKeys(subject)).thenReturn(List.of());
        when(genAiClient.ask(anyString(), any())).thenReturn(new GenAiClient.AskResponse("a", List.of(new GenAiClient.Citation(1, null, "snip")), "m"));
        when(repository.save(any(QAInteraction.class))).thenAnswer(inv -> inv.getArgument(0));

        QAInteraction result = qaService.ask(subject, "q?");

        assertThat(result.getCitations()).isEmpty();
        verify(knowledgeBaseClient, never()).resolveDocuments(anyString(), any());
    }

    @Test
    void unit_qa_askDoesNotResolveWhenNoCitations() {
        String subject = "oidc|123";
        when(knowledgeBaseClient.listDocumentKeys(subject)).thenReturn(List.of());
        when(genAiClient.ask("q?", List.of())).thenReturn(new GenAiClient.AskResponse("no answer", List.of(), "m"));
        when(repository.save(any(QAInteraction.class))).thenAnswer(inv -> inv.getArgument(0));

        QAInteraction result = qaService.ask(subject, "q?");

        assertThat(result.getCitations()).isEmpty();
        verify(knowledgeBaseClient, never()).resolveDocuments(anyString(), any());
    }

    @Test
    void unit_qa_deleteHistoryDelegates() {
        qaService.deleteHistory("oidc|123");
        verify(repository).deleteByUserSubject("oidc|123");
    }

    @Test
    void unit_qa_deleteAllForUserDelegates() {
        qaService.deleteAllForUser("oidc|123");
        verify(repository).deleteByUserSubject("oidc|123");
    }
}
