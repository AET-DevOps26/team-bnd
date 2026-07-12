package com.alexandria.knowledgebase.dto;

/**
 * @param document slim reference to the matched document
 * @param score    semantic relevance score from the GenAI service
 * @param snippet  the closest matching text chunk, so the client can show why it matched
 */
public record SemanticSearchResultDto(DocumentRefDto document, Double score, String snippet) {
}
