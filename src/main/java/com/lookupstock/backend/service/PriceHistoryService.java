package com.lookupstock.backend.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.lookupstock.backend.client.MarketDataProvider;
import com.lookupstock.backend.client.PricePoint;
import com.lookupstock.backend.model.Asset;

/**
 * Liefert die monatlichen Kursdaten einer Anlage für die Simulation (US-04, NF-07).
 * Kursdaten, die jünger als 24 h sind, kommen aus dem Cache in Postgres, ohne Twelve Data aufzurufen.
 * Ältere oder fehlende Kursdaten werden neu abgerufen; ist Twelve Data dann nicht erreichbar,
 * wird die UpstreamUnavailableException (503) weitergegeben und kein veralteter Cache verwendet.
 */
@Service
public class PriceHistoryService {

    static final Duration MAX_CACHE_AGE = Duration.ofHours(24);
    static final String MONTHLY_INTERVAL = "1month";
    /** 10 Jahre Monatswerte; mindestens 36 werden für die Schätzung benötigt (E8). */
    static final int MONTHS_TO_LOAD = 120;

    private final MarketDataProvider marketDataProvider;
    private final PriceCache priceCache;
    private final Clock clock;

    public PriceHistoryService(MarketDataProvider marketDataProvider, PriceCache priceCache, Clock clock) {
        this.marketDataProvider = marketDataProvider;
        this.priceCache = priceCache;
        this.clock = clock;
    }

    /**
     * @return Monatswerte aufsteigend nach Datum; leer, wenn Twelve Data für die Anlage keine Kurse kennt
     */
    public List<PricePoint> getMonthlyPrices(Asset asset) {
        Instant now = clock.instant();

        Optional<PriceCache.CachedPrices> cached = priceCache.load(asset.getId());
        if (cached.isPresent() && isFresh(cached.get().fetchedAt(), now)) {
            return cached.get().prices();
        }

        // wirft UpstreamUnavailableException, wenn Twelve Data nicht erreichbar ist (503)
        List<PricePoint> fresh = marketDataProvider.getHistoricalPrices(
                asset.getSymbol(), asset.getMicCode(), MONTHLY_INTERVAL, MONTHS_TO_LOAD);

        List<PricePoint> sorted = fresh.stream()
                .filter(p -> p.date() != null && p.close() != null)
                .sorted(Comparator.comparing(PricePoint::date))
                .toList();
        if (!sorted.isEmpty()) {
            priceCache.replace(asset.getId(), sorted, now);
        }
        return sorted;
    }

    // „jünger als 24 h“: genau 24 h alt gilt bereits als veraltet
    static boolean isFresh(Instant fetchedAt, Instant now) {
        return fetchedAt.isAfter(now.minus(MAX_CACHE_AGE));
    }
}
