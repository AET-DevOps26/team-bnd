package com.alexandria.app.search;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SearchQueryRepository extends JpaRepository<SearchQuery, UUID> {
  List<SearchQuery> findByUserIdOrderByTimestampDesc(UUID userId);

  void deleteByUserId(UUID userId);
}
