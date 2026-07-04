package com.alexandria.knowledgebase.search;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SearchQueryRepository extends JpaRepository<SearchQuery, java.util.UUID> {
    List<SearchQuery> findByUserSubjectOrderByTimestampDesc(String userSubject);

    void deleteByUserSubject(String userSubject);
}
