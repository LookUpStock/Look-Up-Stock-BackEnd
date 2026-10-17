package com.lookupstock.backend.dto;

import java.time.LocalDate;
import java.util.List;

import com.lookupstock.backend.model.SimulationParameters;
import com.lookupstock.backend.model.SimulationResult;
import com.lookupstock.backend.model.TimelinePoint;
import com.lookupstock.backend.model.SimulationMetrics;

/**
 * Antwort von POST /api/v1/simulations laut API-Design.
 * id ist null, solange Simulationen nicht gespeichert werden (US-08, Sprint 3);
 * metrics enthält die aus den Simulationspfaden berechneten Kennzahlen.
 */
public record SimulationResponse(
        Long id,
        Long portfolioId,
        ParametersResponse parameters,
        EstimatesResponse estimates,
        List<TimelinePoint> timeline,
        MetricsResponse metrics,
        long runtimeMs) {

    public record ParametersResponse(double startCapital, double monthlyContribution,
                                     int horizonYears, int paths, long seed) {
    }

    public record EstimatesResponse(LocalDate historyFrom, LocalDate historyTo, List<AssetEstimate> assets) {
    }

    public record MetricsResponse(double totalContributions, double medianEndValue,
                                  double p5EndValue, double p95EndValue,
                                  double valueAtRisk95, double maxDrawdownMedian,
                                  Double targetProbability) {
        static MetricsResponse from(SimulationMetrics metrics) {
            return new MetricsResponse(metrics.totalContributions(), metrics.medianEndValue(),
                    metrics.p5EndValue(), metrics.p95EndValue(), metrics.valueAtRisk95(),
                    metrics.maxDrawdownMedian(), metrics.targetProbability());
        }
    }

    public record AssetEstimate(Long assetId, double annualReturn, double annualVolatility) {
    }

    public static SimulationResponse from(SimulationResult result) {
        SimulationParameters p = result.parameters();
        return new SimulationResponse(
                null,
                result.portfolioId(),
                new ParametersResponse(p.startCapital(), p.monthlyContribution(),
                        p.horizonYears(), p.paths(), p.seed()),
                new EstimatesResponse(
                        result.estimation().historyFrom(),
                        result.estimation().historyTo(),
                        result.estimation().estimates().stream()
                                .map(e -> new AssetEstimate(e.asset().getId(), e.annualReturn(), e.annualVolatility()))
                                .toList()),
                result.timeline(),
                MetricsResponse.from(result.metrics()),
                result.runtimeMs());
    }
}
