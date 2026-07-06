package com.alexandria.qa.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

@Component
public class KnowledgeBaseClient {

    private final RestClient restClient;

    public KnowledgeBaseClient(@Value("${knowledgebase.base-url:http://knowledgebase-service:8080}") String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.restClient = RestClient.builder().requestFactory(factory).baseUrl(baseUrl).build();
    }

    public List<String> listDocumentKeys(String userSubject) {
        return restClient.get().uri("/internal/knowledgebase/users/{subject}/document-keys", userSubject).retrieve().body(new ParameterizedTypeReference<List<String>>() {
        });
    }
}
