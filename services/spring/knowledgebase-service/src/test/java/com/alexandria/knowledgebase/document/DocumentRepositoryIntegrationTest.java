package com.alexandria.knowledgebase.document;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class DocumentRepositoryIntegrationTest {

    @Autowired
    private DocumentRepository documentRepository;

    private static final String OWNER = "oidc|doc_test_user";
    private static final String OTHER = "oidc|other_user";

    @Test
    void integration_docRepo_saveAndFindByIdWorks() {
        Document document = new Document(OWNER, "report.pdf", "/uploads/report.pdf", "application/pdf", 1024L);

        Document saved = documentRepository.save(document);

        assertThat(saved.getId()).isNotNull();
        Optional<Document> found = documentRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getFileName()).isEqualTo("report.pdf");
        assertThat(found.get().getOwnerSubject()).isEqualTo(OWNER);
    }

    @Test
    void integration_docRepo_findByOwnerSubjectReturnsUserDocuments() {
        documentRepository.save(new Document(OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 1L));
        documentRepository.save(new Document(OWNER, "b.pdf", "/uploads/b.pdf", "application/pdf", 1L));
        documentRepository.save(new Document(OTHER, "c.pdf", "/uploads/c.pdf", "application/pdf", 1L));

        List<Document> results = documentRepository.findByOwnerSubject(OWNER);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(Document::getFileName).containsExactlyInAnyOrder("a.pdf", "b.pdf");
    }

    @Test
    void integration_docRepo_findObjectKeysByOwnerSubjectReturnsOnlyKeys() {
        documentRepository.save(new Document(OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 1L));
        documentRepository.save(new Document(OWNER, "b.pdf", "/uploads/b.pdf", "application/pdf", 1L));
        documentRepository.save(new Document(OTHER, "c.pdf", "/uploads/c.pdf", "application/pdf", 1L));

        List<String> keys = documentRepository.findObjectKeysByOwnerSubject(OWNER);

        assertThat(keys).containsExactlyInAnyOrder("/uploads/a.pdf", "/uploads/b.pdf");
    }

    @Test
    void integration_docRepo_findByOwnerSubjectAndObjectKeyInScopesToOwner() {
        documentRepository.save(new Document(OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 1L));
        documentRepository.save(new Document(OWNER, "b.pdf", "/uploads/b.pdf", "application/pdf", 1L));
        documentRepository.save(new Document(OTHER, "c.pdf", "/uploads/c.pdf", "application/pdf", 1L));

        List<Document> results = documentRepository.findByOwnerSubjectAndObjectKeyIn(OWNER, List.of("/uploads/a.pdf", "/uploads/c.pdf"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getObjectKey()).isEqualTo("/uploads/a.pdf");
    }

    @Test
    void integration_docRepo_searchByFileNameOrContentMatchesFileName() {
        documentRepository.save(new Document(OWNER, "Annual Report.pdf", "/uploads/a.pdf", "application/pdf", 1L));
        documentRepository.save(new Document(OWNER, "Meeting notes.txt", "/uploads/b.txt", "text/plain", 1L));

        List<Document> results = documentRepository.searchByFileNameOrContent(OWNER, "report");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getFileName()).isEqualTo("Annual Report.pdf");
    }

    @Test
    void integration_docRepo_searchByFileNameOrContentMatchesRawTextContent() {
        Document doc = new Document(OWNER, "Meeting notes.txt", "/uploads/b.txt", "text/plain", 1L);
        doc.setRawTextContent("We discussed the quarterly budget in detail.");
        documentRepository.save(doc);
        documentRepository.save(new Document(OWNER, "unrelated.pdf", "/uploads/c.pdf", "application/pdf", 1L));

        List<Document> results = documentRepository.searchByFileNameOrContent(OWNER, "budget");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getFileName()).isEqualTo("Meeting notes.txt");
    }

    @Test
    void integration_docRepo_searchByFileNameOrContentScopesToOwner() {
        Document mine = new Document(OWNER, "mine.txt", "/uploads/mine.txt", "text/plain", 1L);
        mine.setRawTextContent("shared keyword here");
        documentRepository.save(mine);
        Document theirs = new Document(OTHER, "theirs.txt", "/uploads/theirs.txt", "text/plain", 1L);
        theirs.setRawTextContent("shared keyword here");
        documentRepository.save(theirs);

        List<Document> results = documentRepository.searchByFileNameOrContent(OWNER, "keyword");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getOwnerSubject()).isEqualTo(OWNER);
    }

    @Test
    void integration_docRepo_existsByIdAndOwnerSubjectChecksOwner() {
        Document doc = documentRepository.save(new Document(OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 1L));

        assertThat(documentRepository.existsByIdAndOwnerSubject(doc.getId(), OWNER)).isTrue();
        assertThat(documentRepository.existsByIdAndOwnerSubject(doc.getId(), OTHER)).isFalse();
        assertThat(documentRepository.existsByIdAndOwnerSubject(UUID.randomUUID(), OWNER)).isFalse();
    }

    @Test
    void integration_docRepo_deleteByOwnerSubjectRemovesOnlyThoseRows() {
        documentRepository.save(new Document(OWNER, "a.pdf", "/uploads/a.pdf", "application/pdf", 1L));
        documentRepository.save(new Document(OWNER, "b.pdf", "/uploads/b.pdf", "application/pdf", 1L));
        Document keep = documentRepository.save(new Document(OTHER, "c.pdf", "/uploads/c.pdf", "application/pdf", 1L));

        documentRepository.deleteByOwnerSubject(OWNER);

        assertThat(documentRepository.findByOwnerSubject(OWNER)).isEmpty();
        assertThat(documentRepository.findById(keep.getId())).isPresent();
    }
}
