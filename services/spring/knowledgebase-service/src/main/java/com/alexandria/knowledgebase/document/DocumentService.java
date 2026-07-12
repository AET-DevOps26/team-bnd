package com.alexandria.knowledgebase.document;

import com.alexandria.knowledgebase.exception.DocumentNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {
    private final DocumentRepository repository;

    public DocumentService(DocumentRepository repository) {
        this.repository = repository;
    }

    public Document save(Document document) {
        return repository.save(document);
    }

    public Document findById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
    }

    public Document findByIdAndOwner(UUID id, String ownerSubject) {
        Document document = findById(id);
        if (!document.getOwnerSubject().equals(ownerSubject)) {
            throw new DocumentNotFoundException(id);
        }
        return document;
    }

    public List<Document> findByOwnerSubject(String ownerSubject) {
        return repository.findByOwnerSubject(ownerSubject);
    }

    public List<String> findObjectKeysByOwnerSubject(String ownerSubject) {
        return repository.findObjectKeysByOwnerSubject(ownerSubject);
    }

    public List<Document> findByOwnerSubjectAndObjectKeyIn(String ownerSubject, Collection<String> objectKeys) {
        return repository.findByOwnerSubjectAndObjectKeyIn(ownerSubject, objectKeys);
    }

    public List<Document> searchByFileNameOrContent(String ownerSubject, String query) {
        return repository.searchByFileNameOrContent(ownerSubject, query);
    }

    public void delete(UUID id, String ownerSubject) {
        if (!repository.existsByIdAndOwnerSubject(id, ownerSubject)) {
            throw new DocumentNotFoundException(id);
        }
        repository.deleteById(id);
    }

    public void deleteAllByOwner(String ownerSubject) {
        repository.deleteByOwnerSubject(ownerSubject);
    }
}
