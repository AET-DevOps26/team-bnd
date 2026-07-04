package com.alexandria.user.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class QAClient {

    private final RestClient restClient;

    public QAClient(@Value("${qa.base-url:http://qa-service:8080}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public void deleteUserData(String oidcSubject) {
        restClient.delete()
                .uri("/api/v1/qa/internal/users/{subject}", oidcSubject)
                .retrieve()
                .toBodilessEntity();
    }
}
