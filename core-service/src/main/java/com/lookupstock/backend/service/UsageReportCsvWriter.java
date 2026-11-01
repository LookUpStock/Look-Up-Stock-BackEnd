package com.lookupstock.backend.service;

import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

import com.lookupstock.backend.model.UsageMetric;
import com.lookupstock.backend.model.UsageReport;

/**
 * Erzeugt die CSV-Datei mit den Kennzahlen eines Monats (US-12, E41).
 * Semikolon als Trennzeichen und UTF-8 mit BOM, damit Excel mit deutscher Einstellung
 * Spalten und Umlaute richtig erkennt. Bezeichnungen und Werte wie im Dashboard.
 */
@Component
public class UsageReportCsvWriter {

    // Byte Order Mark: kennzeichnet die Datei für Excel als UTF-8
    private static final String BOM = "﻿";
    private static final String SEPARATOR = ";";
    // Zeilenende wie in Windows bzw. RFC 4180
    private static final String LINE_END = "\r\n";

    /** Dateiname laut E41, z. B. lookupstock-nutzung-2026-10.csv */
    public String fileName(UsageReport report) {
        return "lookupstock-nutzung-" + report.month() + ".csv";
    }

    /** Inhalt der Datei: Kopfzeile Monat;Kennzahl;Wert und je Kennzahl eine Zeile */
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

    // Felder mit Semikolon, Anführungszeichen oder Zeilenumbruch in Anführungszeichen setzen (RFC 4180)
    static String escape(String value) {
        if (value.contains(SEPARATOR) || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
