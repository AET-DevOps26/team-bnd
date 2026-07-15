package com.alexandria.qa.integration;

import com.alexandria.genai.client.api.AiApi;
import com.alexandria.genai.client.model.GenAiAskResponse;
import com.alexandria.genai.client.model.GenAiCitation;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GenAiClientTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void unit_qa_askMapsAnswerAndCitations() {
        AiApi aiApi = mock(AiApi.class);
        GenAiCitation citation = new GenAiCitation().marker(1).objectKey("/uploads/a.pdf").snippet("snip");
        when(aiApi.askGenaiAskPost(any())).thenReturn(new GenAiAskResponse().answer("answer").citations(List.of(citation)).modelUsed("model"));

        GenAiClient.AskResponse result = new GenAiClient(aiApi, registry).ask("q?", List.of("/uploads/a.pdf"));

        assertThat(result.answer()).isEqualTo("answer");
        assertThat(result.modelUsed()).isEqualTo("model");
        assertThat(result.citations()).singleElement().satisfies(c -> {
            assertThat(c.marker()).isEqualTo(1);
            assertThat(c.objectKey()).isEqualTo("/uploads/a.pdf");
            assertThat(c.snippet()).isEqualTo("snip");
        });
        assertThat(registry.get("genai.client.calls").tags("operation", "ask", "outcome", "success").counter().count()).isEqualTo(1.0);
    }

    @Test
    void unit_qa_askRecordsErrorMetricOnFailure() {
        AiApi aiApi = mock(AiApi.class);
        when(aiApi.askGenaiAskPost(any())).thenThrow(new RuntimeException("genai down"));

        assertThatThrownBy(() -> new GenAiClient(aiApi, registry).ask("q?", List.of())).isInstanceOf(RuntimeException.class);

        assertThat(registry.get("genai.client.calls").tags("operation", "ask", "outcome", "error").counter().count()).isEqualTo(1.0);
    }
}
