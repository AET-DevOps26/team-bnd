package com.alexandria.user;

import com.alexandria.user.dto.UpdatePreferencesRequest;
import com.alexandria.user.exception.PreferencesSerializationException;
import com.alexandria.user.exception.UserDeletionException;
import com.alexandria.user.exception.UserNotFoundException;
import com.alexandria.user.integration.KnowledgeBaseClient;
import com.alexandria.user.integration.QAClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Account lifecycle and user preferences.
 *
 * <p>Users are provisioned lazily from OIDC claims on first sight. Preferences are stored
 * as a small JSON blob on the user row and fall back to sane defaults if missing or
 * corrupted. Deleting an account fans the delete out to knowledgebase-service and
 * qa-service before the local row is removed.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository repository;
    private final KnowledgeBaseClient knowledgeBaseClient;
    private final QAClient qaClient;
    private final ObjectMapper objectMapper;

    public UserService(UserRepository repository, KnowledgeBaseClient knowledgeBaseClient, QAClient qaClient, ObjectMapper objectMapper) {
        this.repository = repository;
        this.knowledgeBaseClient = knowledgeBaseClient;
        this.qaClient = qaClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns the user for the given OIDC subject, creating one on first login.
     * If a row already exists for the email (e.g. a pre-OIDC account), it is adopted by
     * attaching the subject rather than creating a duplicate.
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

    public UserPreferences getPreferences(User user) {
        return parsePreferences(user.getPreferences());
    }

    /**
     * Deletes the user and purges their data in knowledgebase-service and qa-service.
     * If either peer delete fails the local row is kept and a UserDeletionException is
     * thrown, so the operation can be retried until every service has converged.
     */
    public void deleteUser(UUID id) {
        // KB and QA services key their rows on the OIDC subject, not on the
        // local user UUID, so we fan out on the subject.
        String subject = repository.findById(id).orElseThrow(() -> new UserNotFoundException(id)).getOidcSubject();

        // Fan-out runs outside any @Transactional method so a hung peer can't
        // hold a Postgres connection and exhaust the Hikari pool. The peer
        // deletes are idempotent (keyed on the OIDC subject), so we try both
        // even if one fails and keep the local user row as a retry anchor: it
        // is dropped only after both peers succeed, and a partial failure is
        // surfaced so the whole delete can be retried until it converges.
        List<String> failedServices = new ArrayList<>();
        try {
            knowledgeBaseClient.deleteUserData(subject);
        } catch (Exception e) {
            log.warn("Failed to fan out user delete to knowledgebase-service for {}: {}", subject, e.getMessage());
            failedServices.add("knowledgebase-service");
        }
        try {
            qaClient.deleteUserData(subject);
        } catch (Exception e) {
            log.warn("Failed to fan out user delete to qa-service for {}: {}", subject, e.getMessage());
            failedServices.add("qa-service");
        }

        if (!failedServices.isEmpty()) {
            throw new UserDeletionException(id, failedServices);
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
        User currentUser = findByOidcSubject(oidcSubject).orElseThrow(() -> new UserNotFoundException(oidcSubject));

        UserPreferences currentPrefs = parsePreferences(currentUser.getPreferences());

        UserPreferences updatedPrefs = new UserPreferences(
                request.darkTheme() != null ? request.darkTheme() : currentPrefs.darkTheme(), request.language() != null ? request.language() : currentPrefs.language());

        try {
            String updatedJson = objectMapper.writeValueAsString(updatedPrefs);
            currentUser.setPreferences(updatedJson);
            this.repository.save(currentUser);
        } catch (JsonProcessingException e) {
            log.error(
                    "Failed to serialize preferences for user {}: {}", currentUser.getId(), e.getMessage(), e);
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
                    "Corrupted preferences JSON detected, falling back to defaults: {}", e.getMessage(), e);
            return UserPreferences.defaultPreferences();
        }
    }
}
