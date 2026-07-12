package com.alexandria.knowledgebase.dto;

import java.util.List;

public record TextSearchResponseDto(List<DocumentRefDto> results) {
}
