package com.lookupstock.backend.dto;

import java.math.BigDecimal;
import java.util.List;

import com.lookupstock.backend.model.DemoPortfolio;

/**
 * Antwort von GET /api/v1/demo/portfolios (US-09).
 * Ohne assetId: Beispielportfolios haben keine festen Asset-IDs, die Anlagen entstehen erst beim Simulieren.
 */
public record DemoPortfolioListResponse(List<Item> portfolios) {

    public record Item(long id, String name, List<Position> positions) {

        public static Item from(DemoPortfolio demo) {
            return new Item(demo.id(), demo.name(), demo.positions().stream()
                    .map(p -> new Position(p.symbol(), p.name(), p.weight()))
                    .toList());
        }
    }

    public record Position(String symbol, String name, BigDecimal weight) {
    }

    public static DemoPortfolioListResponse from(List<DemoPortfolio> demos) {
        return new DemoPortfolioListResponse(demos.stream().map(Item::from).toList());
    }
}
