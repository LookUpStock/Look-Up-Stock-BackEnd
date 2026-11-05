package com.lookupstock.marktdaten.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.lookupstock.marktdaten.model.SearchHit;
import com.lookupstock.marktdaten.twelvedata.MarketDataProvider;

@Service
public class SearchService {

    private final MarketDataProvider marketDataProvider;
    private final SearchCache searchCache;

    public SearchService(MarketDataProvider marketDataProvider, SearchCache searchCache) {
        this.marketDataProvider = marketDataProvider;
        this.searchCache = searchCache;
    }

    public List<SearchHit> search(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Parameter q fehlt.");
        }
        String trimmed = query.trim();
        return searchCache.getOrLoad(trimmed, () -> marketDataProvider.search(trimmed));
    }
}
