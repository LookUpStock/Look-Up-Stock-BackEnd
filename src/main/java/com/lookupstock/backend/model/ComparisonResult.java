package com.lookupstock.backend.model;

import java.time.Instant;
import java.util.List;

/**
 * Ergebnis eines Vergleichs; wird vom ComparisonService erzeugt (GRASP Creator, ADR-05).
 *
 * @param parameters gemeinsame Parameter aller Einträge, inklusive des verwendeten Seeds
 * @param estimation gemeinsame Schätzung mit einem Schätzzeitraum für alle Anlagen (E17)
 * @param items      Ergebnis je Eintrag in der Reihenfolge der Anfrage
 * @param pairwise   paarweiser Vergleich der Endwerte (bei zwei Einträgen genau ein Paar)
 * @param runtimeMs  Rechenzeit auf dem Server in Millisekunden
 * @param createdAt  Zeitpunkt der Berechnung
 */
public record ComparisonResult(
        SimulationParameters parameters,
        EstimationResult estimation,
        List<Item> items,
        List<Pairwise> pairwise,
        long runtimeMs,
        Instant createdAt) {

    public ComparisonResult {
        items = List.copyOf(items);
        pairwise = List.copyOf(pairwise);
    }

    public record Item(String label, Long portfolioId, Long assetId, List<TimelinePoint> timeline, Metrics metrics) {
    }

    /**
     * @param a                        Index des ersten Eintrags
     * @param b                        Index des zweiten Eintrags
     * @param probabilityAOutperformsB Anteil der Pfade, in denen Eintrag a am Ende mehr wert ist als Eintrag b
     */
    public record Pairwise(int a, int b, double probabilityAOutperformsB) {
    }
}
