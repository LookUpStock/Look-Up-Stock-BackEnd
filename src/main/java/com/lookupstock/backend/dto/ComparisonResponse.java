package com.lookupstock.backend.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lookupstock.backend.model.ComparisonResult;
import com.lookupstock.backend.model.Metrics;
import com.lookupstock.backend.model.TimelinePoint;

/**
 * Antwort von POST /api/v1/comparisons laut API-Design.
 * id und name sind null, solange Vergleiche nicht gespeichert werden (US-08).
 * estimates zeigt den gemeinsamen Schätzzeitraum (ADR-05, Konsequenzen).
 */
public record ComparisonResponse(
        Long id,
        String name,
        SimulationResponse.ParametersResponse parameters,
        SimulationResponse.EstimatesResponse estimates,
        List<ItemResponse> results,
        List<PairwiseResponse> pairwise,
        long runtimeMs,
        Instant createdAt) {

    // je Eintrag erscheint nur portfolioId oder assetId, wie im API-Design
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ItemResponse(String label, Long portfolioId, Long assetId,
                               List<TimelinePoint> timeline, Metrics metrics) {
    }

    public record PairwiseResponse(int a, int b, double probabilityAOutperformsB) {
    }

    public static ComparisonResponse from(ComparisonResult result) {
        return new ComparisonResponse(
                null,
                null,
                SimulationResponse.ParametersResponse.from(result.parameters()),
                SimulationResponse.EstimatesResponse.from(result.estimation()),
                result.items().stream()
                        .map(i -> new ItemResponse(i.label(), i.portfolioId(), i.assetId(), i.timeline(), i.metrics()))
                        .toList(),
                result.pairwise().stream()
                        .map(p -> new PairwiseResponse(p.a(), p.b(), p.probabilityAOutperformsB()))
                        .toList(),
                result.runtimeMs(),
                result.createdAt());
    }
}
