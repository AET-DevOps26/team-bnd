package com.alexandria.qa;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Getter/setter and equals/hashCode coverage the service tests only touch indirectly.
class QaModelTest {

    @Test
    void unit_qa_citationSettersAndEquality() {
        QaCitation citation = new QaCitation();
        citation.setMarker(2);
        citation.setObjectKey("/uploads/a.pdf");
        citation.setDocumentId("doc-1");
        citation.setFileName("a.pdf");
        citation.setSnippet("snip");

        assertThat(citation.getMarker()).isEqualTo(2);
        assertThat(citation.getObjectKey()).isEqualTo("/uploads/a.pdf");
        assertThat(citation.getDocumentId()).isEqualTo("doc-1");
        assertThat(citation.getFileName()).isEqualTo("a.pdf");
        assertThat(citation.getSnippet()).isEqualTo("snip");

        QaCitation same = new QaCitation(2, "/uploads/a.pdf", "doc-1", "a.pdf", "snip");
        QaCitation different = new QaCitation(3, "/uploads/b.pdf", "doc-2", "b.pdf", "other");
        assertThat(citation).isEqualTo(citation).isEqualTo(same).isNotEqualTo(different).isNotEqualTo("not a citation");
        assertThat(citation.hashCode()).isEqualTo(same.hashCode());
    }

    @Test
    void unit_qa_interactionSetters() {
        QAInteraction interaction = new QAInteraction();
        List<QaCitation> citations = List.of(new QaCitation(1, "/uploads/a.pdf", "doc", "a.pdf", "snip"));
        Instant now = Instant.now();
        interaction.setUserSubject("owner");
        interaction.setQuestion("q?");
        interaction.setAnswer("a");
        interaction.setCitations(citations);
        interaction.setTimestamp(now);
        interaction.setModelUsed("model");

        assertThat(interaction.getUserSubject()).isEqualTo("owner");
        assertThat(interaction.getQuestion()).isEqualTo("q?");
        assertThat(interaction.getAnswer()).isEqualTo("a");
        assertThat(interaction.getCitations()).hasSize(1);
        assertThat(interaction.getTimestamp()).isEqualTo(now);
        assertThat(interaction.getModelUsed()).isEqualTo("model");

        interaction.setCitations(null);
        assertThat(interaction.getCitations()).isEmpty();
    }
}
