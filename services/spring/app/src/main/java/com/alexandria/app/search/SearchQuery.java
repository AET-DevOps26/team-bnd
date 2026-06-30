package com.alexandria.app.search;

import com.alexandria.app.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "search_queries")
public class SearchQuery {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false)
  private String queryText;

  @Column(nullable = false)
  private Instant timestamp;

  @Column(nullable = false)
  private Integer resultCount;

  public SearchQuery() {}

  public SearchQuery(User user, String queryText, Integer resultCount) {
    this.user = user;
    this.queryText = queryText;
    this.resultCount = resultCount;
    this.timestamp = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
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
