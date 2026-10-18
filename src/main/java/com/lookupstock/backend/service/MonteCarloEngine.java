package com.lookupstock.backend.service;

import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Component;

import com.lookupstock.backend.model.Estimate;
import com.lookupstock.backend.model.EstimationResult;
import com.lookupstock.backend.model.SimulationParameters;
import com.lookupstock.backend.model.WeightedAsset;

/**
 * Erzeugt zufällige Wertverläufe eines Portfolios in Monatsschritten.
 * Die Gewichte werden jeden Monat auf die Zielgewichte zurückgesetzt (Rebalancing).
 * Mit gleichem Seed und gleichen Eingaben entsteht immer dasselbe Ergebnis (NF-08).
 */
@Component
public class MonteCarloEngine {

    private static final int MONTHS_PER_YEAR = 12;

    /**
     * Simuliert alle Pfade.
     *
     * @param weights    Anteile der Anlagen am Portfolio, in fester Reihenfolge
     * @param estimation geschätzte Rendite und Volatilität je Anlage
     * @param parameters geprüfte Eingaben, der Seed muss gesetzt sein
     * @return Portfoliowerte als [Pfad][Monat], Monat 0 ist das Startkapital
     */
    public double[][] simulate(List<WeightedAsset> weights,
                               EstimationResult estimation,
                               SimulationParameters parameters) {

        // ===== A: Eingaben absichern =====
        if (parameters.seed() == null) {
            throw new IllegalArgumentException("Die Engine benötigt einen gesetzten Seed.");
        }

        // ===== B: Werte je Anlage vorbereiten (Jahreswerte in Monatswerte umrechnen) =====
        int assetCount = weights.size();
        double[] shares = new double[assetCount];
        double[] monthlyReturns = new double[assetCount];
        double[] monthlyVolatilities = new double[assetCount];

        for (int i = 0; i < assetCount; i++) {
            WeightedAsset weightedAsset = weights.get(i);
            Estimate estimate = estimation.findByAssetId(weightedAsset.asset().getId());

            shares[i] = weightedAsset.share().doubleValue();
            monthlyReturns[i] = estimate.annualReturn() / MONTHS_PER_YEAR;
            monthlyVolatilities[i] = estimate.annualVolatility() / Math.sqrt(MONTHS_PER_YEAR);
        }

        // ===== C: Parameter in einfache Variablen übernehmen =====
        int pathCount = parameters.paths();
        int months = parameters.horizonMonths();
        double startCapital = parameters.startCapital();
        double contribution = parameters.monthlyContribution();

        // ===== D: Ergebnistabelle und Zufallsgenerator anlegen =====
        double[][] paths = new double[pathCount][months + 1];
        Random random = new Random(parameters.seed());

        // ===== E: Simulation – jeder Pfad, jeder Monat, jede Anlage =====
        for (int p = 0; p < pathCount; p++) {
            double value = startCapital;
            paths[p][0] = value;

            for (int m = 1; m <= months; m++) {
                // Portfoliorendite = gewichtete Summe der Zufallsrenditen aller Anlagen
                double portfolioReturn = 0.0;
                for (int a = 0; a < assetCount; a++) {
                    double assetReturn = monthlyReturns[a]
                            + monthlyVolatilities[a] * random.nextGaussian();
                    portfolioReturn += shares[a] * assetReturn;
                }

                // Wert entwickeln (nie unter 0), danach Sparrate am Monatsende einzahlen
                value = Math.max(0.0, value * (1.0 + portfolioReturn)) + contribution;
                paths[p][m] = value;
            }
        }

        return paths;
    }

    /** Beide Varianten erhalten je Pfad und Monat exakt dieselben Normalverteilungen. */
    public ComparisonPaths compare(List<WeightedAsset> first, List<WeightedAsset> second,
                                   EstimationResult estimation, SimulationParameters parameters) {
        if (parameters.seed() == null) {
            throw new IllegalArgumentException("Die Engine benötigt einen gesetzten Seed.");
        }
        Model firstModel = model(first, estimation);
        Model secondModel = model(second, estimation);
        int pathsCount = parameters.paths();
        int months = parameters.horizonMonths();
        double[][] firstPaths = new double[pathsCount][months + 1];
        double[][] secondPaths = new double[pathsCount][months + 1];
        int maxAssets = Math.max(firstModel.shares.length, secondModel.shares.length);
        double[] normals = new double[maxAssets];

        for (int p = 0; p < pathsCount; p++) {
            double firstValue = parameters.startCapital();
            double secondValue = parameters.startCapital();
            firstPaths[p][0] = firstValue;
            secondPaths[p][0] = secondValue;
            Random random = new Random(pathSeed(parameters.seed(), p));
            for (int m = 1; m <= months; m++) {
                for (int a = 0; a < maxAssets; a++) normals[a] = random.nextGaussian();
                firstValue = Math.max(0.0, firstValue * (1.0 + portfolioReturn(firstModel, normals)))
                        + parameters.monthlyContribution();
                secondValue = Math.max(0.0, secondValue * (1.0 + portfolioReturn(secondModel, normals)))
                        + parameters.monthlyContribution();
                firstPaths[p][m] = firstValue;
                secondPaths[p][m] = secondValue;
            }
        }
        return new ComparisonPaths(firstPaths, secondPaths);
    }

    private static Model model(List<WeightedAsset> weights, EstimationResult estimation) {
        int count = weights.size();
        double[] shares = new double[count];
        double[] means = new double[count];
        double[] volatilities = new double[count];
        for (int i = 0; i < count; i++) {
            WeightedAsset weighted = weights.get(i);
            Estimate estimate = estimation.findByAssetId(weighted.asset().getId());
            shares[i] = weighted.share().doubleValue();
            means[i] = estimate.annualReturn() / MONTHS_PER_YEAR;
            volatilities[i] = estimate.annualVolatility() / Math.sqrt(MONTHS_PER_YEAR);
        }
        return new Model(shares, means, volatilities);
    }

    private static double portfolioReturn(Model model, double[] normals) {
        double result = 0.0;
        for (int a = 0; a < model.shares.length; a++) {
            result += model.shares[a] * (model.means[a] + model.volatilities[a] * normals[a]);
        }
        return result;
    }

    private static long pathSeed(long seed, int path) {
        long value = seed ^ (0x9E3779B97F4A7C15L * (path + 1L));
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    public record ComparisonPaths(double[][] first, double[][] second) {
    }

    private record Model(double[] shares, double[] means, double[] volatilities) {
    }
}
