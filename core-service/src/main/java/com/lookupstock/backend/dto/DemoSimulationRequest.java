package com.lookupstock.backend.dto;

public record DemoSimulationRequest(Long portfolioId, SimulationRequest.Parameters parameters) {
}
