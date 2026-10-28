package com.lookupstock.backend.service;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.lookupstock.backend.client.MarketDataProvider;
import com.lookupstock.backend.client.PricePoint;
import com.lookupstock.backend.model.Asset;

/**
 * Liefert die monatlichen Kursdaten einer Anlage für die Simulation (US-04).
 * Seit BE #106 liegt der Kurs-Cache (24 h, NF-07) im marktdaten-service (ADR-07); dieser Service fragt die
 * Monatswerte über den MarketDataProvider ab. Ist der marktdaten-service oder Twelve Data nicht erreichbar,
 * wird die UpstreamUnavailableException (503) weitergegeben.
 * Zurückgegeben werden nur abgeschlossene Monate; der laufende Monat zählt weder zur Schätzung
 * noch zu den 36 Monatswerten (E22, B5).
 */
@Service
public class PriceHistoryService {

    static final String MONTHLY_INTERVAL = "1month";
    /** 10 Jahre Monatswerte; mindestens 36 werden für die Schätzung benötigt (E8). */
    static final int MONTHS_TO_LOAD = 120;
    /**
     * Nachkommastellen wie im Kurs-Cache des marktdaten-service (NUMERIC(19,6)). Der marktdaten-service rundet
     * bereits; hier wird zur Sicherheit noch einmal gerundet, damit gleiche Eingaben bei gleichem Seed immer
     * dasselbe Ergebnis liefern (NF-08, Docs #81), auch wenn ein anderer Anbieter angebunden ist (NF-12).
     */
    static final int PRICE_SCALE = 6;

    private final MarketDataProvider marketDataProvider;
    private final Clock clock;

    public PriceHistoryService(MarketDataProvider marketDataProvider, Clock clock) {
        this.marketDataProvider = marketDataProvider;
        this.clock = clock;
    }

    /**
     * @return Monatswerte abgeschlossener Monate aufsteigend nach Datum; leer, wenn der Anbieter
     *         für die Anlage keine Kurse kennt
     */
    public List<PricePoint> getMonthlyPrices(Asset asset) {
        // wirft UpstreamUnavailableException, wenn die Kurse nicht abrufbar sind (503)
        List<PricePoint> prices = marketDataProvider.getHistoricalPrices(
                asset.getSymbol(), asset.getMicCode(), MONTHLY_INTERVAL, MONTHS_TO_LOAD);

        List<PricePoint> sorted = prices.stream()
                .filter(p -> p.date() != null && p.close() != null)
                .map(p -> new PricePoint(p.date(), p.close().setScale(PRICE_SCALE, RoundingMode.HALF_UP)))
                .sorted(Comparator.comparing(PricePoint::date))
                .toList();
        return completedMonths(sorted, YearMonth.now(clock));
    }

    // E22: Werte des laufenden (und eines späteren) Monats werden nicht verwendet
    static List<PricePoint> completedMonths(List<PricePoint> prices, YearMonth currentMonth) {
        return prices.stream()
                .filter(p -> YearMonth.from(p.date()).isBefore(currentMonth))
                .toList();
    }
}
