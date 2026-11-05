package com.lookupstock.marktdaten.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PricePoint(LocalDate date, BigDecimal close) {
}
