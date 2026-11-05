package com.lookupstock.backend.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.lookupstock.backend.controller.PortfolioController;
import com.lookupstock.backend.exception.GlobalExceptionHandler;
import com.lookupstock.backend.model.User;
import com.lookupstock.backend.service.AuthService;
import com.lookupstock.backend.service.PortfolioService;

@WebMvcTest(PortfolioController.class)
@Import({ GlobalExceptionHandler.class, CorsConfig.class })
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5500")
class CorsConfigTest {

    private static final String ORIGIN = "http://localhost:5500";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PortfolioService portfolioService;

    @MockitoBean
    private AuthService authService;

    @Test
    void fehlerantwort405EnthaeltCorsHeader_H7() throws Exception {
        mockMvc.perform(post("/api/v1/portfolios/list").header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void fehlerantwort401EnthaeltCorsHeader() throws Exception {
        mockMvc.perform(get("/api/v1/portfolios").header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN));
    }

    @Test
    void erfolgreicheAntwortEnthaeltCorsHeaderUndFreigegebeneHeader() throws Exception {
        when(authService.authenticate("gueltig")).thenReturn(new User("anna@example.com", "hash"));
        when(portfolioService.findAll(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/portfolios").header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer gueltig"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Content-Disposition"));
    }

    @Test
    void preflightWirdBeantwortet() throws Exception {
        mockMvc.perform(options("/api/v1/portfolios")
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN));
    }

    @Test
    void fremderUrsprungWirdAbgelehnt() throws Exception {
        mockMvc.perform(get("/api/v1/portfolios").header(HttpHeaders.ORIGIN, "https://evil.example.com"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
