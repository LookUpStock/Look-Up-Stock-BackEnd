package com.lookupstock.backend.model;

import java.time.LocalDate;
import java.util.List;

public record EstimationResult(LocalDate historyFrom, LocalDate historyTo, List<Estimate> estimates) {

    public EstimationResult {
        estimates = List.copyOf(estimates);
    }

    public Estimate findByAssetId(Long assetId) {
        return estimates.stream()
                .filter(e -> e.asset().getId().equals(assetId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Keine Schätzung für assetId " + assetId + " vorhanden."));
    }
}