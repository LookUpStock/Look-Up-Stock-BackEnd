package com.lookupstock.marktdaten.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.lookupstock.marktdaten.model.SearchHit;
import com.lookupstock.marktdaten.twelvedata.MarketDataProvider;

/**
 * Anlagensuche für den core-service (US-02). Derselbe Suchbegriff wird 24 h aus dem Cache beantwortet (NF-02).
 * Die fachlichen Prüfregeln (mindestens 2 Zeichen, limit) bleiben im core-service; hier wird nur ein leerer Begriff abgewiesen.
 */
@Service
public class SearchService {

    private final MarketDataProvider marketDataProvider;
    private final SearchCache searchCache;

    public SearchService(MarketDataProvider marketDataProvider, SearchCache searchCache) {
        this.marketDataProvider = marketDataProvider;
        this.searchCache = searchCache;
    }

    /** @throws IllegalArgumentException bei leerem Suchbegriff (400) */
    public List<SearchHit> search(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Parameter q fehlt.");
        }
        String trimmed = query.trim();
        // wirft UpstreamUnavailableException, wenn Twelve Data nicht erreichbar ist; Fehler werden nicht gespeichert
        return searchCache.getOrLoad(trimmed, () -> marketDataProvider.search(trimmed));
    }
}
