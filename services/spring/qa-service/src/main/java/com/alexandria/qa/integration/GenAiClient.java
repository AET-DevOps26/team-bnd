package com.alexandria.qa.integration;

import com.alexandria.genai.client.api.AiApi;
import com.alexandria.genai.client.invoker.ApiClient;
import com.alexandria.genai.client.model.GenAiAskRequest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final MeterRegistry meterRegistry;

    @Autowired
    public GenAiClient(@Value("${genai.base-url:http://localhost:8000}") String baseUrl, MeterRegistry meterRegistry) {
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(30));
        RestClient restClient = ApiClient.buildRestClientBuilder().requestFactory(factory).build();
        ApiClient apiClient = new ApiClient(restClient).setBasePath(baseUrl);
        this.genaiClient = new AiApi(apiClient);
        this.meterRegistry = meterRegistry;
    }

    GenAiClient(AiApi genaiClient, MeterRegistry meterRegistry) {
        this.genaiClient = genaiClient;
        this.meterRegistry = meterRegistry;
    }

    public AskResponse ask(String question, List<String> objectKeys) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";
        try {
            var r = genaiClient.askGenaiAskPost(new GenAiAskRequest().question(question).objectKeys(objectKeys));
            List<Citation> citations = r.getCitations().stream().map(c -> new Citation(c.getMarker(), c.getObjectKey(), c.getSnippet())).toList();
            return new AskResponse(r.getAnswer(), citations, r.getModelUsed());
        } catch (RuntimeException e) {
            outcome = "error";
            throw e;
        } finally {
            sample.stop(meterRegistry.timer("genai.client.call.duration", "operation", "ask", "outcome", outcome));
            meterRegistry.counter("genai.client.calls", "operation", "ask", "outcome", outcome).increment();
        }
    }

    public record AskResponse(String answer, List<Citation> citations, String modelUsed) {
    }

    public record Citation(Integer marker, String objectKey, String snippet) {
    }
}
