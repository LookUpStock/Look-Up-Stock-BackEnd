package com.lookupstock.backend.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.lookupstock.backend.model.EstimationResult;
import com.lookupstock.backend.model.Metrics;
import com.lookupstock.backend.model.SavedSimulation;
import com.lookupstock.backend.model.SimulationParameters;
import com.lookupstock.backend.model.SimulationResult;
import com.lookupstock.backend.model.TimelinePoint;

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

        public static ParametersResponse from(SimulationParameters p) {
            return new ParametersResponse(p.startCapital(), p.monthlyContribution(),
                    p.horizonYears(), p.paths(), p.seed(), p.targetAmount());
        }
    }

    public record EstimatesResponse(LocalDate historyFrom, LocalDate historyTo, List<AssetEstimate> assets) {

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
                null,
                result.portfolioId(),
                ParametersResponse.from(result.parameters()),
                EstimatesResponse.from(result.estimation()),
                result.timeline(),
                result.metrics(),
                result.runtimeMs(),
                null);
    }

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