package com.alexandria.knowledgebase.dto;

import com.alexandria.knowledgebase.document.SummaryStatus;

import java.time.Instant;

public record DocumentSummaryDto(
                                 String summary,
                                 String modelUsed,
                                 Instant generatedAt,
                                 SummaryStatus status,
                                 String errorMessage) {
}
