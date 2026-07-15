package com.alexandria.knowledgebase.document;

import com.alexandria.knowledgebase.search.SearchQuery;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// Getter/setter and relationship-helper coverage the service tests only touch indirectly.
class DocumentModelTest {

    @Test
    void unit_kb_documentSettersAndTagLinkBothSides() {
        Document doc = new Document("owner", "a.pdf", "/uploads/a.pdf", "application/pdf", 100L);
        doc.setFileName("b.pdf");
        doc.setObjectKey("/uploads/b.pdf");
        doc.setFileType("text/plain");
        doc.setFileSize(200L);
        doc.setOwnerSubject("owner2");
        doc.setRawTextContent("hello");

        assertThat(doc.getFileName()).isEqualTo("b.pdf");
        assertThat(doc.getObjectKey()).isEqualTo("/uploads/b.pdf");
        assertThat(doc.getFileType()).isEqualTo("text/plain");
        assertThat(doc.getFileSize()).isEqualTo(200L);
        assertThat(doc.getOwnerSubject()).isEqualTo("owner2");
        assertThat(doc.getRawTextContent()).isEqualTo("hello");

        Tag tag = new Tag("finance", TagSource.USER);
        doc.addTag(tag);
        assertThat(doc.getTags()).containsExactly(tag);

        doc.removeTag(tag);
        assertThat(doc.getTags()).isEmpty();
    }

    @Test
    void unit_kb_documentSummaryAndEntitiesAccessors() {
        Document doc = new Document("owner", "a.pdf", "/uploads/a.pdf", "application/pdf", 100L);
        Summary summary = new Summary(doc, "content", "model");
        doc.setSummary(summary);
        assertThat(doc.getSummary()).isSameAs(summary);

        ExtractedEntity entity = new ExtractedEntity(doc, "Ada", EntityType.PERSON, 0.9);
        doc.setExtractedEntities(List.of(entity));
        assertThat(doc.getExtractedEntities()).containsExactly(entity);

        doc.setExtractedEntities(null);
        assertThat(doc.getExtractedEntities()).isEmpty();

        doc.setTags(null);
        assertThat(doc.getTags()).isNull();
        doc.setTags(Set.of(new Tag("x", TagSource.AUTO)));
        assertThat(doc.getTags()).hasSize(1);
    }

    @Test
    void unit_kb_tagSetters() {
        Tag tag = new Tag("finance", TagSource.USER);
        tag.setLabel("budget");
        tag.setSource(TagSource.AUTO);
        assertThat(tag.getLabel()).isEqualTo("budget");
        assertThat(tag.getSource()).isEqualTo(TagSource.AUTO);

        tag.setDocuments(null);
        assertThat(tag.getDocuments()).isEmpty();
    }

    @Test
    void unit_kb_extractedEntitySetters() {
        Document doc = new Document("owner", "a.pdf", "/uploads/a.pdf", "application/pdf", 100L);
        ExtractedEntity entity = new ExtractedEntity();
        entity.setDocument(doc);
        entity.setName("Ada");
        entity.setType(EntityType.PERSON);
        entity.setConfidence(0.8);

        assertThat(entity.getDocument()).isSameAs(doc);
        assertThat(entity.getName()).isEqualTo("Ada");
        assertThat(entity.getType()).isEqualTo(EntityType.PERSON);
        assertThat(entity.getConfidence()).isEqualTo(0.8);
    }

    @Test
    void unit_kb_summarySetters() {
        Document doc = new Document("owner", "a.pdf", "/uploads/a.pdf", "application/pdf", 100L);
        Summary summary = new Summary();
        summary.setDocument(doc);
        summary.setContent("text");
        summary.setModelUsed("gpt");

        assertThat(summary.getDocument()).isSameAs(doc);
        assertThat(summary.getContent()).isEqualTo("text");
        assertThat(summary.getModelUsed()).isEqualTo("gpt");
        assertThat(summary.getGeneratedAt()).isNull();
    }

    @Test
    void unit_kb_searchQuerySetters() {
        SearchQuery query = new SearchQuery("owner", "report", 5);
        assertThat(query.getUserSubject()).isEqualTo("owner");
        assertThat(query.getQueryText()).isEqualTo("report");
        assertThat(query.getResultCount()).isEqualTo(5);
        assertThat(query.getTimestamp()).isNotNull();

        query.setUserSubject("owner2");
        query.setQueryText("budget");
        query.setResultCount(10);
        assertThat(query.getUserSubject()).isEqualTo("owner2");
        assertThat(query.getQueryText()).isEqualTo("budget");
        assertThat(query.getResultCount()).isEqualTo(10);
    }

    @Test
    void unit_kb_entityTypeAndTagSourceValues() {
        assertThat(EntityType.valueOf("PERSON")).isEqualTo(EntityType.PERSON);
        assertThat(TagSource.valueOf("USER")).isEqualTo(TagSource.USER);
    }
}
