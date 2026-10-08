package com.lookupstock.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.lookupstock.backend.exception.GlobalExceptionHandler;
import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.NotFoundException;
import com.lookupstock.backend.exception.ValidationException;
import com.lookupstock.backend.model.Asset;
import com.lookupstock.backend.model.Portfolio;
import com.lookupstock.backend.service.PortfolioService;

@WebMvcTest(PortfolioController.class)
@Import(GlobalExceptionHandler.class)
class PortfolioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PortfolioService portfolioService;

    @Test
    void anlegenLiefert201() throws Exception {
        Portfolio p = new Portfolio("Mein Weltportfolio");
        p.addPosition(new Asset("EUNL", null, "MSCI World", "XETRA", "EUR", "ETF", "XETR"), new BigDecimal("100"));
        when(portfolioService.create(any())).thenReturn(p);

        mockMvc.perform(post("/api/v1/portfolios").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mein Weltportfolio\",\"positions\":[{\"assetId\":1,\"weight\":100}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Mein Weltportfolio"));
    }

    @Test
    void pruefFehlerLiefert400() throws Exception {
        when(portfolioService.create(any())).thenThrow(new ValidationException(
                Messages.WEIGHT_SUM,
                List.of(new ValidationException.FieldError("positions", Messages.WEIGHT_SUM),
                        new ValidationException.FieldError("positions", Messages.weightSumDetail(new BigDecimal("95"))))));

        mockMvc.perform(post("/api/v1/portfolios").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value(Messages.WEIGHT_SUM))
                .andExpect(jsonPath("$.error.details[0].field").value("positions"))
                .andExpect(jsonPath("$.error.details[1].message").value(Messages.weightSumDetail(new BigDecimal("95"))));
    }

    @Test
    void unbekannteIdLiefert404() throws Exception {
        when(portfolioService.findById(9999L))
                .thenThrow(new NotFoundException(Messages.PORTFOLIO_NOT_FOUND));

        mockMvc.perform(post("/api/v1/portfolios/9999/details"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value(Messages.PORTFOLIO_NOT_FOUND));
    }

    @Test
    void kaputtesJsonLiefert400() throws Exception {
        mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"positions\":[{\"assetId\":1,\"weight\":\"abc\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void listeLiefert200() throws Exception {
        Portfolio p = new Portfolio("Mein Weltportfolio");
        p.addPosition(new Asset("EUNL", null, "MSCI World", "XETRA", "EUR", "ETF", "XETR"), new BigDecimal("100"));
        when(portfolioService.findAll()).thenReturn(List.of(p));

        mockMvc.perform(post("/api/v1/portfolios/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolios[0].name").value("Mein Weltportfolio"))
                .andExpect(jsonPath("$.portfolios[0].positions[0].symbol").value("EUNL"));
    }
}
