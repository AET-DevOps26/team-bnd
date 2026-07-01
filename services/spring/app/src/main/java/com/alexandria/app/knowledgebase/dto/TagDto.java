package com.alexandria.app.knowledgebase.dto;

public record TagDto(String name, long documentCount // number of documents using this tag
) {
}
