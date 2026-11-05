package com.lookupstock.backend.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.lookupstock.backend.exception.ValidationException;
import com.lookupstock.backend.exception.ValidationException.FieldError;

class SimulationParametersTest {

    @Test
    void validate_validInput_doesNotThrow() {
        SimulationParameters parameters = params(10_000.0, 200.0, 20, 10_000);

        assertThatCode(parameters::validate).doesNotThrowAnyException();
    }

    @Test
    void constructor_withoutPaths_usesDefault10000() {
        SimulationParameters parameters = params(10_000.0, 200.0, 20, null);

        assertThat(parameters.paths()).isEqualTo(10_000);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 40})
    void validate_horizonAtBoundary_isAccepted_AT0410(int horizonYears) {
        SimulationParameters parameters = params(10_000.0, 200.0, horizonYears, 10_000);

        assertThatCode(parameters::validate).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 41})
    void validate_horizonOutsideBoundary_isRejected_AT0410(int horizonYears) {
        ValidationException e = validationErrorOf(params(10_000.0, 200.0, horizonYears, 10_000));

        assertThat(e.getMessage())
                .isEqualTo("Der Anlagehorizont muss eine ganze Zahl von 1 bis 40 Jahren sein.");
        assertThat(e.getErrors()).extracting(FieldError::field).containsExactly("horizonYears");
        assertThat(e.getErrors()).extracting(FieldError::message).containsExactly("Wert: " + horizonYears);
    }

    @Test
    void validate_horizonMissing_isRejected_AT0410() {
        ValidationException e = validationErrorOf(params(10_000.0, 200.0, null, 10_000));

        assertThat(e.getMessage()).isEqualTo("Bitte alle Pflichtfelder mit Werten ab 0 ausfüllen.");
        assertThat(e.getErrors()).extracting(FieldError::field).containsExactly("horizonYears");
        assertThat(e.getErrors()).extracting(FieldError::message).containsExactly("Das Feld fehlt.");
    }

    @ParameterizedTest
    @CsvSource({
            "0, 200",
            "10000, 0"
    })
    void validate_onlyOneAmountZero_isAccepted_AT0411(double startCapital, double contribution) {
        SimulationParameters parameters = params(startCapital, contribution, 20, 10_000);

        assertThatCode(parameters::validate).doesNotThrowAnyException();
    }

    @Test
    void validate_bothAmountsZero_isRejected_AT0411() {
        ValidationException e = validationErrorOf(params(0.0, 0.0, 20, 10_000));

        assertThat(e.getMessage()).isEqualTo("Startkapital und Sparrate dürfen nicht beide 0 sein.");
        assertThat(e.getErrors()).extracting(FieldError::field)
                .containsExactly("startCapital", "monthlyContribution");
    }

    @Test
    void validate_negativeStartCapital_isRejected_AT0411() {
        ValidationException e = validationErrorOf(params(-1.0, 200.0, 20, 10_000));

        assertThat(e.getMessage()).isEqualTo("Bitte alle Pflichtfelder mit Werten ab 0 ausfüllen.");
        assertThat(e.getErrors()).extracting(FieldError::field).containsExactly("startCapital");
        assertThat(e.getErrors()).extracting(FieldError::message)
                .containsExactly("Der Wert darf nicht negativ sein.");
    }

    @Test
    void validate_contributionMissing_isRejected_AT0411() {
        ValidationException e = validationErrorOf(params(10_000.0, null, 20, 10_000));

        assertThat(e.getErrors()).extracting(FieldError::field).containsExactly("monthlyContribution");
        assertThat(e.getErrors()).extracting(FieldError::message).containsExactly("Das Feld fehlt.");
    }

    @ParameterizedTest
    @ValueSource(ints = {1_000, 50_000})
    void validate_pathsAtBoundary_isAccepted_AT0412(int paths) {
        SimulationParameters parameters = params(10_000.0, 200.0, 20, paths);

        assertThatCode(parameters::validate).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {999, 50_001})
    void validate_pathsOutsideBoundary_isRejected_AT0412(int paths) {
        ValidationException e = validationErrorOf(params(10_000.0, 200.0, 20, paths));

        assertThat(e.getMessage())
                .isEqualTo("Die Anzahl der Simulationspfade muss zwischen 1.000 und 50.000 liegen.");
        assertThat(e.getErrors()).extracting(FieldError::field).containsExactly("paths");
    }

    @Test
    void validate_multipleErrors_collectsAllFields() {
        ValidationException e = validationErrorOf(params(-1.0, 200.0, 41, 999));

        assertThat(e.getErrors()).extracting(FieldError::field)
                .containsExactly("startCapital", "horizonYears", "paths");
        assertThat(e.getMessage()).isEqualTo("Bitte alle Pflichtfelder mit Werten ab 0 ausfüllen.");
    }

    @Test
    void horizonMonths_convertsYearsToMonths() {
        assertThat(params(10_000.0, 200.0, 20, 10_000).horizonMonths()).isEqualTo(240);
    }

    @Test
    void withSeed_returnsCopyWithSeed_andKeepsOriginal() {
        SimulationParameters original = new SimulationParameters(10_000.0, 200.0, 20, 10_000, null);

        SimulationParameters withSeed = original.withSeed(42L);

        assertThat(withSeed.seed()).isEqualTo(42L);
        assertThat(withSeed.startCapital()).isEqualTo(10_000.0);
        assertThat(original.seed()).isNull();
    }

    private static SimulationParameters params(Double startCapital, Double contribution,
                                               Integer horizonYears, Integer paths) {
        return new SimulationParameters(startCapital, contribution, horizonYears, paths, 42L);
    }

    private static ValidationException validationErrorOf(SimulationParameters parameters) {
        Throwable thrown = catchThrowable(parameters::validate);
        assertThat(thrown).isInstanceOf(ValidationException.class);
        return (ValidationException) thrown;
    }

    @Test
    void constructor_withoutTarget_targetAmountIsNull() {
        SimulationParameters parameters = new SimulationParameters(10_000.0, 200.0, 20, 10_000, 42L);

        assertThat(parameters.targetAmount()).isNull();
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.0})
    void validate_targetNotPositive_isRejected(double targetAmount) {
        ValidationException e = validationErrorOf(
                new SimulationParameters(10_000.0, 200.0, 20, 10_000, 42L, targetAmount));

        assertThat(e.getMessage()).isEqualTo("Der Zielbetrag muss größer als 0 sein.");
        assertThat(e.getErrors()).extracting(FieldError::field).containsExactly("targetAmount");
    }

    @Test
    void validate_positiveTarget_isAccepted() {
        SimulationParameters parameters = new SimulationParameters(10_000.0, 200.0, 20, 10_000, 42L, 100_000.0);

        assertThatCode(parameters::validate).doesNotThrowAnyException();
    }

    @Test
    void withSeed_keepsTargetAmount() {
        SimulationParameters parameters = new SimulationParameters(10_000.0, 200.0, 20, 10_000, null, 100_000.0);

        assertThat(parameters.withSeed(7L).targetAmount()).isEqualTo(100_000.0);
    }
}