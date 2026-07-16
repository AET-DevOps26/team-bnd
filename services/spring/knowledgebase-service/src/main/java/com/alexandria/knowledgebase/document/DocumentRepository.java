package com.alexandria.knowledgebase.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findByOwnerSubject(String ownerSubject);

    // Scoped column update: the entities and tags pipelines run concurrently, and a full-entity
    // persist writes every column from each worker's own snapshot, letting them revert each
    // other's status. Touching a single status column keeps the two writers independent.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Document d SET d.entitiesStatus = :status WHERE d.id = :id")
    void updateEntitiesStatus(@Param("id") UUID id, @Param("status") SummaryStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Document d SET d.tagsStatus = :status WHERE d.id = :id")
    void updateTagsStatus(@Param("id") UUID id, @Param("status") SummaryStatus status);

    @Query("SELECT d.objectKey FROM Document d WHERE d.ownerSubject = :ownerSubject")
    List<String> findObjectKeysByOwnerSubject(@Param("ownerSubject") String ownerSubject);

    List<Document> findByOwnerSubjectAndObjectKeyIn(String ownerSubject, Collection<String> objectKeys);

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
