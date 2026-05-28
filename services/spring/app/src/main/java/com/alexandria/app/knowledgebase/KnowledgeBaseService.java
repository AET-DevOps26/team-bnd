package com.alexandria.app.knowledgebase;

import com.alexandria.app.document.*;
import com.alexandria.app.exception.DocumentNotFoundException;
import com.alexandria.app.qa.QAInteraction;
import com.alexandria.app.qa.QAInteractionRepository;
import com.alexandria.app.search.SearchQuery;
import com.alexandria.app.search.SearchQueryRepository;
import com.alexandria.app.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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

    public KnowledgeBaseService(
            DocumentRepository documentRepository,
            SummaryRepository summaryRepository,
            ExtractedEntityRepository extractedEntityRepository,
            TagRepository tagRepository,
            SearchQueryRepository searchQueryRepository,
            QAInteractionRepository qaInteractionRepository,
            GenAiClient genAiClient) {
        this.documentRepository = documentRepository;
        this.summaryRepository = summaryRepository;
        this.extractedEntityRepository = extractedEntityRepository;
        this.tagRepository = tagRepository;
        this.searchQueryRepository = searchQueryRepository;
        this.qaInteractionRepository = qaInteractionRepository;
        this.genAiClient = genAiClient;
    }

    @Transactional
    public Document createDocument(User owner, String fileName, String filePath, String fileType, Long fileSize, String textContent) {
        Document document = new Document(owner, fileName, filePath, fileType, fileSize);
        document.setRawTextContent(textContent);
        document = documentRepository.save(document);

        if (textContent != null && !textContent.isBlank()) {
            processSummary(document, textContent);
            processEntities(document, textContent);
        }

        return document;
    }

    private void processSummary(Document document, String textContent) {
        try {
            GenAiClient.SummarizeResponse response = genAiClient.summarize(textContent);
            Summary summary = new Summary(document, response.summary(), response.modelUsed());
            summaryRepository.save(summary);
            document.setSummary(summary);
        } catch (Exception e) {
            log.warn("GenAI summarization failed for document {}: {}", document.getId(), e.getMessage());
        }
    }

    private void processEntities(Document document, String textContent) {
        try {
            GenAiClient.ExtractResponse response = genAiClient.extract(textContent);
            for (GenAiClient.ExtractedEntityDto dto : response.entities()) {
                ExtractedEntity entity = new ExtractedEntity(document, dto.name(), dto.type(), dto.confidence());
                extractedEntityRepository.save(entity);
                document.getExtractedEntities().add(entity);
            }
        } catch (Exception e) {
            log.warn("GenAI entity extraction failed for document {}: {}", document.getId(), e.getMessage());
        }
    }

    public List<Document> getDocuments(UUID ownerId) {
        return documentRepository.findByOwnerId(ownerId);
    }

    public Document getDocument(UUID id, UUID ownerId) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
        if (!document.getOwner().getId().equals(ownerId)) {
            throw new DocumentNotFoundException(id);
        }
        return document;
    }

    @Transactional
    public void deleteDocument(UUID id, UUID ownerId) {
        if (!documentRepository.existsByIdAndOwnerId(id, ownerId)) {
            throw new DocumentNotFoundException(id);
        }
        documentRepository.deleteById(id);
    }

    public List<Document> search(User user, String queryText) {
        List<Document> results = documentRepository.findByOwnerIdAndFileNameContainingIgnoreCase(
                user.getId(), queryText);

        SearchQuery searchQuery = new SearchQuery(user, queryText, results.size());
        searchQueryRepository.save(searchQuery);

        return results;
    }

    @Transactional
    public QAInteraction ask(User user, String question) {
        List<UUID> documentIds = documentRepository.findByOwnerId(user.getId())
                .stream()
                .map(Document::getId)
                .toList();

        GenAiClient.AskResponse response = genAiClient.ask(question, documentIds);

        QAInteraction interaction = new QAInteraction(
                user,
                question,
                response.answer(),
                response.sourceDocumentIds(),
                response.modelUsed()
        );
        return qaInteractionRepository.save(interaction);
    }

    @Transactional
    public void addTag(UUID documentId, UUID ownerId, String label, TagSource source) {
        Document document = getDocument(documentId, ownerId);

        Tag tag = tagRepository.findByLabel(label)
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

    public List<SearchQuery> getSearchHistory(UUID userId) {
        return searchQueryRepository.findByUserIdOrderByTimestampDesc(userId);
    }
}
