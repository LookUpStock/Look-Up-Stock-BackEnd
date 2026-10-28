package com.lookupstock.backend.dto;

import java.util.List;

import com.lookupstock.backend.dto.SimulationResponse.EstimatesResponse;
import com.lookupstock.backend.model.Metrics;
import com.lookupstock.backend.model.TimelinePoint;

/**
 * Inhalt der JSON-Spalte simulation.result (US-08): der berechnete Teil der Antwort.
 * Parameter, Name und Zeitstempel stehen in eigenen Spalten und sind hier nicht enthalten.
 */
public record StoredSimulationResult(
        EstimatesResponse estimates,
        List<TimelinePoint> timeline,
        Metrics metrics) {

    // Übernimmt den berechneten Teil aus einer frisch gerechneten Antwort
    public static StoredSimulationResult from(SimulationResponse response) {
        return new StoredSimulationResult(response.estimates(), response.timeline(), response.metrics());
    }
}