package com.lookupstock.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.lookupstock.backend.exception.ForbiddenException;
import com.lookupstock.backend.exception.GlobalExceptionHandler;
import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.ValidationException;
import com.lookupstock.backend.model.UsageMetric;
import com.lookupstock.backend.model.UsageReport;
import com.lookupstock.backend.model.User;
import com.lookupstock.backend.service.AuthService;
import com.lookupstock.backend.service.UsageReportService;

/**
 * Controller-Tests für GET /api/v1/reports/usage (BE #111): 200, 400, 401, 403 und Aufbau laut API-Design.
 */
@WebMvcTest(ReportController.class)
@Import(GlobalExceptionHandler.class)
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UsageReportService usageReportService;

    @MockitoBean
    private AuthService authService;

    private static final User USER = new User("chefin@example.com", "hash");

    @BeforeEach
    void anmelden() {
        when(authService.authenticate("gueltig")).thenReturn(USER);
    }

    @Test
    void mitRolleLiefert200MitNeunKennzahlen() throws Exception {
        Map<UsageMetric, Long> values = new EnumMap<>(UsageMetric.class);
        for (UsageMetric metric : UsageMetric.values()) {
            values.put(metric, 0L);
        }
        values.put(UsageMetric.LOGINS, 27L);
        when(usageReportService.report(eq("2026-10"), eq(USER)))
                .thenReturn(new UsageReport(YearMonth.of(2026, 10), false, LocalDate.of(2026, 10, 31), values));

        mockMvc.perform(get("/api/v1/reports/usage").param("month", "2026-10")
                        .header("Authorization", "Bearer gueltig"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-10"))
                .andExpect(jsonPath("$.preliminary").value(false))
                .andExpect(jsonPath("$.trackingSince").value("2026-10-31"))
                .andExpect(jsonPath("$.metrics.length()").value(9))
                .andExpect(jsonPath("$.metrics[1].key").value("logins"))
                .andExpect(jsonPath("$.metrics[1].label").value("Anmeldungen"))
                .andExpect(jsonPath("$.metrics[1].value").value(27));
    }

    @Test
    void ohneAnmeldungLiefert401() throws Exception {
        mockMvc.perform(get("/api/v1/reports/usage"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        verifyNoInteractions(usageReportService);
    }

    @Test
    void ohneRolleLiefert403() throws Exception {
        when(usageReportService.report(any(), eq(USER))).thenThrow(new ForbiddenException(Messages.FORBIDDEN));

        mockMvc.perform(get("/api/v1/reports/usage").header("Authorization", "Bearer gueltig"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value("Dafür fehlt die Berechtigung."));
    }

    @Test
    void monatOhneAuswertungLiefert400() throws Exception {
        when(usageReportService.report(eq("2026-09"), eq(USER)))
                .thenThrow(new ValidationException(Messages.REPORT_NOT_AVAILABLE, List.of()));

        mockMvc.perform(get("/api/v1/reports/usage").param("month", "2026-09")
                        .header("Authorization", "Bearer gueltig"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("Für diesen Monat liegen keine Auswertungen vor."));
    }
}
