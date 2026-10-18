package com.lookupstock.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.lookupstock.backend.service.SavedSimulationService;
import com.lookupstock.backend.exception.GlobalExceptionHandler;
import com.lookupstock.backend.exception.InsufficientDataException;
import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.UnauthorizedException;
import com.lookupstock.backend.exception.UpstreamUnavailableException;
import com.lookupstock.backend.model.Asset;
import com.lookupstock.backend.model.Estimate;
import com.lookupstock.backend.model.EstimationResult;
import com.lookupstock.backend.model.Metrics;
import com.lookupstock.backend.model.SimulationParameters;
import com.lookupstock.backend.model.SimulationResult;
import com.lookupstock.backend.model.TimelinePoint;
import com.lookupstock.backend.model.User;
import com.lookupstock.backend.service.AuthService;
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
                {"token":"gueltig","portfolioId":5,"parameters":{"startCapital":10000,"monthlyContribution":200,\
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

        mockMvc.perform(post("/api/v1/simulations").contentType(MediaType.APPLICATION_JSON).content(body("20", "42")))
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
        mockMvc.perform(post("/api/v1/simulations").contentType(MediaType.APPLICATION_JSON).content(body("2.5", "42")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value(Messages.HORIZON_INVALID))
                .andExpect(jsonPath("$.error.details[0].field").value("horizonYears"));
        verifyNoInteractions(simulationService);
    }

    @Test
    void kommazahlBeimSeedLiefert400() throws Exception {
        mockMvc.perform(post("/api/v1/simulations").contentType(MediaType.APPLICATION_JSON).content(body("20", "4.2")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(Messages.SEED_INVALID));
        verifyNoInteractions(simulationService);
    }

    @Test
    void ohneTokenLiefert401() throws Exception {
        when(authService.authenticate(null)).thenThrow(new UnauthorizedException(Messages.NOT_LOGGED_IN));

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

        mockMvc.perform(post("/api/v1/simulations").contentType(MediaType.APPLICATION_JSON).content(body("20", "42")))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_DATA"))
                .andExpect(jsonPath("$.error.details[0].assetId").value(1))
                .andExpect(jsonPath("$.error.details[0].name").value("MSCI World"));
    }

    @Test
    void anbieterAusfallLiefert503() throws Exception {
        when(simulationService.simulate(any(), any(), any()))
                .thenThrow(new UpstreamUnavailableException(Messages.UPSTREAM_UNAVAILABLE, null));

        mockMvc.perform(post("/api/v1/simulations").contentType(MediaType.APPLICATION_JSON).content(body("20", "42")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("UPSTREAM_UNAVAILABLE"));
    }
}
