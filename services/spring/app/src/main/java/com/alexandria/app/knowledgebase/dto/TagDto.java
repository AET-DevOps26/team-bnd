package com.alexandria.app.knowledgebase.dto;

/**
 * @param name          tag label
 * @param documentCount number of documents using this tag
 */
public record TagDto(String name, long documentCount) {
}
