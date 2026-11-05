package com.lookupstock.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Random;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import com.lookupstock.backend.model.Metrics;
import com.lookupstock.backend.model.SimulationParameters;
import com.lookupstock.backend.model.TimelinePoint;

class MetricsCalculatorTest {

    private final MetricsCalculator calculator = new MetricsCalculator();

    @Test
    void percentiles_elevenUnsortedValues_returnsInterpolatedValues() {
        double[][] paths = {
                {50}, {10}, {100}, {0}, {70}, {30}, {90}, {20}, {80}, {40}, {60}
        };

        List<TimelinePoint> timeline = calculator.percentiles(paths);

        TimelinePoint point = timeline.get(0);
        assertThat(point.p5()).isCloseTo(5.0, within(1e-9));
        assertThat(point.p50()).isCloseTo(50.0, within(1e-9));
        assertThat(point.p95()).isCloseTo(95.0, within(1e-9));
    }

    @Test
    void percentiles_returnsOnePointPerMonthStartingAtZero() {
        double[][] paths = {
                {100, 110, 120, 130},
                {100, 105, 115, 125},
                {100, 95, 90, 85}
        };

        List<TimelinePoint> timeline = calculator.percentiles(paths);

        assertThat(timeline).hasSize(4);
        assertThat(timeline).extracting(TimelinePoint::month).containsExactly(0, 1, 2, 3);
    }

    @Test
    void percentiles_identicalPaths_allPercentilesEqual() {
        double[][] paths = {
                {10_000, 10_200},
                {10_000, 10_200},
                {10_000, 10_200}
        };

        TimelinePoint lastPoint = calculator.percentiles(paths).get(1);

        assertThat(lastPoint.p5()).isEqualTo(10_200.0);
        assertThat(lastPoint.p50()).isEqualTo(10_200.0);
        assertThat(lastPoint.p95()).isEqualTo(10_200.0);
    }

    @Test
    void percentiles_randomPaths_p5NotAboveP50NotAboveP95_AT0403() {
        Random random = new Random(42);
        double[][] paths = new double[1_000][25];
        for (double[] path : paths) {
            for (int m = 0; m < path.length; m++) {
                path[m] = 10_000 + random.nextGaussian() * 2_000;
            }
        }

        List<TimelinePoint> timeline = calculator.percentiles(paths);

        for (TimelinePoint point : timeline) {
            assertThat(point.p5()).isLessThanOrEqualTo(point.p50());
            assertThat(point.p50()).isLessThanOrEqualTo(point.p95());
        }
    }

    @Test
    void percentiles_doesNotModifyInput() {
        double[][] paths = {
                {100, 300},
                {100, 100},
                {100, 200}
        };
        double[][] copy = {
                {100, 300},
                {100, 100},
                {100, 200}
        };

        calculator.percentiles(paths);

        assertThat(paths).isDeepEqualTo(copy);
    }

