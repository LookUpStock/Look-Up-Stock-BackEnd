package com.lookupstock.backend.dto;

import java.time.Instant;
import java.util.List;

import com.lookupstock.backend.model.Portfolio;

public record PortfolioResponse(Long id, String name, Instant createdAt,
                                Instant updatedAt, List<PositionResponse> positions) {

    public static PortfolioResponse from(Portfolio p) {
        return new PortfolioResponse(p.getId(), p.getName(), p.getCreatedAt(), p.getUpdatedAt(),
                p.getPositions().stream().map(PositionResponse::from).toList());
    }
    public record PortfolioListResponse(List<PortfolioResponse> portfolios) {}
}