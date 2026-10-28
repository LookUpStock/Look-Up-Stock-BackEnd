package com.lookupstock.marktdaten.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Ein Schlusskurs; gleiche Felder wie PricePoint im core-service. */
public record PricePoint(LocalDate date, BigDecimal close) {
}
