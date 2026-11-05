package com.lookupstock.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.lookupstock.backend.model.AuthToken;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    // Fetch the user eagerly to avoid LazyInitializationException outside the transaction.
    @EntityGraph(attributePaths = "user")
    Optional<AuthToken> findByToken(String token);

    long deleteByToken(String token);
}