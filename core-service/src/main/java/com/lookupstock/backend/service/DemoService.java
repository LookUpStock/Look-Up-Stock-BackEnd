package com.lookupstock.backend.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.NotFoundException;
import com.lookupstock.backend.exception.ValidationException;
import com.lookupstock.backend.model.Asset;
import com.lookupstock.backend.model.DemoPortfolio;
import com.lookupstock.backend.model.EstimationResult;
import com.lookupstock.backend.model.SimulationParameters;
import com.lookupstock.backend.model.SimulationResult;
import com.lookupstock.backend.model.SimulationRun;
import com.lookupstock.backend.model.UsageEventType;
import com.lookupstock.backend.model.WeightedAsset;
import com.lookupstock.backend.queue.UsageEventRecorder;
import com.lookupstock.backend.repository.AssetRepository;

@Service
public class DemoService {

    public static final int MAX_PATHS = 10_000;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final AssetRepository assetRepository;
    private final ParameterEstimator parameterEstimator;
    private final SimulationService simulationService;
    private final UsageEventRecorder usageEventRecorder;

    public DemoService(AssetRepository assetRepository,
                       ParameterEstimator parameterEstimator,
                       SimulationService simulationService,
                       UsageEventRecorder usageEventRecorder) {
        this.assetRepository = assetRepository;
        this.parameterEstimator = parameterEstimator;
        this.simulationService = simulationService;
        this.usageEventRecorder = usageEventRecorder;
    }

    public List<DemoPortfolio> portfolios() {
        return DemoPortfolio.ALL;
    }

    @Transactional
    public SimulationResult simulate(Long portfolioId, SimulationParameters parameters) {
        if (portfolioId == null) {
            throw new ValidationException(Messages.PORTFOLIO_ID_REQUIRED,
                    List.of(new ValidationException.FieldError("portfolioId", Messages.PORTFOLIO_ID_REQUIRED)));
        }
        if (parameters == null) {
            throw new ValidationException(Messages.PARAMETERS_REQUIRED,
                    List.of(new ValidationException.FieldError("parameters", Messages.PARAMETERS_REQUIRED)));
        }
        parameters.validate();
        if (parameters.paths() > MAX_PATHS) {
            throw new ValidationException(Messages.DEMO_PATHS_INVALID,
                    List.of(new ValidationException.FieldError("paths", Messages.valueDetail(parameters.paths()))));
        }

        DemoPortfolio demo = DemoPortfolio.ALL.stream()
                .filter(p -> p.id() == portfolioId)
                .findFirst()
                .orElseThrow(() -> new NotFoundException(Messages.DEMO_PORTFOLIO_NOT_FOUND));

        SimulationParameters effective = simulationService.ensureSeed(parameters);

        long start = System.nanoTime();

        List<WeightedAsset> weights = demo.positions().stream()
                .map(p -> new WeightedAsset(findOrCreate(p), p.weight().divide(HUNDRED)))
                .toList();
        List<Asset> assets = weights.stream().map(WeightedAsset::asset).toList();

        EstimationResult estimation = parameterEstimator.estimate(assets);

        SimulationRun run = simulationService.run(weights, estimation, effective);

        long runtimeMs = (System.nanoTime() - start) / 1_000_000;
        usageEventRecorder.record(UsageEventType.DEMO_SIMULATION_RUN);
        return new SimulationResult(demo.id(), effective, estimation, run.timeline(), run.metrics(), runtimeMs);
    }

    private Asset findOrCreate(DemoPortfolio.Position position) {
        return assetRepository.findBySymbolAndMicCode(position.symbol(), position.micCode())
                .orElseGet(() -> assetRepository.save(new Asset(
                        position.symbol(), null, position.name(), position.exchange(),
                        position.currency(), position.type(), position.micCode())));
    }
}
