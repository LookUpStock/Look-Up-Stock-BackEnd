package com.lookupstock.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.lookupstock.backend.client.PricePoint;
import com.lookupstock.backend.exception.InsufficientDataException;
import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.model.Asset;
import com.lookupstock.backend.model.EstimationResult;

class ParameterEstimatorTest {

    private PriceHistoryService priceHistoryService;
    private ParameterEstimator estimator;

    @BeforeEach
    void setUp() {
        priceHistoryService = mock(PriceHistoryService.class);
        estimator = new ParameterEstimator(priceHistoryService);
    }

    private static Asset asset(long id, String name) {
        Asset a = new Asset("S" + id, null, name, "XETRA", "EUR", "ETF", "XETR");
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    private static List<PricePoint> growth(LocalDate start, int count, double factor) {
        List<PricePoint> prices = new ArrayList<>();
        double price = 100.0;
        for (int i = 0; i < count; i++) {
            prices.add(new PricePoint(start.plusMonths(i), BigDecimal.valueOf(price)));
            price *= factor;
        }
        return prices;
    }

    @Test
    void konstantesWachstumErgibtZwoelfProzentOhneVolatilitaet() {
        Asset a = asset(1, "MSCI World");
        when(priceHistoryService.getMonthlyPrices(a)).thenReturn(growth(LocalDate.of(2020, 1, 1), 60, 1.01));

        EstimationResult result = estimator.estimate(List.of(a));

        assertThat(result.estimates()).hasSize(1);
        assertThat(result.estimates().getFirst().annualReturn()).isCloseTo(0.12, within(1e-9));
        assertThat(result.estimates().getFirst().annualVolatility()).isCloseTo(0.0, within(1e-9));
        assertThat(result.historyFrom()).isEqualTo(LocalDate.of(2020, 1, 1));
        assertThat(result.historyTo()).isEqualTo(LocalDate.of(2024, 12, 1));
    }

    @Test
    void volatilitaetAusAbwechselndenRenditen() {
        Asset a = asset(1, "A");
        List<PricePoint> prices = new ArrayList<>();
        double price = 100.0;
        for (int i = 0; i < 37; i++) {
            prices.add(new PricePoint(LocalDate.of(2020, 1, 1).plusMonths(i), BigDecimal.valueOf(price)));
            price *= (i % 2 == 0) ? 1.1 : 0.9;
        }
        when(priceHistoryService.getMonthlyPrices(a)).thenReturn(prices);

        var estimate = estimator.estimate(List.of(a)).estimates().getFirst();

        assertThat(estimate.annualReturn()).isCloseTo(0.0, within(1e-9));
        assertThat(estimate.annualVolatility()).isCloseTo(0.1 * Math.sqrt(36.0 / 35.0) * Math.sqrt(12), within(1e-9));
    }

    @Test
    void genau36MonatswerteReichen() {
        Asset a = asset(1, "A");
        when(priceHistoryService.getMonthlyPrices(a)).thenReturn(growth(LocalDate.of(2020, 1, 1), 36, 1.01));

        assertThat(estimator.estimate(List.of(a)).estimates()).hasSize(1);
    }

    @Test
    void fuenfunddreissigMonatswerteLiefern422() {
        Asset a = asset(1, "Neuer ETF");
        when(priceHistoryService.getMonthlyPrices(a)).thenReturn(growth(LocalDate.of(2020, 1, 1), 35, 1.01));

        assertThatThrownBy(() -> estimator.estimate(List.of(a)))
                .isInstanceOf(InsufficientDataException.class)
                .hasMessageContaining("Neuer ETF");
    }

    @Test
    void alleBetroffenenAnlagenWerdenGenannt() {
        Asset lang = asset(1, "Lang");
        Asset kurz1 = asset(2, "Kurz 1");
        Asset kurz2 = asset(3, "Kurz 2");
        when(priceHistoryService.getMonthlyPrices(lang)).thenReturn(growth(LocalDate.of(2015, 1, 1), 120, 1.01));
        when(priceHistoryService.getMonthlyPrices(kurz1)).thenReturn(growth(LocalDate.of(2024, 1, 1), 20, 1.01));
        when(priceHistoryService.getMonthlyPrices(kurz2)).thenReturn(List.of());

        assertThatThrownBy(() -> estimator.estimate(List.of(lang, kurz1, kurz2)))
                .isInstanceOfSatisfying(InsufficientDataException.class, e ->
                        assertThat(e.getAssets()).extracting(InsufficientDataException.AffectedAsset::assetId)
                                .containsExactlyInAnyOrder(2L, 3L));
    }

    @Test
    void ohneKurseWirdNichtAlsZuKurzGemeldet() {
        Asset eunl = asset(1, "iShares Core MSCI World");
        when(priceHistoryService.getMonthlyPrices(eunl)).thenReturn(List.of());

        assertThatThrownBy(() -> estimator.estimate(List.of(eunl)))
                .isInstanceOfSatisfying(InsufficientDataException.class, e -> {
                    assertThat(e.getMessage()).contains("keine Kurse").doesNotContain("3 Jahre");
                    assertThat(e.getAssets()).extracting(InsufficientDataException.AffectedAsset::detail)
                            .containsExactly(Messages.NO_PRICE_DATA_DETAIL);
                });
    }

    @Test
    void ohneKurseUndZuKurzWerdenGetrenntGenannt() {
        Asset ohne = asset(1, "Ohne Kurse");
        Asset kurz = asset(2, "Kurz");
        when(priceHistoryService.getMonthlyPrices(ohne)).thenReturn(List.of());
        when(priceHistoryService.getMonthlyPrices(kurz)).thenReturn(growth(LocalDate.of(2024, 1, 1), 20, 1.01));

        assertThatThrownBy(() -> estimator.estimate(List.of(ohne, kurz)))
                .isInstanceOfSatisfying(InsufficientDataException.class, e -> {
                    assertThat(e.getMessage()).contains("keine Kurse: Ohne Kurse").contains("3 Jahre Kursdaten vor: Kurz");
                    assertThat(e.getAssets()).extracting(InsufficientDataException.AffectedAsset::detail)
                            .containsExactly(Messages.NO_PRICE_DATA_DETAIL, Messages.INSUFFICIENT_DATA_DETAIL);
                });
    }

    @Test
    void gemeinsamerSchaetzzeitraum() {
        Asset a = asset(1, "A");
        Asset b = asset(2, "B");
        when(priceHistoryService.getMonthlyPrices(a)).thenReturn(growth(LocalDate.of(2015, 1, 1), 120, 1.01));
        when(priceHistoryService.getMonthlyPrices(b)).thenReturn(growth(LocalDate.of(2020, 1, 1), 48, 1.02));

        EstimationResult result = estimator.estimate(List.of(a, b));

        assertThat(result.historyFrom()).isEqualTo(LocalDate.of(2020, 1, 1));
        assertThat(result.historyTo()).isEqualTo(LocalDate.of(2023, 12, 1));
        assertThat(result.estimates()).hasSize(2);
    }

    @Test
    void zuKurzeUeberschneidungNenntAnlageMitSpaetestemBeginn() {
        Asset alt = asset(1, "Alt");
        Asset neu = asset(2, "Neu");
        when(priceHistoryService.getMonthlyPrices(alt)).thenReturn(growth(LocalDate.of(2016, 1, 1), 84, 1.01));
        when(priceHistoryService.getMonthlyPrices(neu)).thenReturn(growth(LocalDate.of(2021, 1, 1), 48, 1.01));

        assertThatThrownBy(() -> estimator.estimate(List.of(alt, neu)))
                .isInstanceOfSatisfying(InsufficientDataException.class, e ->
                        assertThat(e.getAssets()).extracting(InsufficientDataException.AffectedAsset::name)
                                .containsExactly("Neu"));
    }
}
