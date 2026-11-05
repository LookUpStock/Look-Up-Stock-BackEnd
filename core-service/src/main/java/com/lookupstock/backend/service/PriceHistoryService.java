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

@Service
public class PriceHistoryService {

    static final String MONTHLY_INTERVAL = "1month";
    static final int MONTHS_TO_LOAD = 120;
    // Rounded to the cache precision (NUMERIC(19,6)) so equal seeds always give equal results.
    static final int PRICE_SCALE = 6;

    private final MarketDataProvider marketDataProvider;
    private final Clock clock;

    public PriceHistoryService(MarketDataProvider marketDataProvider, Clock clock) {
        this.marketDataProvider = marketDataProvider;
        this.clock = clock;
    }

    public List<PricePoint> getMonthlyPrices(Asset asset) {
        List<PricePoint> prices = marketDataProvider.getHistoricalPrices(
                asset.getSymbol(), asset.getMicCode(), MONTHLY_INTERVAL, MONTHS_TO_LOAD);

        List<PricePoint> sorted = prices.stream()
                .filter(p -> p.date() != null && p.close() != null)
                .map(p -> new PricePoint(p.date(), p.close().setScale(PRICE_SCALE, RoundingMode.HALF_UP)))
                .sorted(Comparator.comparing(PricePoint::date))
                .toList();
        return completedMonths(sorted, YearMonth.now(clock));
    }

    // Only completed months are used; the current month is excluded.
    static List<PricePoint> completedMonths(List<PricePoint> prices, YearMonth currentMonth) {
        return prices.stream()
                .filter(p -> YearMonth.from(p.date()).isBefore(currentMonth))
                .toList();
    }
}
