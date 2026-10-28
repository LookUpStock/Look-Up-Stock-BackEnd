package com.lookupstock.backend.dto;

import java.math.BigDecimal;

public record AssetStatsResponse(
        String symbol,
        String interval,
        int dataPoints,
        BigDecimal totalReturn,
        BigDecimal annualizedVolatility,
        BigDecimal averageDailyReturn,
        String startDate,
        String endDate) {}
