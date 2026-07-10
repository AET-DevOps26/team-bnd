package com.alexandria.knowledgebase.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findByOwnerSubject(String ownerSubject);

    @Query("""
            SELECT d FROM Document d
            WHERE d.ownerSubject = :ownerSubject
              AND (LOWER(d.fileName) LIKE LOWER(CONCAT('%', :query, '%'))
                   OR LOWER(d.rawTextContent) LIKE LOWER(CONCAT('%', :query, '%')))
            """)
    List<Document> searchByFileNameOrContent(@Param("ownerSubject") String ownerSubject, @Param("query") String query);

    boolean existsByIdAndOwnerSubject(UUID id, String ownerSubject);

    void deleteByOwnerSubject(String ownerSubject);
}
