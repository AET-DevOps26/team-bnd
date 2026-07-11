package com.alexandria.qa.integration;

import com.alexandria.genai.client.api.AiApi;
import com.alexandria.genai.client.invoker.ApiClient;
import com.alexandria.genai.client.model.GenAiAskRequest;
import com.alexandria.genai.client.model.GenAiCitation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

@Component
public class GenAiClient {

    private final AiApi genaiClient;

    public GenAiClient(@Value("${genai.base-url:http://localhost:8000}") String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(30));
        RestClient restClient = ApiClient.buildRestClientBuilder().requestFactory(factory).build();
        ApiClient apiClient = new ApiClient(restClient).setBasePath(baseUrl);
        this.genaiClient = new AiApi(apiClient);
    }

    public AskResponse ask(String question, List<String> objectKeys) {
        var r = genaiClient.askGenaiAskPost(new GenAiAskRequest().question(question).objectKeys(objectKeys));
        List<String> sourceObjectKeys = r.getCitations().stream().map(GenAiCitation::getObjectKey).toList();
        return new AskResponse(r.getAnswer(), sourceObjectKeys, r.getModelUsed());
    }

    public record AskResponse(String answer, List<String> sourceObjectKeys, String modelUsed) {
    }
}
