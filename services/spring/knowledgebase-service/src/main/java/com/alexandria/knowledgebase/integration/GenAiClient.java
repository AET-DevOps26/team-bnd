package com.alexandria.knowledgebase.integration;

import com.alexandria.knowledgebase.document.EntityType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.List;

@Component
public class GenAiClient {

    private final RestClient restClient;

    public GenAiClient(@Value("${genai.base-url:http://localhost:8000}") String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(java.time.Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(java.time.Duration.ofSeconds(30));
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(baseUrl)
                .build();
    }

    public SummarizeResponse summarize(String objectKey) {
        return restClient
                .post()
                .uri("/genai/summarize")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SummarizeRequest(objectKey))
                .retrieve()
                .body(SummarizeResponse.class);
    }

    public ExtractResponse extract(String objectKey) {
        return restClient
                .post()
                .uri("/genai/extract")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ExtractRequest(objectKey))
                .retrieve()
                .body(ExtractResponse.class);
    }

    public AskResponse ask(String question, List<String> objectKeys) {
        return restClient
                .post()
                .uri("/genai/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AskRequest(question, objectKeys))
                .retrieve()
                .body(AskResponse.class);
    }

    public record SummarizeRequest(String objectKey) {
    }

    public record SummarizeResponse(String summary, String modelUsed) {
    }

    public record ExtractRequest(String objectKey) {
    }

    public record ExtractResponse(List<ExtractedEntityDto> entities, String modelUsed) {
    }

    public record ExtractedEntityDto(String name, EntityType type, Double confidence) {
    }

    public record AskRequest(String question, List<String> objectKeys) {
    }

    public record AskResponse(String answer, List<String> sourceObjectKeys, String modelUsed) {
    }
}
