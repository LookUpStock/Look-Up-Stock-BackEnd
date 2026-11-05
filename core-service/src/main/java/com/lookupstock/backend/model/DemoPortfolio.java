package com.lookupstock.backend.model;

import java.math.BigDecimal;
import java.util.List;

public record DemoPortfolio(long id, String name, List<Position> positions) {

    public record Position(String symbol, String micCode, String name, String exchange,
                           String currency, String type, BigDecimal weight) {}

    // US-listed assets: the Twelve Data free plan has no Xetra prices.
    public static final List<DemoPortfolio> ALL = List.of(
        new DemoPortfolio(900, "Beispiel: Weltportfolio", List.of(
            new Position("URTH", "ARCX", "iShares MSCI World ETF", "NYSE", "USD", "ETF", new BigDecimal("100")))),
        new DemoPortfolio(901, "Beispiel: Welt 70 / Schwellenländer 30", List.of(
            new Position("URTH", "ARCX", "iShares MSCI World ETF", "NYSE", "USD", "ETF", new BigDecimal("70")),
            new Position("IEMG", "ARCX", "iShares Core MSCI Emerging Markets ETF", "NYSE", "USD", "ETF", new BigDecimal("30")))));
}
