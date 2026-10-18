package com.lookupstock.backend.model;

import java.util.List;

/**
 * Ergebnis eines einzelnen Simulationslaufs aus SimulationService.run (ADR-05).
 *
 * @param timeline  5., 50. und 95. Perzentil je Monat (Fächerdiagramm)
 * @param metrics   Kennzahlen am Ende des Anlagehorizonts (US-05)
 * @param endValues Portfoliowert am Ende je Pfad, in Pfad-Reihenfolge; Grundlage für den paarweisen Vergleich
 */
public record SimulationRun(List<TimelinePoint> timeline, Metrics metrics, double[] endValues) {
}
