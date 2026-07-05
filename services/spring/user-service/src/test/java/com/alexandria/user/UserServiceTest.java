package com.alexandria.user;

import com.alexandria.user.dto.UpdatePreferencesRequest;
import com.alexandria.user.exception.UserDeletionException;
import com.alexandria.user.exception.UserNotFoundException;
import com.alexandria.user.integration.KnowledgeBaseClient;
import com.alexandria.user.integration.QAClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private KnowledgeBaseClient knowledgeBaseClient;

    @Mock
    private QAClient qaClient;

    @Mock
    private ObjectMapper objectMapper;

    private UserService userService;

    @BeforeEach
    void setupUserService() {
        userService = new UserService(userRepository, knowledgeBaseClient, qaClient, objectMapper);
    }

    @Test
    void unit_user_findOrCreateReturnsExistingUser() {
        String oidcSubject = "auth0|123456";
        User existingUser = new User(oidcSubject, "testuser", "test@example.com");
        when(userRepository.findByOidcSubject(oidcSubject)).thenReturn(Optional.of(existingUser));

        User result = userService.findOrCreateByOidcSubject(oidcSubject, "testuser", "test@example.com");

        assertThat(result.getOidcSubject()).isEqualTo(oidcSubject);
        assertThat(result.getUsername()).isEqualTo("testuser");
        verify(userRepository, never()).save(any());
    }

    @Test
    void unit_user_findOrCreateCreatesNewUser() {
        String oidcSubject = "auth0|789012";
        when(userRepository.findByOidcSubject(oidcSubject)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.findOrCreateByOidcSubject(oidcSubject, "newuser", "new@example.com");

        assertThat(result.getOidcSubject()).isEqualTo(oidcSubject);
        assertThat(result.getUsername()).isEqualTo("newuser");
        assertThat(result.getEmail()).isEqualTo("new@example.com");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void unit_user_findByOidcSubjectReturnsUser() {
        String oidcSubject = "auth0|123456";
        User user = new User(oidcSubject, "testuser", "test@example.com");
        when(userRepository.findByOidcSubject(oidcSubject)).thenReturn(Optional.of(user));

        Optional<User> result = userService.findByOidcSubject(oidcSubject);

        assertThat(result).isPresent();
        assertThat(result.get().getOidcSubject()).isEqualTo(oidcSubject);
    }

    @Test
    void unit_user_findByOidcSubjectReturnsEmptyWhenNotFound() {
        when(userRepository.findByOidcSubject("nonexistent")).thenReturn(Optional.empty());

        Optional<User> result = userService.findByOidcSubject("nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    void unit_user_deleteSuccessFansOutToPeerServices() {
        UUID userId = UUID.randomUUID();
        User user = new User("oidc|123", "testuser", "test@example.com");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        userService.deleteUser(userId);

        verify(knowledgeBaseClient).deleteUserData("oidc|123");
        verify(qaClient).deleteUserData("oidc|123");
        verify(userRepository).deleteById(userId);
    }

    @Test
    void unit_user_deleteAttemptsBothPeersButAbortsWhenOneFails() {
        UUID userId = UUID.randomUUID();
        User user = new User("oidc|123", "testuser", "test@example.com");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        doThrow(new RuntimeException("kb down")).when(knowledgeBaseClient).deleteUserData(any());

        assertThatThrownBy(() -> userService.deleteUser(userId))
                .isInstanceOf(UserDeletionException.class);

        verify(qaClient).deleteUserData("oidc|123");
        verify(userRepository, never()).deleteById(any());
    }

    @Test
    void unit_user_deleteNotFoundThrowsException() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser(userId))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining(userId.toString());

        verify(userRepository, never()).deleteById(any());
        verify(knowledgeBaseClient, never()).deleteUserData(any());
    }

    @Test
    void unit_user_updatePreferencesUpdatesFields() throws JsonProcessingException {
        String oidcSubject = "auth0|123";
        User user = new User(oidcSubject, "testuser", "test@example.com");
        user.setPreferences("{\"darkTheme\":false,\"language\":\"en\"}");
        when(userRepository.findByOidcSubject(oidcSubject)).thenReturn(Optional.of(user));

        ObjectMapper realMapper = new ObjectMapper();
        userService = new UserService(userRepository, knowledgeBaseClient, qaClient, realMapper);

        UpdatePreferencesRequest request = new UpdatePreferencesRequest(true, "de");
        UserService.UserPreferences result = userService.updatePreferences(oidcSubject, request);

        assertThat(result.darkTheme()).isTrue();
        assertThat(result.language()).isEqualTo("de");
        verify(userRepository).save(user);
    }

    @Test
    void unit_user_updatePreferencesPartialUpdate() throws JsonProcessingException {
        String oidcSubject = "auth0|123";
        User user = new User(oidcSubject, "testuser", "test@example.com");
        user.setPreferences("{\"darkTheme\":true,\"language\":\"en\"}");
        when(userRepository.findByOidcSubject(oidcSubject)).thenReturn(Optional.of(user));

        ObjectMapper realMapper = new ObjectMapper();
        userService = new UserService(userRepository, knowledgeBaseClient, qaClient, realMapper);

        UpdatePreferencesRequest request = new UpdatePreferencesRequest(null, "fr");
        UserService.UserPreferences result = userService.updatePreferences(oidcSubject, request);

        assertThat(result.darkTheme()).isTrue();
        assertThat(result.language()).isEqualTo("fr");
    }

    @Test
    void unit_user_updatePreferencesThrowsWhenUserNotFound() {
        when(userRepository.findByOidcSubject("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> userService.updatePreferences("unknown", new UpdatePreferencesRequest(true, "en")))
                .isInstanceOf(UserNotFoundException.class);
    }
}
