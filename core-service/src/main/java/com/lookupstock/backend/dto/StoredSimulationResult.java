package com.lookupstock.backend.dto;

import java.util.List;

import com.lookupstock.backend.dto.SimulationResponse.EstimatesResponse;
import com.lookupstock.backend.model.Metrics;
import com.lookupstock.backend.model.TimelinePoint;

public record StoredSimulationResult(
        EstimatesResponse estimates,
        List<TimelinePoint> timeline,
        Metrics metrics) {

    public static StoredSimulationResult from(SimulationResponse response) {
        return new StoredSimulationResult(response.estimates(), response.timeline(), response.metrics());
    }
}