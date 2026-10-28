package com.lookupstock.backend.dto;

import java.util.List;
public record SearchResponse(String query, List<AssetResponse> results) {}
