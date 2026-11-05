package com.lookupstock.backend.service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.ValidationException;
import com.lookupstock.backend.model.Asset;
import com.lookupstock.backend.model.EstimationResult;
import com.lookupstock.backend.model.Metrics;
import com.lookupstock.backend.model.Portfolio;
import com.lookupstock.backend.model.SimulationParameters;
import com.lookupstock.backend.model.SimulationResult;
import com.lookupstock.backend.model.SimulationRun;
import com.lookupstock.backend.model.TimelinePoint;
import com.lookupstock.backend.model.UsageEventType;
import com.lookupstock.backend.model.User;
import com.lookupstock.backend.model.WeightedAsset;
import com.lookupstock.backend.queue.UsageEventRecorder;

@Service
public class SimulationService {

    private final PortfolioService portfolioService;
    private final ParameterEstimator parameterEstimator;
    private final MonteCarloEngine engine;
    private final MetricsCalculator metricsCalculator;
    private final UsageEventRecorder usageEventRecorder;
    private final LongSupplier seedGenerator;

    @Autowired
    public SimulationService(PortfolioService portfolioService,
                             ParameterEstimator parameterEstimator,
                             MonteCarloEngine engine,
                             MetricsCalculator metricsCalculator,
                             UsageEventRecorder usageEventRecorder) {
        this(portfolioService, parameterEstimator, engine, metricsCalculator, usageEventRecorder,
                () -> ThreadLocalRandom.current().nextInt(1, 1_000_000));
    }

    SimulationService(PortfolioService portfolioService,
                      ParameterEstimator parameterEstimator,
                      MonteCarloEngine engine,
                      MetricsCalculator metricsCalculator,
                      UsageEventRecorder usageEventRecorder,
                      LongSupplier seedGenerator) {
        this.portfolioService = portfolioService;
        this.parameterEstimator = parameterEstimator;
        this.engine = engine;
        this.metricsCalculator = metricsCalculator;
        this.usageEventRecorder = usageEventRecorder;
        this.seedGenerator = seedGenerator;
    }

    // Read-only transaction so the lazy portfolio positions can be loaded.
    @Transactional(readOnly = true)
    public SimulationResult simulate(Long portfolioId, SimulationParameters parameters, User user) {
        SimulationResult result = calculate(portfolioId, parameters, user);
        usageEventRecorder.record(UsageEventType.SIMULATION_RUN);
        return result;
    }

    // Like simulate, but does not count a simulation run: the UI already counted it when the user simulated.
    @Transactional(readOnly = true)
    public SimulationResult simulateForSave(Long portfolioId, SimulationParameters parameters, User user) {
        return calculate(portfolioId, parameters, user);
    }

    private SimulationResult calculate(Long portfolioId, SimulationParameters parameters, User user) {
        if (portfolioId == null) {
            throw new ValidationException(Messages.PORTFOLIO_ID_REQUIRED,
                    List.of(new ValidationException.FieldError("portfolioId", Messages.PORTFOLIO_ID_REQUIRED)));
        }
        if (parameters == null) {
            throw new ValidationException(Messages.PARAMETERS_REQUIRED,
                    List.of(new ValidationException.FieldError("parameters", Messages.PARAMETERS_REQUIRED)));
        }
        parameters.validate();

        Portfolio portfolio = portfolioService.findById(portfolioId, user);

        SimulationParameters effective = ensureSeed(parameters);

        long start = System.nanoTime();

        List<WeightedAsset> weights = portfolio.getWeights();
        List<Asset> assets = weights.stream().map(WeightedAsset::asset).toList();

        EstimationResult estimation = parameterEstimator.estimate(assets);

        SimulationRun run = run(weights, estimation, effective);

        long runtimeMs = (System.nanoTime() - start) / 1_000_000;
        return new SimulationResult(portfolio.getId(), effective, estimation, run.timeline(), run.metrics(), runtimeMs);
    }

    public SimulationParameters ensureSeed(SimulationParameters parameters) {
        return parameters.seed() != null
                ? parameters
                : parameters.withSeed(seedGenerator.getAsLong());
    }

    // The engine creates a new Random(seed) per call, so equal seeds give equal random numbers.
    public SimulationRun run(List<WeightedAsset> weights, EstimationResult estimation, SimulationParameters parameters) {
        double[][] paths = engine.simulate(weights, estimation, parameters);
        List<TimelinePoint> timeline = metricsCalculator.percentiles(paths);
        Metrics metrics = metricsCalculator.metrics(paths, parameters);

        double[] endValues = new double[paths.length];
        for (int p = 0; p < paths.length; p++) {
            endValues[p] = paths[p][paths[p].length - 1];
        }
        return new SimulationRun(timeline, metrics, endValues);
    }
}
