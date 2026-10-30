package com.lookupstock.backend.model;

/**
 * Art eines Nutzungsereignisses (US-11, E37).
 * Erfasst wird nur nach einer erfolgreich abgeschlossenen Aktion (Antwort 2xx).
 * Der Name wird als Text in usage_event.event_type gespeichert.
 */
public enum UsageEventType {

    /** Konto erfolgreich angelegt (POST /auth/register) */
    REGISTRATION,

    /** Erfolgreich angemeldet (POST /auth/login) */
    LOGIN,

    /** Portfolio angelegt (POST /portfolios) */
    PORTFOLIO_CREATED,

    /** Simulation durchgeführt, auch wenn sie gespeichert wird (POST /simulations) */
    SIMULATION_RUN,

    /** Simulation gespeichert, zusätzlich zu SIMULATION_RUN (POST /simulations mit save) */
    SIMULATION_SAVED,

    /** Vergleich durchgeführt (POST /comparisons) */
    COMPARISON_RUN,

    /** Beispielsimulation ohne Anmeldung durchgeführt (POST /demo/simulations) */
    DEMO_SIMULATION_RUN,

    /** Gültige Anlagensuche, auch wenn das Ergebnis aus dem Cache kommt */
    ASSET_SEARCH
}