package com.lookupstock.backend.model;

/**
 * Zusammenfassende Kennzahlen einer Simulation.
 * valueAtRisk95 ist der Verlust gegenüber den Einzahlungen im 5. Perzentil;
 * maxDrawdown ist der Median des größten zwischenzeitlichen Rückgangs je Pfad.
 */
public record SimulationMetrics(double totalContributions, double medianEndValue,
                                double p5EndValue, double p95EndValue,
                                double valueAtRisk95, double maxDrawdownMedian,
                                Double targetProbability) {
}
