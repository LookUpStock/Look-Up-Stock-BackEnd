package com.lookupstock.backend.dto;

import java.math.BigDecimal;

import com.lookupstock.backend.model.Position;

public record PositionResponse(Long assetId, String symbol, String name, BigDecimal weight) {

    public static PositionResponse from(Position p) {
        return new PositionResponse(p.getAsset().getId(), p.getAsset().getSymbol(), p.getAsset().getName(), p.getWeight());
    }
}
