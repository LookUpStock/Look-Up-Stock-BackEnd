package com.lookupstock.backend.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.lookupstock.backend.client.PricePoint;
import com.lookupstock.backend.dto.AssetStatsResponse;
import com.lookupstock.backend.exception.ValidationException;
import com.lookupstock.backend.model.Asset;
import com.lookupstock.backend.repository.AssetRepository;

@Service
public class AssetStatisticsService {

    private static final int MINIMUM_PRICE_POINTS = 2;
    private static final int TRADING_DAYS_PER_YEAR = 252;

    private final PriceHistoryService priceHistoryService;
    private final AssetRepository assetRepository;

    public AssetStatisticsService(PriceHistoryService priceHistoryService, AssetRepository assetRepository) {
        this.priceHistoryService = priceHistoryService;
        this.assetRepository = assetRepository;
    }

    public AssetStatsResponse calculate(String symbol, String interval, Integer outputSize) {
        String normalizedSymbol = symbol == null ? "" : symbol.trim();
        String normalizedInterval = interval == null || interval.isBlank() ? "1day" : interval.trim();
        int size = outputSize == null ? 30 : outputSize;

        if (normalizedSymbol.isBlank()) {
            throw new ValidationException("Bitte ein Symbol angeben.",
                    List.of(new ValidationException.FieldError("symbol", "Bitte ein Symbol angeben.")));
        }
        if (size < MINIMUM_PRICE_POINTS) {
            throw new ValidationException("Für die Berechnung werden mindestens 2 Kurse benötigt.",
                    List.of(new ValidationException.FieldError("outputSize", "Für die Berechnung werden mindestens 2 Kurse benötigt.")));
        }

        Asset asset = assetRepository.findFirstBySymbolIgnoreCaseOrderByIdAsc(normalizedSymbol)
                .orElseGet(() -> new Asset(normalizedSymbol, null, normalizedSymbol, null, null, null, null));
        List<PricePoint> prices = priceHistoryService.getPrices(asset, normalizedInterval, size);
        if (prices == null || prices.size() < MINIMUM_PRICE_POINTS) {
            throw new ValidationException("Für das Symbol wurden zu wenige historische Kurse gefunden.",
                    List.of(new ValidationException.FieldError("symbol", "Für das Symbol wurden zu wenige historische Kurse gefunden.")));
        }

        List<BigDecimal> returns = calculateReturns(prices);
        BigDecimal totalReturn = calculateTotalReturn(prices.getFirst().close(), prices.getLast().close());
        BigDecimal avgDailyReturn = calculateAverage(returns);
        BigDecimal volatility = calculateVolatility(returns, avgDailyReturn);
        BigDecimal annualizedVolatility = volatility.multiply(BigDecimal.valueOf(Math.sqrt(TRADING_DAYS_PER_YEAR)), MathContext.DECIMAL64);

        return new AssetStatsResponse(
                normalizedSymbol,
                normalizedInterval,
                prices.size(),
                totalReturn,
                annualizedVolatility,
                avgDailyReturn,
                prices.getFirst().date().toString(),
                prices.getLast().date().toString());
    }

    private List<BigDecimal> calculateReturns(List<PricePoint> prices) {
        List<BigDecimal> returns = new ArrayList<>();
        for (int i = 1; i < prices.size(); i++) {
            BigDecimal previous = prices.get(i - 1).close();
            BigDecimal current = prices.get(i).close();
            if (previous == null || current == null || previous.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            returns.add(current.divide(previous, MathContext.DECIMAL64).subtract(BigDecimal.ONE));
        }
        if (returns.isEmpty()) {
            throw new ValidationException("Es konnten keine gültigen Renditen aus den Kursdaten berechnet werden.",
                    List.of(new ValidationException.FieldError("symbol", "Es konnten keine gültigen Renditen aus den Kursdaten berechnet werden.")));
        }
        return returns;
    }

    private BigDecimal calculateTotalReturn(BigDecimal first, BigDecimal last) {
        if (first == null || last == null || first.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return last.divide(first, MathContext.DECIMAL64).subtract(BigDecimal.ONE);
    }

    private BigDecimal calculateAverage(List<BigDecimal> values) {
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), MathContext.DECIMAL64);
    }

    private BigDecimal calculateVolatility(List<BigDecimal> returns, BigDecimal averageReturn) {
        BigDecimal varianceSum = BigDecimal.ZERO;
        for (BigDecimal rate : returns) {
            BigDecimal diff = rate.subtract(averageReturn);
            varianceSum = varianceSum.add(diff.multiply(diff));
        }
        BigDecimal variance = varianceSum.divide(BigDecimal.valueOf(Math.max(returns.size() - 1, 1)), MathContext.DECIMAL64);
        return BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));
    }
}
