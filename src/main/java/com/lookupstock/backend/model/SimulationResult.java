package com.lookupstock.backend.model;

import java.util.List;

/**
 * Ergebnis einer Simulation; wird vom SimulationService erzeugt (GRASP Creator).
 *
 * @param parameters verwendete Parameter, inklusive des tatsächlich verwendeten Seeds
 * @param runtimeMs  Rechenzeit auf dem Server in Millisekunden (Nachweis NF-01)
 */
public record SimulationResult(
        Long portfolioId,
        SimulationParameters parameters,
        EstimationResult estimation,
        List<TimelinePoint> timeline,
        long runtimeMs) {
}
