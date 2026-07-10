package com.alexandria.knowledgebase.dto;

import com.alexandria.knowledgebase.document.Document;

/**
 * @param document the matched document
 * @param score    semantic relevance score from the GenAI service
 * @param snippet  the closest matching text chunk, so the client can show why it matched
 */
public record SemanticSearchResultDto(Document document, Double score, String snippet) {
}
