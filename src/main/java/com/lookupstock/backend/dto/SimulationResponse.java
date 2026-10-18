package com.lookupstock.backend.dto;

import java.time.LocalDate;
import java.util.List;

import com.lookupstock.backend.model.EstimationResult;
import com.lookupstock.backend.model.Metrics;
import com.lookupstock.backend.model.SimulationParameters;
import com.lookupstock.backend.model.SimulationResult;
import com.lookupstock.backend.model.TimelinePoint;

/**
 * Antwort von POST /api/v1/simulations laut API-Design.
 * id ist null, solange Simulationen nicht gespeichert werden (US-08, Sprint 3);
 */
public record SimulationResponse(
        Long id,
        Long portfolioId,
        ParametersResponse parameters,
        EstimatesResponse estimates,
        List<TimelinePoint> timeline,
        Metrics metrics,
        long runtimeMs) {

    public record ParametersResponse(double startCapital, double monthlyContribution,
                                     int horizonYears, int paths, long seed, Double targetAmount) {

        // auch vom Vergleich genutzt (ComparisonResponse)
        public static ParametersResponse from(SimulationParameters p) {
            return new ParametersResponse(p.startCapital(), p.monthlyContribution(),
                    p.horizonYears(), p.paths(), p.seed(), p.targetAmount());
        }
    }

    public record EstimatesResponse(LocalDate historyFrom, LocalDate historyTo, List<AssetEstimate> assets) {

        // auch vom Vergleich genutzt (ComparisonResponse)
        public static EstimatesResponse from(EstimationResult estimation) {
            return new EstimatesResponse(
                    estimation.historyFrom(),
                    estimation.historyTo(),
                    estimation.estimates().stream()
                            .map(e -> new AssetEstimate(e.asset().getId(), e.annualReturn(), e.annualVolatility()))
                            .toList());
        }
    }

    public record AssetEstimate(Long assetId, double annualReturn, double annualVolatility) {
    }

    public static SimulationResponse from(SimulationResult result) {
        return new SimulationResponse(
                null,
                result.portfolioId(),
                ParametersResponse.from(result.parameters()),
                EstimatesResponse.from(result.estimation()),
                result.timeline(),
                result.metrics(),
                result.runtimeMs());
    }
}
