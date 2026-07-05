package com.alexandria.app.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TagRepository extends JpaRepository<Tag, UUID> {
    Optional<Tag> findByLabel(String label);

    boolean existsByLabel(String label);

    /**
     * Returns the count of documents per tag label for the given owner, grouped and
     * counted at the database level to avoid loading every document into memory.
     */
    @Query("""
            SELECT t.label AS label, COUNT(d.id) AS documentCount
            FROM Tag t
            JOIN t.documents d
            WHERE d.owner.id = :ownerId
            GROUP BY t.label
            """)
    List<TagCountProjection> findTagCountsByOwnerId(@Param("ownerId") UUID ownerId);

    interface TagCountProjection {
        String getLabel();

        long getDocumentCount();
    }
}
