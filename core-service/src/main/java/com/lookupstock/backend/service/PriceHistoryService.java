package com.lookupstock.backend.service;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
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
 * Zurückgegeben werden nur abgeschlossene Monate; der laufende Monat zählt weder zur Schätzung
 * noch zu den 36 Monatswerten (E22, B5). Der Cache speichert die Rohdaten, gefiltert wird beim Lesen,
 * damit ein beim Monatswechsel noch gültiger Cache den gerade abgeschlossenen Monat liefert.
 */
@Service
public class PriceHistoryService {

    static final Duration MAX_CACHE_AGE = Duration.ofHours(24);
    static final String MONTHLY_INTERVAL = "1month";
    /** 10 Jahre Monatswerte; mindestens 36 werden für die Schätzung benötigt (E8). */
    static final int MONTHS_TO_LOAD = 120;
    /**
     * Nachkommastellen der Schlusskurse wie in der Spalte price_history.close_price (NUMERIC(19,6)).
     * Frisch geladene Kurse werden genauso gerundet wie gespeicherte, damit ein Lauf mit frischen
     * und ein Lauf mit gecachten Kursen bei gleichem Seed dasselbe Ergebnis liefern (NF-08, Docs #81).
     */
    static final int PRICE_SCALE = 6;

    private final MarketDataProvider marketDataProvider;
    private final PriceCache priceCache;
    private final Clock clock;

    public PriceHistoryService(MarketDataProvider marketDataProvider, PriceCache priceCache, Clock clock) {
        this.marketDataProvider = marketDataProvider;
        this.priceCache = priceCache;
        this.clock = clock;
    }

    /**
     * @return Monatswerte abgeschlossener Monate aufsteigend nach Datum; leer, wenn Twelve Data
     *         für die Anlage keine Kurse kennt
     */
    public List<PricePoint> getMonthlyPrices(Asset asset) {
        Instant now = clock.instant();
        YearMonth currentMonth = YearMonth.now(clock);

        Optional<PriceCache.CachedPrices> cached = priceCache.load(asset.getId());
        if (cached.isPresent() && isFresh(cached.get().fetchedAt(), now)) {
            return completedMonths(cached.get().prices(), currentMonth);
        }

        // wirft UpstreamUnavailableException, wenn Twelve Data nicht erreichbar ist (503)
        List<PricePoint> fresh = marketDataProvider.getHistoricalPrices(
                asset.getSymbol(), asset.getMicCode(), MONTHLY_INTERVAL, MONTHS_TO_LOAD);

        List<PricePoint> sorted = fresh.stream()
                .filter(p -> p.date() != null && p.close() != null)
                .map(p -> new PricePoint(p.date(), p.close().setScale(PRICE_SCALE, RoundingMode.HALF_UP)))
                .sorted(Comparator.comparing(PricePoint::date))
                .toList();
        if (!sorted.isEmpty()) {
            priceCache.replace(asset.getId(), sorted, now);
        }
        return completedMonths(sorted, currentMonth);
    }

    // E22: Werte des laufenden (und eines späteren) Monats werden nicht verwendet
    static List<PricePoint> completedMonths(List<PricePoint> prices, YearMonth currentMonth) {
        return prices.stream()
                .filter(p -> YearMonth.from(p.date()).isBefore(currentMonth))
                .toList();
    }

    // „jünger als 24 h“: genau 24 h alt gilt bereits als veraltet
    static boolean isFresh(Instant fetchedAt, Instant now) {
        return fetchedAt.isAfter(now.minus(MAX_CACHE_AGE));
    }
}
