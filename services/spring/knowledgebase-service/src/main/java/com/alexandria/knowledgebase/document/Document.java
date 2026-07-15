package com.alexandria.knowledgebase.document;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // OIDC subject of the owning user. We deliberately store this as a plain
    // string instead of a foreign key so this service does not have to reach
    // into the user-service database.
    @Column(name = "owner_subject", nullable = false)
    private String ownerSubject;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String objectKey;

    @Column(nullable = false)
    private String fileType;

    @Column(nullable = false)
    private Long fileSize;

    @Column(columnDefinition = "TEXT")
    @JsonIgnore
    private String rawTextContent;

    @Column(nullable = false)
    private Instant createdAt;

    @OneToOne(mappedBy = "document", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Summary summary;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExtractedEntity> extractedEntities = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "document_tags", joinColumns = @JoinColumn(name = "document_id"), inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags = new HashSet<>();

    // Pipeline processing states for entity extraction and tagging. Unlike summaries,
    // these are collections with no single row to update, so the status lives here.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SummaryStatus entitiesStatus = SummaryStatus.COMPLETED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SummaryStatus tagsStatus = SummaryStatus.COMPLETED;

    public Document() {
    }

    public Document(String ownerSubject, String fileName, String objectKey, String fileType, Long fileSize) {
        this.ownerSubject = ownerSubject;
        this.fileName = fileName;
        this.objectKey = objectKey;
        this.fileType = fileType;
        this.fileSize = fileSize;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getOwnerSubject() {
        return ownerSubject;
    }

    public void setOwnerSubject(String ownerSubject) {
        this.ownerSubject = ownerSubject;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getRawTextContent() {
        return rawTextContent;
    }

    public void setRawTextContent(String rawTextContent) {
        this.rawTextContent = rawTextContent;
    }

    public Summary getSummary() {
        return summary;
    }

    public void setSummary(Summary summary) {
        this.summary = summary;
    }

    public List<ExtractedEntity> getExtractedEntities() {
        return extractedEntities == null ? new ArrayList<>() : new ArrayList<>(extractedEntities);
    }

    public void setExtractedEntities(List<ExtractedEntity> extractedEntities) {
        this.extractedEntities = extractedEntities == null ? new ArrayList<>() : new ArrayList<>(extractedEntities);
    }

    public Set<Tag> getTags() {
        return tags;
    }

    public void setTags(Set<Tag> tags) {
        this.tags = tags;
    }

    public void addTag(Tag tag) {
        tags.add(tag);
        tag.getDocuments().add(this);
    }

    public void removeTag(Tag tag) {
        tags.remove(tag);
        tag.getDocuments().remove(this);
    }

    public SummaryStatus getEntitiesStatus() {
        return entitiesStatus;
    }

    public void setEntitiesStatus(SummaryStatus entitiesStatus) {
        this.entitiesStatus = entitiesStatus;
    }

    public SummaryStatus getTagsStatus() {
        return tagsStatus;
    }

    public void setTagsStatus(SummaryStatus tagsStatus) {
        this.tagsStatus = tagsStatus;
    }
}
