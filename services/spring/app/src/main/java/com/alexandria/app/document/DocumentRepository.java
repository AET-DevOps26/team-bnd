package com.alexandria.app.document;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {
  List<Document> findByOwnerId(UUID ownerId);

  List<Document> findByOwnerIdAndFileNameContainingIgnoreCase(UUID ownerId, String fileName);

  boolean existsByIdAndOwnerId(UUID id, UUID ownerId);

  void deleteByOwnerId(UUID ownerId);
}
