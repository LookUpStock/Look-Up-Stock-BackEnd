package com.lookupstock.backend.model;

import java.time.Instant;
import java.util.List;

public record ComparisonResult(
        SimulationParameters parameters,
        EstimationResult estimation,
        List<Item> items,
        List<Pairwise> pairwise,
        long runtimeMs,
        Instant createdAt) {

    public ComparisonResult {
        items = List.copyOf(items);
        pairwise = List.copyOf(pairwise);
    }

    public record Item(String label, Long portfolioId, Long assetId, List<TimelinePoint> timeline, Metrics metrics) {
    }

    public record Pairwise(int a, int b, double probabilityAOutperformsB) {
    }
}
