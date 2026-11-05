package com.lookupstock.backend.model;

public record Metrics(
        double totalContributions,
        double medianEndValue,
        double p5EndValue,
        double p95EndValue,
        double valueAtRisk95,
        double maxDrawdownMedian,
        Double targetProbability) {
}