package com.lookupstock.backend.dto;

import java.util.List;

public record PortfolioRequest(String name, List<PositionRequest> positions) {}