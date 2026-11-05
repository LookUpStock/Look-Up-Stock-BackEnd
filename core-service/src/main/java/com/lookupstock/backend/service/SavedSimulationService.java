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

    @Transactional
    public SavedSimulation save(SimulationResult result, String name, User owner) {
        Portfolio portfolio = portfolioService.findById(result.portfolioId(), owner);
        SimulationParameters p = result.parameters();

        StoredSimulationResult stored = StoredSimulationResult.from(SimulationResponse.from(result));
        String json = objectMapper.writeValueAsString(stored);

        SavedSimulation simulation = new SavedSimulation(owner, portfolio, name,
                BigDecimal.valueOf(p.startCapital()),
                BigDecimal.valueOf(p.monthlyContribution()),
                p.horizonYears(), p.paths(), p.seed(),
                p.targetAmount() == null ? null : BigDecimal.valueOf(p.targetAmount()),
                json, result.runtimeMs());
        SavedSimulation saved = savedSimulationRepository.save(simulation);
        usageEventRecorder.record(UsageEventType.SIMULATION_SAVED);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<SavedSimulation> findAll(User owner) {
        return savedSimulationRepository.findAllByOwnerOrderByCreatedAtDesc(owner);
    }

    @Transactional(readOnly = true)
    public SavedSimulation findById(Long id, User owner) {
        return savedSimulationRepository.findByIdAndOwner(id, owner)
                .orElseThrow(() -> new NotFoundException(Messages.SIMULATION_NOT_FOUND));
    }

    @Transactional
    public void delete(Long id, User owner) {
        savedSimulationRepository.delete(findById(id, owner));
    }

    public StoredSimulationResult readResult(SavedSimulation simulation) {
        return objectMapper.readValue(simulation.getResult(), StoredSimulationResult.class);
    }
}