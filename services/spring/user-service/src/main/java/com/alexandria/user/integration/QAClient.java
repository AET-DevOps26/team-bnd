package com.alexandria.user.integration;

import com.alexandria.common.internal.HmacRequestSigningInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Internal client used to purge a user's Q&amp;A history in qa-service when their account is
 * deleted. Calls the /internal endpoints, so requests are signed with an HMAC signature
 * by the shared interceptor rather than carrying a user bearer token.
 */
@Component
public class QAClient {

    private final RestClient restClient;

    public QAClient(@Value("${qa.base-url:http://qa-service:8080}") String baseUrl, HmacRequestSigningInterceptor internalSigner) {
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.restClient = RestClient.builder().requestFactory(factory).baseUrl(baseUrl).requestInterceptor(internalSigner).build();
    }

    public void deleteUserData(String oidcSubject) {
        restClient.delete().uri("/internal/qa/users/{subject}", oidcSubject).retrieve().toBodilessEntity();
    }
}
