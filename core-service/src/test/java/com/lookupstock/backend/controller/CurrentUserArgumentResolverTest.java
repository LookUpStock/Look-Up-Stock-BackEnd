package com.lookupstock.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.lookupstock.backend.exception.GlobalExceptionHandler;
import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.model.User;
import com.lookupstock.backend.service.AuthService;
import com.lookupstock.backend.service.PortfolioService;

/** BE #91: Token kommt im Header „Authorization: Bearer &lt;token&gt;“; geprüft über GET /api/v1/portfolios. */
@WebMvcTest(PortfolioController.class)
@Import(GlobalExceptionHandler.class)
class CurrentUserArgumentResolverTest {

    private static final User OWNER = new User("anna@example.com", "hash");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PortfolioService portfolioService;

    @MockitoBean
    private AuthService authService;

    @Test
    void ohneHeaderLiefert401OhneAuthService() throws Exception {
        mockMvc.perform(get("/api/v1/portfolios"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").value(Messages.NOT_LOGGED_IN));

        verifyNoInteractions(authService, portfolioService);
    }

    @Test
    void falschesSchemaLiefert401() throws Exception {
        mockMvc.perform(get("/api/v1/portfolios").header("Authorization", "Basic abc"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(authService, portfolioService);
    }

    @Test
    void leeresTokenLiefert401() throws Exception {
        mockMvc.perform(get("/api/v1/portfolios").header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(authService, portfolioService);
    }

    @Test
    void kleingeschriebenesBearerIstGueltig() throws Exception {
        when(authService.authenticate("gueltig")).thenReturn(OWNER);
        when(portfolioService.findAll(OWNER)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/portfolios").header("Authorization", "bearer gueltig"))
                .andExpect(status().isOk());

        verify(portfolioService).findAll(OWNER);
    }

    @Test
    void authServiceOhneNutzerLiefert401() throws Exception {
        // authenticate liefert normalerweise einen Nutzer oder wirft; null wird trotzdem abgefangen
        mockMvc.perform(get("/api/v1/portfolios").header("Authorization", "Bearer unbekannt"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(portfolioService);
    }

    @Test
    void extractTokenPrueftSchemaUndTrimmt() {
        assertThat(CurrentUserArgumentResolver.extractToken(null)).isNull();
        assertThat(CurrentUserArgumentResolver.extractToken("Basic abc")).isNull();
        assertThat(CurrentUserArgumentResolver.extractToken("Bearer")).isNull();
        assertThat(CurrentUserArgumentResolver.extractToken("Bearer    ")).isNull();
        assertThat(CurrentUserArgumentResolver.extractToken("Bearerabc")).isNull();
        assertThat(CurrentUserArgumentResolver.extractToken("BEARER  abc ")).isEqualTo("abc");
    }
}
