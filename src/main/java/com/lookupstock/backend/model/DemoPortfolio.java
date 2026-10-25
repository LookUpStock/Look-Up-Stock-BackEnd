package com.lookupstock.backend.model;

import java.math.BigDecimal;
import java.util.List;

/** US-09: fest vorgegebenes Beispielportfolio für Gäste; nicht in der Datenbank, gehört keinem Nutzer. */
public record DemoPortfolio(long id, String name, List<Position> positions) {

    /** Anlage des Beispielportfolios; weight in Prozent wie beim Portfolio. */
    public record Position(String symbol, String micCode, String name, String exchange,
                           String currency, String type, BigDecimal weight) {}

    public static final List<DemoPortfolio> ALL = List.of(
        new DemoPortfolio(900, "Beispiel: Weltportfolio", List.of(
            new Position("EUNL", "XETR", "iShares Core MSCI World", "XETRA", "EUR", "ETF", new BigDecimal("100")))),
        new DemoPortfolio(901, "Beispiel: Welt 70 / Schwellenländer 30", List.of(
            new Position("EUNL", "XETR", "iShares Core MSCI World", "XETRA", "EUR", "ETF", new BigDecimal("70")),
            new Position("IS3N", "XETR", "iShares Core MSCI EM IMI", "XETRA", "EUR", "ETF", new BigDecimal("30")))));
}
