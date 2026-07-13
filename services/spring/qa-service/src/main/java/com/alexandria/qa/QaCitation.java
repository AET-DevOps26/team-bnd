package com.alexandria.qa;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

@Embeddable
public class QaCitation {

    @Column(name = "marker", nullable = false)
    private int marker;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "document_id")
    private String documentId;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "snippet", columnDefinition = "TEXT")
    private String snippet;

    public QaCitation() {
    }

    public QaCitation(int marker, String objectKey, String documentId, String fileName, String snippet) {
        this.marker = marker;
        this.objectKey = objectKey;
        this.documentId = documentId;
        this.fileName = fileName;
        this.snippet = snippet;
    }

    public int getMarker() {
        return marker;
    }

    public void setMarker(int marker) {
        this.marker = marker;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof QaCitation that)) return false;
        return marker == that.marker && Objects.equals(objectKey, that.objectKey) && Objects.equals(documentId, that.documentId) && Objects.equals(fileName, that.fileName) && Objects.equals(snippet, that.snippet);
    }

    @Override
    public int hashCode() {
        return Objects.hash(marker, objectKey, documentId, fileName, snippet);
    }
}
