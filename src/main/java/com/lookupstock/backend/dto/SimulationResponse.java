package com.lookupstock.backend.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.lookupstock.backend.model.Metrics;
import com.lookupstock.backend.model.SavedSimulation;
import com.lookupstock.backend.model.SimulationParameters;
import com.lookupstock.backend.model.SimulationResult;
import com.lookupstock.backend.model.TimelinePoint;

/**
 * Antwort von POST /api/v1/simulations und POST /api/v1/simulations/{id}/details laut API-Design.
 * id, name und createdAt sind null, wenn die Simulation nicht gespeichert wurde (save: false).
 */
public record SimulationResponse(
        Long id,
        String name,
        Long portfolioId,
        ParametersResponse parameters,
        EstimatesResponse estimates,
        List<TimelinePoint> timeline,
        Metrics metrics,
        long runtimeMs,
        Instant createdAt) {

    public record ParametersResponse(double startCapital, double monthlyContribution,
                                     int horizonYears, int paths, long seed, Double targetAmount) {
    }

    public record EstimatesResponse(LocalDate historyFrom, LocalDate historyTo, List<AssetEstimate> assets) {
    }

    public record AssetEstimate(Long assetId, double annualReturn, double annualVolatility) {
    }

    // Frisch gerechnete, nicht gespeicherte Simulation
    public static SimulationResponse from(SimulationResult result) {
        SimulationParameters p = result.parameters();
        return new SimulationResponse(
                null,
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
                result.metrics(),
                result.runtimeMs(),
                null);
    }

    // Gespeicherte Simulation: Parameter aus den Spalten, Ergebnis aus der JSON-Spalte, ohne Neuberechnung
    public static SimulationResponse from(SavedSimulation saved, StoredSimulationResult stored) {
        return new SimulationResponse(
                saved.getId(),
                saved.getName(),
                saved.getPortfolio().getId(),
                new ParametersResponse(
                        saved.getStartCapital().doubleValue(),
                        saved.getMonthlyContribution().doubleValue(),
                        saved.getHorizonYears(), saved.getPaths(), saved.getSeed(),
                        saved.getTargetAmount() == null ? null : saved.getTargetAmount().doubleValue()),
                stored.estimates(),
                stored.timeline(),
                stored.metrics(),
                saved.getRuntimeMs(),
                saved.getCreatedAt());
    }
}