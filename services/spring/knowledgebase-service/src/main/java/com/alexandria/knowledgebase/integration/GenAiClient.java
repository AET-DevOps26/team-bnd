package com.alexandria.knowledgebase.integration;

import com.alexandria.genai.client.api.AiApi;
import com.alexandria.genai.client.invoker.ApiClient;
import com.alexandria.genai.client.model.GenAiDeleteIndexResponse;
import com.alexandria.genai.client.model.GenAiExtractRequest;
import com.alexandria.genai.client.model.GenAiIndexRequest;
import com.alexandria.genai.client.model.GenAiSearchRequest;
import com.alexandria.genai.client.model.GenAiSummarizeRequest;
import com.alexandria.genai.client.model.GenAiTagRequest;
import com.alexandria.knowledgebase.document.EntityType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

@Component
public class GenAiClient {

    private final AiApi genaiClient;

    @Autowired
    public GenAiClient(@Value("${genai.base-url:http://localhost:8000}") String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(30));
        RestClient restClient = ApiClient.buildRestClientBuilder().requestFactory(factory).build();
        ApiClient apiClient = new ApiClient(restClient).setBasePath(baseUrl);
        this.genaiClient = new AiApi(apiClient);
    }

    GenAiClient(AiApi genaiClient) {
        this.genaiClient = genaiClient;
    }

    public SummarizeResponse summarize(String objectKey) {
        var r = genaiClient.summarizeDocumentGenaiSummarizePost(new GenAiSummarizeRequest().objectKey(objectKey));
        return new SummarizeResponse(r.getSummary(), r.getModelUsed());
    }

    public ExtractResponse extract(String objectKey) {
        var r = genaiClient.extractGenaiExtractPost(new GenAiExtractRequest().objectKey(objectKey));
        List<ExtractedEntityDto> entities = r.getEntities().stream().map(e -> {
            EntityType type = parseEntityType(e.getType());
            if (type == null) {
                return null;
            }
            return new ExtractedEntityDto(e.getName(), type, toDouble(e.getConfidence()));
        }).filter(Objects::nonNull).toList();
        return new ExtractResponse(entities, r.getModelUsed());
    }

    private static EntityType parseEntityType(String value) {
        if (value == null) {
            return null;
        }
        try {
            return EntityType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public SearchResponse search(String query, List<String> objectKeys, Integer limit) {
        var request = new GenAiSearchRequest().query(query).objectKeys(objectKeys == null ? List.of() : objectKeys);
        if (limit != null) {
            request.limit(limit);
        }
        var r = genaiClient.searchGenaiSearchPost(request);
        List<SearchResult> results = r.getResults().stream().map(res -> new SearchResult(res.getObjectKey(), toDouble(res.getScore()), res.getSnippet())).toList();
        return new SearchResponse(results, r.getEmbeddingModel());
    }

    public TagResponse tag(String objectKey, List<String> knownTags) {
        var request = new GenAiTagRequest().objectKey(objectKey).knownTags(knownTags == null ? List.of() : knownTags);
        var r = genaiClient.tagGenaiTagPost(request);
        return new TagResponse(r.getTags(), r.getModelUsed());
    }

    public IndexResponse index(String objectKey) {
        var r = genaiClient.indexGenaiIndexPost(new GenAiIndexRequest().objectKey(objectKey));
        return new IndexResponse(r.getObjectKey(), r.getChunksIndexed(), r.getEmbeddingModel());
    }

    public DeleteIndexResponse deleteIndex(String objectKey) {
        GenAiDeleteIndexResponse r = genaiClient.deleteIndexGenaiIndexObjectKeyDelete(objectKey);
        return new DeleteIndexResponse(r.getObjectKey(), r.getChunksDeleted());
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    public record SummarizeResponse(String summary, String modelUsed) {
    }

    public record ExtractResponse(List<ExtractedEntityDto> entities, String modelUsed) {
    }

    public record ExtractedEntityDto(String name, EntityType type, Double confidence) {
    }

    public record TagResponse(List<String> tags, String modelUsed) {
    }

    public record SearchResponse(List<SearchResult> results, String embeddingModel) {
    }

    public record SearchResult(String objectKey, Double score, String snippet) {
    }

    public record IndexResponse(String objectKey, Integer chunksIndexed, String embeddingModel) {
    }

    public record DeleteIndexResponse(String objectKey, Integer chunksDeleted) {
    }
}
