package com.lookupstock.backend.model;

/**
 * @param totalContributions Summe aus Startkapital und allen Sparraten in Euro (E19)
 * @param medianEndValue     Portfoliowert am Ende im mittleren Verlauf (p50)
 * @param p5EndValue         Portfoliowert am Ende im ungünstigen Verlauf (p5)
 * @param p95EndValue        Portfoliowert am Ende im günstigen Verlauf (p95)
 * @param valueAtRisk95      Verlust gegenüber den Einzahlungen im ungünstigen Verlauf, mindestens 0
 * @param maxDrawdownMedian  Median des größten Rückgangs vom Höchststand je Pfad, als Anteil (0.31 = 31 %)
 * @param targetProbability  Anteil der Pfade, die den Zielbetrag erreichen; null ohne Zielbetrag
 */

public record Metrics(
        double totalContributions,
        double medianEndValue,
        double p5EndValue,
        double p95EndValue,
        double valueAtRisk95,
        double maxDrawdownMedian,
        Double targetProbability) {
}