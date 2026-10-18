package com.lookupstock.backend.dto;

import java.util.List;

import com.lookupstock.backend.model.ComparisonResult;
import com.lookupstock.backend.model.SimulationParameters;

/** Antwort von POST /api/v1/comparisons. Nicht gespeicherte Vergleiche haben id/name null. */
public record ComparisonResponse(Long id, String name, ParametersResponse parameters,
                                 List<ItemResponse> results,
                                 List<ComparisonResult.PairwiseResult> pairwise,
                                 long runtimeMs, String createdAt) {
    public record ParametersResponse(double startCapital, double monthlyContribution,
                                     int horizonYears, int paths, long seed) {
        static ParametersResponse from(SimulationParameters p) {
            return new ParametersResponse(p.startCapital(), p.monthlyContribution(),
                    p.horizonYears(), p.paths(), p.seed());
        }
    }

    public record ItemResponse(String label, Long portfolioId, Long assetId,
                               List<com.lookupstock.backend.model.TimelinePoint> timeline,
                               ComparisonResult.Metrics metrics) {
        static ItemResponse from(ComparisonResult.ItemResult result) {
            return new ItemResponse(result.label(), result.portfolioId(), result.assetId(),
                    result.timeline(), result.metrics());
        }
    }

    public static ComparisonResponse from(ComparisonResult result) {
        return new ComparisonResponse(null, null, ParametersResponse.from(result.parameters()),
                result.results().stream().map(ItemResponse::from).toList(),
                result.pairwise(), result.runtimeMs(), null);
    }
}
