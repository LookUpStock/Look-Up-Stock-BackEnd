package com.lookupstock.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.lookupstock.backend.dto.SimulationResponse;
import com.lookupstock.backend.dto.StoredSimulationResult;
import com.lookupstock.backend.exception.GlobalExceptionHandler;
import com.lookupstock.backend.exception.InsufficientDataException;
import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.NotFoundException;
import com.lookupstock.backend.exception.UpstreamUnavailableException;
import com.lookupstock.backend.model.Asset;
import com.lookupstock.backend.model.Estimate;
import com.lookupstock.backend.model.EstimationResult;
import com.lookupstock.backend.model.Metrics;
import com.lookupstock.backend.model.Portfolio;
import com.lookupstock.backend.model.SavedSimulation;
import com.lookupstock.backend.model.SimulationParameters;
import com.lookupstock.backend.model.SimulationResult;
import com.lookupstock.backend.model.TimelinePoint;
import com.lookupstock.backend.model.User;
import com.lookupstock.backend.service.AuthService;
import com.lookupstock.backend.service.SavedSimulationService;
import com.lookupstock.backend.service.SimulationService;

@WebMvcTest(SimulationController.class)
@Import(GlobalExceptionHandler.class)
class SimulationControllerTest {

    private static final User OWNER = new User("anna@example.com", "hash");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SimulationService simulationService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private SavedSimulationService savedSimulationService;

    private Asset asset;

    @BeforeEach
    void setUp() {
        asset = new Asset("EUNL", null, "MSCI World", "XETRA", "EUR", "ETF", "XETR");
        ReflectionTestUtils.setField(asset, "id", 1L);
        when(authService.authenticate("gueltig")).thenReturn(OWNER);
    }

    private static String body(String horizon, String seed) {
        return """
                {"portfolioId":5,"parameters":{"startCapital":10000,"monthlyContribution":200,\
                "horizonYears":%s,"paths":10000,"seed":%s}}""".formatted(horizon, seed);
    }

