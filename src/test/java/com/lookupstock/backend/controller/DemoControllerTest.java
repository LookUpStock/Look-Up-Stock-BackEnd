package com.lookupstock.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.lookupstock.backend.exception.GlobalExceptionHandler;
import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.NotFoundException;
import com.lookupstock.backend.exception.UpstreamUnavailableException;
import com.lookupstock.backend.model.Asset;
import com.lookupstock.backend.model.DemoPortfolio;
import com.lookupstock.backend.model.Estimate;
import com.lookupstock.backend.model.EstimationResult;
import com.lookupstock.backend.model.Metrics;
import com.lookupstock.backend.model.SimulationParameters;
import com.lookupstock.backend.model.SimulationResult;
import com.lookupstock.backend.model.TimelinePoint;
import com.lookupstock.backend.service.DemoService;

/** US-09: öffentliche Endpunkte des Gastmodus, ohne Authorization-Header. */
@WebMvcTest(DemoController.class)
@Import(GlobalExceptionHandler.class)
class DemoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DemoService demoService;

    private static String body(String seed) {
        return """
                {"portfolioId":900,"parameters":{"startCapital":10000,"monthlyContribution":200,\
                "horizonYears":20,"paths":10000,"seed":%s}}""".formatted(seed);
    }

    @Test
    void beispielportfoliosOhneHeader() throws Exception {
        when(demoService.portfolios()).thenReturn(DemoPortfolio.ALL);

        mockMvc.perform(get("/api/v1/demo/portfolios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolios[0].id").value(900))
                .andExpect(jsonPath("$.portfolios[0].positions[0].symbol").value("EUNL"))
                .andExpect(jsonPath("$.portfolios[0].positions[0].weight").value(100))
                .andExpect(jsonPath("$.portfolios[1].positions.length()").value(2));
    }

    @Test
    void beispielsimulationOhneHeaderLiefert200OhneId() throws Exception {
        Asset asset = new Asset("EUNL", null, "iShares Core MSCI World", "XETRA", "EUR", "ETF", "XETR");
        ReflectionTestUtils.setField(asset, "id", 1L);
        SimulationParameters params = new SimulationParameters(10_000.0, 200.0, 20, 10_000, 42L);
        SimulationResult result = new SimulationResult(900L, params,
                new EstimationResult(LocalDate.of(2016, 10, 1), LocalDate.of(2026, 9, 1),
                        List.of(new Estimate(asset, 0.07, 0.15))),
                List.of(new TimelinePoint(0, 10_000, 10_000, 10_000)),
                new Metrics(58_000, 10_250, 9_800, 10_700, 0, 0.3, null),
                12);
        when(demoService.simulate(eq(900L), eq(params))).thenReturn(result);

        mockMvc.perform(post("/api/v1/demo/simulations").contentType(MediaType.APPLICATION_JSON).content(body("42")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolioId").value(900))
                .andExpect(jsonPath("$.id").isEmpty())
                .andExpect(jsonPath("$.parameters.seed").value(42))
                .andExpect(jsonPath("$.runtimeMs").value(12));
    }

    @Test
    void unbekanntesBeispielportfolioLiefert404() throws Exception {
        when(demoService.simulate(any(), any()))
                .thenThrow(new NotFoundException(Messages.DEMO_PORTFOLIO_NOT_FOUND));

        mockMvc.perform(post("/api/v1/demo/simulations").contentType(MediaType.APPLICATION_JSON).content(body("42")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value(Messages.DEMO_PORTFOLIO_NOT_FOUND));
    }

    @Test
    void kommazahlBeimSeedLiefert400() throws Exception {
        mockMvc.perform(post("/api/v1/demo/simulations").contentType(MediaType.APPLICATION_JSON).content(body("4.2")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value(Messages.SEED_INVALID));
        verifyNoInteractions(demoService);
    }

    @Test
    void kursdatenNichtVerfuegbarLiefert503() throws Exception {
        when(demoService.simulate(any(), any()))
                .thenThrow(new UpstreamUnavailableException(Messages.UPSTREAM_UNAVAILABLE, null));

        mockMvc.perform(post("/api/v1/demo/simulations").contentType(MediaType.APPLICATION_JSON).content(body("42")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("UPSTREAM_UNAVAILABLE"));
    }
}
