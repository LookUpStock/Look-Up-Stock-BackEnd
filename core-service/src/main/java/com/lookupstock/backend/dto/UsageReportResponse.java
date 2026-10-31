package com.lookupstock.backend.dto;

import java.time.LocalDate;
import java.util.List;

import com.lookupstock.backend.model.UsageReport;

/**
 * Antwort auf GET /reports/usage (US-11), Aufbau laut API-Design „Auswertungen“.
 */
public record UsageReportResponse(String month, boolean preliminary, LocalDate trackingSince,
                                  List<MetricValue> metrics) {

    /** Eine Kennzahl mit technischem Schlüssel, deutschem Anzeigetext und Wert */
    public record MetricValue(String key, String label, long value) {}

    public static UsageReportResponse from(UsageReport report) {
        List<MetricValue> metrics = report.values().entrySet().stream()
                .map(e -> new MetricValue(e.getKey().getKey(), e.getKey().getLabel(), e.getValue()))
                .toList();
        return new UsageReportResponse(report.month().toString(), report.preliminary(),
                report.trackingSince(), metrics);
    }
}
