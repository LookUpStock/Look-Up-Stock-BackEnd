package com.lookupstock.backend.model;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

/**
 * Nutzungskennzahlen eines Monats (US-11). Grundlage für das Dashboard und den CSV-Export (US-12).
 *
 * @param month         ausgewerteter Kalendermonat (Europe/Berlin, E38)
 * @param preliminary   true für den laufenden Monat
 * @param trackingSince Tag des ersten erfassten Ereignisses
 * @param values        alle neun Kennzahlen in der Reihenfolge von UsageMetric
 */
public record UsageReport(YearMonth month, boolean preliminary, LocalDate trackingSince,
                          Map<UsageMetric, Long> values) {
}
