package com.lookupstock.backend.dto;

import java.util.List;

public record PortfolioRequest(String token, String name, List<PositionRequest> positions) {}