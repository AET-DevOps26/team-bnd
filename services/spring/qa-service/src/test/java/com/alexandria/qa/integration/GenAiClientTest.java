package com.alexandria.qa.integration;

import com.alexandria.genai.client.api.AiApi;
import com.alexandria.genai.client.model.GenAiAskResponse;
import com.alexandria.genai.client.model.GenAiCitation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GenAiClientTest {

    @Test
    void unit_qa_askMapsAnswerAndCitations() {
        AiApi aiApi = mock(AiApi.class);
        GenAiCitation citation = new GenAiCitation().marker(1).objectKey("/uploads/a.pdf").snippet("snip");
        when(aiApi.askGenaiAskPost(any())).thenReturn(new GenAiAskResponse().answer("answer").citations(List.of(citation)).modelUsed("model"));

        GenAiClient.AskResponse result = new GenAiClient(aiApi).ask("q?", List.of("/uploads/a.pdf"));

        assertThat(result.answer()).isEqualTo("answer");
        assertThat(result.modelUsed()).isEqualTo("model");
        assertThat(result.citations()).singleElement().satisfies(c -> {
            assertThat(c.marker()).isEqualTo(1);
            assertThat(c.objectKey()).isEqualTo("/uploads/a.pdf");
            assertThat(c.snippet()).isEqualTo("snip");
        });
    }
}
