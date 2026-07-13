package com.alexandria.knowledgebase;

import com.alexandria.knowledgebase.document.*;
import com.alexandria.knowledgebase.dto.DocumentRefDto;
import com.alexandria.knowledgebase.dto.SemanticSearchResponseDto;
import com.alexandria.knowledgebase.dto.SemanticSearchResultDto;
import com.alexandria.knowledgebase.dto.UpdateDocumentRequest;
import com.alexandria.knowledgebase.integration.GenAiClient;
import com.alexandria.knowledgebase.integration.GenAiClient.ExtractResponse;
import com.alexandria.knowledgebase.integration.GenAiClient.ExtractedEntityDto;
import com.alexandria.knowledgebase.integration.GenAiClient.SummarizeResponse;
import com.alexandria.knowledgebase.integration.GenAiClient.TagResponse;
import com.alexandria.knowledgebase.search.SearchQuery;
import com.alexandria.knowledgebase.search.SearchQueryRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final DocumentService documentService;
    private final SummaryRepository summaryRepository;
    private final ExtractedEntityRepository extractedEntityRepository;
    private final TagRepository tagRepository;
    private final SearchQueryRepository searchQueryRepository;
    private final GenAiClient genAiClient;
    private final TextExtractor textExtractor;
    private final ObjectStorageService objectStorageService;
    // Self-reference so the @Async pipeline method is entered through the Spring proxy;
    // a plain this.processDocumentAsync(...) call would run inline and defeat the point.
    private final ObjectProvider<KnowledgeBaseService> self;

    public KnowledgeBaseService(
                                DocumentService documentService, SummaryRepository summaryRepository, ExtractedEntityRepository extractedEntityRepository, TagRepository tagRepository, SearchQueryRepository searchQueryRepository, GenAiClient genAiClient, TextExtractor textExtractor, ObjectStorageService objectStorageService, ObjectProvider<KnowledgeBaseService> self) {
        this.documentService = documentService;
        this.summaryRepository = summaryRepository;
        this.extractedEntityRepository = extractedEntityRepository;
        this.tagRepository = tagRepository;
        this.searchQueryRepository = searchQueryRepository;
        this.genAiClient = genAiClient;
        this.textExtractor = textExtractor;
        this.objectStorageService = objectStorageService;
        this.self = self;
    }

    @Transactional
    public Document createDocument(String ownerSubject, String fileName, String objectKey, String fileType, Long fileSize, String textContent) {
        FileNameValidator.validate(fileName);
        Document document = new Document(ownerSubject, fileName, objectKey, fileType, fileSize);
        document.setRawTextContent(textContent);
        document = documentService.save(document);

        if (textContent != null && !textContent.isBlank()) {
            self.getObject().processDocumentAsync(document.getId());
        }

        return document;
    }

    @Transactional
    public Document uploadDocument(String ownerSubject, MultipartFile file) {
        String fileName = file.getOriginalFilename();
        FileNameValidator.validate(fileName);
        String fileType = file.getContentType();
        Long fileSize = file.getSize();
        String objectKey = "/uploads/" + UUID.randomUUID() + "/" + fileName;

        String textContent = textExtractor.extract(file);

        Document document = new Document(ownerSubject, fileName, objectKey, fileType, fileSize);
        document.setRawTextContent(textContent);
        document = documentService.save(document);
        objectStorageService.upload(objectKey, file);

        self.getObject().processDocumentAsync(document.getId());

        return document;
    }

    // Runs the GenAI pipeline off the request thread so uploads return as soon as the
    // file is stored. Reloads the document by id because the persistence context that
    // created it does not carry over to this thread, and the row may already be gone.
    @Async
    @Transactional
    public void processDocumentAsync(UUID documentId) {
        Document document;
        try {
            document = documentService.findById(documentId);
        } catch (Exception e) {
            log.warn("Skipping async GenAI processing for missing document {}: {}", documentId, e.getMessage());
            return;
        }
        String textContent = document.getRawTextContent();
        if (textContent == null || textContent.isBlank()) {
            return;
        }
        processSummary(document);
        processEntities(document);
        processTags(document);
        processIndexing(document);
    }

    private void processSummary(Document document) {
        try {
            SummarizeResponse response = genAiClient.summarize(document.getObjectKey());
            Summary summary = new Summary(document, response.summary(), response.modelUsed());
            summaryRepository.save(summary);
            document.setSummary(summary);
        } catch (Exception e) {
            log.warn("GenAI summarization failed for document {}: {}", document.getId(), e.getMessage());
        }
    }

    private void processEntities(Document document) {
        try {
            ExtractResponse response = genAiClient.extract(document.getObjectKey());
            for (ExtractedEntityDto dto : response.entities()) {
                ExtractedEntity entity = new ExtractedEntity(document, dto.name(), dto.type(), dto.confidence());
                extractedEntityRepository.save(entity);
                document.getExtractedEntities().add(entity);
            }
        } catch (Exception e) {
            log.warn(
                    "GenAI entity extraction failed for document {}: {}", document.getId(), e.getMessage());
        }
    }

    private void processTags(Document document) {
        try {
            // pass existing labels so the model reuses them instead of inventing near-duplicates
            List<String> knownTags = List.copyOf(getTagsForUserWithCount(document.getOwnerSubject()).keySet());
            TagResponse response = genAiClient.tag(document.getObjectKey(), knownTags);
            for (String label : response.tags()) {
                if (label == null || label.isBlank()) {
                    continue;
                }
                Tag tag = tagRepository.findByLabel(label).orElseGet(() -> tagRepository.save(new Tag(label, TagSource.AUTO)));
                document.addTag(tag);
            }
            documentService.save(document);
        } catch (Exception e) {
            log.warn("GenAI tagging failed for document {}: {}", document.getId(), e.getMessage());
        }
    }

    private void processIndexing(Document document) {
        try {
            genAiClient.index(document.getObjectKey());
        } catch (Exception e) {
            log.warn("GenAI indexing failed for document {}: {}", document.getId(), e.getMessage());
        }
    }

    public List<Document> getDocuments(String ownerSubject) {
        return documentService.findByOwnerSubject(ownerSubject);
    }

    public Document getDocument(UUID id, String ownerSubject) {
        return documentService.findByIdAndOwner(id, ownerSubject);
    }

    public List<Document> resolveDocuments(String ownerSubject, Collection<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return List.of();
        }
        return documentService.findByOwnerSubjectAndObjectKeyIn(ownerSubject, objectKeys);
    }

    @Transactional
    public void reprocessSummary(UUID id, String ownerSubject) {
        Document document = getDocument(id, ownerSubject);
        Summary existing = document.getSummary();
        if (existing != null) {
            document.setSummary(null);
            summaryRepository.delete(existing);
            summaryRepository.flush();
        }
        processSummary(document);
    }

    @Transactional
    public void reprocessEntities(UUID id, String ownerSubject) {
        Document document = getDocument(id, ownerSubject);
        extractedEntityRepository.deleteByDocumentId(id);
        extractedEntityRepository.flush();
        processEntities(document);
    }

    @Transactional
    public void reprocessTags(UUID id, String ownerSubject) {
        Document document = getDocument(id, ownerSubject);
        // only drop the auto tags, user-added tags stay
        List<Tag> autoTags = document.getTags().stream().filter(tag -> tag.getSource() == TagSource.AUTO).toList();
        for (Tag tag : autoTags) {
            document.removeTag(tag);
        }
        documentService.save(document);
        processTags(document);
    }

    public Summary getDocumentSummary(UUID id, String ownerSubject) {
        Document document = getDocument(id, ownerSubject);
        return document.getSummary();
    }

    public List<ExtractedEntity> getDocumentEntities(UUID id, String ownerSubject) {
        Document document = getDocument(id, ownerSubject);
        return document.getExtractedEntities();
    }

    @Transactional(readOnly = true)
    public Optional<byte[]> getFileContent(UUID documentId, String ownerSubject) {
        Document document = getDocument(documentId, ownerSubject);
        try {
            return Optional.of(objectStorageService.download(document.getObjectKey()));
        } catch (Exception e) {
            log.warn("Failed to download file for document {}: {}", documentId, e.getMessage());
            return Optional.empty();
        }
    }

    @Transactional
    public void deleteDocument(UUID id, String ownerSubject) {
        Document document = documentService.findByIdAndOwner(id, ownerSubject);
        String objectKey = document.getObjectKey();
        documentService.delete(id, ownerSubject);
        objectStorageService.delete(objectKey);
        try {
            genAiClient.deleteIndex(objectKey);
        } catch (Exception e) {
            log.warn("GenAI index deletion failed for object key {}: {}", objectKey, e.getMessage());
        }
    }

    @Transactional
    public Document updateDocument(UUID id, @Valid UpdateDocumentRequest request, String ownerSubject) {
        Document document = getDocument(id, ownerSubject);
        if (request.fileName() != null) {
            FileNameValidator.validate(request.fileName());
            document.setFileName(request.fileName());
        }
        return documentService.save(document);
    }

    public List<DocumentRefDto> search(String userSubject, String queryText) {
        List<Document> matches = documentService.searchByFileNameOrContent(userSubject, queryText);

        SearchQuery searchQuery = new SearchQuery(userSubject, queryText, matches.size());
        searchQueryRepository.save(searchQuery);

        return matches.stream().map(DocumentRefDto::from).toList();
    }

    public SemanticSearchResponseDto semanticSearch(String userSubject, String queryText, Integer limit) {
        List<String> objectKeys = documentService.findObjectKeysByOwnerSubject(userSubject);

        List<SemanticSearchResultDto> results;
        boolean fallbackUsed = false;
        try {
            GenAiClient.SearchResponse response = genAiClient.search(queryText, objectKeys, limit);
            List<GenAiClient.SearchResult> hits = response.results();
            Map<String, Document> byObjectKey = hits.isEmpty() ? Map.of() : documentService.findByOwnerSubjectAndObjectKeyIn(userSubject, hits.stream().map(GenAiClient.SearchResult::objectKey).toList()).stream().collect(Collectors.toMap(Document::getObjectKey, d -> d, (a, b) -> a));
            results = hits.stream().map(hit -> {
                Document document = byObjectKey.get(hit.objectKey());
                return document == null ? null : new SemanticSearchResultDto(DocumentRefDto.from(document), hit.score(), hit.snippet());
            }).filter(Objects::nonNull).toList();
        } catch (Exception e) {
            log.warn("GenAI semantic search failed for user {}: {}", userSubject, e.getMessage());
            results = keywordFallback(userSubject, queryText);
            fallbackUsed = true;
        }

        // an empty index returns no hits, fall back to keyword search so the user still gets something
        if (results.isEmpty() && !fallbackUsed) {
            results = keywordFallback(userSubject, queryText);
            fallbackUsed = true;
        }

        SearchQuery searchQuery = new SearchQuery(userSubject, queryText, results.size());
        searchQueryRepository.save(searchQuery);

        return new SemanticSearchResponseDto(results, fallbackUsed);
    }

    private List<SemanticSearchResultDto> keywordFallback(String userSubject, String queryText) {
        return documentService.searchByFileNameOrContent(userSubject, queryText).stream().map(d -> new SemanticSearchResultDto(DocumentRefDto.from(d), null, null)).toList();
    }

    @Transactional
    public void addTag(UUID documentId, String ownerSubject, String label, TagSource source) {
        Document document = getDocument(documentId, ownerSubject);

        Tag tag = tagRepository.findByLabel(label).orElseGet(() -> tagRepository.save(new Tag(label, source)));

        document.addTag(tag);
        documentService.save(document);
    }

    @Transactional
    public void removeTag(UUID documentId, String ownerSubject, UUID tagId) {
        Document document = getDocument(documentId, ownerSubject);
        Tag tag = tagRepository.findById(tagId).orElse(null);
        if (tag != null) {
            document.removeTag(tag);
            documentService.save(document);
        }
    }

    public List<SearchQuery> getSearchHistory(String userSubject) {
        return searchQueryRepository.findByUserSubjectOrderByTimestampDesc(userSubject);
    }

    @Transactional
    public void deleteSearchHistory(String userSubject) {
        searchQueryRepository.deleteByUserSubject(userSubject);
    }

    public Map<String, Long> getTagsForUserWithCount(String userSubject) {
        return tagRepository.findTagCountsByOwnerSubject(userSubject).stream().collect(
                Collectors.toMap(
                        TagRepository.TagCountProjection::getLabel, TagRepository.TagCountProjection::getDocumentCount));
    }

    @Transactional
    public void deleteAllForUser(String userSubject) {
        for (Document doc : documentService.findByOwnerSubject(userSubject)) {
            try {
                objectStorageService.delete(doc.getObjectKey());
            } catch (Exception e) {
                log.warn("Failed to delete S3 object for document {}: {}", doc.getId(), e.getMessage());
            }
            try {
                genAiClient.deleteIndex(doc.getObjectKey());
            } catch (Exception e) {
                log.warn("GenAI index deletion failed for document {}: {}", doc.getId(), e.getMessage());
            }
        }
        documentService.deleteAllByOwner(userSubject);
        searchQueryRepository.deleteByUserSubject(userSubject);
    }
}
