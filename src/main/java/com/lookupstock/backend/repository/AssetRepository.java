package com.lookupstock.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lookupstock.backend.model.Asset;

public interface AssetRepository extends JpaRepository<Asset, Long> {

    Optional<Asset> findBySymbolAndMicCode(String symbol, String micCode);

    Optional<Asset> findFirstBySymbolIgnoreCaseOrderByIdAsc(String symbol);
}
