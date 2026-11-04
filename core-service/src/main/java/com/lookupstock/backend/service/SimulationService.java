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

    // Für Tests: fester Seed-Generator
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

    /**
     * Führt eine Simulation für ein eigenes Portfolio des Nutzers durch und zählt sie als
     * durchgeführte Simulation (E37).
     * Fehler: 400 (Eingaben), 404 (Portfolio fehlt oder gehört einem anderen Nutzer, NF-04),
     * 503 (Kursdaten nicht abrufbar, NF-07), 422 (weniger als 3 Jahre Kurshistorie, E6/E8).
     */
    // readOnly-Transaktion, damit die Positionen des Portfolios nachgeladen werden können;
    // der Kursdaten-Cache schreibt in einer eigenen Transaktion (PriceCache)
    @Transactional(readOnly = true)
    public SimulationResult simulate(Long portfolioId, SimulationParameters parameters, User user) {
        SimulationResult result = calculate(portfolioId, parameters, user);
        // Nur erfolgreiche Simulationen zählen (E37); Vergleich und Beispielsimulation nutzen run() und zählen eigene Ereignisse
        usageEventRecorder.record(UsageEventType.SIMULATION_RUN);
        return result;
    }

    /**
     * Rechnet wie {@link #simulate}, zählt aber keine durchgeführte Simulation.
     * Für POST /simulations mit save: true: Die Oberfläche hat vorher schon simuliert und dabei
     * SIMULATION_RUN erfasst; beim Speichern zählt nur SIMULATION_SAVED (B8, E37).
     */
    @Transactional(readOnly = true)
    public SimulationResult simulateForSave(Long portfolioId, SimulationParameters parameters, User user) {
        return calculate(portfolioId, parameters, user);
    }

    private SimulationResult calculate(Long portfolioId, SimulationParameters parameters, User user) {
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
        SimulationParameters effective = ensureSeed(parameters);

        long start = System.nanoTime();

        // 4. Gewichte vom Portfolio (Information Expert, ADR-03)
        List<WeightedAsset> weights = portfolio.getWeights();
        List<Asset> assets = weights.stream().map(WeightedAsset::asset).toList();

        // 5. Rendite und Volatilität schätzen (503 bzw. 422)
        EstimationResult estimation = parameterEstimator.estimate(assets);

        // 6. Pfade simulieren, Perzentile und Kennzahlen berechnen
        SimulationRun run = run(weights, estimation, effective);

        long runtimeMs = (System.nanoTime() - start) / 1_000_000;
        return new SimulationResult(portfolio.getId(), effective, estimation, run.timeline(), run.metrics(), runtimeMs);
    }

    /**
     * Setzt einen Seed, falls keiner angegeben ist (NF-08). Auch der Vergleich nutzt diese Methode,
     * damit die Regel nur an einer Stelle steht (ADR-05).
     */
    public SimulationParameters ensureSeed(SimulationParameters parameters) {
        return parameters.seed() != null
                ? parameters
                : parameters.withSeed(seedGenerator.getAsLong());
    }

    /**
     * Simuliert einen Satz Gewichtungen mit fertiger Schätzung und liefert Verlauf, Kennzahlen und Endwerte.
     * Wird von simulate (US-04) und vom ComparisonService (US-07, ADR-05) gemeinsam genutzt.
     * Die Engine erzeugt je Aufruf ein neues Random(seed); gleicher Seed heißt daher gleiche Zufallszahlen.
     *
     * @param parameters geprüfte Parameter mit gesetztem Seed
     */
    public SimulationRun run(List<WeightedAsset> weights, EstimationResult estimation, SimulationParameters parameters) {
        double[][] paths = engine.simulate(weights, estimation, parameters);
        List<TimelinePoint> timeline = metricsCalculator.percentiles(paths);
        Metrics metrics = metricsCalculator.metrics(paths, parameters);

        // Endwert je Pfad, Grundlage für probabilityAOutperformsB im Vergleich
        double[] endValues = new double[paths.length];
        for (int p = 0; p < paths.length; p++) {
            endValues[p] = paths[p][paths[p].length - 1];
        }
        return new SimulationRun(timeline, metrics, endValues);
    }
}
