package com.lookupstock.backend.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lookupstock.backend.dto.SimulationRequest;
import com.lookupstock.backend.dto.SimulationResponse;
import com.lookupstock.backend.model.SimulationParameters;
import com.lookupstock.backend.model.User;
import com.lookupstock.backend.service.AuthService;
import com.lookupstock.backend.service.SimulationService;

/**
 * POST /api/v1/simulations (US-04). Übersetzt nur HTTP; die Koordination liegt im SimulationService (ADR-02).
 */
@RestController
@RequestMapping("/api/v1/simulations")
public class SimulationController {

    private final SimulationService simulationService;
    private final AuthService authService;

    public SimulationController(SimulationService simulationService, AuthService authService) {
        this.simulationService = simulationService;
        this.authService = authService;
    }

    @PostMapping
    public SimulationResponse simulate(@RequestBody SimulationRequest request) {
        User user = authService.authenticate(request.token());
        SimulationParameters parameters = request.parameters() == null
                ? null
                : request.parameters().toSimulationParameters();
        return SimulationResponse.from(simulationService.simulate(request.portfolioId(), parameters, user));
    }
}
