package com.lookupstock.backend.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Ergebnis der Parameterschätzung für alle Anlagen eines Portfolios.
 * Alle Anlagen werden im selben Schätzzeitraum geschätzt.
 *
 * @param historyFrom erster Monat des gemeinsamen Schätzzeitraums
 * @param historyTo   letzter Monat des gemeinsamen Schätzzeitraums
 * @param estimates   Schätzung je Anlage
 */
public record EstimationResult(LocalDate historyFrom, LocalDate historyTo, List<Estimate> estimates) {

    // Kompakter Konstruktor: macht die Liste unveränderlich
    public EstimationResult {
        estimates = List.copyOf(estimates);
    }

    /**
     * Liefert die Schätzung für eine bestimmte Anlage.
     *
     * @param assetId ID der gesuchten Anlage
     * @return die passende Schätzung
     * @throws IllegalArgumentException wenn für die Anlage keine Schätzung vorliegt
     */
    public Estimate findByAssetId(Long assetId) {
        return estimates.stream()
                .filter(e -> e.asset().getId().equals(assetId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Keine Schätzung für assetId " + assetId + " vorhanden."));
    }
}