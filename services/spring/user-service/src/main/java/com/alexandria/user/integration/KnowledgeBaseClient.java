package com.alexandria.user.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class KnowledgeBaseClient {

    private final RestClient restClient;

    public KnowledgeBaseClient(@Value("${knowledgebase.base-url:http://knowledgebase-service:8080}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public void deleteUserData(String oidcSubject) {
        restClient.delete()
                .uri("/api/v1/knowledgebase/internal/users/{subject}", oidcSubject)
                .retrieve()
                .toBodilessEntity();
    }
}
