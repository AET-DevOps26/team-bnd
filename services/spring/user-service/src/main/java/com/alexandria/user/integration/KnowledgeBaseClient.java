package com.alexandria.user.integration;

import com.alexandria.common.internal.HmacRequestSigningInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class KnowledgeBaseClient {

    private final RestClient restClient;

    public KnowledgeBaseClient(@Value("${knowledgebase.base-url:http://knowledgebase-service:8080}") String baseUrl, HmacRequestSigningInterceptor internalSigner) {
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.restClient = RestClient.builder().requestFactory(factory).baseUrl(baseUrl).requestInterceptor(internalSigner).build();
    }

    public void deleteUserData(String oidcSubject) {
        restClient.delete().uri("/internal/knowledgebase/users/{subject}", oidcSubject).retrieve().toBodilessEntity();
    }
}
