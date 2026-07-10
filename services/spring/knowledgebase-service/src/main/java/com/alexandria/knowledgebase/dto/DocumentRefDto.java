package com.alexandria.knowledgebase.dto;

import com.alexandria.knowledgebase.document.Document;

import java.time.Instant;
import java.util.UUID;

/**
 * Slim view of a document for search results, enough to render a result row.
 */
public record DocumentRefDto(UUID id, String fileName, String fileType, Long fileSize, Instant createdAt) {
    public static DocumentRefDto from(Document document) {
        return new DocumentRefDto(document.getId(), document.getFileName(), document.getFileType(), document.getFileSize(), document.getCreatedAt());
    }
}
