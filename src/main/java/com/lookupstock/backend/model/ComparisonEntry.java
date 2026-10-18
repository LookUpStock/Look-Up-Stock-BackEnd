package com.lookupstock.backend.model;

/**
 * Ein Eintrag im Vergleich (US-07): entweder ein eigenes Portfolio oder eine einzelne Anlage.
 *
 * @param label       Bezeichnung des Eintrags; leer = Name des Portfolios bzw. der Anlage
 * @param portfolioId ID eines eigenen Portfolios, oder null
 * @param assetId     ID einer Anlage aus der Suche, oder null
 */
public record ComparisonEntry(String label, Long portfolioId, Long assetId) {
}
