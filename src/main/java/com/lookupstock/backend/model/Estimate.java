package com.lookupstock.backend.model;

/**
 * Geschätzte Jahresrendite und Jahresvolatilität einer Anlage.
 * Die Werte werden aus der Kurshistorie geschätzt und sind die Grundlage der Simulation.
 *
 * @param asset            die Anlage, zu der die Schätzung gehört
 * @param annualReturn     erwartete Rendite pro Jahr als Dezimalzahl (0.078 = 7,8 %)
 * @param annualVolatility Schwankung pro Jahr als Dezimalzahl (0.152 = 15,2 %)
 */
public record Estimate(Asset asset, double annualReturn, double annualVolatility) {
}