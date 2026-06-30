package com.alexandria.app.user;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Users are identified by their OIDC subject claim (oidcSubject). Local user records are created on
 * first authenticated request.
 */
@Entity
@Table(name = "users")
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true)
  private String oidcSubject;

  @Column(unique = true)
  private String username;

  @Column(unique = true)
  private String email;

  @Column(nullable = false)
  private Instant createdAt;

  private String preferences; // JSON string for now

  public User() {}

  public User(String oidcSubject, String username, String email) {
    this.oidcSubject = oidcSubject;
    this.username = username;
    this.email = email;
  }

  // PrePersist: JPA lifecycle hook on first insertion of entity
  @PrePersist
  void prePersist() {
    createdAt = Instant.now();
  }

  // Getters & Setters
  public UUID getId() {
    return id;
  }

  public String getOidcSubject() {
    return oidcSubject;
  }

  public void setOidcSubject(String oidcSubject) {
    this.oidcSubject = oidcSubject;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public String getPreferences() {
    return preferences;
  }

  public void setPreferences(String preferences) {
    this.preferences = preferences;
  }
}
