package com.lookupstock.backend.client;

import java.util.List;

public interface MarketDataProvider {

    List<SearchHit> search(String query);

    /**
     * Historische Kurse eines Symbols an einer bestimmten Börse.
     *
     * @param micCode Börsenkennung (z. B. XETR); null = Standardbörse des Anbieters
     * @return Kurse in der Reihenfolge des Anbieters; leer, wenn der Anbieter das Symbol nicht kennt
     */
    List<PricePoint> getHistoricalPrices(String symbol, String micCode, String interval, int outputSize);

    /** Historische Kurse ohne Angabe der Börse. */
    default List<PricePoint> getHistoricalPrices(String symbol, String interval, int outputSize) {
        return getHistoricalPrices(symbol, null, interval, outputSize);
    }
}
