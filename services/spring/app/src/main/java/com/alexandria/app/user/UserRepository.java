package com.alexandria.app.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByOidcSubject(String oidcSubject);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByOidcSubject(String oidcSubject);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}