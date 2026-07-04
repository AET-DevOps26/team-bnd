package com.alexandria.knowledgebase.search;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "search_queries")
public class SearchQuery {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_subject", nullable = false)
    private String userSubject;

    @Column(nullable = false)
    private String queryText;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false)
    private Integer resultCount;

    public SearchQuery() {
    }

    public SearchQuery(String userSubject, String queryText, Integer resultCount) {
        this.userSubject = userSubject;
        this.queryText = queryText;
        this.resultCount = resultCount;
        this.timestamp = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getUserSubject() {
        return userSubject;
    }

    public void setUserSubject(String userSubject) {
        this.userSubject = userSubject;
    }

    public String getQueryText() {
        return queryText;
    }

    public void setQueryText(String queryText) {
        this.queryText = queryText;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Integer getResultCount() {
        return resultCount;
    }

    public void setResultCount(Integer resultCount) {
        this.resultCount = resultCount;
    }
}
