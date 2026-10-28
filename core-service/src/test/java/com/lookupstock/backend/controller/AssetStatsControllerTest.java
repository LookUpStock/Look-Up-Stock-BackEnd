package com.lookupstock.backend.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.lookupstock.backend.dto.AssetStatsResponse;
import com.lookupstock.backend.exception.GlobalExceptionHandler;
import com.lookupstock.backend.service.AssetService;
import com.lookupstock.backend.service.AssetStatisticsService;

@WebMvcTest(AssetController.class)
@Import(GlobalExceptionHandler.class)
class AssetStatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AssetService assetService;

    @MockitoBean
    private AssetStatisticsService assetStatisticsService;

    @Test
    void statsReturnsOk() throws Exception {
        when(assetStatisticsService.calculate("AAPL", "1day", 30))
                .thenReturn(new AssetStatsResponse(
                        "AAPL",
                        "1day",
                        30,
                        new BigDecimal("0.1245"),
                        new BigDecimal("0.1821"),
                        new BigDecimal("0.0042"),
                        "2024-09-01",
                        "2024-10-01"));

        mockMvc.perform(get("/api/v1/assets/AAPL/stats")
                .param("interval", "1day")
                .param("outputSize", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.interval").value("1day"))
                .andExpect(jsonPath("$.dataPoints").value(30))
                .andExpect(jsonPath("$.totalReturn").value(0.1245))
                .andExpect(jsonPath("$.annualizedVolatility").value(0.1821))
                .andExpect(jsonPath("$.averageDailyReturn").value(0.0042));
    }
}
