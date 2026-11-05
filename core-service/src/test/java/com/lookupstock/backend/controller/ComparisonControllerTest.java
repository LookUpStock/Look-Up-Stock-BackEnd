package com.lookupstock.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.lookupstock.backend.exception.GlobalExceptionHandler;
import com.lookupstock.backend.exception.InsufficientDataException;
import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.NotFoundException;
import com.lookupstock.backend.exception.UpstreamUnavailableException;
import com.lookupstock.backend.exception.ValidationException;
import com.lookupstock.backend.model.Asset;
import com.lookupstock.backend.model.ComparisonEntry;
import com.lookupstock.backend.model.ComparisonResult;
import com.lookupstock.backend.model.Estimate;
import com.lookupstock.backend.model.EstimationResult;
import com.lookupstock.backend.model.Metrics;
import com.lookupstock.backend.model.SimulationParameters;
import com.lookupstock.backend.model.TimelinePoint;
import com.lookupstock.backend.model.User;
import com.lookupstock.backend.service.AuthService;
import com.lookupstock.backend.service.ComparisonService;

@WebMvcTest(ComparisonController.class)
@Import(GlobalExceptionHandler.class)
class ComparisonControllerTest {

    private static final User OWNER = new User("anna@example.com", "hash");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ComparisonService comparisonService;

    @MockitoBean
    private AuthService authService;

    private Asset asset;

    @BeforeEach
    void setUp() {
        asset = new Asset("EUNL", null, "MSCI World", "XETRA", "EUR", "ETF", "XETR");
        ReflectionTestUtils.setField(asset, "id", 31L);
        when(authService.authenticate("gueltig")).thenReturn(OWNER);
    }

    private static String body(String seed) {
        return """
                {"items":[{"label":"Mein Weltportfolio","portfolioId":5},{"assetId":31}],\
                "parameters":{"startCapital":10000,"monthlyContribution":200,"horizonYears":20,"paths":10000,\
                "seed":%s},"save":false,"name":null}""".formatted(seed);
    }

    @Test
    void vergleichLiefert200MitBeidenErgebnissen() throws Exception {
        SimulationParameters params = new SimulationParameters(10_000.0, 200.0, 20, 10_000, 42L);
        List<ComparisonEntry> entries = List.of(
                new ComparisonEntry("Mein Weltportfolio", 5L, null),
                new ComparisonEntry(null, null, 31L));
        Metrics metrics = new Metrics(58_000, 112_340.55, 61_200.1, 205_880.9, 0, 0.31, null);
        ComparisonResult result = new ComparisonResult(params,
                new EstimationResult(LocalDate.of(2016, 10, 1), LocalDate.of(2026, 9, 1),
                        List.of(new Estimate(asset, 0.07, 0.15))),
                List.of(new ComparisonResult.Item("Mein Weltportfolio", 5L, null,
                                List.of(new TimelinePoint(0, 10_000, 10_000, 10_000)), metrics),
                        new ComparisonResult.Item("MSCI World", null, 31L,
                                List.of(new TimelinePoint(0, 10_000, 10_000, 10_000)), metrics)),
                List.of(new ComparisonResult.Pairwise(0, 1, 0.46)),
                1620, Instant.parse("2026-10-20T09:00:00Z"));
        when(comparisonService.compare(eq(entries), eq(params), eq(OWNER))).thenReturn(result);

        mockMvc.perform(post("/api/v1/comparisons").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer gueltig").content(body("42")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.parameters.seed").value(42))
                .andExpect(jsonPath("$.estimates.historyFrom").value("2016-10-01"))
                .andExpect(jsonPath("$.results[0].label").value("Mein Weltportfolio"))
                .andExpect(jsonPath("$.results[0].portfolioId").value(5))
                .andExpect(jsonPath("$.results[0].assetId").doesNotExist())
                .andExpect(jsonPath("$.results[1].assetId").value(31))
                .andExpect(jsonPath("$.results[1].portfolioId").doesNotExist())
                .andExpect(jsonPath("$.results[1].metrics.medianEndValue").value(112_340.55))
                .andExpect(jsonPath("$.pairwise[0].a").value(0))
                .andExpect(jsonPath("$.pairwise[0].b").value(1))
                .andExpect(jsonPath("$.pairwise[0].probabilityAOutperformsB").value(0.46))
                .andExpect(jsonPath("$.runtimeMs").value(1620))
                .andExpect(jsonPath("$.createdAt").value("2026-10-20T09:00:00Z"));
    }

    @Test
    void kommazahlBeimSeedLiefert400WieBeiDerSimulation() throws Exception {
        mockMvc.perform(post("/api/v1/comparisons").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer gueltig").content(body("4.2")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value(Messages.SEED_INVALID));
        verifyNoInteractions(comparisonService);
    }

    @Test
    void falscheAnzahlLiefert400() throws Exception {
        when(comparisonService.compare(any(), any(), any())).thenThrow(new ValidationException(
                Messages.COMPARISON_ITEM_COUNT,
                List.of(new ValidationException.FieldError("items", Messages.itemCountDetail(1)))));

        mockMvc.perform(post("/api/v1/comparisons").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer gueltig").content(body("42")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(Messages.COMPARISON_ITEM_COUNT))
                .andExpect(jsonPath("$.error.details[0].field").value("items"));
    }

    @Test
    void ohneTokenLiefert401() throws Exception {
        mockMvc.perform(post("/api/v1/comparisons").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        verifyNoInteractions(comparisonService);
    }

    @Test
    void fremdesPortfolioLiefert404() throws Exception {
        when(comparisonService.compare(any(), any(), any()))
                .thenThrow(new NotFoundException(Messages.PORTFOLIO_NOT_FOUND));

        mockMvc.perform(post("/api/v1/comparisons").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer gueltig").content(body("42")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void zuWenigKursdatenLiefert422MitAnlage() throws Exception {
        when(comparisonService.compare(any(), any(), any()))
                .thenThrow(new InsufficientDataException(List.of(asset)));

        mockMvc.perform(post("/api/v1/comparisons").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer gueltig").content(body("42")))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_DATA"))
                .andExpect(jsonPath("$.error.details[0].assetId").value(31));
    }

    @Test
    void anbieterAusfallLiefert503() throws Exception {
        when(comparisonService.compare(any(), any(), any()))
                .thenThrow(new UpstreamUnavailableException(Messages.UPSTREAM_UNAVAILABLE, null));

        mockMvc.perform(post("/api/v1/comparisons").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer gueltig").content(body("42")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("UPSTREAM_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.message").value(Messages.UPSTREAM_UNAVAILABLE));
    }
}
