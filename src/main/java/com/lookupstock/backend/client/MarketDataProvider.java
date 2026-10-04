package com.lookupstock.backend.client;

import java.util.List;

public interface MarketDataProvider {
    List<SearchHit> search(String query);
}
