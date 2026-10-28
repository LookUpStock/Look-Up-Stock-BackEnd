package com.lookupstock.backend.repository;

import com.lookupstock.backend.model.Portfolio;
import com.lookupstock.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    List<Portfolio> findAllByOwnerOrderByCreatedAtDesc(User owner);

    Optional<Portfolio> findByIdAndOwner(Long id, User owner);
}