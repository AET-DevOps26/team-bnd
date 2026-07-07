package com.alexandria.knowledgebase.dto;

import com.alexandria.knowledgebase.document.EntityType;

public record DocumentEntityDto(String name, EntityType type, double confidence) {
}
