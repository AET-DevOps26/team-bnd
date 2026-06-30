package com.alexandria.app.document;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SummaryRepository extends JpaRepository<Summary, UUID> {
  Optional<Summary> findByDocumentId(UUID documentId);

  void deleteByDocumentId(UUID documentId);
}
