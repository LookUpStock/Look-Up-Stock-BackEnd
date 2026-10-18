package com.lookupstock.backend.dto;

import java.util.List;

import com.lookupstock.backend.model.ComparisonEntry;

/**
 * Body von POST /api/v1/comparisons (US-07). Die Parameter haben dasselbe Format und dieselben Prüfungen
 * wie bei POST /simulations (SimulationRequest.Parameters).
 * save und name sind für das Speichern vorgesehen (US-08, BE #68) und werden noch nicht ausgewertet.
 */
public record ComparisonRequest(String token, List<Item> items, SimulationRequest.Parameters parameters,
                                Boolean save, String name) {

    public record Item(String label, Long portfolioId, Long assetId) {
    }

    /** null bleibt null; die Anzahl prüft der ComparisonService (400). */
    public List<ComparisonEntry> toEntries() {
        if (items == null) {
            return null;
        }
        // Stream.toList erlaubt null-Elemente; ein leerer Eintrag wird im Service als ungültig gemeldet
        return items.stream()
                .map(item -> item == null ? null : new ComparisonEntry(item.label(), item.portfolioId(), item.assetId()))
                .toList();
    }
}
