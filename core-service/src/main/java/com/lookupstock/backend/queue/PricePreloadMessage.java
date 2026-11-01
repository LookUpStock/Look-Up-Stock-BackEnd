package com.lookupstock.backend.queue;

import java.util.List;

/**
 * Inhalt einer Nachricht zum Thema kursdaten.vorladen: Symbol und MIC-Code der Anlagen eines Portfolios.
 * Keine IDs des core-service, weil der marktdaten-service die Tabellen des Core nicht kennt (ADR-07).
 */
public record PricePreloadMessage(List<Instrument> instruments) {

    /** @param micCode Börsenkennung, null = Standardbörse des Anbieters */
    public record Instrument(String symbol, String micCode) {
    }
}
