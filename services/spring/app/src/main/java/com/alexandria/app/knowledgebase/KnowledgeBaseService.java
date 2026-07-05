package com.alexandria.app.knowledgebase;

import com.alexandria.app.document.Document;
import com.alexandria.app.document.DocumentRepository;
import com.alexandria.app.document.ExtractedEntity;
import com.alexandria.app.document.ExtractedEntityRepository;
import com.alexandria.app.document.Summary;
import com.alexandria.app.document.SummaryRepository;
import com.alexandria.app.document.Tag;
import com.alexandria.app.document.TagRepository;
import com.alexandria.app.document.TagSource;
import com.alexandria.app.exception.DocumentNotFoundException;
import com.alexandria.app.knowledgebase.dto.UpdateDocumentRequest;
import com.alexandria.app.qa.QAInteraction;
import com.alexandria.app.qa.QAInteractionRepository;
import com.alexandria.app.search.SearchQuery;
import com.alexandria.app.search.SearchQueryRepository;
import com.alexandria.app.user.User;
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
    private final QAInteractionRepository qaInteractionRepository;
    private final GenAiClient genAiClient;
    private final TextExtractor textExtractor;
    private final ObjectStorageService objectStorageService;

    public KnowledgeBaseService(
            DocumentRepository documentRepository,
            SummaryRepository summaryRepository,
            ExtractedEntityRepository extractedEntityRepository,
            TagRepository tagRepository,
            SearchQueryRepository searchQueryRepository,
            QAInteractionRepository qaInteractionRepository,
            GenAiClient genAiClient,
            TextExtractor textExtractor,
            ObjectStorageService objectStorageService) {
        this.documentRepository = documentRepository;
        this.summaryRepository = summaryRepository;
        this.extractedEntityRepository = extractedEntityRepository;
        this.tagRepository = tagRepository;
        this.searchQueryRepository = searchQueryRepository;
        this.qaInteractionRepository = qaInteractionRepository;
        this.genAiClient = genAiClient;
        this.textExtractor = textExtractor;
        this.objectStorageService = objectStorageService;
    }

    @Transactional
    public Document createDocument(
            User owner,
            String fileName,
            String objectKey,
            String fileType,
            Long fileSize,
            String textContent) {
        FileNameValidator.validate(fileName);
        Document document = new Document(owner, fileName, objectKey, fileType, fileSize);
        document.setRawTextContent(textContent);
        document = documentRepository.save(document);

        if (textContent != null && !textContent.isBlank()) {
            processSummary(document);
            processEntities(document);
        }

        return document;
    }

    @Transactional
    public Document uploadDocument(User owner, MultipartFile file) {
        String fileName = file.getOriginalFilename();
        FileNameValidator.validate(fileName);
        String fileType = file.getContentType();
        Long fileSize = file.getSize();
        String objectKey = "/uploads/" + UUID.randomUUID() + "/" + fileName;

        String textContent = textExtractor.extract(file);

        Document document = new Document(owner, fileName, objectKey, fileType, fileSize);
        document.setRawTextContent(textContent);
        document = documentRepository.save(document);
        objectStorageService.upload(objectKey, file);

        processSummary(document);
        processEntities(document);

        return document;
    }

    private void processSummary(Document document) {
        try {
            GenAiClient.SummarizeResponse response = genAiClient.summarize(document.getObjectKey());
            Summary summary = new Summary(document, response.summary(), response.modelUsed());
            summaryRepository.save(summary);
            document.setSummary(summary);
        } catch (Exception e) {
            log.warn("GenAI summarization failed for document {}: {}", document.getId(), e.getMessage());
        }
    }

    private void processEntities(Document document) {
        try {
            GenAiClient.ExtractResponse response = genAiClient.extract(document.getObjectKey());
            for (GenAiClient.ExtractedEntityDto dto : response.entities()) {
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

    public List<Document> getDocuments(UUID ownerId) {
        return documentRepository.findByOwnerId(ownerId);
    }

    public Document getDocument(UUID id, UUID ownerId) {
        Document document =
                documentRepository.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
        if (!document.getOwner().getId().equals(ownerId)) {
            throw new DocumentNotFoundException(id);
        }
        return document;
    }

    @Transactional
    public void reprocessSummary(UUID id, UUID ownerId) {
        Document document = getDocument(id, ownerId);
        Summary existing = document.getSummary();
        if (existing != null) {
            document.setSummary(null);
            summaryRepository.delete(existing);
            summaryRepository.flush();
        }
        processSummary(document);
    }

    @Transactional
    public void reprocessEntities(UUID id, UUID ownerId) {
        Document document = getDocument(id, ownerId);
        extractedEntityRepository.deleteByDocumentId(id);
        extractedEntityRepository.flush();
        processEntities(document);
    }

    public Summary getDocumentSummary(UUID id, UUID ownerId) {
        Document document = getDocument(id, ownerId);
        return document.getSummary();
    }

    public List<ExtractedEntity> getDocumentEntities(UUID id, UUID ownerId) {
        Document document = getDocument(id, ownerId);
        return document.getExtractedEntities();
    }

    @Transactional(readOnly = true)
    public Optional<byte[]> getFileContent(UUID documentId, UUID ownerId) {
        Document document = getDocument(documentId, ownerId);
        try {
            return Optional.of(objectStorageService.download(document.getObjectKey()));
        } catch (Exception e) {
            log.warn("Failed to download file for document {}: {}", documentId, e.getMessage());
            return Optional.empty();
        }
    }

    @Transactional
    public void deleteDocument(UUID id, UUID ownerId) {
        if (!documentRepository.existsByIdAndOwnerId(id, ownerId)) {
            throw new DocumentNotFoundException(id);
        }
        documentRepository
                .findById(id)
                .map(Document::getObjectKey)
                .ifPresent(objectStorageService::delete);
        documentRepository.deleteById(id);
    }

    @Transactional
    public Document updateDocument(UUID id, @Valid UpdateDocumentRequest request, UUID ownerId) {
        Document document = getDocument(id, ownerId);
        if (request.fileName() != null) {
            FileNameValidator.validate(request.fileName());
            document.setFileName(request.fileName());
        }
        return documentRepository.save(document);
    }

    public List<Document> search(User user, String queryText) {
        List<Document> results =
                documentRepository.findByOwnerIdAndFileNameContainingIgnoreCase(user.getId(), queryText);

        SearchQuery searchQuery = new SearchQuery(user, queryText, results.size());
        searchQueryRepository.save(searchQuery);

        return results;
    }

    @Transactional
    public QAInteraction ask(User user, String question) {
        List<String> objectKeys =
                documentRepository.findByOwnerId(user.getId()).stream()
                        .map(Document::getObjectKey)
                        .toList();

        GenAiClient.AskResponse response = genAiClient.ask(question, objectKeys);

        QAInteraction interaction =
                new QAInteraction(
                        user, question, response.answer(), response.sourceObjectKeys(), response.modelUsed());
        return qaInteractionRepository.save(interaction);
    }

    @Transactional
    public void addTag(UUID documentId, UUID ownerId, String label, TagSource source) {
        Document document = getDocument(documentId, ownerId);

        Tag tag =
                tagRepository
                        .findByLabel(label)
                        .orElseGet(() -> tagRepository.save(new Tag(label, source)));

        document.addTag(tag);
        documentRepository.save(document);
    }

    @Transactional
    public void removeTag(UUID documentId, UUID ownerId, UUID tagId) {
        Document document = getDocument(documentId, ownerId);
        Tag tag = tagRepository.findById(tagId).orElse(null);
        if (tag != null) {
            document.removeTag(tag);
            documentRepository.save(document);
        }
    }

    public List<QAInteraction> getQAHistory(UUID userId) {
        return qaInteractionRepository.findByUserIdOrderByTimestampDesc(userId);
    }

    @Transactional
    public void deleteQAHistory(UUID userId) {
        qaInteractionRepository.deleteByUserId(userId);
    }

    public List<SearchQuery> getSearchHistory(UUID userId) {
        return searchQueryRepository.findByUserIdOrderByTimestampDesc(userId);
    }

    @Transactional
    public void deleteSearchHistory(UUID userId) {
        searchQueryRepository.deleteByUserId(userId);
    }

    public Map<String, Long> getTagsForUserWithCount(UUID userId) {
        return tagRepository.findTagCountsByOwnerId(userId).stream()
                .collect(
                        Collectors.toMap(
                                TagRepository.TagCountProjection::getLabel,
                                TagRepository.TagCountProjection::getDocumentCount));
    }
}
