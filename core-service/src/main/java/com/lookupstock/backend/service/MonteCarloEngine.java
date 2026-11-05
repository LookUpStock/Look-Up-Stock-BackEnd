package com.lookupstock.backend.service;

import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Component;

import com.lookupstock.backend.model.Estimate;
import com.lookupstock.backend.model.EstimationResult;
import com.lookupstock.backend.model.SimulationParameters;
import com.lookupstock.backend.model.WeightedAsset;

// Weights are reset to the target weights every month (rebalancing); equal seed and inputs give equal results.
@Component
public class MonteCarloEngine {

    private static final int MONTHS_PER_YEAR = 12;

    public double[][] simulate(List<WeightedAsset> weights,
                               EstimationResult estimation,
                               SimulationParameters parameters) {

        if (parameters.seed() == null) {
            throw new IllegalArgumentException("Die Engine benötigt einen gesetzten Seed.");
        }

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

        int pathCount = parameters.paths();
        int months = parameters.horizonMonths();
        double startCapital = parameters.startCapital();
        double contribution = parameters.monthlyContribution();

        double[][] paths = new double[pathCount][months + 1];
        Random random = new Random(parameters.seed());

        for (int p = 0; p < pathCount; p++) {
            double value = startCapital;
            paths[p][0] = value;

            for (int m = 1; m <= months; m++) {
                double portfolioReturn = 0.0;
                for (int a = 0; a < assetCount; a++) {
                    double assetReturn = monthlyReturns[a]
                            + monthlyVolatilities[a] * random.nextGaussian();
                    portfolioReturn += shares[a] * assetReturn;
                }

                // Apply the return (value never below 0), then add the monthly contribution at month end.
                value = Math.max(0.0, value * (1.0 + portfolioReturn)) + contribution;
                paths[p][m] = value;
            }
        }

        return paths;
    }
}