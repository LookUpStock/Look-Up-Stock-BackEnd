package com.lookupstock.backend.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import com.lookupstock.backend.model.Metrics;
import com.lookupstock.backend.model.SimulationParameters;
import com.lookupstock.backend.model.TimelinePoint;

/**
 * Wertet simulierte Pfade aus: je Monat das 5., 50. und 95. Perzentil für das
 * Fächerdiagramm (BE #49) und die Kennzahlen zum Ende des Anlagehorizonts (US-05, BE #71).
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

    /**
     * Berechnet die Kennzahlen einer Simulation (US-05).
     *
     * @param paths      Portfoliowerte als [Pfad][Monat], wie von der MonteCarloEngine geliefert
     * @param parameters geprüfte Eingaben; daraus kommen Einzahlungen und Zielbetrag
     * @return Kennzahlen zum Ende des Anlagehorizonts
     * @throws IllegalArgumentException wenn keine Pfade oder keine Parameter übergeben werden
     */
    public Metrics metrics(double[][] paths, SimulationParameters parameters) {

        // ===== A: Eingabe absichern =====
        if (paths == null || paths.length == 0) {
            throw new IllegalArgumentException("Es wurden keine Pfade übergeben.");
        }
        if (parameters == null) {
            throw new IllegalArgumentException("Es wurden keine Parameter übergeben.");
        }

        // ===== B: Summe der Einzahlungen (E19): Startkapital + Monate × Sparrate =====
        double totalContributions = parameters.startCapital()
                + parameters.horizonMonths() * parameters.monthlyContribution();

        // ===== C: Je Pfad Endwert, größten Rückgang und Zielerreichung bestimmen =====
        int pathCount = paths.length;
        int lastMonth = paths[0].length - 1;
        Double targetAmount = parameters.targetAmount();
        double[] endValues = new double[pathCount];
        double[] drawdowns = new double[pathCount];
        int pathsReachingTarget = 0;

        for (int p = 0; p < pathCount; p++) {
            endValues[p] = paths[p][lastMonth];
            drawdowns[p] = maxDrawdown(paths[p]);
            if (targetAmount != null && endValues[p] >= targetAmount) {
                pathsReachingTarget++;
            }
        }

        // ===== D: Sortieren und Perzentile ablesen (gleiche Methode wie im Fächerdiagramm) =====
        Arrays.sort(endValues);
        Arrays.sort(drawdowns);
        double p5EndValue = percentile(endValues, P5);
        double medianEndValue = percentile(endValues, P50);
        double p95EndValue = percentile(endValues, P95);

        // ===== E: Kennzahlen ableiten =====
        // Value at Risk: so viel liegt der ungünstige Verlauf unter den Einzahlungen, nie negativ
        double valueAtRisk95 = Math.max(0.0, totalContributions - p5EndValue);
        double maxDrawdownMedian = percentile(drawdowns, P50);
        // Ohne Zielbetrag entfällt die Wahrscheinlichkeit (null statt 0)
        Double targetProbability = targetAmount == null
                ? null
                : (double) pathsReachingTarget / pathCount;

        return new Metrics(totalContributions, medianEndValue, p5EndValue, p95EndValue,
                valueAtRisk95, maxDrawdownMedian, targetProbability);
    }

    /**
     * Größter Rückgang eines Pfades von einem bisherigen Höchststand, als Anteil.
     * Beispiel: Höchststand 120, danach Tiefstand 90 ergibt 0.25.
     *
     * @param path Portfoliowerte eines Pfades, Monat für Monat
     * @return Wert zwischen 0 (nie gefallen) und 1 (auf 0 gefallen)
     */
    private static double maxDrawdown(double[] path) {
        double peak = path[0];
        double maxDrawdown = 0.0;
        for (double value : path) {
            if (value > peak) {
                peak = value;
            } else if (peak > 0) {
                // Bei Höchststand 0 (Startkapital 0) gibt es noch nichts, das fallen kann
                maxDrawdown = Math.max(maxDrawdown, (peak - value) / peak);
            }
        }
        return maxDrawdown;
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