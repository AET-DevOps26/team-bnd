package com.alexandria.app.user;

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
        User user = new User("testuser", "test@example.com", "hashed_password");

        User saved = userRepository.save(user);

        assertThat(saved.getId()).isNotNull();
        Optional<User> found = userRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("testuser");
        assertThat(found.get().getEmail()).isEqualTo("test@example.com");
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void integration_userRepo_findByUsernameWorks() {
        User user = new User("findme", "findme@example.com", "hashed_password");
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
        User user = new User("emailuser", "unique@example.com", "hashed_password");
        userRepository.save(user);

        Optional<User> found = userRepository.findByEmail("unique@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("emailuser");
    }

    @Test
    void integration_userRepo_existsByUsernameReturnsCorrectValue() {
        User user = new User("existing", "existing@example.com", "hashed_password");
        userRepository.save(user);

        assertThat(userRepository.existsByUsername("existing")).isTrue();
        assertThat(userRepository.existsByUsername("nonexistent")).isFalse();
    }

    @Test
    void integration_userRepo_existsByEmailReturnsCorrectValue() {
        User user = new User("emailtest", "exists@example.com", "hashed_password");
        userRepository.save(user);

        assertThat(userRepository.existsByEmail("exists@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("notexists@example.com")).isFalse();
    }

    @Test
    void integration_userRepo_duplicateUsernameThrowsConstraintViolation() {
        User user1 = new User("duplicate", "first@example.com", "hashed_password");
        userRepository.saveAndFlush(user1);

        User user2 = new User("duplicate", "second@example.com", "hashed_password");

        assertThatThrownBy(() -> userRepository.saveAndFlush(user2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void integration_userRepo_duplicateEmailThrowsConstraintViolation() {
        User user1 = new User("first", "duplicate@example.com", "hashed_password");
        userRepository.saveAndFlush(user1);

        User user2 = new User("second", "duplicate@example.com", "hashed_password");

        assertThatThrownBy(() -> userRepository.saveAndFlush(user2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void integration_userRepo_deleteRemovesUser() {
        User user = new User("todelete", "delete@example.com", "hashed_password");
        User saved = userRepository.save(user);

        userRepository.deleteById(saved.getId());

        assertThat(userRepository.findById(saved.getId())).isEmpty();
    }
}
