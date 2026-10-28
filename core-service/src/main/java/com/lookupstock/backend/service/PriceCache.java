package com.lookupstock.backend.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.lookupstock.backend.client.PricePoint;
import com.lookupstock.backend.model.Asset;
import com.lookupstock.backend.model.PriceHistory;
import com.lookupstock.backend.repository.AssetRepository;
import com.lookupstock.backend.repository.PriceHistoryRepository;

/**
 * Lesen und Schreiben der zwischengespeicherten Kursdaten in Postgres (NF-07).
 * Eigene Klasse, damit das Speichern in einer eigenen Transaktion läuft:
 * Der Cache bleibt erhalten, auch wenn die Simulation danach scheitert (z. B. 422).
 */
@Component
public class PriceCache {

    private final PriceHistoryRepository priceHistoryRepository;
    private final AssetRepository assetRepository;

    public PriceCache(PriceHistoryRepository priceHistoryRepository, AssetRepository assetRepository) {
        this.priceHistoryRepository = priceHistoryRepository;
        this.assetRepository = assetRepository;
    }

    /** Zwischengespeicherte Monatswerte einer Anlage, aufsteigend nach Datum, mit Abrufzeitpunkt. */
    public record CachedPrices(Instant fetchedAt, List<PricePoint> prices) {
    }

    @Transactional(readOnly = true)
    public Optional<CachedPrices> load(Long assetId) {
        List<PriceHistory> rows = priceHistoryRepository.findByAssetIdOrderByPriceDateAsc(assetId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        // alle Zeilen einer Anlage stammen aus demselben Abruf; zur Sicherheit der älteste Zeitpunkt
        Instant fetchedAt = rows.stream()
                .map(PriceHistory::getFetchedAt)
                .min(Comparator.naturalOrder())
                .orElseThrow();
        List<PricePoint> prices = rows.stream()
                .map(r -> new PricePoint(r.getPriceDate(), r.getClosePrice()))
                .toList();
        return Optional.of(new CachedPrices(fetchedAt, prices));
    }

    /** Ersetzt alle Monatswerte einer Anlage durch einen neuen Abruf. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void replace(Long assetId, List<PricePoint> prices, Instant fetchedAt) {
        priceHistoryRepository.deleteByAssetId(assetId);
        Asset asset = assetRepository.getReferenceById(assetId);
        priceHistoryRepository.saveAll(prices.stream()
                .map(p -> new PriceHistory(asset, p.date(), p.close(), fetchedAt))
                .toList());
    }
}
