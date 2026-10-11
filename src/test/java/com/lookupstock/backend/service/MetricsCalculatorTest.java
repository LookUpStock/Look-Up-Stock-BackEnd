package com.lookupstock.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.lookupstock.backend.model.TimelinePoint;

class MetricsCalculatorTest {

    private final MetricsCalculator calculator = new MetricsCalculator();

    // ---------- Rechenbeispiel mit bekanntem Ergebnis ----------

    @Test
    void percentiles_elevenUnsortedValues_returnsInterpolatedValues() {
        // 11 Pfade mit je einem Monat, absichtlich unsortiert: Werte 0, 10, …, 100
        double[][] paths = {
                {50}, {10}, {100}, {0}, {70}, {30}, {90}, {20}, {80}, {40}, {60}
        };

        List<TimelinePoint> timeline = calculator.percentiles(paths);

        TimelinePoint point = timeline.get(0);
        assertThat(point.p5()).isCloseTo(5.0, within(1e-9));
        assertThat(point.p50()).isCloseTo(50.0, within(1e-9));
        assertThat(point.p95()).isCloseTo(95.0, within(1e-9));
    }

    // ---------- Aufbau der Zeitreihe ----------

    @Test
    void percentiles_returnsOnePointPerMonthStartingAtZero() {
        // 3 Pfade, 4 Spalten = Monat 0 bis 3
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

    // ---------- Eigenschaft, die immer gelten muss (AT-04-03) ----------

    @Test
    void percentiles_randomPaths_p5NotAboveP50NotAboveP95_AT0403() {
        // 1.000 zufällige Pfade über 24 Monate, fester Seed für reproduzierbare Testdaten
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

    // ---------- Eingabe bleibt unverändert ----------

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

    // ---------- Ungültige Eingabe ----------

    @Test
    void percentiles_emptyOrNullInput_throwsException() {
        assertThatThrownBy(() -> calculator.percentiles(new double[0][0]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.percentiles(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}