package com.lookupstock.backend.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.lookupstock.backend.client.PricePoint;
import com.lookupstock.backend.exception.InsufficientDataException;
import com.lookupstock.backend.model.Asset;
import com.lookupstock.backend.model.Estimate;
import com.lookupstock.backend.model.EstimationResult;

/**
 * Schätzt Jahresrendite und Jahresvolatilität je Anlage aus monatlichen Kursdaten (US-04).
 * Regeln: mindestens 36 Monatswerte je Anlage (E8), sonst 422 mit allen betroffenen Anlagen (E6);
 * Anlagen ganz ohne Kurse werden gesondert gemeldet (Docs #80);
 * alle Anlagen werden im selben Zeitraum geschätzt (gemeinsamer Schätzzeitraum, Glossar).
 * Die Umrechnung passt zur MonteCarloEngine: Monatsrendite × 12, Monatsvolatilität × √12.
 */
@Component
public class ParameterEstimator {

    public static final int MIN_MONTHS = 36;
    private static final int MONTHS_PER_YEAR = 12;

    private final PriceHistoryService priceHistoryService;

    public ParameterEstimator(PriceHistoryService priceHistoryService) {
        this.priceHistoryService = priceHistoryService;
    }

    public EstimationResult estimate(List<Asset> assets) {
        // 1. Kursdaten laden und je Anlage auf mindestens 36 Monatswerte prüfen
        Map<Asset, List<PricePoint>> series = new LinkedHashMap<>();
        List<Asset> noData = new ArrayList<>();
        List<Asset> insufficient = new ArrayList<>();
        for (Asset asset : assets) {
            List<PricePoint> prices = usable(priceHistoryService.getMonthlyPrices(asset));
            if (prices.isEmpty()) {
                // Anbieter liefert gar keine Kurse (z. B. Xetra im kostenlosen Tarif) – nicht „zu kurz“ (Docs #80)
                noData.add(asset);
            } else if (prices.size() < MIN_MONTHS) {
                insufficient.add(asset);
            } else {
                series.put(asset, prices);
            }
        }
        if (!noData.isEmpty() || !insufficient.isEmpty()) {
            throw new InsufficientDataException(noData, insufficient);
        }

        // 2. Gemeinsamer Schätzzeitraum: spätester Beginn bis frühestes Ende
        LocalDate from = series.values().stream().map(p -> p.getFirst().date())
                .max(Comparator.naturalOrder()).orElseThrow();
        LocalDate to = series.values().stream().map(p -> p.getLast().date())
                .min(Comparator.naturalOrder()).orElseThrow();

        Map<Asset, List<PricePoint>> windows = new LinkedHashMap<>();
        series.forEach((asset, prices) -> windows.put(asset, within(prices, from, to)));

        // Überschneiden sich die Zeiträume zu wenig, liegt es an den Anlagen mit dem spätesten Beginn
        boolean windowTooShort = windows.values().stream().anyMatch(w -> w.size() < MIN_MONTHS);
        if (windowTooShort) {
            series.forEach((asset, prices) -> {
                if (prices.getFirst().date().equals(from)) {
                    insufficient.add(asset);
                }
            });
            throw new InsufficientDataException(insufficient);
        }

        // 3. Rendite und Volatilität je Anlage
        List<Estimate> estimates = new ArrayList<>();
        windows.forEach((asset, prices) -> {
            double[] returns = monthlyReturns(prices);
            double mean = mean(returns);
            double sd = sampleStandardDeviation(returns, mean);
            estimates.add(new Estimate(asset, mean * MONTHS_PER_YEAR, sd * Math.sqrt(MONTHS_PER_YEAR)));
        });
        return new EstimationResult(from, to, estimates);
    }

    // aufsteigend sortiert, nur positive Schlusskurse (Division durch 0 vermeiden)
    private static List<PricePoint> usable(List<PricePoint> prices) {
        return prices.stream()
                .filter(p -> p.date() != null && p.close() != null && p.close().signum() > 0)
                .sorted(Comparator.comparing(PricePoint::date))
                .toList();
    }

    private static List<PricePoint> within(List<PricePoint> prices, LocalDate from, LocalDate to) {
        return prices.stream()
                .filter(p -> !p.date().isBefore(from) && !p.date().isAfter(to))
                .toList();
    }

    static double[] monthlyReturns(List<PricePoint> prices) {
        double[] returns = new double[prices.size() - 1];
        for (int i = 1; i < prices.size(); i++) {
            double previous = prices.get(i - 1).close().doubleValue();
            double current = prices.get(i).close().doubleValue();
            returns[i - 1] = current / previous - 1.0;
        }
        return returns;
    }

    private static double mean(double[] values) {
        double sum = 0.0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }

    private static double sampleStandardDeviation(double[] values, double mean) {
        if (values.length < 2) {
            return 0.0;
        }
        double sum = 0.0;
        for (double v : values) {
            sum += (v - mean) * (v - mean);
        }
        return Math.sqrt(sum / (values.length - 1));
    }
}
