package com.lookupstock.backend.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.lookupstock.backend.exception.GlobalExceptionHandler;
import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.UpstreamUnavailableException;
import com.lookupstock.backend.exception.ValidationException;
import com.lookupstock.backend.client.PricePoint;
import com.lookupstock.backend.model.Asset;
import com.lookupstock.backend.service.AssetService;
import com.lookupstock.backend.service.AssetStatisticsService;

@WebMvcTest(AssetController.class)
@Import(GlobalExceptionHandler.class)
class AssetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AssetService assetService;

    @MockitoBean
    private AssetStatisticsService assetStatisticsService;

    private Asset eunl() {
        return new Asset("EUNL", null, "iShares Core MSCI World", "XETR", "EUR", "ETF", "XETR");
    }

    @Test
    void trefferLiefert200() throws Exception {
        when(assetService.search("EUNL", null)).thenReturn(List.of(eunl()));

        mockMvc.perform(get("/api/v1/assets/search").param("q", "EUNL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("EUNL"))
                .andExpect(jsonPath("$.results[0].symbol").value("EUNL"))
                .andExpect(jsonPath("$.results[0].micCode").value("XETR"));
    }

    @Test
    void keineTrefferLiefertLeereListe() throws Exception {
        when(assetService.search("xyzabc", null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/assets/search").param("q", "xyzabc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isEmpty());
    }

    @Test
    void zuKurzerSuchbegriffLiefert400() throws Exception {
        when(assetService.search("M", null)).thenThrow(new ValidationException(
                Messages.QUERY_TOO_SHORT,
                List.of(new ValidationException.FieldError("q", Messages.QUERY_TOO_SHORT))));

        mockMvc.perform(get("/api/v1/assets/search").param("q", "M"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value(Messages.QUERY_TOO_SHORT))
                .andExpect(jsonPath("$.error.details[0].field").value("q"))
                .andExpect(jsonPath("$.error.details[0].message").value(Messages.QUERY_TOO_SHORT));
    }

    @Test
    void apiAusfallLiefert503() throws Exception {
        when(assetService.search("EUNL", null)).thenThrow(
                new UpstreamUnavailableException(Messages.UPSTREAM_UNAVAILABLE, null));

        mockMvc.perform(get("/api/v1/assets/search").param("q", "EUNL"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("UPSTREAM_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.message").value(Messages.UPSTREAM_UNAVAILABLE));
    }

    @Test
    void limitKeineZahlLiefert400() throws Exception {
        mockMvc.perform(get("/api/v1/assets/search").param("q", "msci").param("limit", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("Ungültiger Wert für limit."));
    }

    @Test
    void historischeKurseLiefert200() throws Exception {
        when(assetService.getHistoricalPrices("AAPL", "1day", 2)).thenReturn(List.of(
                new PricePoint(LocalDate.parse("2024-10-02"), new BigDecimal("225.50")),
                new PricePoint(LocalDate.parse("2024-10-01"), new BigDecimal("224.25"))));

        mockMvc.perform(get("/api/v1/assets/AAPL/prices")
                .param("interval", "1day")
                .param("outputSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.interval").value("1day"))
                .andExpect(jsonPath("$.dataPoints").value(2))
                .andExpect(jsonPath("$.prices[0].date").value("2024-10-02"))
                .andExpect(jsonPath("$.prices[0].close").value(225.50));
    }
}
