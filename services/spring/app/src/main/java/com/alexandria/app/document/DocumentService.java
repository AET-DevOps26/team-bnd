package com.alexandria.app.document;

import com.alexandria.app.exception.DocumentNotFoundException;
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

    public List<Document> findByOwnerId(UUID ownerId) {
        return repository.findByOwnerId(ownerId);
    }

    public void delete(UUID id, UUID ownerId) {
        if (!repository.existsByIdAndOwnerId(id, ownerId)) {
            throw new DocumentNotFoundException(id);
        }
        repository.deleteById(id);
    }
}
