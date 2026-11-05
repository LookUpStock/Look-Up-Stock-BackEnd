package com.lookupstock.backend.model;

import java.util.ArrayList;
import java.util.List;

import com.lookupstock.backend.exception.ValidationException;
import com.lookupstock.backend.exception.ValidationException.FieldError;

public record SimulationParameters(
        Double startCapital,
        Double monthlyContribution,
        Integer horizonYears,
        Integer paths,
        Long seed,
        Double targetAmount) {

    public static final int DEFAULT_PATHS = 10_000;
    public static final int MIN_PATHS = 1_000;
    public static final int MAX_PATHS = 50_000;
    public static final int MIN_HORIZON_YEARS = 1;
    public static final int MAX_HORIZON_YEARS = 40;

    public SimulationParameters {
        if (paths == null) {
            paths = DEFAULT_PATHS;
        }
    }

    public SimulationParameters(Double startCapital, Double monthlyContribution,
                                Integer horizonYears, Integer paths, Long seed) {
        this(startCapital, monthlyContribution, horizonYears, paths, seed, null);
    }

    public void validate() {
        List<FieldError> errors = new ArrayList<>();
        String mainMessage = null;

        checkRequiredAmount("startCapital", startCapital, errors);
        checkRequiredAmount("monthlyContribution", monthlyContribution, errors);
        if (horizonYears == null) {
            errors.add(new FieldError("horizonYears", "Das Feld fehlt."));
        }
        if (!errors.isEmpty()) {
            mainMessage = "Bitte alle Pflichtfelder mit Werten ab 0 ausfüllen.";
        }

        if (startCapital != null && monthlyContribution != null
                && startCapital == 0 && monthlyContribution == 0) {
            errors.add(new FieldError("startCapital", "Wert: 0"));
            errors.add(new FieldError("monthlyContribution", "Wert: 0"));
            if (mainMessage == null) {
                mainMessage = "Startkapital und Sparrate dürfen nicht beide 0 sein.";
            }
        }

        if (horizonYears != null
                && (horizonYears < MIN_HORIZON_YEARS || horizonYears > MAX_HORIZON_YEARS)) {
            errors.add(new FieldError("horizonYears", "Wert: " + horizonYears));
            if (mainMessage == null) {
                mainMessage = "Der Anlagehorizont muss eine ganze Zahl von 1 bis 40 Jahren sein.";
            }
        }

        if (paths < MIN_PATHS || paths > MAX_PATHS) {
            errors.add(new FieldError("paths", "Wert: " + paths));
            if (mainMessage == null) {
                mainMessage = "Die Anzahl der Simulationspfade muss zwischen 1.000 und 50.000 liegen.";
            }
        }

        if (targetAmount != null && targetAmount <= 0) {
            errors.add(new FieldError("targetAmount", "Wert: " + targetAmount));
            if (mainMessage == null) {
                mainMessage = "Der Zielbetrag muss größer als 0 sein.";
            }
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(mainMessage, errors);
        }
    }

    public int horizonMonths() {
        return horizonYears * 12;
    }

    public SimulationParameters withSeed(long newSeed) {
        return new SimulationParameters(startCapital, monthlyContribution, horizonYears, paths, newSeed, targetAmount);
    }

    private static void checkRequiredAmount(String field, Double value, List<FieldError> errors) {
        if (value == null) {
            errors.add(new FieldError(field, "Das Feld fehlt."));
        } else if (value < 0) {
            errors.add(new FieldError(field, "Der Wert darf nicht negativ sein."));
        }
    }
}