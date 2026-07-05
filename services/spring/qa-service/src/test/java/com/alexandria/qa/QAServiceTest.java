package com.alexandria.qa;

import com.alexandria.qa.integration.GenAiClient;
import com.alexandria.qa.integration.KnowledgeBaseClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    void unit_qa_askDelegatesToPeerServicesAndPersists() {
        String subject = "oidc|123";
        when(knowledgeBaseClient.listDocumentKeys(subject)).thenReturn(List.of("/uploads/a.pdf"));
        when(genAiClient.ask("q?", List.of("/uploads/a.pdf")))
                .thenReturn(new GenAiClient.AskResponse("answer", List.of("/uploads/a.pdf"), "gpt-oss-120b"));
        when(repository.save(any(QAInteraction.class))).thenAnswer(inv -> inv.getArgument(0));

        QAInteraction result = qaService.ask(subject, "q?");

        assertThat(result.getUserSubject()).isEqualTo(subject);
        assertThat(result.getQuestion()).isEqualTo("q?");
        assertThat(result.getAnswer()).isEqualTo("answer");
        assertThat(result.getModelUsed()).isEqualTo("gpt-oss-120b");
        verify(knowledgeBaseClient).listDocumentKeys(subject);
        verify(genAiClient).ask("q?", List.of("/uploads/a.pdf"));
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
