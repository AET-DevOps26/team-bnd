package com.alexandria.knowledgebase.integration;

import com.alexandria.genai.client.api.AiApi;
import com.alexandria.genai.client.model.GenAiDeleteIndexResponse;
import com.alexandria.genai.client.model.GenAiExtractResponse;
import com.alexandria.genai.client.model.GenAiExtractedEntity;
import com.alexandria.genai.client.model.GenAiIndexResponse;
import com.alexandria.genai.client.model.GenAiSearchResponse;
import com.alexandria.genai.client.model.GenAiSearchResult;
import com.alexandria.genai.client.model.GenAiSummarizeResponse;
import com.alexandria.genai.client.model.GenAiTagResponse;
import com.alexandria.knowledgebase.document.EntityType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GenAiClientTest {

    private GenAiExtractedEntity entity(String name, String type, BigDecimal confidence) {
        return new GenAiExtractedEntity().name(name).type(type).confidence(confidence);
    }

    @Test
    void unit_genai_extractDropsUnknownAndNullEntityTypesAndConvertsConfidence() {
        AiApi aiApi = mock(AiApi.class);
        GenAiExtractResponse response = new GenAiExtractResponse().modelUsed("gpt-test").entities(List.of(
                entity("Ada Lovelace", "PERSON", new BigDecimal("0.95")), entity("Something", "NONSENSE", new BigDecimal("0.5")), entity("Untyped", null, new BigDecimal("0.7"))));
        when(aiApi.extractGenaiExtractPost(any())).thenReturn(response);

        GenAiClient client = new GenAiClient(aiApi);
        GenAiClient.ExtractResponse result = client.extract("/uploads/doc.pdf");

        assertThat(result.modelUsed()).isEqualTo("gpt-test");
        assertThat(result.entities()).hasSize(1);
        GenAiClient.ExtractedEntityDto dto = result.entities().get(0);
        assertThat(dto.name()).isEqualTo("Ada Lovelace");
        assertThat(dto.type()).isEqualTo(EntityType.PERSON);
        assertThat(dto.confidence()).isEqualTo(0.95);
    }

    @Test
    void unit_genai_summarizeMapsResponse() {
        AiApi aiApi = mock(AiApi.class);
        when(aiApi.summarizeDocumentGenaiSummarizePost(any())).thenReturn(new GenAiSummarizeResponse().summary("s").modelUsed("m"));

        GenAiClient.SummarizeResponse result = new GenAiClient(aiApi).summarize("/uploads/a.pdf");

        assertThat(result.summary()).isEqualTo("s");
        assertThat(result.modelUsed()).isEqualTo("m");
    }

    @Test
    void unit_genai_tagMapsResponse() {
        AiApi aiApi = mock(AiApi.class);
        when(aiApi.tagGenaiTagPost(any())).thenReturn(new GenAiTagResponse().tags(List.of("finance")).modelUsed("m"));

        GenAiClient.TagResponse result = new GenAiClient(aiApi).tag("/uploads/a.pdf", List.of("finance"));

        assertThat(result.tags()).containsExactly("finance");
        assertThat(result.modelUsed()).isEqualTo("m");
    }

    @Test
    void unit_genai_tagTreatsNullKnownTagsAsEmpty() {
        AiApi aiApi = mock(AiApi.class);
        when(aiApi.tagGenaiTagPost(any())).thenReturn(new GenAiTagResponse().tags(List.of()).modelUsed("m"));

        new GenAiClient(aiApi).tag("/uploads/a.pdf", null);
    }

    @Test
    void unit_genai_indexMapsResponse() {
        AiApi aiApi = mock(AiApi.class);
        when(aiApi.indexGenaiIndexPost(any())).thenReturn(new GenAiIndexResponse().objectKey("/uploads/a.pdf").chunksIndexed(3).embeddingModel("e"));

        GenAiClient.IndexResponse result = new GenAiClient(aiApi).index("/uploads/a.pdf");

        assertThat(result.objectKey()).isEqualTo("/uploads/a.pdf");
        assertThat(result.chunksIndexed()).isEqualTo(3);
        assertThat(result.embeddingModel()).isEqualTo("e");
    }

    @Test
    void unit_genai_deleteIndexMapsResponse() {
        AiApi aiApi = mock(AiApi.class);
        when(aiApi.deleteIndexGenaiIndexObjectKeyDelete(eq("/uploads/a.pdf"))).thenReturn(new GenAiDeleteIndexResponse().objectKey("/uploads/a.pdf").chunksDeleted(3));

        GenAiClient.DeleteIndexResponse result = new GenAiClient(aiApi).deleteIndex("/uploads/a.pdf");

        assertThat(result.objectKey()).isEqualTo("/uploads/a.pdf");
        assertThat(result.chunksDeleted()).isEqualTo(3);
    }

    @Test
    void unit_genai_searchMapsResultsAndConvertsScore() {
        AiApi aiApi = mock(AiApi.class);
        GenAiSearchResult hit = new GenAiSearchResult().objectKey("/uploads/a.pdf").score(new BigDecimal("0.8")).snippet("snip");
        when(aiApi.searchGenaiSearchPost(any())).thenReturn(new GenAiSearchResponse().results(List.of(hit)).embeddingModel("e"));

        GenAiClient.SearchResponse result = new GenAiClient(aiApi).search("q", List.of("/uploads/a.pdf"), 5);

        assertThat(result.embeddingModel()).isEqualTo("e");
        assertThat(result.results()).singleElement().satisfies(r -> {
            assertThat(r.objectKey()).isEqualTo("/uploads/a.pdf");
            assertThat(r.score()).isEqualTo(0.8);
            assertThat(r.snippet()).isEqualTo("snip");
        });
    }

    @Test
    void unit_genai_searchTreatsNullKeysAndLimitAsDefaults() {
        AiApi aiApi = mock(AiApi.class);
        when(aiApi.searchGenaiSearchPost(any())).thenReturn(new GenAiSearchResponse().results(List.of()).embeddingModel("e"));

        GenAiClient.SearchResponse result = new GenAiClient(aiApi).search("q", null, null);

        assertThat(result.results()).isEmpty();
    }
}
