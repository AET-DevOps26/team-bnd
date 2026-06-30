package com.alexandria.app.document;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TagRepository extends JpaRepository<Tag, UUID> {
  Optional<Tag> findByLabel(String label);

  boolean existsByLabel(String label);
}
