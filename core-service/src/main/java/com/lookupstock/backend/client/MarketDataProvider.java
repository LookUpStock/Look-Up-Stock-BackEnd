package com.lookupstock.backend.client;

import java.util.List;

public interface MarketDataProvider {

    List<SearchHit> search(String query);

    List<PricePoint> getHistoricalPrices(String symbol, String micCode, String interval, int outputSize);

    default List<PricePoint> getHistoricalPrices(String symbol, String interval, int outputSize) {
        return getHistoricalPrices(symbol, null, interval, outputSize);
    }
}
