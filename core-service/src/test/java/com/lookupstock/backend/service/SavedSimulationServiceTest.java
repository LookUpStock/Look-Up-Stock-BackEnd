package com.lookupstock.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.lookupstock.backend.dto.SimulationResponse;
import com.lookupstock.backend.dto.StoredSimulationResult;
import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.NotFoundException;
import com.lookupstock.backend.model.Asset;
import com.lookupstock.backend.model.Estimate;
import com.lookupstock.backend.model.EstimationResult;
import com.lookupstock.backend.model.Metrics;
import com.lookupstock.backend.model.Portfolio;
import com.lookupstock.backend.model.SavedSimulation;
import com.lookupstock.backend.model.SimulationParameters;
import com.lookupstock.backend.model.SimulationResult;
import com.lookupstock.backend.model.TimelinePoint;
import com.lookupstock.backend.model.UsageEventType;
import com.lookupstock.backend.model.User;
import com.lookupstock.backend.queue.UsageEventRecorder;
import com.lookupstock.backend.repository.SavedSimulationRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * Unit-Tests für SavedSimulationService (BE #68, US-08). Repository und PortfolioService sind gemockt,
 * der JSON-Mapper ist echt, damit das Speichern und Wiederherstellen des Ergebnisses mitgeprüft wird.
 */
class SavedSimulationServiceTest {

    // Angemeldeter Nutzer, dem die Simulationen gehören
    private static final User OWNER = new User("anna@example.com", "hash");

    // Zweiter Nutzer für NF-04: sieht die Simulationen von OWNER nicht
    private static final User OTHER = new User("ben@example.com", "hash");

    private SavedSimulationRepository repository;
    private PortfolioService portfolioService;
    private UsageEventRecorder usageEventRecorder;
    private SavedSimulationService service;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        repository = mock(SavedSimulationRepository.class);
        portfolioService = mock(PortfolioService.class);
        usageEventRecorder = mock(UsageEventRecorder.class);
        service = new SavedSimulationService(repository, portfolioService, JsonMapper.builder().build(),
                usageEventRecorder);

        portfolio = new Portfolio("Weltportfolio", OWNER);
        ReflectionTestUtils.setField(portfolio, "id", 5L);
        when(portfolioService.findById(5L, OWNER)).thenReturn(portfolio);

        // save() gibt die übergebene Simulation unverändert zurück, wie die echte Datenbank
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // Ergebnis wie vom SimulationService; targetAmount optional
    private static SimulationResult result(Double targetAmount) {
        Asset asset = new Asset("EUNL", null, "MSCI World", "XETRA", "EUR", "ETF", "XETR");
        ReflectionTestUtils.setField(asset, "id", 1L);
        SimulationParameters params = new SimulationParameters(10_000.0, 200.0, 20, 10_000, 42L, targetAmount);
        return new SimulationResult(5L, params,
                new EstimationResult(LocalDate.of(2016, 10, 1), LocalDate.of(2026, 9, 1),
                        List.of(new Estimate(asset, 0.07, 0.15))),
                List.of(new TimelinePoint(0, 10_000, 10_000, 10_000), new TimelinePoint(1, 9_800, 10_250, 10_700)),
                new Metrics(58_000, 10_250, 9_800, 10_700, 0, 0.3, targetAmount == null ? null : 0.6),
                123);
    }

    // ---------- save ----------

    @Test
    void save_storesNameParametersAndOwner() {
        SavedSimulation saved = service.save(result(100_000.0), "Mein Plan", OWNER);

        assertThat(saved.getOwner()).isSameAs(OWNER);
        assertThat(saved.getPortfolio()).isSameAs(portfolio);
        assertThat(saved.getName()).isEqualTo("Mein Plan");
        assertThat(saved.getStartCapital()).isEqualByComparingTo("10000");
        assertThat(saved.getMonthlyContribution()).isEqualByComparingTo("200");
        assertThat(saved.getHorizonYears()).isEqualTo(20);
        assertThat(saved.getPaths()).isEqualTo(10_000);
        assertThat(saved.getSeed()).isEqualTo(42L);
        assertThat(saved.getTargetAmount()).isEqualByComparingTo(BigDecimal.valueOf(100_000));
        assertThat(saved.getRuntimeMs()).isEqualTo(123L);
        verify(repository).save(saved);
        // Erfolgreiche Aktion wird als Nutzungsereignis erfasst (E37)
        verify(usageEventRecorder).record(UsageEventType.SIMULATION_SAVED);
    }

    @Test
    void save_withoutTargetAmount_storesNull() {
        SavedSimulation saved = service.save(result(null), "Ohne Ziel", OWNER);

        assertThat(saved.getTargetAmount()).isNull();
    }

    @Test
    void save_foreignPortfolio_throwsNotFoundAndStoresNothing() {
        when(portfolioService.findById(5L, OTHER)).thenThrow(new NotFoundException(Messages.PORTFOLIO_NOT_FOUND));

        assertThatThrownBy(() -> service.save(result(null), "Fremd", OTHER))
                .isInstanceOf(NotFoundException.class);
        verify(repository, never()).save(any());
        // Abgelehnte oder fehlgeschlagene Aktion zählt nicht (E37)
        verify(usageEventRecorder, never()).record(any());
    }

    // ---------- readResult ----------

    @Test
    void readResult_returnsExactlyTheStoredResult() {
        // Kernanforderung US-08: Abruf ohne Neuberechnung liefert dasselbe Ergebnis wie beim Speichern
        SimulationResult original = result(100_000.0);
        ArgumentCaptor<SavedSimulation> captor = ArgumentCaptor.forClass(SavedSimulation.class);

        service.save(original, "Mein Plan", OWNER);
        verify(repository).save(captor.capture());
        StoredSimulationResult restored = service.readResult(captor.getValue());

        StoredSimulationResult expected = StoredSimulationResult.from(SimulationResponse.from(original));
        assertThat(restored).isEqualTo(expected);
    }

    // ---------- findAll ----------

    @Test
    void findAll_returnsSimulationsOfOwnerFromRepository() {
        SavedSimulation first = service.save(result(null), "A", OWNER);
        SavedSimulation second = service.save(result(null), "B", OWNER);
        when(repository.findAllByOwnerOrderByCreatedAtDesc(OWNER)).thenReturn(List.of(second, first));

        assertThat(service.findAll(OWNER)).containsExactly(second, first);
    }

    // ---------- findById ----------

    @Test
    void findById_existingId_returnsSimulation() {
        SavedSimulation saved = service.save(result(null), "A", OWNER);
        when(repository.findByIdAndOwner(12L, OWNER)).thenReturn(Optional.of(saved));

        assertThat(service.findById(12L, OWNER)).isSameAs(saved);
    }

    @Test
    void findById_unknownOrForeignId_throwsNotFound() {
        // Fremde ID wird wie eine unbekannte behandelt (NF-04)
        when(repository.findByIdAndOwner(12L, OTHER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(12L, OTHER))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(Messages.SIMULATION_NOT_FOUND);
    }

    // ---------- delete ----------

    @Test
    void delete_existingId_deletesSimulation() {
        SavedSimulation saved = service.save(result(null), "A", OWNER);
        when(repository.findByIdAndOwner(12L, OWNER)).thenReturn(Optional.of(saved));

        service.delete(12L, OWNER);

        verify(repository).delete(saved);
    }

    @Test
    void delete_foreignId_throwsNotFoundAndDeletesNothing() {
        when(repository.findByIdAndOwner(12L, OTHER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(12L, OTHER))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(Messages.SIMULATION_NOT_FOUND);
        verify(repository, never()).delete(any());
    }
}