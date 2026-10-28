package com.lookupstock.backend.client;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PricePoint(LocalDate date, BigDecimal close) {}
