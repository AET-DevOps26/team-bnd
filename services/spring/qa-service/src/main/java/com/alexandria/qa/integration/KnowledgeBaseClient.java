package com.alexandria.qa.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class KnowledgeBaseClient {

    private final RestClient restClient;

    public KnowledgeBaseClient(@Value("${knowledgebase.base-url:http://knowledgebase-service:8080}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public List<String> listDocumentKeys(String userSubject) {
        return restClient.get()
                .uri("/api/v1/knowledgebase/internal/users/{subject}/document-keys", userSubject)
                .retrieve()
                .body(new ParameterizedTypeReference<List<String>>() {});
    }
}
