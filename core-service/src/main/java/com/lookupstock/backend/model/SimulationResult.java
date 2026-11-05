package com.lookupstock.backend.model;

import java.util.List;

public record SimulationResult(
        Long portfolioId,
        SimulationParameters parameters,
        EstimationResult estimation,
        List<TimelinePoint> timeline,
        Metrics metrics,
        long runtimeMs) {
}
