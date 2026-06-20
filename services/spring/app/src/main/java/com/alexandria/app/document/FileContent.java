package com.alexandria.app.document;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.sql.Blob;
import java.util.UUID;

// as in https://www.baeldung.com/jpa-one-to-one
@Entity
@Table(name = "file_contents")
public class FileContent {
    @Id
    @Column(name = "id")
    private UUID id;

    @OneToOne
    @MapsId
    @JoinColumn(name = "id")
    @JsonBackReference
    private Document document;

    @Lob
    @Column(nullable = false)
    @JsonIgnore
    private Blob fileContent;

    // Getters & Setters
    public UUID getId() {
        return id;
    }

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    public Blob getFileContent() {
        return fileContent;
    }

    public void setFileContent(Blob fileContent) {
        this.fileContent = fileContent;
    }
}
