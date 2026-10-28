package com.lookupstock.marktdaten.twelvedata;

import java.util.List;

import com.lookupstock.marktdaten.model.PricePoint;
import com.lookupstock.marktdaten.model.SearchHit;

/** Schnittstelle zum Kursdaten-Anbieter (NF-12); einzige Umsetzung ist TwelveDataProvider. */
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
