package com.lookupstock.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.lookupstock.backend.model.TimelinePoint;
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

    // ---------- Anlagehorizont an den Grenzen (AT-04-10) ----------

    @ParameterizedTest
    @CsvSource({
            "1, 13",     // 1 Jahr = 12 Monate + Monat 0
            "40, 481"    // 40 Jahre = 480 Monate + Monat 0
    })
    void simulate_horizonAtBoundary_returnsExpectedNumberOfMonths_AT0410(int horizonYears, int expectedColumns) {
        Asset asset = assetWithId(17L);

        double[][] paths = engine.simulate(
                List.of(new WeightedAsset(asset, BigDecimal.ONE)),
                estimationFor(new Estimate(asset, 0.07, 0.15)),
                new SimulationParameters(10_000.0, 200.0, horizonYears, 1_000, 42L));

        assertThat(paths[0]).hasSize(expectedColumns);
    }

    // ---------- Startkapital und Sparrate einzeln (AT-04-11) ----------

    @Test
    void simulate_onlyContribution_matchesSavingsPlanFormula() {
        Asset asset = assetWithId(17L);
        double contribution = 200.0;
        double r = 0.06 / 12;
        int n = 5 * 12;

        double[][] paths = engine.simulate(
                List.of(new WeightedAsset(asset, BigDecimal.ONE)),
                estimationFor(new Estimate(asset, 0.06, 0.0)),
                new SimulationParameters(0.0, contribution, 5, 1_000, 42L));

        // Ohne Startkapital zählt nur die Sparplanformel
        double expected = contribution * (Math.pow(1 + r, n) - 1) / r;
        assertThat(paths[0][0]).isEqualTo(0.0);
        assertThat(paths[0][n]).isCloseTo(expected, within(1e-6));
    }

    @Test
    void simulate_onlyStartCapital_matchesCompoundInterest() {
        Asset asset = assetWithId(17L);
        double startCapital = 10_000.0;
        double r = 0.06 / 12;
        int n = 5 * 12;

        double[][] paths = engine.simulate(
                List.of(new WeightedAsset(asset, BigDecimal.ONE)),
                estimationFor(new Estimate(asset, 0.06, 0.0)),
                new SimulationParameters(startCapital, 0.0, 5, 1_000, 42L));

        // Ohne Sparrate zählt nur der Zinseszins auf das Startkapital
        double expected = startCapital * Math.pow(1 + r, n);
        assertThat(paths[0][n]).isCloseTo(expected, within(1e-6));
    }

    // ---------- Wert bleibt nicht negativ ----------

    @Test
    void simulate_extremeVolatility_valuesNeverNegative() {
        Asset asset = assetWithId(17L);

        // 500 % Volatilität pro Jahr: Monatsrenditen unter −100 % kommen häufig vor
        double[][] paths = engine.simulate(
                List.of(new WeightedAsset(asset, BigDecimal.ONE)),
                estimationFor(new Estimate(asset, 0.0, 5.0)),
                new SimulationParameters(10_000.0, 0.0, 5, 1_000, 42L));

        for (double[] path : paths) {
            for (double value : path) {
                assertThat(value).isGreaterThanOrEqualTo(0.0);
            }
        }
    }

    // ---------- Gleiche Zeitreihe bei gleichem Seed (AT-04-13, NF-08) ----------

    @Test
    void simulateAndPercentiles_sameSeed_returnIdenticalTimeline_AT0413() {
        MetricsCalculator calculator = new MetricsCalculator();
        Asset world = assetWithId(17L);
        Asset emerging = assetWithId(23L);
        List<WeightedAsset> weights = List.of(
                new WeightedAsset(world, new BigDecimal("0.70")),
                new WeightedAsset(emerging, new BigDecimal("0.30")));
        EstimationResult estimation = estimationFor(
                new Estimate(world, 0.078, 0.152),
                new Estimate(emerging, 0.051, 0.183));
        SimulationParameters parameters = new SimulationParameters(10_000.0, 200.0, 20, 10_000, 42L);

        List<TimelinePoint> first = calculator.percentiles(engine.simulate(weights, estimation, parameters));
        List<TimelinePoint> second = calculator.percentiles(engine.simulate(weights, estimation, parameters));

        assertThat(first).hasSize(241);
        assertThat(first).isEqualTo(second);
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