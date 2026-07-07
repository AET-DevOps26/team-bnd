package com.alexandria.knowledgebase.search;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SearchQueryRepository extends JpaRepository<SearchQuery, UUID> {
    List<SearchQuery> findByUserSubjectOrderByTimestampDesc(String userSubject);

    void deleteByUserSubject(String userSubject);
}
