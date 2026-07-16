package com.alexandria.knowledgebase.document;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "summaries")
public class Summary {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    @JsonBackReference
    private Document document;

    // Null when status is PENDING or FAILED.
    @Column(columnDefinition = "TEXT")
    private String content;

    @Column
    private Instant generatedAt;

    @Column
    private String modelUsed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SummaryStatus status = SummaryStatus.PENDING;

    // Human-readable reason for FAILED state; null otherwise.
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    public Summary() {
    }

    // Constructor for inserting a PENDING placeholder before the LLM call.
    public Summary(Document document) {
        this.document = document;
        this.status = SummaryStatus.PENDING;
    }

    // Constructor for a completed summary (kept for compatibility with call sites
    // that do not use the two-step pending/complete approach).
    public Summary(Document document, String content, String modelUsed) {
        this.document = document;
        this.content = content;
        this.modelUsed = modelUsed;
        this.generatedAt = Instant.now();
        this.status = SummaryStatus.COMPLETED;
    }

    public UUID getId() {
        return id;
    }

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public String getModelUsed() {
        return modelUsed;
    }

    public void setModelUsed(String modelUsed) {
        this.modelUsed = modelUsed;
    }

    public SummaryStatus getStatus() {
        return status;
    }

    public void setStatus(SummaryStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    // Transition helpers so callers don't have to set individual fields.

    public void markCompleted(String content, String modelUsed) {
        this.content = content;
        this.modelUsed = modelUsed;
        this.generatedAt = Instant.now();
        this.status = SummaryStatus.COMPLETED;
        this.errorMessage = null;
    }

    public void markFailed(String reason) {
        this.status = SummaryStatus.FAILED;
        this.errorMessage = reason;
    }
}
