package com.alexandria.user.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class QAClient {

    private final RestClient restClient;

    public QAClient(@Value("${qa.base-url:http://qa-service:8080}") String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.restClient = RestClient.builder().requestFactory(factory).baseUrl(baseUrl).build();
    }

    public void deleteUserData(String oidcSubject) {
        restClient.delete().uri("/internal/qa/users/{subject}", oidcSubject).retrieve().toBodilessEntity();
    }
}
