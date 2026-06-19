package com.alexandria.app.user;

import com.alexandria.app.exception.UserNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Manages user registration and account operations.
 */
@Service
public class UserService {
    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
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
     * Deletes user account by ID.
     *
     * @param id User UUID.
     * @throws UserNotFoundException If user does not exist.
     */
    public void deleteUser(UUID id) {
        if (!repository.existsById(id)) {
            throw new UserNotFoundException(id);
        }
        repository.deleteById(id);
    }
}
