package com.lookupstock.backend.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import com.lookupstock.backend.model.Metrics;
import com.lookupstock.backend.model.SimulationParameters;
import com.lookupstock.backend.model.TimelinePoint;

@Component
public class MetricsCalculator {

    private static final double P5 = 0.05;
    private static final double P50 = 0.50;
    private static final double P95 = 0.95;

    public List<TimelinePoint> percentiles(double[][] paths) {

        if (paths == null || paths.length == 0) {
            throw new IllegalArgumentException("Es wurden keine Pfade übergeben.");
        }

        int pathCount = paths.length;
        int monthCount = paths[0].length;
        List<TimelinePoint> timeline = new ArrayList<>(monthCount);
        double[] column = new double[pathCount];

        for (int m = 0; m < monthCount; m++) {
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

        return List.copyOf(timeline);
    }

    public Metrics metrics(double[][] paths, SimulationParameters parameters) {

        if (paths == null || paths.length == 0) {
            throw new IllegalArgumentException("Es wurden keine Pfade übergeben.");
        }
        if (parameters == null) {
            throw new IllegalArgumentException("Es wurden keine Parameter übergeben.");
        }

        double totalContributions = parameters.startCapital()
                + parameters.horizonMonths() * parameters.monthlyContribution();

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

        Arrays.sort(endValues);
        Arrays.sort(drawdowns);
        double p5EndValue = percentile(endValues, P5);
        double medianEndValue = percentile(endValues, P50);
        double p95EndValue = percentile(endValues, P95);

        // Value at risk: how far the p5 end value lies below total contributions, never negative.
        double valueAtRisk95 = Math.max(0.0, totalContributions - p5EndValue);
        double maxDrawdownMedian = percentile(drawdowns, P50);
        // Without a target amount the probability is null, not 0.
        Double targetProbability = targetAmount == null
                ? null
                : (double) pathsReachingTarget / pathCount;

        return new Metrics(totalContributions, medianEndValue, p5EndValue, p95EndValue,
                valueAtRisk95, maxDrawdownMedian, targetProbability);
    }

    private static double maxDrawdown(double[] path) {
        double peak = path[0];
        double maxDrawdown = 0.0;
        for (double value : path) {
            if (value > peak) {
                peak = value;
            } else if (peak > 0) {
                // A peak of 0 (no start capital yet) cannot fall.
                maxDrawdown = Math.max(maxDrawdown, (peak - value) / peak);
            }
        }
        return maxDrawdown;
    }

    // Linear interpolation, same as Excel PERCENTILE.INC.
    private static double percentile(double[] sorted, double q) {
        double position = q * (sorted.length - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        double fraction = position - lower;
        return sorted[lower] + fraction * (sorted[upper] - sorted[lower]);
    }
}