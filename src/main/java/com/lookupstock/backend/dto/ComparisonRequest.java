package com.lookupstock.backend.dto;

import java.util.List;

/** Body für POST /api/v1/comparisons. Ein Vergleichseintrag bezeichnet Portfolio oder Einzelanlage. */
public record ComparisonRequest(String token, List<Item> items,
                                SimulationRequest.Parameters parameters,
                                Boolean save, String name) {
    public record Item(String label, Long portfolioId, Long assetId) {
    }
}
