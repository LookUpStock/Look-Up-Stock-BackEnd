package com.lookupstock.backend.dto;

import java.time.Instant;
import java.util.List;

import com.lookupstock.backend.model.SavedSimulation;

public record SavedSimulationListResponse(List<Entry> simulations) {

    public record Entry(Long id, String name, Long portfolioId, Instant createdAt) {

        public static Entry from(SavedSimulation saved) {
            return new Entry(saved.getId(), saved.getName(), saved.getPortfolio().getId(), saved.getCreatedAt());
        }
    }

    public static SavedSimulationListResponse from(List<SavedSimulation> saved) {
        return new SavedSimulationListResponse(saved.stream().map(Entry::from).toList());
    }
}