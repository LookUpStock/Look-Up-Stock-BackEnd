package com.lookupstock.marktdaten.queue;

import java.util.List;

public record PricePreloadMessage(List<Instrument> instruments) {

    public record Instrument(String symbol, String micCode) {
    }
}
