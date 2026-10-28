package com.lookupstock.backend.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.ValidationException;

class PortfolioTest {

    private static final User OWNER = new User("anna@example.com", "hash");

    @Test
    void validate_nameLeerOder101Zeichen_liefertNameInvalid() {
        for (String name : new String[] {"", "a".repeat(101)}) {
            Portfolio portfolio = new Portfolio(name, OWNER);
            portfolio.addPosition(asset(1L, "EUNL"), new BigDecimal("100"));

            assertThatThrownBy(portfolio::validate)
                    .isInstanceOfSatisfying(ValidationException.class, e -> {
                        assertThat(e.getMessage()).isEqualTo(Messages.NAME_INVALID);
                        assertThat(e.getErrors()).containsExactly(
                                new ValidationException.FieldError("name", Messages.NAME_INVALID));
                    });
        }
    }

    @Test
    void validate_summeAusserhalbToleranz_nenntSummeImFestenFormat() {
        // Erwartete Texte bewusst als Literale, damit ein geändertes Format auffällt
        assertSumDetail("Summe der Gewichtungen ist 99.98 %.", "49.98", "50");
        assertSumDetail("Summe der Gewichtungen ist 90 %.", "60.00", "30.00");
    }

    @Test
    void validate_anlageDreifach_wirdNurEinmalGemeldet() {
        Portfolio portfolio = new Portfolio("Mein Depot", OWNER);
        Asset eunl = asset(1L, "EUNL");
        portfolio.addPosition(eunl, new BigDecimal("30"));
        portfolio.addPosition(eunl, new BigDecimal("30"));
        portfolio.addPosition(eunl, new BigDecimal("40"));

        assertThatThrownBy(portfolio::validate)
                .isInstanceOfSatisfying(ValidationException.class, e ->
                        assertThat(e.getErrors()).containsExactly(
                                new ValidationException.FieldError("positions", Messages.DUPLICATE_ASSET)));
    }

    private static void assertSumDetail(String expected, String weight1, String weight2) {
        Portfolio portfolio = new Portfolio("Mein Depot", OWNER);
        portfolio.addPosition(asset(1L, "EUNL"), new BigDecimal(weight1));
        portfolio.addPosition(asset(2L, "IS3N"), new BigDecimal(weight2));

        assertThatThrownBy(portfolio::validate)
                .isInstanceOfSatisfying(ValidationException.class, e ->
                        assertThat(e.getErrors()).containsExactly(
                                new ValidationException.FieldError("positions", Messages.WEIGHT_SUM),
                                new ValidationException.FieldError("positions", expected)));
    }

    private static Asset asset(long id, String symbol) {
        Asset asset = new Asset(symbol, null, symbol, "XETRA", "EUR", "ETF", "XETR");
        ReflectionTestUtils.setField(asset, "id", id);
        return asset;
    }
}
