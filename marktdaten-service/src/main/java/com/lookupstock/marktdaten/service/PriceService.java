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

@Service
public class PriceService {

    private static final Logger log = LoggerFactory.getLogger(PriceService.class);

    static final Duration MAX_CACHE_AGE = Duration.ofHours(24);
    static final String MONTHLY_INTERVAL = "1month";
    // Fresh prices are rounded like cached ones so both give the same result for the same seed.
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

    List<PricePoint> getMonthlyPrices(String symbol, String micCode, int outputSize) {
        Instant now = clock.instant();

        Optional<PriceCacheRepository.CachedPrices> cached = priceCache.load(symbol, micCode);
        if (cached.isPresent() && isFresh(cached.get().fetchedAt(), now)) {
            return cached.get().prices();
        }

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
                log.warn("Kurs-Cache für {} ({}) nicht gespeichert – {}", symbol, micCode, e.getMessage());
            }
        }
        return sorted;
    }

    // Exactly 24 h old already counts as stale.
    static boolean isFresh(Instant fetchedAt, Instant now) {
        return fetchedAt.isAfter(now.minus(MAX_CACHE_AGE));
    }
}
