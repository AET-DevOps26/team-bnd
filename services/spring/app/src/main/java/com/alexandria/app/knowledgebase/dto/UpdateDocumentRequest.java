package com.alexandria.app.knowledgebase.dto;

/**
 * Payload for partially updating document metadata. Currently only renaming is supported.
 *
 * @param fileName new file name for the document, or {@code null} to keep the existing name
 */
public record UpdateDocumentRequest(String fileName) {
}
