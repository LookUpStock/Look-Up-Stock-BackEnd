package com.lookupstock.backend.dto;

import java.util.List;

import com.lookupstock.backend.client.PricePoint;

public record AssetPricesResponse(String symbol, String interval, int dataPoints, List<PricePoint> prices) {}
