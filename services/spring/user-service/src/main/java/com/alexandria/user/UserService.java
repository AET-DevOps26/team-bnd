package com.alexandria.user;

import com.alexandria.user.dto.UpdatePreferencesRequest;
import com.alexandria.user.exception.PreferencesSerializationException;
import com.alexandria.user.exception.UserNotFoundException;
import com.alexandria.user.integration.KnowledgeBaseClient;
import com.alexandria.user.integration.QAClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository repository;
    private final KnowledgeBaseClient knowledgeBaseClient;
    private final QAClient qaClient;
    private final ObjectMapper objectMapper;

    public UserService(UserRepository repository,
                       KnowledgeBaseClient knowledgeBaseClient,
                       QAClient qaClient,
                       ObjectMapper objectMapper) {
        this.repository = repository;
        this.knowledgeBaseClient = knowledgeBaseClient;
        this.qaClient = qaClient;
        this.objectMapper = objectMapper;
    }

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

    public UserPreferences getPreferences(User user) {
        return parsePreferences(user.getPreferences());
    }

    @Transactional
    public void deleteUser(UUID id) {
        User user = repository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        // KB and QA services key their rows on the OIDC subject, not on the
        // local user UUID, so we fan out on the subject.
        String subject = user.getOidcSubject();
        try {
            knowledgeBaseClient.deleteUserData(subject);
        } catch (Exception e) {
            log.warn("Failed to fan out user delete to knowledgebase-service for {}: {}", subject, e.getMessage());
        }
        try {
            qaClient.deleteUserData(subject);
        } catch (Exception e) {
            log.warn("Failed to fan out user delete to qa-service for {}: {}", subject, e.getMessage());
        }

        repository.deleteById(id);
    }

    public record UserPreferences(boolean darkTheme, String language) {
        public static UserPreferences defaultPreferences() {
            return new UserPreferences(false, "en");
        }
    }

    @Transactional
    public UserPreferences updatePreferences(String oidcSubject, UpdatePreferencesRequest request) {
        User currentUser =
                findByOidcSubject(oidcSubject).orElseThrow(() -> new UserNotFoundException(oidcSubject));

        UserPreferences currentPrefs = parsePreferences(currentUser.getPreferences());

        UserPreferences updatedPrefs =
                new UserPreferences(
                        request.darkTheme() != null ? request.darkTheme() : currentPrefs.darkTheme(),
                        request.language() != null ? request.language() : currentPrefs.language());

        try {
            String updatedJson = objectMapper.writeValueAsString(updatedPrefs);
            currentUser.setPreferences(updatedJson);
            this.repository.save(currentUser);
        } catch (JsonProcessingException e) {
            log.error(
                    "Failed to serialize preferences for user {}: {}",
                    currentUser.getId(),
                    e.getMessage(),
                    e);
            throw new PreferencesSerializationException("Failed to serialize user preferences", e);
        }

        return updatedPrefs;
    }

    private UserPreferences parsePreferences(String json) {
        if (json == null || json.isBlank()) {
            return UserPreferences.defaultPreferences();
        }
        try {
            return objectMapper.readValue(json, UserPreferences.class);
        } catch (JsonProcessingException e) {
            log.error(
                    "Corrupted preferences JSON detected, falling back to defaults: {}",
                    e.getMessage(),
                    e);
            return UserPreferences.defaultPreferences();
        }
    }
}
