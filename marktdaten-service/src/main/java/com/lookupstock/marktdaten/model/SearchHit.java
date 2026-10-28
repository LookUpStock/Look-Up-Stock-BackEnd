package com.lookupstock.marktdaten.model;

/** Ein Suchtreffer; gleiche Felder wie SearchHit im core-service, damit die JSON-Antwort direkt passt. */
public record SearchHit(String symbol, String name, String exchange,
                        String micCode, String currency, String type) {
}
