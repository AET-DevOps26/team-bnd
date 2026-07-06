package com.alexandria.qa;

import com.alexandria.qa.integration.GenAiClient;
import com.alexandria.qa.integration.KnowledgeBaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

        QAInteraction interaction = new QAInteraction(
                userSubject, question, response.answer(), response.sourceObjectKeys(), response.modelUsed()
        );
        return repository.save(interaction);
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
