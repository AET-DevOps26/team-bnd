package com.alexandria.knowledgebase.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findByOwnerSubject(String ownerSubject);

    List<Document> findByOwnerSubjectAndFileNameContainingIgnoreCase(String ownerSubject, String fileName);

    boolean existsByIdAndOwnerSubject(UUID id, String ownerSubject);

    void deleteByOwnerSubject(String ownerSubject);
}
