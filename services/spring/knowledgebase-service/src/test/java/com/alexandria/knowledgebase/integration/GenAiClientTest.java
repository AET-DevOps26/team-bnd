package com.alexandria.knowledgebase.integration;

import com.alexandria.genai.client.api.AiApi;
import com.alexandria.genai.client.model.GenAiExtractResponse;
import com.alexandria.genai.client.model.GenAiExtractedEntity;
import com.alexandria.knowledgebase.document.EntityType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
}
