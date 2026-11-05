package com.lookupstock.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.lookupstock.backend.model.UsageMetric;
import com.lookupstock.backend.model.UsageReport;

class UsageReportCsvWriterTest {

    private UsageReportCsvWriter writer;
    private UsageReport report;

    @BeforeEach
    void setUp() {
        writer = new UsageReportCsvWriter();
        Map<UsageMetric, Long> values = new EnumMap<>(UsageMetric.class);
        long value = 1;
        for (UsageMetric metric : UsageMetric.values()) {
            values.put(metric, value++);
        }
        report = new UsageReport(YearMonth.of(2026, 10), false, LocalDate.of(2026, 10, 31), values);
    }

    @Test
    void dateinameEnthaeltDenMonat() {
        assertThat(writer.fileName(report)).isEqualTo("lookupstock-nutzung-2026-10.csv");
    }

    @Test
    void dateiBeginntMitBomUndKopfzeile() {
        byte[] bytes = writer.write(report);

        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);
        assertThat(text(bytes)).startsWith("﻿Monat;Kennzahl;Wert\r\n");
    }

    @Test
    void jeKennzahlEineZeileInFesterReihenfolge() {
        String[] lines = text(writer.write(report)).split("\r\n");

        assertThat(lines).hasSize(10);
        assertThat(lines[1]).isEqualTo("2026-10;Neue Registrierungen;1");
        assertThat(lines[4]).isEqualTo("2026-10;Durchgeführte Simulationen;4");
        assertThat(lines[9]).isEqualTo("2026-10;Nutzerkonten insgesamt am Monatsende;9");
    }

    @Test
    void umlauteBleibenInUtf8Erhalten() {
        String content = new String(writer.write(report), StandardCharsets.UTF_8);

        assertThat(content).contains("Beispielsimulationen (Gäste)").contains("Durchgeführte Vergleiche");
    }

    @Test
    void feldMitSemikolonWirdInAnfuehrungszeichenGesetzt() {
        assertThat(UsageReportCsvWriter.escape("a;b")).isEqualTo("\"a;b\"");
        assertThat(UsageReportCsvWriter.escape("Sag \"Hallo\"")).isEqualTo("\"Sag \"\"Hallo\"\"\"");
        assertThat(UsageReportCsvWriter.escape("Anmeldungen")).isEqualTo("Anmeldungen");
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
