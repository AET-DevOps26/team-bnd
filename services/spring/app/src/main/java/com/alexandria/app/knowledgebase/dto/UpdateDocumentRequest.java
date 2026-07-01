package com.alexandria.app.knowledgebase.dto;

public record UpdateDocumentRequest(
    // currently, only renaming is supported
    String fileName) {}
