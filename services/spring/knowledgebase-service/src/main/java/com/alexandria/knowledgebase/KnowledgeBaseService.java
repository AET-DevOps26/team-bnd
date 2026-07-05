package com.alexandria.knowledgebase;

import com.alexandria.knowledgebase.document.*;
import com.alexandria.knowledgebase.dto.UpdateDocumentRequest;
import com.alexandria.knowledgebase.exception.DocumentNotFoundException;
import com.alexandria.knowledgebase.search.SearchQuery;
import com.alexandria.knowledgebase.search.SearchQueryRepository;
import com.alexandria.knowledgebase.integration.GenAiClient;
import com.alexandria.knowledgebase.integration.GenAiClient.ExtractResponse;
import com.alexandria.knowledgebase.integration.GenAiClient.ExtractedEntityDto;
import com.alexandria.knowledgebase.integration.GenAiClient.SummarizeResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final DocumentRepository documentRepository;
    private final SummaryRepository summaryRepository;
    private final ExtractedEntityRepository extractedEntityRepository;
    private final TagRepository tagRepository;
    private final SearchQueryRepository searchQueryRepository;
    private final GenAiClient genAiClient;
    private final TextExtractor textExtractor;
    private final ObjectStorageService objectStorageService;

    public KnowledgeBaseService(
            DocumentRepository documentRepository,
            SummaryRepository summaryRepository,
            ExtractedEntityRepository extractedEntityRepository,
            TagRepository tagRepository,
            SearchQueryRepository searchQueryRepository,
            GenAiClient genAiClient,
            TextExtractor textExtractor,
            ObjectStorageService objectStorageService) {
        this.documentRepository = documentRepository;
        this.summaryRepository = summaryRepository;
        this.extractedEntityRepository = extractedEntityRepository;
        this.tagRepository = tagRepository;
        this.searchQueryRepository = searchQueryRepository;
        this.genAiClient = genAiClient;
        this.textExtractor = textExtractor;
        this.objectStorageService = objectStorageService;
    }

    @Transactional
    public Document createDocument(String ownerSubject, String fileName, String objectKey, String fileType, Long fileSize, String textContent) {
        FileNameValidator.validate(fileName);
        Document document = new Document(ownerSubject, fileName, objectKey, fileType, fileSize);
        document.setRawTextContent(textContent);
        document = documentRepository.save(document);

        if (textContent != null && !textContent.isBlank()) {
            processSummary(document);
            processEntities(document);
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
        document = documentRepository.save(document);
        objectStorageService.upload(objectKey, file);

        processSummary(document);
        processEntities(document);

        return document;
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
                ExtractedEntity entity =
                        new ExtractedEntity(document, dto.name(), dto.type(), dto.confidence());
                extractedEntityRepository.save(entity);
                document.getExtractedEntities().add(entity);
            }
        } catch (Exception e) {
            log.warn(
                    "GenAI entity extraction failed for document {}: {}", document.getId(), e.getMessage());
        }
    }

    public List<Document> getDocuments(String ownerSubject) {
        return documentRepository.findByOwnerSubject(ownerSubject);
    }

    public Document getDocument(UUID id, String ownerSubject) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
        if (!document.getOwnerSubject().equals(ownerSubject)) {
            throw new DocumentNotFoundException(id);
        }
        return document;
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
        if (!documentRepository.existsByIdAndOwnerSubject(id, ownerSubject)) {
            throw new DocumentNotFoundException(id);
        }
        documentRepository
                .findById(id)
                .map(Document::getObjectKey)
                .ifPresent(objectStorageService::delete);
        documentRepository.deleteById(id);
    }

    @Transactional
    public Document updateDocument(UUID id, @Valid UpdateDocumentRequest request, String ownerSubject) {
        Document document = getDocument(id, ownerSubject);
        if (request.fileName() != null) {
            FileNameValidator.validate(request.fileName());
            document.setFileName(request.fileName());
        }
        return documentRepository.save(document);
    }

    public List<Document> search(String userSubject, String queryText) {
        List<Document> results = documentRepository.findByOwnerSubjectAndFileNameContainingIgnoreCase(
                userSubject, queryText);

        SearchQuery searchQuery = new SearchQuery(userSubject, queryText, results.size());
        searchQueryRepository.save(searchQuery);

        return results;
    }

    @Transactional
    public void addTag(UUID documentId, String ownerSubject, String label, TagSource source) {
        Document document = getDocument(documentId, ownerSubject);

        Tag tag =
                tagRepository
                        .findByLabel(label)
                        .orElseGet(() -> tagRepository.save(new Tag(label, source)));

        document.addTag(tag);
        documentRepository.save(document);
    }

    @Transactional
    public void removeTag(UUID documentId, String ownerSubject, UUID tagId) {
        Document document = getDocument(documentId, ownerSubject);
        Tag tag = tagRepository.findById(tagId).orElse(null);
        if (tag != null) {
            document.removeTag(tag);
            documentRepository.save(document);
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
        return tagRepository.findTagCountsByOwnerSubject(userSubject).stream()
                .collect(
                        Collectors.toMap(
                                TagRepository.TagCountProjection::getLabel,
                                TagRepository.TagCountProjection::getDocumentCount));
    }

    @Transactional
    public void deleteAllForUser(String userSubject) {
        for (Document doc : documentRepository.findByOwnerSubject(userSubject)) {
            try {
                objectStorageService.delete(doc.getObjectKey());
            } catch (Exception e) {
                log.warn("Failed to delete S3 object for document {}: {}", doc.getId(), e.getMessage());
            }
        }
        documentRepository.deleteByOwnerSubject(userSubject);
        searchQueryRepository.deleteByUserSubject(userSubject);
    }

    public List<Document> listDocumentsByObjectKeys(String ownerSubject, List<String> objectKeys) {
        return documentRepository.findByOwnerSubject(ownerSubject).stream()
                .filter(d -> objectKeys.contains(d.getObjectKey()))
                .toList();
    }
}
