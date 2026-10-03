package com.lookupstock.backend.model;

import java.math.BigDecimal;

public record WeightedAsset(Asset asset, BigDecimal share) {}