package com.lookupstock.backend.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lookupstock.backend.dto.ComparisonRequest;
import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.NotFoundException;
import com.lookupstock.backend.exception.ValidationException;
import com.lookupstock.backend.model.Asset;
import com.lookupstock.backend.model.ComparisonResult;
import com.lookupstock.backend.model.EstimationResult;
import com.lookupstock.backend.model.Portfolio;
import com.lookupstock.backend.model.SimulationParameters;
import com.lookupstock.backend.model.TimelinePoint;
import com.lookupstock.backend.model.User;
import com.lookupstock.backend.model.WeightedAsset;
import com.lookupstock.backend.repository.AssetRepository;

@Service
public class ComparisonService {
    private final PortfolioService portfolioService;
    private final AssetRepository assetRepository;
    private final ParameterEstimator parameterEstimator;
    private final MonteCarloEngine engine;
    private final MetricsCalculator metricsCalculator;

    public ComparisonService(PortfolioService portfolioService, AssetRepository assetRepository,
                             ParameterEstimator parameterEstimator, MonteCarloEngine engine,
                             MetricsCalculator metricsCalculator) {
        this.portfolioService = portfolioService;
        this.assetRepository = assetRepository;
        this.parameterEstimator = parameterEstimator;
        this.engine = engine;
        this.metricsCalculator = metricsCalculator;
    }

    @Transactional(readOnly = true)
    public ComparisonResult compare(List<ComparisonRequest.Item> items,
                                    SimulationParameters parameters, User user) {
        validateItems(items);
        if (parameters == null) {
            throw new ValidationException(Messages.PARAMETERS_REQUIRED,
                    List.of(new ValidationException.FieldError("parameters", Messages.PARAMETERS_REQUIRED)));
        }
        parameters.validate();

        List<ResolvedItem> resolved = items.stream().map(item -> resolve(item, user)).toList();
        Map<Long, Asset> allAssets = new LinkedHashMap<>();
        resolved.forEach(item -> item.weights().forEach(weight -> allAssets.putIfAbsent(
                weight.asset().getId(), weight.asset())));
        EstimationResult estimation = parameterEstimator.estimate(new ArrayList<>(allAssets.values()));

        SimulationParameters effective = parameters.seed() == null
                ? parameters.withSeed(java.util.concurrent.ThreadLocalRandom.current().nextInt(1, 1_000_000))
                : parameters;
        long start = System.nanoTime();
        List<ComparisonResult.ItemResult> results = new ArrayList<>(resolved.size());
        double contributions = effective.startCapital()
                + effective.monthlyContribution() * effective.horizonYears() * 12;

        MonteCarloEngine.ComparisonPaths commonPaths = engine.compare(
                resolved.get(0).weights(), resolved.get(1).weights(), estimation, effective);
        double[][][] pathsByItem = { commonPaths.first(), commonPaths.second() };
        for (int i = 0; i < resolved.size(); i++) {
            ResolvedItem item = resolved.get(i);
            double[][] paths = pathsByItem[i];
            results.add(new ComparisonResult.ItemResult(item.label(), item.portfolioId(), item.assetId(),
                    metricsCalculator.percentiles(paths), calculateMetrics(paths, contributions)));
        }

        long better = 0;
        for (int path = 0; path < effective.paths(); path++) {
            if (pathsByItem[0][path][effective.horizonMonths()]
                    > pathsByItem[1][path][effective.horizonMonths()]) {
                better++;
            }
        }
        double probability = (double) better / effective.paths();
        return new ComparisonResult(effective, List.copyOf(results),
                List.of(new ComparisonResult.PairwiseResult(0, 1, probability)),
                (System.nanoTime() - start) / 1_000_000);
    }

    private ResolvedItem resolve(ComparisonRequest.Item item, User user) {
        if (item.portfolioId() != null) {
            Portfolio portfolio = portfolioService.findById(item.portfolioId(), user);
            return new ResolvedItem(label(item.label(), portfolio.getName()), portfolio.getId(), null,
                    portfolio.getWeights());
        }
        Asset asset = assetRepository.findById(item.assetId())
                .orElseThrow(() -> new NotFoundException(Messages.ASSET_NOT_FOUND));
        return new ResolvedItem(label(item.label(), asset.getName()), null, asset.getId(),
                List.of(new WeightedAsset(asset, BigDecimal.ONE)));
    }

    private static void validateItems(List<ComparisonRequest.Item> items) {
        if (items == null || items.size() != 2) {
            throw new ValidationException(Messages.COMPARISON_ITEMS_REQUIRED,
                    List.of(new ValidationException.FieldError("items", Messages.COMPARISON_ITEMS_REQUIRED)));
        }
        for (int i = 0; i < items.size(); i++) {
            ComparisonRequest.Item item = items.get(i);
            boolean hasPortfolio = item != null && item.portfolioId() != null;
            boolean hasAsset = item != null && item.assetId() != null;
            if (hasPortfolio == hasAsset) {
                throw new ValidationException(Messages.COMPARISON_ITEM_INVALID,
                        List.of(new ValidationException.FieldError("items[" + i + "]", Messages.COMPARISON_ITEM_INVALID)));
            }
        }
    }

    private static String label(String requested, String fallback) {
        return requested == null || requested.isBlank() ? fallback : requested.trim();
    }

    private static ComparisonResult.Metrics calculateMetrics(double[][] paths, double contributions) {
        int lastMonth = paths[0].length - 1;
        double[] endings = new double[paths.length];
        double[] drawdowns = new double[paths.length];
        for (int i = 0; i < paths.length; i++) {
            endings[i] = paths[i][lastMonth];
            double peak = paths[i][0];
            double maxDrawdown = 0.0;
            for (double value : paths[i]) {
                peak = Math.max(peak, value);
                if (peak > 0.0) maxDrawdown = Math.max(maxDrawdown, (peak - value) / peak);
            }
            drawdowns[i] = maxDrawdown;
        }
        java.util.Arrays.sort(endings);
        java.util.Arrays.sort(drawdowns);
        double p5 = percentile(endings, 0.05);
        return new ComparisonResult.Metrics(contributions, percentile(endings, 0.50), p5,
                percentile(endings, 0.95), Math.max(0.0, contributions - p5),
                percentile(drawdowns, 0.50), null);
    }

    private static double percentile(double[] sorted, double q) {
        double position = q * (sorted.length - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        return sorted[lower] + (position - lower) * (sorted[upper] - sorted[lower]);
    }

    private record ResolvedItem(String label, Long portfolioId, Long assetId, List<WeightedAsset> weights) {
    }
}
