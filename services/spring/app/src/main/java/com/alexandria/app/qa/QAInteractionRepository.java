package com.alexandria.app.qa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QAInteractionRepository extends JpaRepository<QAInteraction, UUID> {
    List<QAInteraction> findByUserIdOrderByTimestampDesc(UUID userId);
}
