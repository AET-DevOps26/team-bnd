package com.alexandria.app.qa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QAInteractionRepository extends JpaRepository<QAInteraction, UUID> {
  List<QAInteraction> findByUserIdOrderByTimestampDesc(UUID userId);

  void deleteByUserId(UUID userId);
}
