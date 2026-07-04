package com.alexandria.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void integration_userRepo_saveAndFindByIdWorks() {
        User user = new User("oidc_123456", "testuser", "test@example.com");

        User saved = userRepository.save(user);

        assertThat(saved.getId()).isNotNull();
        Optional<User> found = userRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getOidcSubject()).isEqualTo("oidc_123456");
        assertThat(found.get().getUsername()).isEqualTo("testuser");
        assertThat(found.get().getEmail()).isEqualTo("test@example.com");
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void integration_userRepo_findByOidcSubjectWorks() {
        User user = new User("oidc_findme", "findme", "findme@example.com");
        userRepository.save(user);

        Optional<User> found = userRepository.findByOidcSubject("oidc_findme");

        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("findme");
    }

    @Test
    void integration_userRepo_findByUsernameWorks() {
        User user = new User("oidc_789012", "findme", "findme@example.com");
        userRepository.save(user);

        Optional<User> found = userRepository.findByUsername("findme");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("findme@example.com");
    }

    @Test
    void integration_userRepo_findByUsernameNotFoundReturnsEmpty() {
        Optional<User> found = userRepository.findByUsername("nonexistent");

        assertThat(found).isEmpty();
    }

    @Test
    void integration_userRepo_findByEmailWorks() {
        User user = new User("oidc_345678", "emailuser", "unique@example.com");
        userRepository.save(user);

        Optional<User> found = userRepository.findByEmail("unique@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("emailuser");
    }

    @Test
    void integration_userRepo_existsByOidcSubjectReturnsCorrectValue() {
        User user = new User("oidc_existing", "existing", "existing@example.com");
        userRepository.save(user);

        assertThat(userRepository.existsByOidcSubject("oidc_existing")).isTrue();
        assertThat(userRepository.existsByOidcSubject("oidc_nonexistent")).isFalse();
    }

    @Test
    void integration_userRepo_existsByUsernameReturnsCorrectValue() {
        User user = new User("oidc_usernametest", "existing", "existing@example.com");
        userRepository.save(user);

        assertThat(userRepository.existsByUsername("existing")).isTrue();
        assertThat(userRepository.existsByUsername("nonexistent")).isFalse();
    }

    @Test
    void integration_userRepo_existsByEmailReturnsCorrectValue() {
        User user = new User("oidc_emailtest", "emailtest", "exists@example.com");
        userRepository.save(user);

        assertThat(userRepository.existsByEmail("exists@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("notexists@example.com")).isFalse();
    }

    @Test
    void integration_userRepo_duplicateOidcSubjectThrowsConstraintViolation() {
        User user1 = new User("oidc_duplicate", "first", "first@example.com");
        userRepository.saveAndFlush(user1);

        User user2 = new User("oidc_duplicate", "second", "second@example.com");

        assertThatThrownBy(() -> userRepository.saveAndFlush(user2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void integration_userRepo_duplicateUsernameThrowsConstraintViolation() {
        User user1 = new User("oidc_user1", "duplicate", "first@example.com");
        userRepository.saveAndFlush(user1);

        User user2 = new User("oidc_user2", "duplicate", "second@example.com");

        assertThatThrownBy(() -> userRepository.saveAndFlush(user2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void integration_userRepo_duplicateEmailThrowsConstraintViolation() {
        User user1 = new User("oidc_email1", "first", "duplicate@example.com");
        userRepository.saveAndFlush(user1);

        User user2 = new User("oidc_email2", "second", "duplicate@example.com");

        assertThatThrownBy(() -> userRepository.saveAndFlush(user2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void integration_userRepo_deleteRemovesUser() {
        User user = new User("oidc_todelete", "todelete", "delete@example.com");
        User saved = userRepository.save(user);

        userRepository.deleteById(saved.getId());

        assertThat(userRepository.findById(saved.getId())).isEmpty();
    }
}
