package com.lookupstock.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lookupstock.backend.dto.SavedSimulationListResponse;
import com.lookupstock.backend.dto.SimulationRequest;
import com.lookupstock.backend.dto.SimulationResponse;
import com.lookupstock.backend.model.SavedSimulation;
import com.lookupstock.backend.model.SimulationParameters;
import com.lookupstock.backend.model.SimulationResult;
import com.lookupstock.backend.model.User;
import com.lookupstock.backend.service.SavedSimulationService;
import com.lookupstock.backend.service.SimulationService;

/**
 * Simulationen berechnen (US-04) sowie speichern, auflisten, abrufen und löschen (US-08).
 * Übersetzt nur HTTP; die Fachlogik liegt im SimulationService und im SavedSimulationService (ADR-02).
 */
@RestController
@RequestMapping("/api/v1/simulations")
public class SimulationController {

    private final SimulationService simulationService;
    private final SavedSimulationService savedSimulationService;

    public SimulationController(SimulationService simulationService,
                                SavedSimulationService savedSimulationService) {
        this.simulationService = simulationService;
        this.savedSimulationService = savedSimulationService;
    }

    /** Rechnet eine Simulation; bei save: true wird sie zusätzlich gespeichert (201 statt 200). */
    @PostMapping
    public ResponseEntity<SimulationResponse> simulate(@CurrentUser User user,
                                                       @RequestBody SimulationRequest request) {
        // Name vor dem Rechnen prüfen, damit ein fehlender Name nicht erst nach der Berechnung auffällt
        String name = request.validatedName();
        SimulationParameters parameters = request.parameters() == null
                ? null
                : request.parameters().toSimulationParameters();
        // Beim Speichern zählt nur SIMULATION_SAVED, die Simulation davor ist schon gezählt (B8, E37)
        SimulationResult result = request.shouldSave()
                ? simulationService.simulateForSave(request.portfolioId(), parameters, user)
                : simulationService.simulate(request.portfolioId(), parameters, user);

        if (!request.shouldSave()) {
            return ResponseEntity.ok(SimulationResponse.from(result));
        }
        SavedSimulation saved = savedSimulationService.save(result, name, user);
        SimulationResponse body = SimulationResponse.from(saved, savedSimulationService.readResult(saved));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping
    public SavedSimulationListResponse list(@CurrentUser User user) {
        return SavedSimulationListResponse.from(savedSimulationService.findAll(user));
    }

    @GetMapping("/{simulationId}")
    public SimulationResponse details(@CurrentUser User user, @PathVariable Long simulationId) {
        SavedSimulation saved = savedSimulationService.findById(simulationId, user);
        return SimulationResponse.from(saved, savedSimulationService.readResult(saved));
    }

    @DeleteMapping("/{simulationId}")
    public ResponseEntity<Void> delete(@CurrentUser User user, @PathVariable Long simulationId) {
        savedSimulationService.delete(simulationId, user);
        return ResponseEntity.noContent().build();
    }
}
