package com.lookupstock.backend.dto;

/**
 * Body von POST /api/v1/demo/simulations (US-09).
 * Nutzt SimulationRequest.Parameters, damit Kommazahlen bei Horizont und Seed genauso abgelehnt werden.
 * save und name gibt es bewusst nicht: Beispielsimulationen werden nie gespeichert.
 */
public record DemoSimulationRequest(Long portfolioId, SimulationRequest.Parameters parameters) {
}
