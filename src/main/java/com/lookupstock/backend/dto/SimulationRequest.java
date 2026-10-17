package com.lookupstock.backend.dto;

import java.math.BigDecimal;
import java.util.List;

import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.ValidationException;
import com.lookupstock.backend.model.SimulationParameters;

/**
 * Body von POST /api/v1/simulations.
 * Die Zahlen kommen als BigDecimal an, damit Kommazahlen bei Horizont, Pfaden und Seed
 * abgelehnt werden (AT-04-17, AT-04-31) statt still abgeschnitten zu werden.
 */
public record SimulationRequest(String token, Long portfolioId, Parameters parameters) {

    // targetAmount ist optional (US-05); Kommazahlen sind erlaubt, da es ein Eurobetrag ist
    public record Parameters(BigDecimal startCapital, BigDecimal monthlyContribution,
                             BigDecimal horizonYears, BigDecimal paths, BigDecimal seed,
                             BigDecimal targetAmount) {

        /** Wandelt in SimulationParameters um; Wertebereiche prüft danach SimulationParameters.validate(). */
        public SimulationParameters toSimulationParameters() {
            Integer horizon = wholeNumber(horizonYears, "horizonYears", Messages.HORIZON_INVALID);
            Integer pathCount = wholeNumber(paths, "paths", Messages.PATHS_INVALID);
            Long seedValue = null;
            if (seed != null) {
                try {
                    seedValue = seed.longValueExact();
                } catch (ArithmeticException e) {
                    throw invalid("seed", Messages.SEED_INVALID, seed);
                }
            }
            return new SimulationParameters(
                    startCapital == null ? null : startCapital.doubleValue(),
                    monthlyContribution == null ? null : monthlyContribution.doubleValue(),
                    horizon, pathCount, seedValue,
                    targetAmount == null ? null : targetAmount.doubleValue());
        }

        // null bleibt null (Pflichtfeld-Prüfung in validate()); Kommazahl oder zu groß → 400
        private static Integer wholeNumber(BigDecimal value, String field, String message) {
            if (value == null) {
                return null;
            }
            try {
                return value.intValueExact();
            } catch (ArithmeticException e) {
                throw invalid(field, message, value);
            }
        }

        private static ValidationException invalid(String field, String message, BigDecimal value) {
            return new ValidationException(message, List.of(
                    new ValidationException.FieldError(field, Messages.valueDetail(value.toPlainString()))));
        }
    }
}