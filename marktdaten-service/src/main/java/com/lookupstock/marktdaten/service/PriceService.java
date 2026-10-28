package com.lookupstock.marktdaten.service;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.lookupstock.marktdaten.model.PricePoint;
import com.lookupstock.marktdaten.twelvedata.MarketDataProvider;

/**
 * Historische Kurse für den core-service (NF-07). Monatswerte (interval=1month), die die Simulation braucht,
 * kommen 24 h aus dem Kurs-Cache in Postgres, ohne Twelve Data aufzurufen. Ältere oder fehlende Werte werden
 * neu abgerufen; ist Twelve Data dann nicht erreichbar, wird die UpstreamUnavailableException (503) weitergegeben
 * und kein veralteter Cache verwendet. Andere Intervalle (z. B. 1day für Kennzahlen) werden nicht zwischengespeichert.
 * Geliefert werden die Rohdaten inklusive des laufenden Monats; abgeschlossene Monate wählt der core-service aus (E22).
 */
@Service
public class PriceService {

    private static final Logger log = LoggerFactory.getLogger(PriceService.class);

    static final Duration MAX_CACHE_AGE = Duration.ofHours(24);
    static final String MONTHLY_INTERVAL = "1month";
    /**
     * Nachkommastellen wie in der Spalte price_cache.close_price (NUMERIC(19,6)). Frisch geladene Kurse werden
     * genauso gerundet wie gespeicherte, damit frische und gecachte Kurse bei gleichem Seed dasselbe Ergebnis liefern
     * (NF-08, Docs #81).
     */
    static final int PRICE_SCALE = 6;

    private final MarketDataProvider marketDataProvider;
    private final PriceCacheRepository priceCache;
    private final Clock clock;

    public PriceService(MarketDataProvider marketDataProvider, PriceCacheRepository priceCache, Clock clock) {
        this.marketDataProvider = marketDataProvider;
        this.priceCache = priceCache;
        this.clock = clock;
    }

    public List<PricePoint> getPrices(String symbol, String micCode, String interval, int outputSize) {
        if (!MONTHLY_INTERVAL.equals(interval)) {
            return marketDataProvider.getHistoricalPrices(symbol, micCode, interval, outputSize);
        }
        return getMonthlyPrices(symbol, micCode, outputSize);
    }

    /** @return Monatswerte aufsteigend nach Datum; leer, wenn Twelve Data für die Anlage keine Kurse kennt */
    List<PricePoint> getMonthlyPrices(String symbol, String micCode, int outputSize) {
        Instant now = clock.instant();

        Optional<PriceCacheRepository.CachedPrices> cached = priceCache.load(symbol, micCode);
        if (cached.isPresent() && isFresh(cached.get().fetchedAt(), now)) {
            return cached.get().prices();
        }

        // wirft UpstreamUnavailableException, wenn Twelve Data nicht erreichbar ist (503)
        List<PricePoint> fresh = marketDataProvider.getHistoricalPrices(symbol, micCode, MONTHLY_INTERVAL, outputSize);

        List<PricePoint> sorted = fresh.stream()
                .filter(p -> p.date() != null && p.close() != null)
                .map(p -> new PricePoint(p.date(), p.close().setScale(PRICE_SCALE, RoundingMode.HALF_UP)))
                .sorted(Comparator.comparing(PricePoint::date))
                .toList();
        if (!sorted.isEmpty()) {
            try {
                priceCache.replace(symbol, micCode, sorted, now);
            } catch (DataAccessException e) {
                // z. B. zwei gleichzeitige Abrufe derselben Anlage; die Kurse sind trotzdem gültig
                log.warn("Kurs-Cache für {} ({}) nicht gespeichert – {}", symbol, micCode, e.getMessage());
            }
        }
        return sorted;
    }

    // „jünger als 24 h“: genau 24 h alt gilt bereits als veraltet
    static boolean isFresh(Instant fetchedAt, Instant now) {
        return fetchedAt.isAfter(now.minus(MAX_CACHE_AGE));
    }
}
