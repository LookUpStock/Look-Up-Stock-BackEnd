package com.lookupstock.backend.model;

import java.util.List;

public record SimulationRun(List<TimelinePoint> timeline, Metrics metrics, double[] endValues) {
}
