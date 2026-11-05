package com.lookupstock.marktdaten.twelvedata;

import java.util.List;

import com.lookupstock.marktdaten.model.PricePoint;
import com.lookupstock.marktdaten.model.SearchHit;

public interface MarketDataProvider {

    List<SearchHit> search(String query);

    List<PricePoint> getHistoricalPrices(String symbol, String micCode, String interval, int outputSize);

    default List<PricePoint> getHistoricalPrices(String symbol, String interval, int outputSize) {
        return getHistoricalPrices(symbol, null, interval, outputSize);
    }
}
