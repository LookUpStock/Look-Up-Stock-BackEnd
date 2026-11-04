package com.lookupstock.backend.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lookupstock.backend.dto.SimulationResponse;
import com.lookupstock.backend.dto.StoredSimulationResult;
import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.NotFoundException;
import com.lookupstock.backend.model.Portfolio;
import com.lookupstock.backend.model.SavedSimulation;
import com.lookupstock.backend.model.SimulationParameters;
import com.lookupstock.backend.model.SimulationResult;
import com.lookupstock.backend.model.UsageEventType;
import com.lookupstock.backend.model.User;
import com.lookupstock.backend.queue.UsageEventRecorder;
import com.lookupstock.backend.repository.SavedSimulationRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * Speichern, Auflisten, Abrufen und Löschen von Simulationen (US-08, BE #68).
 * Gerechnet wird hier nicht: Das Ergebnis kommt fertig vom SimulationService
 * und wird beim Abrufen unverändert aus der Datenbank zurückgegeben.
 * Alle Methoden arbeiten nur mit Simulationen des übergebenen Nutzers (NF-04).
 */
@Service
public class SavedSimulationService {

    private final SavedSimulationRepository savedSimulationRepository;
    private final PortfolioService portfolioService;
    private final ObjectMapper objectMapper;
    private final UsageEventRecorder usageEventRecorder;

    public SavedSimulationService(SavedSimulationRepository savedSimulationRepository,
                                  PortfolioService portfolioService,
                                  ObjectMapper objectMapper,
                                  UsageEventRecorder usageEventRecorder) {
        this.savedSimulationRepository = savedSimulationRepository;
        this.portfolioService = portfolioService;
        this.objectMapper = objectMapper;
        this.usageEventRecorder = usageEventRecorder;
    }

    /** Speichert eine gerade berechnete Simulation unter dem angegebenen Namen. */
    @Transactional
    public SavedSimulation save(SimulationResult result, String name, User owner) {
        // Portfolio wurde beim Rechnen schon geprüft; erneutes Laden liefert die Entity für den Fremdschlüssel
        Portfolio portfolio = portfolioService.findById(result.portfolioId(), owner);
        SimulationParameters p = result.parameters();

        // Gespeichert wird genau das, was auch in der Antwort steht
        StoredSimulationResult stored = StoredSimulationResult.from(SimulationResponse.from(result));
        String json = objectMapper.writeValueAsString(stored);

        SavedSimulation simulation = new SavedSimulation(owner, portfolio, name,
                BigDecimal.valueOf(p.startCapital()),
                BigDecimal.valueOf(p.monthlyContribution()),
                p.horizonYears(), p.paths(), p.seed(),
                p.targetAmount() == null ? null : BigDecimal.valueOf(p.targetAmount()),
                json, result.runtimeMs());
        SavedSimulation saved = savedSimulationRepository.save(simulation);
        // Nur das Speichern zählt; die Simulation davor wurde schon als SIMULATION_RUN erfasst (B8, E37)
        usageEventRecorder.record(UsageEventType.SIMULATION_SAVED);
        return saved;
    }

    /** Alle eigenen gespeicherten Simulationen, neueste zuerst. */
    @Transactional(readOnly = true)
    public List<SavedSimulation> findAll(User owner) {
        return savedSimulationRepository.findAllByOwnerOrderByCreatedAtDesc(owner);
    }

    /** Eine eigene gespeicherte Simulation. Fremde oder unbekannte ID: 404 (NF-04). */
    @Transactional(readOnly = true)
    public SavedSimulation findById(Long id, User owner) {
        return savedSimulationRepository.findByIdAndOwner(id, owner)
                .orElseThrow(() -> new NotFoundException(Messages.SIMULATION_NOT_FOUND));
    }

    /** Löscht eine eigene gespeicherte Simulation. Fremde oder unbekannte ID: 404 (NF-04). */
    @Transactional
    public void delete(Long id, User owner) {
        savedSimulationRepository.delete(findById(id, owner));
    }

    /** Wandelt die JSON-Spalte wieder in das berechnete Ergebnis um. */
    public StoredSimulationResult readResult(SavedSimulation simulation) {
        return objectMapper.readValue(simulation.getResult(), StoredSimulationResult.class);
    }
}