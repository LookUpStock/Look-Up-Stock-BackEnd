package com.lookupstock.backend.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lookupstock.backend.dto.UsageReportResponse;
import com.lookupstock.backend.model.UsageReport;
import com.lookupstock.backend.model.User;
import com.lookupstock.backend.service.UsageReportCsvWriter;
import com.lookupstock.backend.service.UsageReportService;

/**
 * Auswertungen für die Geschäftsführung (US-11, US-12, CR-01).
 * Übersetzt nur HTTP; Rollenprüfung und Auswertung liegen im UsageReportService.
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    // text/csv mit Zeichensatz, damit Browser und Excel UTF-8 annehmen
    private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final UsageReportService usageReportService;
    private final UsageReportCsvWriter csvWriter;

    public ReportController(UsageReportService usageReportService, UsageReportCsvWriter csvWriter) {
        this.usageReportService = usageReportService;
        this.csvWriter = csvWriter;
    }

    /** Kennzahlen eines Monats; ohne month der laufende Monat (E38). 401 ohne Anmeldung, 403 ohne Rolle. */
    @GetMapping("/usage")
    public UsageReportResponse usage(@CurrentUser User user,
                                     @RequestParam(required = false) String month) {
        return UsageReportResponse.from(usageReportService.report(month, user));
    }

    /**
     * Dieselben Kennzahlen als CSV-Datei zum Herunterladen (US-12, E41).
     * Fehler (400, 401, 403) kommen wie bei /usage als JSON im Fehlerformat.
     */
    @GetMapping("/usage/export")
    public ResponseEntity<byte[]> export(@CurrentUser User user,
                                         @RequestParam(required = false) String month) {
        UsageReport report = usageReportService.report(month, user);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(csvWriter.fileName(report))
                .build();
        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(csvWriter.write(report));
    }
}
