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

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final UsageReportService usageReportService;
    private final UsageReportCsvWriter csvWriter;

    public ReportController(UsageReportService usageReportService, UsageReportCsvWriter csvWriter) {
        this.usageReportService = usageReportService;
        this.csvWriter = csvWriter;
    }

    @GetMapping("/usage")
    public UsageReportResponse usage(@CurrentUser User user,
                                     @RequestParam(required = false) String month) {
        return UsageReportResponse.from(usageReportService.report(month, user));
    }

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
