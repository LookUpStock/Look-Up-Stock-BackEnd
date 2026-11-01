package com.lookupstock.marktdaten.queue;

import java.util.List;

/**
 * Inhalt einer Nachricht zum Thema kursdaten.vorladen; gleicher Aufbau wie PricePreloadMessage im core-service:
 * {"instruments":[{"symbol":"URTH","micCode":"ARCX"}]}
 */
public record PricePreloadMessage(List<Instrument> instruments) {

    public record Instrument(String symbol, String micCode) {
    }
}
