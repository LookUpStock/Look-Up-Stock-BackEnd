package com.lookupstock.backend.dto;

import java.time.LocalDate;
import java.util.List;

import com.lookupstock.backend.model.SimulationParameters;
import com.lookupstock.backend.model.SimulationResult;
import com.lookupstock.backend.model.TimelinePoint;

/**
 * Antwort von POST /api/v1/simulations laut API-Design.
 * id ist null, solange Simulationen nicht gespeichert werden (US-08, Sprint 3);
 * metrics folgt mit US-05 (Sprint 3).
 */
public record SimulationResponse(
        Long id,
        Long portfolioId,
        ParametersResponse parameters,
        EstimatesResponse estimates,
        List<TimelinePoint> timeline,
        long runtimeMs) {

    public record ParametersResponse(double startCapital, double monthlyContribution,
                                     int horizonYears, int paths, long seed, Double targetAmount) {
    }

    public record EstimatesResponse(LocalDate historyFrom, LocalDate historyTo, List<AssetEstimate> assets) {
    }

    public record AssetEstimate(Long assetId, double annualReturn, double annualVolatility) {
    }

    public static SimulationResponse from(SimulationResult result) {
        SimulationParameters p = result.parameters();
        return new SimulationResponse(
                null,
                result.portfolioId(),
                new ParametersResponse(p.startCapital(), p.monthlyContribution(),
                        p.horizonYears(), p.paths(), p.seed(), p.targetAmount()),
                new EstimatesResponse(
                        result.estimation().historyFrom(),
                        result.estimation().historyTo(),
                        result.estimation().estimates().stream()
                                .map(e -> new AssetEstimate(e.asset().getId(), e.annualReturn(), e.annualVolatility()))
                                .toList()),
                result.timeline(),
                result.runtimeMs());
    }
}
