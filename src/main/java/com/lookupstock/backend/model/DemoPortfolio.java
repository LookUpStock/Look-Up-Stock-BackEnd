package com.lookupstock.backend.model;

import java.math.BigDecimal;
import java.util.List;

/** US-09: fest vorgegebenes Beispielportfolio für Gäste; nicht in der Datenbank, gehört keinem Nutzer. */
public record DemoPortfolio(long id, String name, List<Position> positions) {

    /** Anlage des Beispielportfolios; weight in Prozent wie beim Portfolio. */
    public record Position(String symbol, String micCode, String name, String exchange,
                           String currency, String type, BigDecimal weight) {}

    // US-Anlagen, weil Twelve Data im kostenlosen Tarif keine Xetra-Kurse liefert (F3, Docs #80, PO-Entscheidung 18.10.2026)
    public static final List<DemoPortfolio> ALL = List.of(
        new DemoPortfolio(900, "Beispiel: Weltportfolio", List.of(
            new Position("URTH", "ARCX", "iShares MSCI World ETF", "NYSE", "USD", "ETF", new BigDecimal("100")))),
        new DemoPortfolio(901, "Beispiel: Welt 70 / Schwellenländer 30", List.of(
            new Position("URTH", "ARCX", "iShares MSCI World ETF", "NYSE", "USD", "ETF", new BigDecimal("70")),
            new Position("IEMG", "ARCX", "iShares Core MSCI Emerging Markets ETF", "NYSE", "USD", "ETF", new BigDecimal("30")))));
}