    @Test
    void simulationLiefert200MitErgebnis() throws Exception {
        SimulationParameters params = new SimulationParameters(10_000.0, 200.0, 20, 10_000, 42L);
        SimulationResult result = new SimulationResult(5L, params,
                new EstimationResult(LocalDate.of(2016, 10, 1), LocalDate.of(2026, 9, 1),
                        List.of(new Estimate(asset, 0.07, 0.15))),
                List.of(new TimelinePoint(0, 10_000, 10_000, 10_000), new TimelinePoint(1, 9_800, 10_250, 10_700)),
                new Metrics(58_000, 10_250, 9_800, 10_700, 0, 0.3, null),
                123);
        when(simulationService.simulate(eq(5L), eq(params), eq(OWNER))).thenReturn(result);

        mockMvc.perform(post("/api/v1/simulations").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer gueltig").content(body("20", "42")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolioId").value(5))
                .andExpect(jsonPath("$.parameters.seed").value(42))
                .andExpect(jsonPath("$.parameters.horizonYears").value(20))
                .andExpect(jsonPath("$.estimates.historyFrom").value("2016-10-01"))
                .andExpect(jsonPath("$.estimates.assets[0].assetId").value(1))
                .andExpect(jsonPath("$.estimates.assets[0].annualReturn").value(0.07))
                .andExpect(jsonPath("$.timeline[1].p50").value(10_250.0))
                .andExpect(jsonPath("$.metrics.medianEndValue").value(10_250.0))
                .andExpect(jsonPath("$.metrics.maxDrawdownMedian").value(0.3))
                .andExpect(jsonPath("$.runtimeMs").value(123));
    }

    @Test
    void kommazahlBeimHorizontLiefert400() throws Exception {
        mockMvc.perform(post("/api/v1/simulations").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer gueltig").content(body("2.5", "42")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value(Messages.HORIZON_INVALID))
                .andExpect(jsonPath("$.error.details[0].field").value("horizonYears"));
        verifyNoInteractions(simulationService);
    }

    @Test
    void kommazahlBeimSeedLiefert400() throws Exception {
        mockMvc.perform(post("/api/v1/simulations").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer gueltig").content(body("20", "4.2")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(Messages.SEED_INVALID));
        verifyNoInteractions(simulationService);
    }

    @Test
    void ohneTokenLiefert401() throws Exception {
        mockMvc.perform(post("/api/v1/simulations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"portfolioId\":5}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        verifyNoInteractions(simulationService);
    }

    @Test
    void zuWenigKursdatenLiefert422MitAnlage() throws Exception {
        when(simulationService.simulate(any(), any(), any()))
                .thenThrow(new InsufficientDataException(List.of(asset)));

        mockMvc.perform(post("/api/v1/simulations").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer gueltig").content(body("20", "42")))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_DATA"))
                .andExpect(jsonPath("$.error.details[0].assetId").value(1))
                .andExpect(jsonPath("$.error.details[0].name").value("MSCI World"));
    }

    @Test
    void anbieterAusfallLiefert503() throws Exception {
        when(simulationService.simulate(any(), any(), any()))
                .thenThrow(new UpstreamUnavailableException(Messages.UPSTREAM_UNAVAILABLE, null));

        mockMvc.perform(post("/api/v1/simulations").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer gueltig").content(body("20", "42")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("UPSTREAM_UNAVAILABLE"));
    }

    private SimulationResult sampleResult() {
        SimulationParameters params = new SimulationParameters(10_000.0, 200.0, 20, 10_000, 42L);
        return new SimulationResult(5L, params,
                new EstimationResult(LocalDate.of(2016, 10, 1), LocalDate.of(2026, 9, 1),
                        List.of(new Estimate(asset, 0.07, 0.15))),
                List.of(new TimelinePoint(0, 10_000, 10_000, 10_000), new TimelinePoint(1, 9_800, 10_250, 10_700)),
                new Metrics(58_000, 10_250, 9_800, 10_700, 0, 0.3, null),
                123);
    }

    private SavedSimulation sampleSaved() {
        Portfolio portfolio = new Portfolio("Weltportfolio", OWNER);
        ReflectionTestUtils.setField(portfolio, "id", 5L);
        SavedSimulation saved = new SavedSimulation(OWNER, portfolio, "Mein Plan",
                BigDecimal.valueOf(10_000), BigDecimal.valueOf(200), 20, 10_000, 42L, null, "{}", 123L);
        ReflectionTestUtils.setField(saved, "id", 12L);
        ReflectionTestUtils.setField(saved, "createdAt", Instant.parse("2026-10-18T10:00:00Z"));
        return saved;
    }

    private static String saveBody(String nameJson) {
        return """
                {"portfolioId":5,"parameters":{"startCapital":10000,"monthlyContribution":200,\
                "horizonYears":20,"paths":10000,"seed":42},"save":true,"name":%s}""".formatted(nameJson);
    }

    @Test
    void simulateWithSave_returns201WithIdNameAndCreatedAt() throws Exception {
        SimulationResult result = sampleResult();
        SavedSimulation saved = sampleSaved();
        when(simulationService.simulateForSave(any(), any(), eq(OWNER))).thenReturn(result);
        when(savedSimulationService.save(result, "Mein Plan", OWNER)).thenReturn(saved);
        when(savedSimulationService.readResult(saved))
                .thenReturn(StoredSimulationResult.from(SimulationResponse.from(result)));

        mockMvc.perform(post("/api/v1/simulations").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer gueltig").content(saveBody("\"  Mein Plan  \"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.name").value("Mein Plan"))
                .andExpect(jsonPath("$.portfolioId").value(5))
                .andExpect(jsonPath("$.parameters.seed").value(42))
                .andExpect(jsonPath("$.timeline[1].p50").value(10_250.0))
                .andExpect(jsonPath("$.createdAt").value("2026-10-18T10:00:00Z"));
        verify(simulationService, never()).simulate(any(), any(), any());
    }

    @Test
    void simulateWithoutSave_storesNothing() throws Exception {
        when(simulationService.simulate(any(), any(), eq(OWNER))).thenReturn(sampleResult());

        mockMvc.perform(post("/api/v1/simulations").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer gueltig").content(body("20", "42")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isEmpty())
                .andExpect(jsonPath("$.name").isEmpty());
        verify(savedSimulationService, never()).save(any(), any(), any());
        verify(simulationService, never()).simulateForSave(any(), any(), any());
    }

    @Test
    void simulateWithSaveWithoutName_returns400BeforeCalculating() throws Exception {
        mockMvc.perform(post("/api/v1/simulations").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer gueltig").content(saveBody("null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value(Messages.NAME_INVALID))
                .andExpect(jsonPath("$.error.details[0].field").value("name"));
        verifyNoInteractions(simulationService, savedSimulationService);
    }

    @Test
    void simulateWithSaveAndTooLongName_returns400() throws Exception {
        String tooLong = "\"" + "a".repeat(101) + "\"";

        mockMvc.perform(post("/api/v1/simulations").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer gueltig").content(saveBody(tooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(Messages.NAME_INVALID));
        verifyNoInteractions(simulationService, savedSimulationService);
    }

    @Test
    void list_returnsOwnSimulations() throws Exception {
        when(savedSimulationService.findAll(OWNER)).thenReturn(List.of(sampleSaved()));

        mockMvc.perform(get("/api/v1/simulations").header("Authorization", "Bearer gueltig"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulations[0].id").value(12))
                .andExpect(jsonPath("$.simulations[0].name").value("Mein Plan"))
                .andExpect(jsonPath("$.simulations[0].portfolioId").value(5))
                .andExpect(jsonPath("$.simulations[0].createdAt").value("2026-10-18T10:00:00Z"));
    }

    @Test
    void listWithoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/simulations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        verifyNoInteractions(savedSimulationService);
    }

    @Test
    void details_returnsStoredResultWithoutCalculating() throws Exception {
        SavedSimulation saved = sampleSaved();
        when(savedSimulationService.findById(12L, OWNER)).thenReturn(saved);
        when(savedSimulationService.readResult(saved))
                .thenReturn(StoredSimulationResult.from(SimulationResponse.from(sampleResult())));

        mockMvc.perform(get("/api/v1/simulations/12").header("Authorization", "Bearer gueltig"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.name").value("Mein Plan"))
                .andExpect(jsonPath("$.parameters.startCapital").value(10_000.0))
                .andExpect(jsonPath("$.metrics.medianEndValue").value(10_250.0))
                .andExpect(jsonPath("$.runtimeMs").value(123));
        verifyNoInteractions(simulationService);
    }

    @Test
    void detailsOfForeignSimulation_returns404() throws Exception {
        when(savedSimulationService.findById(12L, OWNER))
                .thenThrow(new NotFoundException(Messages.SIMULATION_NOT_FOUND));

        mockMvc.perform(get("/api/v1/simulations/12").header("Authorization", "Bearer gueltig"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value(Messages.SIMULATION_NOT_FOUND));
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/simulations/12").header("Authorization", "Bearer gueltig"))
                .andExpect(status().isNoContent());
        verify(savedSimulationService).delete(12L, OWNER);
    }

    @Test
    void deleteOfForeignSimulation_returns404() throws Exception {
        doThrow(new NotFoundException(Messages.SIMULATION_NOT_FOUND))
                .when(savedSimulationService).delete(12L, OWNER);

        mockMvc.perform(delete("/api/v1/simulations/12").header("Authorization", "Bearer gueltig"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.message").value(Messages.SIMULATION_NOT_FOUND));
    }
}