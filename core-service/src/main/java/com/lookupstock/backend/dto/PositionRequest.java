package com.lookupstock.backend.dto;

import java.math.BigDecimal;

public record PositionRequest(Long assetId, BigDecimal weight) {}