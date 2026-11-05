package com.lookupstock.marktdaten.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.lookupstock.marktdaten.exception.UpstreamUnavailableException;
import com.lookupstock.marktdaten.model.PricePoint;
import com.lookupstock.marktdaten.model.SearchHit;
import com.lookupstock.marktdaten.service.PriceService;
import com.lookupstock.marktdaten.service.SearchService;

@WebMvcTest(InternalController.class)
@TestPropertySource(properties = "marktdaten.internal-api-key=test-key")
class InternalControllerTest {

    private static final String KEY = "test-key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchService searchService;

    @MockitoBean
    private PriceService priceService;

    @Test
    void withoutKeyReturns401() throws Exception {
        mockMvc.perform(get("/internal/search").param("q", "msci"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        verifyNoInteractions(searchService);
    }

    @Test
    void wrongKeyReturns401() throws Exception {
        mockMvc.perform(get("/internal/prices").param("symbol", "URTH").header(InternalApiKeyFilter.HEADER, "falsch"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(priceService);
    }

    @Test
    void searchReturnsHits() throws Exception {
        when(searchService.search("msci")).thenReturn(List.of(
                new SearchHit("URTH", "iShares MSCI World ETF", "NYSE", "ARCX", "USD", "ETF")));

        mockMvc.perform(get("/internal/search").param("q", "msci").header(InternalApiKeyFilter.HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("URTH"))
                .andExpect(jsonPath("$[0].micCode").value("ARCX"));
    }

    @Test
    void pricesUseMonthlyDefaultsAndIsoDates() throws Exception {
        when(priceService.getPrices("URTH", "ARCX", "1month", 120)).thenReturn(List.of(
                new PricePoint(LocalDate.of(2026, 8, 1), new BigDecimal("100.123457"))));

        mockMvc.perform(get("/internal/prices").param("symbol", "URTH").param("mic", "ARCX")
                        .header(InternalApiKeyFilter.HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2026-08-01"))
                .andExpect(jsonPath("$[0].close").value(100.123457));
    }

    @Test
    void missingSymbolReturns400() throws Exception {
        mockMvc.perform(get("/internal/prices").header(InternalApiKeyFilter.HEADER, KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void outputSizeOutOfRangeReturns400() throws Exception {
        mockMvc.perform(get("/internal/prices").param("symbol", "URTH").param("outputsize", "0")
                        .header(InternalApiKeyFilter.HEADER, KEY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upstreamUnavailableReturns503() throws Exception {
        when(priceService.getPrices(any(), any(), any(), anyInt()))
                .thenThrow(new UpstreamUnavailableException(null));

        mockMvc.perform(get("/internal/prices").param("symbol", "URTH").header(InternalApiKeyFilter.HEADER, KEY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("UPSTREAM_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.message").value(UpstreamUnavailableException.MESSAGE));
    }
}
