package com.alexandria.app.knowledgebase.dto;

import com.alexandria.app.document.EntityType;

public record DocumentEntityDto(String name, EntityType type, double confidence) {}
