package com.lookupstock.backend.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.lookupstock.backend.client.MarketDataProvider;
import com.lookupstock.backend.client.PricePoint;
import com.lookupstock.backend.client.SearchHit;
import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.ValidationException;
import com.lookupstock.backend.model.Asset;
import com.lookupstock.backend.repository.AssetRepository;

import jakarta.transaction.Transactional;
@Service
public class AssetService {

    private final MarketDataProvider marketDataProvider;
    private final AssetRepository assetRepository;
    private final SearchCache searchCache;

    public AssetService(MarketDataProvider marketDataProvider, AssetRepository assetRepository, SearchCache searchCache) {
        this.marketDataProvider = marketDataProvider;
        this.assetRepository = assetRepository;
        this.searchCache = searchCache;
    }

    @Transactional
    public List<Asset> search(String query, Integer limit) {
        int max = (limit == null) ? 10 : limit;
        List<ValidationException.FieldError> errors = new ArrayList<>();

        if (query == null || query.trim().length() < 2) {
            errors.add(new ValidationException.FieldError("q", Messages.QUERY_TOO_SHORT));
        }
        if (max < 1 || max > 25) {
            errors.add(new ValidationException.FieldError("limit", Messages.LIMIT_RANGE));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors.get(0).message(), errors);
        }
                // Treffer kommen 24 h aus dem Cache, nur bei Cache-Fehlschlag wird Twelve Data gefragt (NF-02)
        String trimmedQuery = query.trim();
        return searchCache.getOrLoad(trimmedQuery, () -> marketDataProvider.search(trimmedQuery)).stream()
                .limit(max)
                .map(this::findOrCreate)
                .toList();
    }

    public List<PricePoint> getHistoricalPrices(String symbol, String interval, Integer outputSize) {
        String normalizedSymbol = symbol == null ? "" : symbol.trim();
        String normalizedInterval = interval == null || interval.isBlank() ? "1day" : interval.trim();
        int size = outputSize == null ? 30 : outputSize;
        List<ValidationException.FieldError> errors = new ArrayList<>();

        if (normalizedSymbol.isBlank()) {
            errors.add(new ValidationException.FieldError("symbol", "Bitte ein Symbol angeben."));
        }
        if (size < 1 || size > 5000) {
            errors.add(new ValidationException.FieldError("outputSize", "outputSize muss zwischen 1 und 5000 liegen."));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors.get(0).message(), errors);
        }
        return marketDataProvider.getHistoricalPrices(normalizedSymbol, normalizedInterval, size);
    }

    private Asset findOrCreate(SearchHit hit) {
        return assetRepository.findBySymbolAndMicCode(hit.symbol(), hit.micCode())
                .orElseGet(() -> assetRepository.save(new Asset(
                hit.symbol(), null, hit.name(), hit.exchange(),
                hit.currency(), hit.type(), hit.micCode())));
    }
}
