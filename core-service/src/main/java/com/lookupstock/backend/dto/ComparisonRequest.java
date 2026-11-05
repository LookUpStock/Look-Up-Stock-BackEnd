package com.lookupstock.backend.dto;

import java.util.List;

import com.lookupstock.backend.model.ComparisonEntry;

public record ComparisonRequest(List<Item> items, SimulationRequest.Parameters parameters,
                                Boolean save, String name) {

    public record Item(String label, Long portfolioId, Long assetId) {
    }

    public List<ComparisonEntry> toEntries() {
        if (items == null) {
            return null;
        }
        return items.stream()
                .map(item -> item == null ? null : new ComparisonEntry(item.label(), item.portfolioId(), item.assetId()))
                .toList();
    }
}
