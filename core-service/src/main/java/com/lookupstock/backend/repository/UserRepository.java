package com.lookupstock.backend.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lookupstock.backend.model.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // Nutzerkonten, die vor dem Zeitpunkt angelegt wurden (Kennzahl „Nutzerkonten insgesamt“, E39)
    long countByCreatedAtBefore(Instant time);
}
