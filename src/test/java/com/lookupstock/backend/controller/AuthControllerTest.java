package com.lookupstock.backend.controller;

import com.lookupstock.backend.dto.LoginResponse;
import com.lookupstock.backend.exception.ConflictException;
import com.lookupstock.backend.exception.GlobalExceptionHandler;
import com.lookupstock.backend.exception.UnauthorizedException;
import com.lookupstock.backend.model.User;
import com.lookupstock.backend.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void register_liefert201OhnePasswortHash() throws Exception {
        when(authService.register("anna@example.com", "geheim123"))
                .thenReturn(new User("anna@example.com", "HASH"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"anna@example.com\",\"password\":\"geheim123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("anna@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void register_vorhandeneEmail_liefert409() throws Exception {
        when(authService.register(any(), any()))
                .thenThrow(new ConflictException("Diese E-Mail-Adresse ist bereits registriert."));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"anna@example.com\",\"password\":\"geheim123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void login_liefert200MitToken() throws Exception {
        when(authService.login("anna@example.com", "geheim123"))
                .thenReturn(new LoginResponse("abc", Instant.parse("2026-10-14T12:00:00Z")));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"anna@example.com\",\"password\":\"geheim123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("abc"))
                .andExpect(jsonPath("$.expiresAt").value("2026-10-14T12:00:00Z"));
    }

    @Test
    void login_falscheDaten_liefert401() throws Exception {
        when(authService.login(any(), any()))
                .thenThrow(new UnauthorizedException("E-Mail oder Passwort ist falsch."));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"anna@example.com\",\"password\":\"falsch\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void logout_liefert204() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc\"}"))
                .andExpect(status().isNoContent());

        verify(authService).logout("abc");
    }
}