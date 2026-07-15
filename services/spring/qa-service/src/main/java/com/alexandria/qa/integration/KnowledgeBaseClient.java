package com.alexandria.qa.integration;

import com.alexandria.common.internal.HmacRequestSigningInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Internal client for knowledgebase-service, used to fetch a user's document keys and
 * resolve object keys back to document ids and file names when building citations.
 *
 * <p>Talks to the /internal/knowledgebase endpoints, so requests carry an HMAC signature
 * added by the shared signing interceptor rather than a user bearer token.
 */
@Component
public class KnowledgeBaseClient {

    private final RestClient restClient;

    public KnowledgeBaseClient(@Value("${knowledgebase.base-url:http://knowledgebase-service:8080}") String baseUrl, HmacRequestSigningInterceptor internalSigner) {
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.restClient = RestClient.builder().requestFactory(factory).baseUrl(baseUrl).requestInterceptor(internalSigner).build();
    }

    public List<String> listDocumentKeys(String userSubject) {
        return restClient.get().uri("/internal/knowledgebase/users/{subject}/document-keys", userSubject).retrieve().body(new ParameterizedTypeReference<List<String>>() {
        });
    }

    public List<DocumentReference> resolveDocuments(String userSubject, List<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return List.of();
        }
        List<DocumentReference> resolved = restClient.post().uri("/internal/knowledgebase/users/{subject}/documents/resolve", userSubject).body(Map.of("objectKeys", objectKeys)).retrieve().body(new ParameterizedTypeReference<List<DocumentReference>>() {
        });
        return resolved == null ? List.of() : resolved;
    }

    public record DocumentReference(String objectKey, String documentId, String fileName) {
    }
}
