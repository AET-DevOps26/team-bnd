package com.alexandria.app.document;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExtractedEntityRepository extends JpaRepository<ExtractedEntity, UUID> {
  List<ExtractedEntity> findByDocumentId(UUID documentId);

  List<ExtractedEntity> findByType(EntityType type);

  void deleteByDocumentId(UUID documentId);
}
