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
import com.lookupstock.backend.model.WeightedAsset;
import com.lookupstock.backend.repository.AssetRepository;

/**
 * GRASP Controller für den Use-Case „Gastmodus“ (US-09, ADR-02).
 * Rechnet Beispielsimulationen ohne Anmeldung und ohne Speichern; die Rechnung selbst macht der SimulationService.
 */
@Service
public class DemoService {

    // Grenze für Gäste, damit der öffentliche Endpunkt die Last klein hält
    public static final int MAX_PATHS = 10_000;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final AssetRepository assetRepository;
    private final ParameterEstimator parameterEstimator;
    private final SimulationService simulationService;

    public DemoService(AssetRepository assetRepository,
                       ParameterEstimator parameterEstimator,
                       SimulationService simulationService) {
        this.assetRepository = assetRepository;
        this.parameterEstimator = parameterEstimator;
        this.simulationService = simulationService;
    }

    public List<DemoPortfolio> portfolios() {
        return DemoPortfolio.ALL;
    }

    /**
     * Führt eine Simulation für ein Beispielportfolio durch.
     * Fehler: 400 (Eingaben, mehr als 10.000 Pfade), 404 (unbekanntes Beispielportfolio),
     * 503 (Kursdaten nicht abrufbar), 422 (weniger als 3 Jahre Kurshistorie).
     */
    // Schreibende Transaktion: fehlende Anlagen werden in asset angelegt, damit der Kurs-Cache sie findet
    @Transactional
    public SimulationResult simulate(Long portfolioId, SimulationParameters parameters) {
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
        if (parameters.paths() > MAX_PATHS) {
            throw new ValidationException(Messages.DEMO_PATHS_INVALID,
                    List.of(new ValidationException.FieldError("paths", Messages.valueDetail(parameters.paths()))));
        }

        // 2. Beispielportfolio suchen (404)
        DemoPortfolio demo = DemoPortfolio.ALL.stream()
                .filter(p -> p.id() == portfolioId)
                .findFirst()
                .orElseThrow(() -> new NotFoundException(Messages.DEMO_PORTFOLIO_NOT_FOUND));

        // 3. Ohne Seed einen erzeugen (NF-08)
        SimulationParameters effective = simulationService.ensureSeed(parameters);

        long start = System.nanoTime();

        // 4. Gewichte bauen; Prozent → Anteil wie Portfolio.getWeights()
        List<WeightedAsset> weights = demo.positions().stream()
                .map(p -> new WeightedAsset(findOrCreate(p), p.weight().divide(HUNDRED)))
                .toList();
        List<Asset> assets = weights.stream().map(WeightedAsset::asset).toList();

        // 5. Rendite und Volatilität schätzen (503 bzw. 422)
        EstimationResult estimation = parameterEstimator.estimate(assets);

        // 6. Pfade simulieren, Perzentile und Kennzahlen berechnen
        SimulationRun run = simulationService.run(weights, estimation, effective);

        long runtimeMs = (System.nanoTime() - start) / 1_000_000;
        return new SimulationResult(demo.id(), effective, estimation, run.timeline(), run.metrics(), runtimeMs);
    }

    // wie bei der Suche (AssetService): vorhandene Anlage nutzen, sonst anlegen
    private Asset findOrCreate(DemoPortfolio.Position position) {
        return assetRepository.findBySymbolAndMicCode(position.symbol(), position.micCode())
                .orElseGet(() -> assetRepository.save(new Asset(
                        position.symbol(), null, position.name(), position.exchange(),
                        position.currency(), position.type(), position.micCode())));
    }
}
