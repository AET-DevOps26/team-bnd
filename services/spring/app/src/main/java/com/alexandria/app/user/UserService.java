package com.alexandria.app.user;

import com.alexandria.app.user.UserRepository;
import com.alexandria.app.dto.RegisterRequest;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserService {
    private final UserRepository repository;
    private final PasswordEncoder encoder;

    public UserService(UserRepository repository, PasswordEncoder encoder) {
        this.repository = repository;
        this.encoder = encoder;
    }

    public User register(RegisterRequest request) {
        if (repository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("Username already taken.");
        }

        if (repository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already registered.");
        }

        User user = new User(request.username(), request.email(), encoder.encode(request.password()));
        return repository.save(user);
    }

    public void deleteUser(UUID id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("User with given id does not exist.");
        }

        repository.deleteById(id);
    }
}