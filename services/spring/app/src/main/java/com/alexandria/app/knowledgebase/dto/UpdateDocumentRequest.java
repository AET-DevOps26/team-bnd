package com.alexandria.app.knowledgebase.dto;

/**
 * Payload for partially updating document metadata. Currently only renaming is supported.
 *
 * @param fileName new file name for the document, or {@code null} to keep the existing name. When
 *     supplied, it must satisfy: non-blank, at most 255 characters, no control characters, no path
 *     separators or shell/HTML metacharacters, and not
 *     a reserved OS name.
 */
public record UpdateDocumentRequest(String fileName) {
}
