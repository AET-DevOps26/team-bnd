package com.alexandria.app.knowledgebase;

import com.alexandria.app.document.EntityType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

@Component
public class GenAiClient {

    private final RestClient restClient;

    public GenAiClient(@Value("${genai.base-url:http://localhost:8000}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public SummarizeResponse summarize(String content) {
        return restClient.post()
                .uri("/genai/summarize")
                .body(new SummarizeRequest(content))
                .retrieve()
                .body(SummarizeResponse.class);
    }

    public ExtractResponse extract(String content) {
        return restClient.post()
                .uri("/genai/extract")
                .body(new ExtractRequest(content))
                .retrieve()
                .body(ExtractResponse.class);
    }

    public AskResponse ask(String question, List<UUID> documentIds) {
        return restClient.post()
                .uri("/genai/ask")
                .body(new AskRequest(question, documentIds))
                .retrieve()
                .body(AskResponse.class);
    }

    public record SummarizeRequest(String content) {}

    public record SummarizeResponse(String summary, String modelUsed) {}

    public record ExtractRequest(String content) {}

    public record ExtractResponse(List<ExtractedEntityDto> entities, String modelUsed) {}

    public record ExtractedEntityDto(String name, EntityType type, Double confidence) {}

    public record AskRequest(String question, List<UUID> documentIds) {}

    public record AskResponse(String answer, List<UUID> sourceDocumentIds, String modelUsed) {}
}
