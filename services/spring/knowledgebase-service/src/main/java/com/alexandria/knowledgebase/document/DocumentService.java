package com.alexandria.knowledgebase.document;

import com.alexandria.knowledgebase.exception.DocumentNotFoundException;
import org.springframework.stereotype.Service;

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

    public List<Document> findByOwnerSubject(String ownerSubject) {
        return repository.findByOwnerSubject(ownerSubject);
    }

    public void delete(UUID id, String ownerSubject) {
        if (!repository.existsByIdAndOwnerSubject(id, ownerSubject)) {
            throw new DocumentNotFoundException(id);
        }
        repository.deleteById(id);
    }
}
