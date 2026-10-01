package com.lookupstock.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

// KI-Log Nr. XX: Tests für Ping-Endpunkt und CORS mit KI (Claude) erstellt, vom Team geprüft

@WebMvcTest(PingController.class)
class PingControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pingReturnsStatusOk() throws Exception {
        mockMvc.perform(get("/api/v1/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void corsAllowsFrontendOrigin() throws Exception {
        mockMvc.perform(get("/api/v1/ping").header("Origin", "http://127.0.0.1:5500"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5500"));
    }

    @Test
    void corsRejectsForeignOrigin() throws Exception {
        mockMvc.perform(get("/api/v1/ping").header("Origin", "http://boese-seite.example"))
                .andExpect(status().isForbidden());
    }
}