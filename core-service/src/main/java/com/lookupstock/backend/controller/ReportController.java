package com.lookupstock.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lookupstock.backend.dto.UsageReportResponse;
import com.lookupstock.backend.model.User;
import com.lookupstock.backend.service.UsageReportService;

/**
 * Auswertungen für die Geschäftsführung (US-11, CR-01).
 * Übersetzt nur HTTP; Rollenprüfung und Auswertung liegen im UsageReportService.
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final UsageReportService usageReportService;

    public ReportController(UsageReportService usageReportService) {
        this.usageReportService = usageReportService;
    }

    /** Kennzahlen eines Monats; ohne month der laufende Monat (E38). 401 ohne Anmeldung, 403 ohne Rolle. */
    @GetMapping("/usage")
    public UsageReportResponse usage(@CurrentUser User user,
                                     @RequestParam(required = false) String month) {
        return UsageReportResponse.from(usageReportService.report(month, user));
    }
}
