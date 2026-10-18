package com.lookupstock.backend.model;

import java.util.List;

public record ComparisonResult(SimulationParameters parameters,
                               List<ItemResult> results,
                               List<PairwiseResult> pairwise,
                               long runtimeMs) {
    public record ItemResult(String label, Long portfolioId, Long assetId,
                             List<TimelinePoint> timeline, Metrics metrics) {
    }

    public record Metrics(double totalContributions, double medianEndValue,
                          double p5EndValue, double p95EndValue,
                          double valueAtRisk95, double maxDrawdownMedian,
                          Double targetProbability) {
    }

    public record PairwiseResult(int a, int b, double probabilityAOutperformsB) {
    }
}
