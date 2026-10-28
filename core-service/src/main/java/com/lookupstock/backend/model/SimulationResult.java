package com.lookupstock.backend.model;

import java.util.List;

/**
 * Ergebnis einer Simulation; wird vom SimulationService erzeugt (GRASP Creator).
 *
 * @param parameters verwendete Parameter, inklusive des tatsächlich verwendeten Seeds
 * @param metrics   Kennzahlen am Ende des Anlagehorizonts (US-05, BE #71)
 * @param runtimeMs  Rechenzeit auf dem Server in Millisekunden (Nachweis NF-01)
 */
public record SimulationResult(
        Long portfolioId,
        SimulationParameters parameters,
        EstimationResult estimation,
        List<TimelinePoint> timeline,
        Metrics metrics,
        long runtimeMs) {
}
