package com.alexandria.app.knowledgebase.dto;

import java.time.Instant;

public record DocumentSummaryDto(String summary, String modelUsed, Instant generatedAt) {}
