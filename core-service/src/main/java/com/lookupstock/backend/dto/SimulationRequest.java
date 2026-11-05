package com.lookupstock.backend.dto;

import java.math.BigDecimal;
import java.util.List;

import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.ValidationException;
import com.lookupstock.backend.model.SimulationParameters;

// BigDecimal so decimal values for horizon, paths and seed are rejected instead of silently truncated.
public record SimulationRequest(Long portfolioId, Parameters parameters,
                                Boolean save, String name) {

    private static final int MAX_NAME_LENGTH = 100;

    public boolean shouldSave() {
        return Boolean.TRUE.equals(save);
    }

    public String validatedName() {
        if (!shouldSave()) {
            return null;
        }
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
            throw new ValidationException(Messages.NAME_INVALID, List.of(
                    new ValidationException.FieldError("name", Messages.NAME_INVALID)));
        }
        return trimmed;
    }

    public record Parameters(BigDecimal startCapital, BigDecimal monthlyContribution,
                             BigDecimal horizonYears, BigDecimal paths, BigDecimal seed,
                             BigDecimal targetAmount) {

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