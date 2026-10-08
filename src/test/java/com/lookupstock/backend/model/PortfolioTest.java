package com.lookupstock.backend.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.ValidationException;

class PortfolioTest {

    @Test
    void validate_nameLeerOder101Zeichen_liefertNameInvalid() {
        for (String name : new String[] {"", "a".repeat(101)}) {
            Portfolio portfolio = new Portfolio(name);
            portfolio.addPosition(asset(1L, "EUNL"), new BigDecimal("100"));

            assertThatThrownBy(portfolio::validate)
                    .isInstanceOfSatisfying(ValidationException.class, e -> {
                        assertThat(e.getMessage()).isEqualTo(Messages.NAME_INVALID);
                        assertThat(e.getErrors()).containsExactly(
                                new ValidationException.FieldError("name", Messages.NAME_INVALID));
                    });
        }
    }

    private static Asset asset(long id, String symbol) {
        Asset asset = new Asset(symbol, null, symbol, "XETRA", "EUR", "ETF", "XETR");
        ReflectionTestUtils.setField(asset, "id", id);
        return asset;
    }
}
