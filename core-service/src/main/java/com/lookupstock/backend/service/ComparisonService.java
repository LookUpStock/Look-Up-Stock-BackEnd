package com.lookupstock.backend.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.NotFoundException;
import com.lookupstock.backend.exception.ValidationException.FieldError;
import com.lookupstock.backend.exception.ValidationException;
import com.lookupstock.backend.model.Asset;
import com.lookupstock.backend.model.ComparisonEntry;
import com.lookupstock.backend.model.ComparisonResult;
import com.lookupstock.backend.model.EstimationResult;
import com.lookupstock.backend.model.Portfolio;
import com.lookupstock.backend.model.SimulationParameters;
import com.lookupstock.backend.model.SimulationRun;
import com.lookupstock.backend.model.UsageEventType;
import com.lookupstock.backend.model.User;
import com.lookupstock.backend.model.WeightedAsset;
import com.lookupstock.backend.queue.UsageEventRecorder;
import com.lookupstock.backend.repository.AssetRepository;

@Service
public class ComparisonService {

    public static final int ITEM_COUNT = 2;

    private final PortfolioService portfolioService;
    private final AssetRepository assetRepository;
    private final ParameterEstimator parameterEstimator;
    private final SimulationService simulationService;
    private final Clock clock;
    private final UsageEventRecorder usageEventRecorder;

    public ComparisonService(PortfolioService portfolioService,
                             AssetRepository assetRepository,
                             ParameterEstimator parameterEstimator,
                             SimulationService simulationService,
                             Clock clock,
                             UsageEventRecorder usageEventRecorder) {
        this.portfolioService = portfolioService;
        this.assetRepository = assetRepository;
        this.parameterEstimator = parameterEstimator;
        this.simulationService = simulationService;
        this.clock = clock;
        this.usageEventRecorder = usageEventRecorder;
    }

    // Read-only transaction so the lazy portfolio positions can be loaded.
    @Transactional(readOnly = true)
    public ComparisonResult compare(List<ComparisonEntry> entries, SimulationParameters parameters, User user) {
        validateEntries(entries);
        if (parameters == null) {
            throw new ValidationException(Messages.PARAMETERS_REQUIRED,
                    List.of(new FieldError("parameters", Messages.PARAMETERS_REQUIRED)));
        }
        parameters.validate();

        List<ResolvedEntry> resolved = entries.stream().map(entry -> resolve(entry, user)).toList();

        SimulationParameters effective = simulationService.ensureSeed(parameters);

        long start = System.nanoTime();

        Map<Long, Asset> distinctAssets = new LinkedHashMap<>();
        for (ResolvedEntry entry : resolved) {
            for (WeightedAsset weighted : entry.weights()) {
                distinctAssets.putIfAbsent(weighted.asset().getId(), weighted.asset());
            }
        }
        EstimationResult estimation = parameterEstimator.estimate(List.copyOf(distinctAssets.values()));

        List<SimulationRun> runs = resolved.stream()
                .map(entry -> simulationService.run(entry.weights(), estimation, effective))
                .toList();

        List<ComparisonResult.Item> items = new ArrayList<>();
        for (int i = 0; i < resolved.size(); i++) {
            ResolvedEntry entry = resolved.get(i);
            SimulationRun run = runs.get(i);
            items.add(new ComparisonResult.Item(entry.label(), entry.portfolioId(), entry.assetId(),
                    run.timeline(), run.metrics()));
        }
        List<ComparisonResult.Pairwise> pairwise = new ArrayList<>();
        for (int a = 0; a < runs.size(); a++) {
            for (int b = a + 1; b < runs.size(); b++) {
                pairwise.add(new ComparisonResult.Pairwise(a, b,
                        shareOfPathsWhereAIsHigher(runs.get(a).endValues(), runs.get(b).endValues())));
            }
        }

        long runtimeMs = (System.nanoTime() - start) / 1_000_000;
        usageEventRecorder.record(UsageEventType.COMPARISON_RUN);
        return new ComparisonResult(effective, estimation, items, pairwise, runtimeMs, clock.instant());
    }

    private static void validateEntries(List<ComparisonEntry> entries) {
        int count = entries == null ? 0 : entries.size();
        if (count != ITEM_COUNT) {
            throw new ValidationException(Messages.COMPARISON_ITEM_COUNT,
                    List.of(new FieldError("items", Messages.itemCountDetail(count))));
        }
        List<FieldError> errors = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            ComparisonEntry entry = entries.get(i);
            if (entry == null || (entry.portfolioId() == null) == (entry.assetId() == null)) {
                errors.add(new FieldError("items[" + i + "]", Messages.COMPARISON_ITEM_INVALID));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(Messages.COMPARISON_ITEM_INVALID, errors);
        }
    }

    private ResolvedEntry resolve(ComparisonEntry entry, User user) {
        if (entry.portfolioId() != null) {
            Portfolio portfolio = portfolioService.findById(entry.portfolioId(), user);
            return new ResolvedEntry(labelOrDefault(entry.label(), portfolio.getName()),
                    portfolio.getId(), null, portfolio.getWeights());
        }
        Asset asset = assetRepository.findById(entry.assetId())
                .orElseThrow(() -> new NotFoundException(Messages.COMPARISON_ASSET_NOT_FOUND));
        return new ResolvedEntry(labelOrDefault(entry.label(), asset.getName()),
                null, asset.getId(), List.of(new WeightedAsset(asset, BigDecimal.ONE)));
    }

    private static String labelOrDefault(String label, String fallback) {
        return label == null || label.isBlank() ? fallback : label.trim();
    }

    private static double shareOfPathsWhereAIsHigher(double[] a, double[] b) {
        int higher = 0;
        for (int p = 0; p < a.length; p++) {
            if (a[p] > b[p]) {
                higher++;
            }
        }
        return (double) higher / a.length;
    }

    private record ResolvedEntry(String label, Long portfolioId, Long assetId, List<WeightedAsset> weights) {
    }
}
