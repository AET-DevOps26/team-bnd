package com.alexandria.knowledgebase.dto;

import java.util.List;
import java.util.UUID;

public record DocumentEntityResponseDto(UUID documentId, List<DocumentEntityDto> entities) {
}
