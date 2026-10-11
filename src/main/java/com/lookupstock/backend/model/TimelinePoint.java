package com.lookupstock.backend.model;

/**
 * Ein Punkt im Fächerdiagramm: die Perzentile des Portfoliowerts in einem Monat.
 *
 * @param month Monat ab Start, beginnend bei 0
 * @param p5    Portfoliowert, den 95 % der Pfade übertreffen (ungünstiger Verlauf)
 * @param p50   Median des Portfoliowerts (mittlerer Verlauf)
 * @param p95   Portfoliowert, den nur 5 % der Pfade übertreffen (günstiger Verlauf)
 */
public record TimelinePoint(int month, double p5, double p50, double p95) {
}