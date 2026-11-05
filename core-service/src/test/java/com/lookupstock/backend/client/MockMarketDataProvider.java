package com.lookupstock.backend.client;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.UpstreamUnavailableException;

public class MockMarketDataProvider implements MarketDataProvider {

    public static final LocalDate DEFAULT_LAST_MONTH = LocalDate.of(2026, 9, 1);

    public static final String WORLD_ETF = "EUNL";
    public static final String EMERGING_ETF = "IS3N";
    public static final String SHORT_HISTORY_A = "SHRT1";
    public static final String SHORT_HISTORY_B = "SHRT2";
    public static final String EXACT_36_MONTHS = "EXCT36";
    public static final String CONSTANT_GROWTH = "CONST";
    public static final List<String> PERFORMANCE_SET = List.of("PERF1", "PERF2", "PERF3", "PERF4", "PERF5");

    public static final int LONG_HISTORY_MONTHS = 120;
    public static final int SHORT_HISTORY_MONTHS = 35;
    public static final int MSCI_SEARCH_HIT_COUNT = 26;
    public static final double CONSTANT_MONTHLY_GROWTH = 0.005;

    private static final int PRICE_SCALE = 6;

    private final Map<String, SearchHit> searchHits = new LinkedHashMap<>();
    private final Map<String, List<PricePoint>> priceSeries = new LinkedHashMap<>();

    private boolean unavailable;
    private int priceRequestCount;

    public MockMarketDataProvider() {
        this(DEFAULT_LAST_MONTH);
    }

    public MockMarketDataProvider(LocalDate lastMonth) {
        LocalDate last = lastMonth.withDayOfMonth(1);

        addAsset(new SearchHit(WORLD_ETF, "iShares Core MSCI World UCITS ETF", "XETRA", "XETR", "EUR", "ETF"),
                fluctuatingSeries(last, LONG_HISTORY_MONTHS, 60.0, 0.006, 0.04));
        addAsset(new SearchHit(EMERGING_ETF, "iShares Core MSCI EM IMI UCITS ETF", "XETRA", "XETR", "EUR", "ETF"),
                fluctuatingSeries(last, LONG_HISTORY_MONTHS, 25.0, 0.004, 0.07));

        addAsset(new SearchHit(SHORT_HISTORY_A, "Kurze Historie A", "NASDAQ", "XNAS", "USD", "ETF"),
                fluctuatingSeries(last, SHORT_HISTORY_MONTHS, 30.0, 0.01, 0.10));
        addAsset(new SearchHit(SHORT_HISTORY_B, "Kurze Historie B", "NASDAQ", "XNAS", "USD", "ETF"),
                fluctuatingSeries(last, SHORT_HISTORY_MONTHS, 20.0, 0.008, 0.12));
        addAsset(new SearchHit(EXACT_36_MONTHS, "Genau drei Jahre", "NASDAQ", "XNAS", "USD", "ETF"),
                fluctuatingSeries(last, 36, 40.0, 0.005, 0.05));

        addAsset(new SearchHit(CONSTANT_GROWTH, "Konstantes Wachstum", "NYSE", "XNYS", "USD", "ETF"),
                fluctuatingSeries(last, LONG_HISTORY_MONTHS, 100.0, CONSTANT_MONTHLY_GROWTH, 0.0));

        for (int i = 0; i < PERFORMANCE_SET.size(); i++) {
            String symbol = PERFORMANCE_SET.get(i);
            addAsset(new SearchHit(symbol, "Messanlage " + (i + 1), "NYSE", "XNYS", "USD", "Common Stock"),
                    fluctuatingSeries(last, LONG_HISTORY_MONTHS, 50.0 + 10 * i, 0.003 + 0.001 * i, 0.03 + 0.01 * i));
        }

        for (int i = 1; i <= MSCI_SEARCH_HIT_COUNT; i++) {
            String symbol = String.format(Locale.ROOT, "MSCI%02d", i);
            searchHits.put(symbol, new SearchHit(symbol, "MSCI Testindex " + i, "NYSE", "XNYS", "USD", "ETF"));
        }
    }

    @Override
    public List<SearchHit> search(String query) {
        failIfUnavailable();
        String needle = query.toLowerCase(Locale.ROOT);
        return searchHits.values().stream()
                .filter(hit -> hit.symbol().toLowerCase(Locale.ROOT).contains(needle)
                        || hit.name().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
    }

    @Override
    public List<PricePoint> getHistoricalPrices(String symbol, String micCode, String interval, int outputSize) {
        priceRequestCount++;
        failIfUnavailable();
        List<PricePoint> ascending = priceSeries.getOrDefault(symbol, List.of());
        return ascending.reversed().stream().limit(outputSize).toList();
    }

    public void setUnavailable(boolean unavailable) {
        this.unavailable = unavailable;
    }

    public int getPriceRequestCount() {
        return priceRequestCount;
    }

    private void addAsset(SearchHit hit, List<PricePoint> series) {
        searchHits.put(hit.symbol(), hit);
        priceSeries.put(hit.symbol(), series);
    }

    private void failIfUnavailable() {
        if (unavailable) {
            throw new UpstreamUnavailableException(Messages.UPSTREAM_UNAVAILABLE, null);
        }
    }

    private static List<PricePoint> fluctuatingSeries(LocalDate lastMonth, int months,
            double startPrice, double monthlyDrift, double amplitude) {
        LocalDate firstMonth = lastMonth.minusMonths(months - 1L);
        List<PricePoint> points = new ArrayList<>(months);
        for (int i = 0; i < months; i++) {
            double price = startPrice * Math.pow(1 + monthlyDrift, i) * (1 + amplitude * Math.sin(i));
            points.add(new PricePoint(firstMonth.plusMonths(i),
                    BigDecimal.valueOf(price).setScale(PRICE_SCALE, RoundingMode.HALF_UP)));
        }
        return List.copyOf(points);
    }
}