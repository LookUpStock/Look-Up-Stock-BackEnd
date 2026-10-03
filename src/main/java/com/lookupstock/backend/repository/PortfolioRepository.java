package com.lookupstock.backend.repository;

import com.lookupstock.backend.model.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
}