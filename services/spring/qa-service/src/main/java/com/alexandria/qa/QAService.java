package com.alexandria.qa;

import com.alexandria.qa.integration.GenAiClient;
import com.alexandria.qa.integration.KnowledgeBaseClient;
import com.alexandria.qa.integration.KnowledgeBaseClient.DocumentReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class QAService {

    private final QAInteractionRepository repository;
    private final GenAiClient genAiClient;
    private final KnowledgeBaseClient knowledgeBaseClient;

    public QAService(QAInteractionRepository repository, GenAiClient genAiClient, KnowledgeBaseClient knowledgeBaseClient) {
        this.repository = repository;
        this.genAiClient = genAiClient;
        this.knowledgeBaseClient = knowledgeBaseClient;
    }

    @Transactional
    public QAInteraction ask(String userSubject, String question) {
        List<String> objectKeys = knowledgeBaseClient.listDocumentKeys(userSubject);

        GenAiClient.AskResponse response = genAiClient.ask(question, objectKeys);

        List<QaCitation> citations = resolveCitations(userSubject, response.citations());

        QAInteraction interaction = new QAInteraction(
                userSubject, question, response.answer(), citations, response.modelUsed()
        );
        return repository.save(interaction);
    }

    private List<QaCitation> resolveCitations(String userSubject, List<GenAiClient.Citation> citations) {
        if (citations == null || citations.isEmpty()) {
            return List.of();
        }
        List<String> keys = citations.stream().map(GenAiClient.Citation::objectKey).distinct().toList();
        Map<String, DocumentReference> byKey = knowledgeBaseClient.resolveDocuments(userSubject, keys).stream().collect(Collectors.toMap(DocumentReference::objectKey, Function.identity(), (a, b) -> a));

        return citations.stream().map(c -> {
            DocumentReference ref = byKey.get(c.objectKey());
            String documentId = ref == null ? null : ref.documentId();
            String fileName = ref == null ? null : ref.fileName();
            return new QaCitation(c.marker() == null ? 0 : c.marker(), c.objectKey(), documentId, fileName, c.snippet());
        }).toList();
    }

    public List<QAInteraction> getHistory(String userSubject) {
        return repository.findByUserSubjectOrderByTimestampDesc(userSubject);
    }

    @Transactional
    public void deleteHistory(String userSubject) {
        repository.deleteByUserSubject(userSubject);
    }

    @Transactional
    public void deleteAllForUser(String userSubject) {
        deleteHistory(userSubject);
    }
}
