package com.alexandria.app.document;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "tags")
public class Tag {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TagSource source;

    @ManyToMany(mappedBy = "tags")
    private Set<Document> documents = new HashSet<>();

    public Tag() {
    }

    public Tag(String label, TagSource source) {
        this.label = label;
        this.source = source;
    }

    public UUID getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public TagSource getSource() {
        return source;
    }

    public void setSource(TagSource source) {
        this.source = source;
    }

    public Set<Document> getDocuments() {
        return new HashSet<>(documents);
    }

    public void setDocuments(Set<Document> documents) {
        this.documents = documents == null ? new HashSet<>() : new HashSet<>(documents);
    }
}
