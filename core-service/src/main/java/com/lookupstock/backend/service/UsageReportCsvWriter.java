package com.lookupstock.backend.service;

import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

import com.lookupstock.backend.model.UsageMetric;
import com.lookupstock.backend.model.UsageReport;

// Semicolon separator and UTF-8 with BOM so Excel with German settings detects columns and umlauts.
@Component
public class UsageReportCsvWriter {

    private static final String BOM = "﻿";
    private static final String SEPARATOR = ";";
    private static final String LINE_END = "\r\n";

    public String fileName(UsageReport report) {
        return "lookupstock-nutzung-" + report.month() + ".csv";
    }

    public byte[] write(UsageReport report) {
        StringBuilder csv = new StringBuilder(BOM);
        csv.append("Monat").append(SEPARATOR).append("Kennzahl").append(SEPARATOR).append("Wert").append(LINE_END);
        String month = report.month().toString();
        for (UsageMetric metric : UsageMetric.values()) {
            csv.append(month).append(SEPARATOR)
                    .append(escape(metric.getLabel())).append(SEPARATOR)
                    .append(report.values().getOrDefault(metric, 0L))
                    .append(LINE_END);
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    // Quote fields containing separators, quotes or line breaks (RFC 4180).
    static String escape(String value) {
        if (value.contains(SEPARATOR) || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
