package com.alexandria.knowledgebase.dto;

import java.util.List;

public record SemanticSearchResponseDto(List<SemanticSearchResultDto> results) {
}
