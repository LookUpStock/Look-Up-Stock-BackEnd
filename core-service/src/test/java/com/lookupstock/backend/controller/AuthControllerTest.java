package com.lookupstock.backend.controller;

import com.lookupstock.backend.model.UserRole;
import com.lookupstock.backend.dto.LoginResponse;
import com.lookupstock.backend.exception.ConflictException;
import com.lookupstock.backend.exception.GlobalExceptionHandler;
import com.lookupstock.backend.exception.Messages;
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
                .thenThrow(new ConflictException(Messages.EMAIL_TAKEN));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"anna@example.com\",\"password\":\"geheim123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"))
                .andExpect(jsonPath("$.error.message").value(Messages.EMAIL_TAKEN));
    }

    @Test
    void login_liefert200MitToken() throws Exception {
        when(authService.login("anna@example.com", "geheim123"))
                .thenReturn(new LoginResponse("abc", Instant.parse("2026-10-14T12:00:00Z"), UserRole.USER));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"anna@example.com\",\"password\":\"geheim123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("abc"))
                .andExpect(jsonPath("$.expiresAt").value("2026-10-14T12:00:00Z"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void login_falscheDaten_liefert401() throws Exception {
        when(authService.login(any(), any()))
                .thenThrow(new UnauthorizedException(Messages.INVALID_CREDENTIALS));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"anna@example.com\",\"password\":\"falsch\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").value(Messages.INVALID_CREDENTIALS));
    }

    @Test
    void logout_liefert204() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer abc"))
                .andExpect(status().isNoContent());

        verify(authService).logout("abc");
    }

    @Test
    void logout_ohneHeader_liefert204() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent());

        verify(authService).logout(null);
    }
}