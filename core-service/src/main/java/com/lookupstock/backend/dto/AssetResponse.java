package com.lookupstock.backend.dto;

import com.lookupstock.backend.model.Asset;
public record AssetResponse(Long id, String symbol, String isin, String name,
                            String exchange, String micCode, String currency, String type) {

    public static AssetResponse from(Asset a) {
        return new AssetResponse(a.getId(), a.getSymbol(), a.getIsin(), a.getName(),
                a.getExchange(), a.getMicCode(), a.getCurrency(), a.getType());
    }
}
