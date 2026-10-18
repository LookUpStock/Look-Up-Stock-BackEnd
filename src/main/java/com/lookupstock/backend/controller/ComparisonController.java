package com.lookupstock.backend.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lookupstock.backend.dto.ComparisonRequest;
import com.lookupstock.backend.dto.ComparisonResponse;
import com.lookupstock.backend.model.SimulationParameters;
import com.lookupstock.backend.model.User;
import com.lookupstock.backend.service.AuthService;
import com.lookupstock.backend.service.ComparisonService;

/**
 * POST /api/v1/comparisons (US-07). Übersetzt nur HTTP; die Koordination liegt im ComparisonService (ADR-05).
 */
@RestController
@RequestMapping("/api/v1/comparisons")
public class ComparisonController {

    private final ComparisonService comparisonService;
    private final AuthService authService;

    public ComparisonController(ComparisonService comparisonService, AuthService authService) {
        this.comparisonService = comparisonService;
        this.authService = authService;
    }

    @PostMapping
    public ComparisonResponse compare(@RequestBody ComparisonRequest request) {
        User user = authService.authenticate(request.token());
        SimulationParameters parameters = request.parameters() == null
                ? null
                : request.parameters().toSimulationParameters();
        return ComparisonResponse.from(comparisonService.compare(request.toEntries(), parameters, user));
    }
}
