package com.lookupstock.backend.model;

import java.util.ArrayList;
import java.util.List;

import com.lookupstock.backend.exception.ValidationException;
import com.lookupstock.backend.exception.ValidationException.FieldError;

/**
 * Eingaben des Nutzers für eine Monte-Carlo-Simulation.
 * Die Klasse prüft ihre Werte selbst (Information Expert, ADR-03).
 *
 * @param startCapital        einmaliger Anfangsbetrag in Euro
 * @param monthlyContribution monatliche Sparrate in Euro
 * @param horizonYears        Anlagehorizont in ganzen Jahren
 * @param paths               Anzahl der Simulationspfade (Standard 10.000)
 * @param seed                Startwert der Zufallszahlen, darf fehlen
 * @param targetAmount        Zielbetrag in Euro für die Kennzahl targetProbability, darf fehlen (US-05)
 */
public record SimulationParameters(
        Double startCapital,
        Double monthlyContribution,
        Integer horizonYears,
        Integer paths,
        Long seed,
        Double targetAmount) {

    // Grenzwerte laut API-Design
    public static final int DEFAULT_PATHS = 10_000;
    public static final int MIN_PATHS = 1_000;
    public static final int MAX_PATHS = 50_000;
    public static final int MIN_HORIZON_YEARS = 1;
    public static final int MAX_HORIZON_YEARS = 40;

    // Kompakter Konstruktor: setzt den Standardwert, wenn keine Pfadanzahl angegeben ist
    public SimulationParameters {
        if (paths == null) {
            paths = DEFAULT_PATHS;
        }
    }

    // Konstruktor ohne Zielbetrag, damit bestehender Code und Tests unverändert bleiben
    public SimulationParameters(Double startCapital, Double monthlyContribution,
                                Integer horizonYears, Integer paths, Long seed) {
        this(startCapital, monthlyContribution, horizonYears, paths, seed, null);
    }

    /**
     * Prüft alle Eingaben und sammelt dabei sämtliche Fehler.
     *
     * @throws ValidationException wenn mindestens eine Eingabe ungültig ist
     */
    public void validate() {
        List<FieldError> errors = new ArrayList<>();
        String mainMessage = null;

        // 1. Pflichtfelder: vorhanden und nicht negativ
        checkRequiredAmount("startCapital", startCapital, errors);
        checkRequiredAmount("monthlyContribution", monthlyContribution, errors);
        if (horizonYears == null) {
            errors.add(new FieldError("horizonYears", "Das Feld fehlt."));
        }
        if (!errors.isEmpty()) {
            mainMessage = "Bitte alle Pflichtfelder mit Werten ab 0 ausfüllen.";
        }

        // 2. Startkapital und Sparrate dürfen nicht beide 0 sein
        if (startCapital != null && monthlyContribution != null
                && startCapital == 0 && monthlyContribution == 0) {
            errors.add(new FieldError("startCapital", "Wert: 0"));
            errors.add(new FieldError("monthlyContribution", "Wert: 0"));
            if (mainMessage == null) {
                mainMessage = "Startkapital und Sparrate dürfen nicht beide 0 sein.";
            }
        }

        // 3. Anlagehorizont zwischen 1 und 40 Jahren
        if (horizonYears != null
                && (horizonYears < MIN_HORIZON_YEARS || horizonYears > MAX_HORIZON_YEARS)) {
            errors.add(new FieldError("horizonYears", "Wert: " + horizonYears));
            if (mainMessage == null) {
                mainMessage = "Der Anlagehorizont muss eine ganze Zahl von 1 bis 40 Jahren sein.";
            }
        }

        // 4. Pfadanzahl zwischen 1.000 und 50.000
        if (paths < MIN_PATHS || paths > MAX_PATHS) {
            errors.add(new FieldError("paths", "Wert: " + paths));
            if (mainMessage == null) {
                mainMessage = "Die Anzahl der Simulationspfade muss zwischen 1.000 und 50.000 liegen.";
            }
        }

        // 5. Zielbetrag ist optional; wenn angegeben, muss er größer als 0 sein
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

    /**
     * Anzahl der simulierten Monate.
     *
     * @return Anlagehorizont in Monaten
     */
    public int horizonMonths() {
        return horizonYears * 12;
    }

    /**
     * Erzeugt eine Kopie mit dem angegebenen Seed.
     * Wird vom SimulationService genutzt, wenn der Nutzer keinen Seed angegeben hat.
     *
     * @param newSeed der zu verwendende Seed
     * @return neue Parameter mit gesetztem Seed
     */
    public SimulationParameters withSeed(long newSeed) {
        return new SimulationParameters(startCapital, monthlyContribution, horizonYears, paths, newSeed, targetAmount);
    }

    // Hilfsmethode: prüft einen Geldbetrag auf "vorhanden" und "nicht negativ"
    private static void checkRequiredAmount(String field, Double value, List<FieldError> errors) {
        if (value == null) {
            errors.add(new FieldError(field, "Das Feld fehlt."));
        } else if (value < 0) {
            errors.add(new FieldError(field, "Der Wert darf nicht negativ sein."));
        }
    }
}