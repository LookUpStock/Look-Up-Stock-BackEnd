package com.lookupstock.backend.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import com.lookupstock.backend.model.TimelinePoint;
import com.lookupstock.backend.model.SimulationMetrics;

/**
 * Wertet simulierte Pfade aus und berechnet je Monat das 5., 50. und 95. Perzentil
 * für das Fächerdiagramm.
 */
@Component
public class MetricsCalculator {

    private static final double P5 = 0.05;
    private static final double P50 = 0.50;
    private static final double P95 = 0.95;

    /**
     * Berechnet für jeden Monat einen Verlaufspunkt.
     *
     * @param paths Portfoliowerte als [Pfad][Monat], wie von der MonteCarloEngine geliefert
     * @return ein TimelinePoint je Monat, beginnend bei Monat 0
     * @throws IllegalArgumentException wenn keine Pfade übergeben werden
     */
    public List<TimelinePoint> percentiles(double[][] paths) {

        // ===== A: Eingabe absichern =====
        if (paths == null || paths.length == 0) {
            throw new IllegalArgumentException("Es wurden keine Pfade übergeben.");
        }

        // ===== B: Größen bestimmen und Speicher vorbereiten =====
        int pathCount = paths.length;
        int monthCount = paths[0].length;
        List<TimelinePoint> timeline = new ArrayList<>(monthCount);
        double[] column = new double[pathCount];

        // ===== C: Für jeden Monat die Spalte sortieren und Perzentile ablesen =====
        for (int m = 0; m < monthCount; m++) {
            // Werte aller Pfade für Monat m in die Spalte kopieren
            for (int p = 0; p < pathCount; p++) {
                column[p] = paths[p][m];
            }
            Arrays.sort(column);

            timeline.add(new TimelinePoint(
                    m,
                    percentile(column, P5),
                    percentile(column, P50),
                    percentile(column, P95)));
        }

        // ===== D: Unveränderliche Liste zurückgeben =====
        return List.copyOf(timeline);
    }

    /** Berechnet Endwert- und Verlustrisiken direkt aus sämtlichen Simulationspfaden. */
    public SimulationMetrics summarize(double[][] paths, double totalContributions) {
        if (paths == null || paths.length == 0 || paths[0].length == 0) {
            throw new IllegalArgumentException("Es wurden keine Simulationswerte übergeben.");
        }

        double[] endValues = new double[paths.length];
        double[] maxDrawdowns = new double[paths.length];
        for (int i = 0; i < paths.length; i++) {
            double peak = paths[i][0];
            double maxDrawdown = 0.0;
            for (double value : paths[i]) {
                peak = Math.max(peak, value);
                if (peak > 0.0) {
                    maxDrawdown = Math.max(maxDrawdown, (peak - value) / peak);
                }
            }
            endValues[i] = paths[i][paths[i].length - 1];
            maxDrawdowns[i] = maxDrawdown;
        }

        Arrays.sort(endValues);
        Arrays.sort(maxDrawdowns);
        double p5EndValue = percentile(endValues, P5);
        return new SimulationMetrics(
                totalContributions,
                percentile(endValues, P50),
                p5EndValue,
                percentile(endValues, P95),
                Math.max(0.0, totalContributions - p5EndValue),
                percentile(maxDrawdowns, P50),
                null);
    }

    /**
     * Liest ein Perzentil aus sortierten Werten mit linearer Interpolation ab
     * (entspricht QUANTIL.INKL in Excel).
     *
     * @param sorted aufsteigend sortierte Werte
     * @param q      gewünschtes Perzentil als Anteil, z. B. 0.05
     * @return interpolierter Wert an der Position q
     */
    private static double percentile(double[] sorted, double q) {
        double position = q * (sorted.length - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        double fraction = position - lower;
        return sorted[lower] + fraction * (sorted[upper] - sorted[lower]);
    }
}