    @Test
    void percentiles_emptyOrNullInput_throwsException() {
        assertThatThrownBy(() -> calculator.percentiles(new double[0][0]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.percentiles(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static double[][] pathsWithEndValues(double... endValues) {
        double[][] paths = new double[endValues.length][13];
        for (int p = 0; p < endValues.length; p++) {
            Arrays.fill(paths[p], 1_000);
            paths[p][12] = endValues[p];
        }
        return paths;
    }

    @Test
    void metrics_knownExample_returnsContributionsEndValuesAndValueAtRisk() {
        double[][] paths = pathsWithEndValues(50, 10, 100, 0, 70, 30, 90, 20, 80, 40, 60);
        SimulationParameters parameters = new SimulationParameters(1_000.0, 100.0, 1, 1_000, 42L);

        Metrics metrics = calculator.metrics(paths, parameters);

        assertThat(metrics.totalContributions()).isCloseTo(2_200.0, within(1e-9));
        assertThat(metrics.p5EndValue()).isCloseTo(5.0, within(1e-9));
        assertThat(metrics.medianEndValue()).isCloseTo(50.0, within(1e-9));
        assertThat(metrics.p95EndValue()).isCloseTo(95.0, within(1e-9));
        assertThat(metrics.valueAtRisk95()).isCloseTo(2_195.0, within(1e-9));
    }

    @Test
    void metrics_contributionsFromApiExample_returns58000() {
        double[][] paths = new double[1][241];
        SimulationParameters parameters = new SimulationParameters(10_000.0, 200.0, 20, 10_000, 42L);

        assertThat(calculator.metrics(paths, parameters).totalContributions()).isEqualTo(58_000.0);
    }

    @Test
    void metrics_p5AboveContributions_valueAtRiskIsZero() {
        double[][] paths = { {100, 110, 120} };
        SimulationParameters parameters = new SimulationParameters(100.0, 0.0, 1, 1_000, 1L);

        assertThat(calculator.metrics(paths, parameters).valueAtRisk95()).isZero();
    }

    @Test
    void metrics_drawdownFromPeak120To90_returnsQuarter() {
        double[][] paths = { {100, 120, 90, 130} };
        SimulationParameters parameters = new SimulationParameters(100.0, 0.0, 1, 1_000, 1L);

        assertThat(calculator.metrics(paths, parameters).maxDrawdownMedian()).isCloseTo(0.25, within(1e-9));
    }

    @Test
    void metrics_pathOnlyRising_drawdownIsZero() {
        double[][] paths = { {100, 110, 120} };
        SimulationParameters parameters = new SimulationParameters(100.0, 0.0, 1, 1_000, 1L);

        assertThat(calculator.metrics(paths, parameters).maxDrawdownMedian()).isZero();
    }

    @Test
    void metrics_startCapitalZero_noDivisionByZero() {
        double[][] paths = { {0, 0, 50, 40} };
        SimulationParameters parameters = new SimulationParameters(0.0, 100.0, 1, 1_000, 1L);

        assertThat(calculator.metrics(paths, parameters).maxDrawdownMedian()).isCloseTo(0.2, within(1e-9));
    }

    @Test
    void metrics_withTarget_returnsShareOfPathsReachingIt() {
        double[][] paths = pathsWithEndValues(50, 10, 100, 0, 70, 30, 90, 20, 80, 40, 60);
        SimulationParameters parameters = new SimulationParameters(1_000.0, 100.0, 1, 1_000, 42L, 60.0);

        assertThat(calculator.metrics(paths, parameters).targetProbability()).isCloseTo(5.0 / 11, within(1e-9));
    }

    @Test
    void metrics_withoutTarget_targetProbabilityIsNull() {
        double[][] paths = pathsWithEndValues(10, 20, 30);
        SimulationParameters parameters = new SimulationParameters(1_000.0, 100.0, 1, 1_000, 42L);

        assertThat(calculator.metrics(paths, parameters).targetProbability()).isNull();
    }

    @Test
    void metrics_endValuesMatchLastTimelinePoint() {
        Random random = new Random(7);
        double[][] paths = new double[500][13];
        for (double[] path : paths) {
            for (int m = 0; m < path.length; m++) {
                path[m] = 10_000 + random.nextGaussian() * 1_000;
            }
        }
        SimulationParameters parameters = new SimulationParameters(10_000.0, 0.0, 1, 1_000, 7L);

        Metrics metrics = calculator.metrics(paths, parameters);
        TimelinePoint last = calculator.percentiles(paths).get(12);

        assertThat(metrics.p5EndValue()).isEqualTo(last.p5());
        assertThat(metrics.medianEndValue()).isEqualTo(last.p50());
        assertThat(metrics.p95EndValue()).isEqualTo(last.p95());
    }

    @Test
    void metrics_sameInput_sameResult_NF08() {
        double[][] paths = pathsWithEndValues(10, 20, 30);
        SimulationParameters parameters = new SimulationParameters(1_000.0, 100.0, 1, 1_000, 42L, 25.0);

        assertThat(calculator.metrics(paths, parameters)).isEqualTo(calculator.metrics(paths, parameters));
    }

    @Test
    void metrics_doesNotModifyInput() {
        double[][] paths = { {100, 300}, {100, 100}, {100, 200} };
        double[][] copy = { {100, 300}, {100, 100}, {100, 200} };
        SimulationParameters parameters = new SimulationParameters(100.0, 0.0, 1, 1_000, 1L);

        calculator.metrics(paths, parameters);

        assertThat(paths).isDeepEqualTo(copy);
    }

    @Test
    void metrics_nullOrEmptyInput_throwsException() {
        SimulationParameters parameters = new SimulationParameters(1_000.0, 100.0, 1, 1_000, 42L);

        assertThatThrownBy(() -> calculator.metrics(null, parameters))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.metrics(new double[0][0], parameters))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.metrics(new double[][] { {1} }, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}