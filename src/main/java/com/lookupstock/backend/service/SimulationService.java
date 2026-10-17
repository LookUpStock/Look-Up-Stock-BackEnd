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
import com.lookupstock.backend.model.Portfolio;
import com.lookupstock.backend.model.SimulationParameters;
import com.lookupstock.backend.model.SimulationResult;
import com.lookupstock.backend.model.TimelinePoint;
import com.lookupstock.backend.model.User;
import com.lookupstock.backend.model.WeightedAsset;

/**
 * GRASP Controller für den Use-Case „Simulation durchführen“ (ADR-02, BE #50).
 * Koordiniert die Schritte laut Sequenzdiagramm US-04, rechnet aber nicht selbst.
 */
@Service
public class SimulationService {

    private final PortfolioService portfolioService;
    private final ParameterEstimator parameterEstimator;
    private final MonteCarloEngine engine;
    private final MetricsCalculator metricsCalculator;
    private final LongSupplier seedGenerator;

    @Autowired
    public SimulationService(PortfolioService portfolioService,
                             ParameterEstimator parameterEstimator,
                             MonteCarloEngine engine,
                             MetricsCalculator metricsCalculator) {
        this(portfolioService, parameterEstimator, engine, metricsCalculator,
                () -> ThreadLocalRandom.current().nextInt(1, 1_000_000));
    }

    // Für Tests: fester Seed-Generator
    SimulationService(PortfolioService portfolioService,
                      ParameterEstimator parameterEstimator,
                      MonteCarloEngine engine,
                      MetricsCalculator metricsCalculator,
                      LongSupplier seedGenerator) {
        this.portfolioService = portfolioService;
        this.parameterEstimator = parameterEstimator;
        this.engine = engine;
        this.metricsCalculator = metricsCalculator;
        this.seedGenerator = seedGenerator;
    }

    /**
     * Führt eine Simulation für ein eigenes Portfolio des Nutzers durch.
     * Fehler: 400 (Eingaben), 404 (Portfolio fehlt oder gehört einem anderen Nutzer, NF-04),
     * 503 (Kursdaten nicht abrufbar, NF-07), 422 (weniger als 3 Jahre Kurshistorie, E6/E8).
     */
    // readOnly-Transaktion, damit die Positionen des Portfolios nachgeladen werden können;
    // der Kursdaten-Cache schreibt in einer eigenen Transaktion (PriceCache)
    @Transactional(readOnly = true)
    public SimulationResult simulate(Long portfolioId, SimulationParameters parameters, User user) {
        // 1. Eingaben prüfen (400)
        if (portfolioId == null) {
            throw new ValidationException(Messages.PORTFOLIO_ID_REQUIRED,
                    List.of(new ValidationException.FieldError("portfolioId", Messages.PORTFOLIO_ID_REQUIRED)));
        }
        if (parameters == null) {
            throw new ValidationException(Messages.PARAMETERS_REQUIRED,
                    List.of(new ValidationException.FieldError("parameters", Messages.PARAMETERS_REQUIRED)));
        }
        parameters.validate();

        // 2. Eigenes Portfolio laden (404)
        Portfolio portfolio = portfolioService.findById(portfolioId, user);

        // 3. Ohne Seed einen erzeugen; er wird im Ergebnis zurückgegeben (NF-08)
        SimulationParameters effective = parameters.seed() != null
                ? parameters
                : parameters.withSeed(seedGenerator.getAsLong());

        long start = System.nanoTime();

        // 4. Gewichte vom Portfolio (Information Expert, ADR-03)
        List<WeightedAsset> weights = portfolio.getWeights();
        List<Asset> assets = weights.stream().map(WeightedAsset::asset).toList();

        // 5. Rendite und Volatilität schätzen (503 bzw. 422)
        EstimationResult estimation = parameterEstimator.estimate(assets);

        // 6. Pfade simulieren und Perzentile berechnen
        double[][] paths = engine.simulate(weights, estimation, effective);
        List<TimelinePoint> timeline = metricsCalculator.percentiles(paths);
        double contributions = effective.startCapital()
                + effective.monthlyContribution() * effective.horizonYears() * 12;
        var metrics = metricsCalculator.summarize(paths, contributions);

        long runtimeMs = (System.nanoTime() - start) / 1_000_000;
        return new SimulationResult(portfolio.getId(), effective, estimation, timeline, metrics, runtimeMs);
    }
}
