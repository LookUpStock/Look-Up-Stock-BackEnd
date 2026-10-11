package com.lookupstock.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.lookupstock.backend.model.Asset;
import com.lookupstock.backend.model.Estimate;
import com.lookupstock.backend.model.EstimationResult;
import com.lookupstock.backend.model.SimulationParameters;
import com.lookupstock.backend.model.WeightedAsset;

// KI-Log Nr. XX: Unit-Tests mit KI erstellt, vom Team geprüft und angepasst

class MonteCarloEngineTest {

    private final MonteCarloEngine engine = new MonteCarloEngine();

    // ---------- Aufbau des Ergebnisses ----------

    @Test
    void simulate_returnsOneRowPerPathAndOneColumnPerMonthPlusStart() {
        Asset asset = assetWithId(17L);
        SimulationParameters parameters = new SimulationParameters(10_000.0, 200.0, 2, 1_000, 42L);

        double[][] paths = engine.simulate(
                List.of(new WeightedAsset(asset, BigDecimal.ONE)),
                estimationFor(new Estimate(asset, 0.07, 0.15)),
                parameters);

        // 1.000 Pfade, 2 Jahre = 24 Monate + Monat 0 = 25 Spalten
        assertThat(paths).hasNumberOfRows(1_000);
        assertThat(paths[0]).hasSize(25);
        // Jeder Pfad beginnt beim Startkapital
        for (double[] path : paths) {
            assertThat(path[0]).isEqualTo(10_000.0);
        }
    }

    // ---------- Reproduzierbarkeit (NF-08) ----------

    @Test
    void simulate_sameSeed_returnsIdenticalPaths_NF08() {
        Asset asset = assetWithId(17L);
        List<WeightedAsset> weights = List.of(new WeightedAsset(asset, BigDecimal.ONE));
        EstimationResult estimation = estimationFor(new Estimate(asset, 0.078, 0.152));
        SimulationParameters parameters = new SimulationParameters(10_000.0, 200.0, 20, 1_000, 42L);

        double[][] first = engine.simulate(weights, estimation, parameters);
        double[][] second = engine.simulate(weights, estimation, parameters);

        assertThat(first).isDeepEqualTo(second);
    }

    @Test
    void simulate_differentSeed_returnsDifferentPaths() {
        Asset asset = assetWithId(17L);
        List<WeightedAsset> weights = List.of(new WeightedAsset(asset, BigDecimal.ONE));
        EstimationResult estimation = estimationFor(new Estimate(asset, 0.078, 0.152));

        double[][] seed42 = engine.simulate(weights, estimation,
                new SimulationParameters(10_000.0, 200.0, 5, 1_000, 42L));
        double[][] seed43 = engine.simulate(weights, estimation,
                new SimulationParameters(10_000.0, 200.0, 5, 1_000, 43L));

        assertThat(seed42).isNotEqualTo(seed43);
    }

    // ---------- Test-Orakel: Volatilität 0 ----------

    @Test
    void simulate_zeroVolatility_matchesExactFormula() {
        Asset asset = assetWithId(17L);
        double annualReturn = 0.06;
        double startCapital = 10_000.0;
        double contribution = 200.0;
        int years = 10;

        double[][] paths = engine.simulate(
                List.of(new WeightedAsset(asset, BigDecimal.ONE)),
                estimationFor(new Estimate(asset, annualReturn, 0.0)),
                new SimulationParameters(startCapital, contribution, years, 1_000, 42L));

        // Erwarteter Endwert: Zinseszins auf das Startkapital plus Sparplanformel
        double r = annualReturn / 12;
        int n = years * 12;
        double growth = Math.pow(1 + r, n);
        double expected = startCapital * growth + contribution * (growth - 1) / r;

        // Ohne Zufall sind alle Pfade gleich und treffen den exakten Wert
        for (double[] path : paths) {
            assertThat(path[n]).isCloseTo(expected, within(1e-6));
        }
    }

    // ---------- Monatliches Rebalancing ----------

    @Test
    void simulate_twoAssets_usesWeightedAverageReturn() {
        Asset world = assetWithId(17L);
        Asset emerging = assetWithId(23L);
        List<WeightedAsset> weights = List.of(
                new WeightedAsset(world, new BigDecimal("0.70")),
                new WeightedAsset(emerging, new BigDecimal("0.30")));
        // 12 % pro Jahr = 1 % pro Monat; zweite Anlage 0 %; kein Zufall
        EstimationResult estimation = estimationFor(
                new Estimate(world, 0.12, 0.0),
                new Estimate(emerging, 0.0, 0.0));

        double[][] paths = engine.simulate(weights, estimation,
                new SimulationParameters(10_000.0, 0.0, 1, 1_000, 42L));

        // 0,7 × 1 % + 0,3 × 0 % = 0,7 % → 10.000 € werden nach einem Monat zu 10.070 €
        assertThat(paths[0][1]).isCloseTo(10_070.0, within(1e-9));
    }

    // ---------- Fehlender Seed ----------

    @Test
    void simulate_withoutSeed_throwsException() {
        Asset asset = assetWithId(17L);

        assertThatThrownBy(() -> engine.simulate(
                List.of(new WeightedAsset(asset, BigDecimal.ONE)),
                estimationFor(new Estimate(asset, 0.07, 0.15)),
                new SimulationParameters(10_000.0, 200.0, 1, 1_000, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- Hilfsmethoden ----------

    // Erzeugt eine Testanlage mit gesetzter ID (sonst vergibt die Datenbank die ID)
    private static Asset assetWithId(long id) {
        Asset asset = new Asset("TEST" + id, null, "Testanlage " + id, "XETRA", "EUR", "ETF", null);
        ReflectionTestUtils.setField(asset, "id", id);
        return asset;
    }

    // Verpackt Schätzungen in ein EstimationResult mit beliebigem Zeitraum
    private static EstimationResult estimationFor(Estimate... estimates) {
        return new EstimationResult(LocalDate.of(2016, 9, 1), LocalDate.of(2026, 8, 31), List.of(estimates));
    }
}