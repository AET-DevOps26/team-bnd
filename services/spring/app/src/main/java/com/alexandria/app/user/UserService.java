package com.alexandria.app.user;

import com.alexandria.app.document.Document;
import com.alexandria.app.document.DocumentRepository;
import com.alexandria.app.exception.UserNotFoundException;
import com.alexandria.app.knowledgebase.ObjectStorageService;
import com.alexandria.app.qa.QAInteractionRepository;
import com.alexandria.app.search.SearchQueryRepository;
import com.alexandria.app.user.dto.UpdatePreferencesRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages user registration and account operations.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository repository;
    private final DocumentRepository documentRepository;
    private final QAInteractionRepository qaInteractionRepository;
    private final SearchQueryRepository searchQueryRepository;
    private final ObjectStorageService objectStorageService;
    private final ObjectMapper objectMapper;

    public UserService(
            UserRepository repository,
            DocumentRepository documentRepository,
            QAInteractionRepository qaInteractionRepository,
            SearchQueryRepository searchQueryRepository,
            ObjectStorageService objectStorageService,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.documentRepository = documentRepository;
        this.qaInteractionRepository = qaInteractionRepository;
        this.searchQueryRepository = searchQueryRepository;
        this.objectStorageService = objectStorageService;
        this.objectMapper = objectMapper;
    }

    /**
     * Finds existing user or creates new one if not present
     *
     * @param oidcSubject OIDC subject of user.
     * @param username    Username of user.
     * @param email       E-mail of user.
     * @return
     */
    @Transactional
    public User findOrCreateByOidcSubject(String oidcSubject, String username, String email) {
        Optional<User> bySubject = repository.findByOidcSubject(oidcSubject);
        if (bySubject.isPresent()) {
            return bySubject.get();
        }

        Optional<User> byEmail = repository.findByEmail(email);
        if (byEmail.isPresent()) {
            User user = byEmail.get();
            user.setOidcSubject(oidcSubject);
            return repository.save(user);
        }

        return repository.save(new User(oidcSubject, username, email));
    }

    public Optional<User> findByOidcSubject(String oidcSubject) {
        return repository.findByOidcSubject(oidcSubject);
    }

    /**
     * Deletes user account by ID, including all associated data.
     *
     * @param id User UUID.
     * @throws UserNotFoundException If user does not exist.
     */
    @Transactional
    public void deleteUser(UUID id) {
        if (!repository.existsById(id)) {
            throw new UserNotFoundException(id);
        }

        List<Document> documents = documentRepository.findByOwnerId(id);
        for (Document doc : documents) {
            try {
                objectStorageService.delete(doc.getObjectKey());
            } catch (Exception e) {
                log.warn("Failed to delete S3 object for document {}: {}", doc.getId(), e.getMessage());
            }
        }
        documentRepository.deleteByOwnerId(id);

        qaInteractionRepository.deleteByUserId(id);
        searchQueryRepository.deleteByUserId(id);

        repository.deleteById(id);
    }

    public record UserPreferences(
            // additional fields may be added in later API versions
            boolean darkTheme,
            String language
    ) {
        // provide sensible defaults for new users
        public static UserPreferences defaultPreferences() {
            return new UserPreferences(false, "en");
        }
    }

    @Transactional
    public UserPreferences updatePreferences(String oidcSubject, UpdatePreferencesRequest request) {
        User currentUser = findByOidcSubject(oidcSubject)
                .orElseThrow(() -> new UserNotFoundException(oidcSubject));

        UserPreferences currentPrefs = parsePreferences(currentUser.getPreferences());

        UserPreferences updatedPrefs = new UserPreferences(
                request.darkTheme() != null ? request.darkTheme() : currentPrefs.darkTheme(),
                request.language() != null ? request.language() : currentPrefs.language()
        );

        try {
            String updatedJson = objectMapper.writeValueAsString(updatedPrefs);
            currentUser.setPreferences(updatedJson);
            this.repository.save(currentUser);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize user preferences", e);
        }

        // 5. Return the updated domain object to the controller
        return updatedPrefs;
    }

    private UserPreferences parsePreferences(String json) {
        if (json == null || json.isBlank()) {
            return UserPreferences.defaultPreferences();
        }
        try {
            return objectMapper.readValue(json, UserPreferences.class);
        } catch (JsonProcessingException e) {
            // Fallback to defaults if the database JSON is somehow corrupted
            // Alternatively, you could throw a custom exception here
            return UserPreferences.defaultPreferences();
        }
    }
}
