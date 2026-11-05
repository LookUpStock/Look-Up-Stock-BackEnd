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

    @PostMapping
    public ResponseEntity<SimulationResponse> simulate(@CurrentUser User user,
                                                       @RequestBody SimulationRequest request) {
        // Validate the name before computing so a missing name is reported immediately.
        String name = request.validatedName();
        SimulationParameters parameters = request.parameters() == null
                ? null
                : request.parameters().toSimulationParameters();
        // When saving, only SIMULATION_SAVED is counted; the preceding simulation was already counted.
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
