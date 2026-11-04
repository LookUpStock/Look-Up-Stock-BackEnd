package com.lookupstock.backend.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.lookupstock.backend.client.MarketDataProvider;
import com.lookupstock.backend.client.SearchHit;
import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.ValidationException;
import com.lookupstock.backend.model.Asset;
import com.lookupstock.backend.model.UsageEventType;
import com.lookupstock.backend.queue.UsageEventRecorder;
import com.lookupstock.backend.repository.AssetRepository;

import jakarta.transaction.Transactional;

@Service
public class AssetService {

    private final MarketDataProvider marketDataProvider;
    private final AssetRepository assetRepository;
    private final UsageEventRecorder usageEventRecorder;

    public AssetService(MarketDataProvider marketDataProvider, AssetRepository assetRepository,
                        UsageEventRecorder usageEventRecorder) {
        this.marketDataProvider = marketDataProvider;
        this.assetRepository = assetRepository;
        this.usageEventRecorder = usageEventRecorder;
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
        // Der Such-Cache (24 h, NF-02) liegt seit BE #106 im marktdaten-service (ADR-07)
        String trimmedQuery = query.trim();
        List<Asset> results = marketDataProvider.search(trimmedQuery).stream()
                .limit(max)
                .map(this::findOrCreate)
                .toList();
        // Gültige Suche zählt auch ohne Treffer oder aus dem Cache, nicht bei 400 oder 503 (E37)
        usageEventRecorder.record(UsageEventType.ASSET_SEARCH);
        return results;
    }

    private Asset findOrCreate(SearchHit hit) {
        return assetRepository.findBySymbolAndMicCode(hit.symbol(), hit.micCode())
                .orElseGet(() -> assetRepository.save(new Asset(
                hit.symbol(), null, hit.name(), hit.exchange(),
                hit.currency(), hit.type(), hit.micCode())));
    }
}
